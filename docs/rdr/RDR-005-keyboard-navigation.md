---
title: "Keyboard Navigation and Physical Cursor Model"
id: RDR-005
type: Feature
status: closed
accepted_date: 2026-03-09
closed_date: 2026-03-09
close_reason: implemented
priority: P3
author: Hal Hildebrand
reviewed-by: self
created: 2026-03-08
related_issues: []
---

# RDR-005: Keyboard Navigation and Physical Cursor Model

> Revise during planning; lock at implementation.
> If wrong, abandon code and iterate RDR.

## Problem Statement

Keyboard navigation in Kramer is dead code. `FocusController.TRAVERSAL_INPUT_MAP` defines handlers for UP, DOWN, LEFT, RIGHT, TAB, SHIFT+TAB, and ENTER, but the input map is never installed — there is no `bind()` method. The comment at line 49 confirms this is intentional: "TRAVERSAL_INPUT_MAP is never installed."

Even if wired up, two additional bugs prevent navigation:
- `FocusTraversalNode` constructor (line 63) calls `parent.setCurrent()` (no-arg). When `parent` is `FocusController` directly, this is a no-op — `current` is never set. (For intermediate `FocusTraversalNode` parents, the no-arg overload correctly delegates to `parent.setCurrent(this)`, so the bug only manifests for top-level containers.)
- All handler methods (`down()`, `up()`, etc.) guard with `if (current == null) return`, so they never execute.

Beyond the dead code, the navigation model diverges from the paper:
- **Paper**: cursor follows physical position in the visual layout, stored as a point
- **Kramer**: cursor follows logical schema structure via bias-based traversal, stored as a `FocusTraversalNode` reference

The paper also specifies cell-level selection granularity (labels independently selectable), Home/End navigation, and Page Up/Down as cursor movement — none of which exist.

## Context

### Dead Code Inventory

| Component | Status | Location |
|-----------|--------|----------|
| `TRAVERSAL_INPUT_MAP` | Defined, never installed | `FocusController.java:54-91` |
| `FocusController.setCurrent()` handlers | Called by input map only | `FocusController.java:111-180` |
| `FocusController.current` | Always null for top-level FTNs | `FocusTraversalNode.java:63` calls no-arg `setCurrent()` |
| `FocusTraversalNode.Bias` | Used by handlers | Logical, not physical |
| Home/End keys | Not defined | — |
| Page Up/Down as cursor | Not defined | `ScrollHandler` consumes via `installOverride` |

### Paper's Cursor Model (§4)

1. Cursor state = **point on the layout** (not cell reference)
2. Arrow keys move cursor in **physical direction** (visual position)
3. Works correctly with merged cells (point resolves to containing cell)
4. Home/End: move to start/end of current row
5. Page Up/Down: move cursor by one viewport height

### Current Model Issues

`FocusTraversalNode.Bias` reinterprets arrow keys based on container orientation:
- HORIZONTAL bias: DOWN = `traverseNext()` (next sibling container)
- VERTICAL bias: DOWN = `selectNext()` (next item in current container)

This follows schema structure, not visual position. In a hybrid layout where a table sits inside an outline, pressing DOWN in the outline should move to the next visual element below — but the logical model might jump to a completely different part of the schema.

### Platform Constraints

**KeyEvent dispatch**: JavaFX delivers `KeyEvent`s to the focus owner, not to parent layout panes. `AutoLayout` extends `AnchorPane`, which never holds keyboard focus. Installing the input map on `AutoLayout` would silently produce nothing. The map must be installed on each `VirtualFlow` — the actual focus-owning node — following the `ScrollHandler` pattern.

**Layout instability**: `AutoLayout.resize()` calls `autoLayout()` which replaces the entire control tree (`getChildren().setAll(node)` + `old.dispose()`). Any cursor state stored as scene coordinates is invalidated on every resize. The paper assumes a stable layout; Kramer's adaptive layout is deliberately unstable.

**Cell virtualization**: `VirtualFlow.hit(x, y)` only operates on visible cells (range `firstVisible` through `lastVisible`). Off-screen positions return `HitAfterCells`/`HitBeforeCells`, not a usable cell reference. Navigating past the last visible cell requires index-based advancement before hit-testing.

## Proposed Solution

### Phase 1: Wire Up Existing Navigation

1. Install input map on each `VirtualFlow` (not `AutoLayout`) via `InputMapTemplate.installFallback`
2. Fix `FocusTraversalNode` constructor: `parent.setCurrent()` → `parent.setCurrent(this)`
3. Verify `focusTraversable = true` on container nodes
4. Test that arrow keys produce logical navigation with existing bias model

### Phase 2: Hybrid Cursor Model

Replace the bias-based traversal with a physical model that falls back to logical advancement for off-screen navigation:

1. **Cursor state** = `(dataItemIdentity, fieldPath, scenePosition, cellBounds)` — the data identity survives layout rebuilds; the scene position is re-derived after each `autoLayout()` call
2. On arrow key: compute new position by moving in the pressed direction by `cellBounds.height` (DOWN/UP) or `cellBounds.width` (LEFT/RIGHT)
3. Hit-test the new position to find the containing cell
4. **On-screen hit**: select target cell, update cursor state
5. **Off-screen hit** (`HitAfterCells`/`HitBeforeCells`): fall back to index-based advancement — get last visible item index, increment, call `show(nextIdx)` to scroll and materialize the cell, then select it
6. **After layout rebuild**: listen to `AutoLayout` layout-complete events, re-derive scene position from stored `(dataItemIdentity, fieldPath)`, re-hit-test to restore cursor

```java
// Cursor state — survives layout rebuilds
record CursorState(
    Object dataIdentity,    // JSON node identity or index
    String fieldPath,       // schema path to focused field
    Point2D scenePosition,  // cached, re-derived after relayout
    Bounds cellBounds       // cached, used for step size
) {}

void moveDown() {
    double step = cursorState.cellBounds().getHeight();
    Point2D newPos = new Point2D(cursorState.scenePosition().getX(),
                                  cursorState.scenePosition().getY() + step);
    CellHit hit = hitTestScene(newPos);
    if (hit instanceof CellHit ch) {
        select(ch.getCell());
        updateCursorState(ch);
    } else {
        // Off-screen: fall back to index-based advancement
        int nextIdx = lastVisibleIndex() + 1;
        if (nextIdx < itemCount()) {
            show(nextIdx);
            select(getCell(nextIdx));
            updateCursorState(nextIdx);
        }
    }
}
```

### Phase 3: Cross-Container Hit Dispatch

Add a scene-coordinate hit API that traverses the containment hierarchy:

1. Add `hitScene(Point2D scenePoint)` method to `LayoutContainer` — converts scene point to local coordinates via `sceneToLocal()`, delegates to existing `hit(x, y)`
2. Implement on: `VirtualFlow`, `OutlineCell`, `OutlineColumn`, `NestedCell`, `Span`
3. Root-level dispatch from `AutoLayout`: walk child containers, call `hitScene()` on each until one returns a cell

### Phase 4: Cell-Level Selection

Labels in `OutlineElement` and `NestedCell` are plain JavaFX `Label` nodes, not `LayoutCell` instances. Making them independently selectable requires a wrapper:

1. Create `LabelCell` adapter implementing `LayoutCell` — wraps a `Label` node
2. Fix `OutlineElement.hit()` to include label/bullet nodes in hit test via `LabelCell`
3. Add `LabelCell` to `NestedCell.hit()` resolution

### Phase 5: Additional Keys

1. Add Home/End to `TRAVERSAL_INPUT_MAP` — move cursor to left/right edge of current row
2. Modify `ScrollHandler` to also move cursor after scrolling for PAGE_UP/PAGE_DOWN (preserves scroll behavior, adds cursor movement)
3. Explicitly defer TAB/SHIFT+TAB to continue using logical traversal — physical and logical orders will differ in hybrid layouts; this is acceptable as TAB traditionally follows DOM/focus order, not visual position

### Phase 6: Accessibility (Deferred)

Out of scope for initial implementation. Track separately:
- Set `AccessibleRole` on selectable nodes for screen reader navigation
- Add `AccessibleAttribute` for cell identity announcements
- Verify `node.requestFocus()` interaction with platform accessibility

## Implementation Plan

### Phase 1: Wire Up Existing Navigation (Fix Dead Code)
1. Add `FocusController.bind(VirtualFlow)` — install input map on the VirtualFlow node (the focus owner), not on AutoLayout
2. Fix `FocusTraversalNode` constructor: change `parent.setCurrent()` → `parent.setCurrent(this)` at line 63
3. Install binding in `VirtualFlow` constructor alongside `ScrollHandler`, passing through `FocusTraversal` chain to reach `FocusController`
4. Verify `focusTraversable = true` on container nodes participating in navigation
5. Test that arrow keys produce logical navigation with existing bias model

### Phase 2: Hybrid Cursor Model
6. Define `CursorState` record: `(dataIdentity, fieldPath, scenePosition, cellBounds)`
7. Add `cursorState` field to `FocusController`
8. Rewrite arrow key handlers to use physical hit-testing for on-screen cells
9. Implement off-screen fallback: on `HitAfterCells`/`HitBeforeCells`, advance by index, call `show(nextIdx)`, select materialized cell
10. Add `AutoLayout` layout-rebuild listener: after `autoLayout()` completes, re-derive `scenePosition` from `(dataIdentity, fieldPath)` and update `cursorState`
11. Store `cellBounds` when selecting a cell — use `cellBounds.height`/`width` as step size for navigation (resolves undefined `currentCellHeight`)

### Phase 3: Cross-Container Hit Dispatch
12. Add `hitScene(Point2D scenePoint)` to `LayoutContainer` interface — `sceneToLocal()` + delegate to existing `hit(x, y)`
13. Implement on `VirtualFlow`, `OutlineCell`, `OutlineColumn`, `NestedCell`, `Span`, `OutlineElement`
14. Add root-level dispatch in `AutoLayout`: walk child containers, call `hitScene()` recursively
15. Verify `VirtualFlow.hit()` side-effecting `layout()` call (line 381) is acceptable at navigation frequency; extract or skip if needed

### Phase 4: Cell-Level Selection
16. Create `LabelCell` adapter class implementing `LayoutCell` — wraps a `Label` node with selection/focus pseudo-class support
17. Fix `OutlineElement.hit()` to include label/bullet nodes via `LabelCell` wrappers
18. Add `LabelCell` to `NestedCell.hit()` resolution

### Phase 5: Additional Keys
19. Add Home/End to `TRAVERSAL_INPUT_MAP` — move cursor to left/right edge of current row
20. Modify `ScrollHandler.scrollUp()`/`scrollDown()` to also move cursor after scrolling
21. Document that TAB/SHIFT+TAB intentionally retains logical traversal order

### Phase 6: Accessibility (Deferred — track as separate RDR)
22. Set `AccessibleRole.TABLE_CELL` / `AccessibleRole.LIST_ITEM` on selectable nodes
23. Add `AccessibleAttribute.TEXT` for cell content announcements
24. Verify screen reader navigation with custom focus handling

## Trade-offs

- **Physical model is more complex**: requires hit-testing on every key press. Performance is acceptable — `LayoutContainer.hit()` iterates O(visible cells) with `Node.contains()`, sub-millisecond for typical layouts (10-50 visible cells).
- **Physical model is more correct**: handles merged cells, hybrid layouts, and column sets naturally.
- **Logical fallback is mandatory** (not optional): off-screen navigation, layout edge traversal, and post-resize cursor recovery all require index-based/identity-based advancement when hit-testing cannot resolve a cell. The fallback is a core design component, not an optimization.
- **TAB uses logical order, arrows use physical order**: this asymmetry is intentional and follows platform conventions (TAB = focus order, arrows = spatial navigation).
- **Cursor stored as data identity, not scene coordinates**: adds complexity (identity resolution after relayout) but survives Kramer's adaptive layout rebuilds. Scene coordinates are cached for hit-testing but treated as ephemeral.

## Test Plan

### Phase 1: Basic Navigation
- **P1-1**: Arrow keys in pure table → cursor moves to adjacent cell
- **P1-2**: Arrow keys in outline → cursor moves to next/previous element
- **P1-3**: TAB traverses containers in logical order
- **P1-4**: ENTER activates the focused cell

### Phase 2: Physical Cursor
- **P2-1**: Arrow keys in hybrid layout → cursor crosses outline/table boundary physically (specify expected target cell by position)
- **P2-2**: DOWN at last visible cell → auto-scroll, cursor lands on next data item (off-screen fallback)
- **P2-3**: UP at first visible cell → auto-scroll backward, cursor lands on previous data item
- **P2-4**: Window resize while cursor is active → cursor recovers to same data item after relayout
- **P2-5**: LEFT/RIGHT in outline row (VERTICAL bias) → cursor moves to adjacent column, not to parent container
- **P2-6**: Navigation in empty layout → no crash, cursor stays at origin
- **P2-7**: Navigation in single-cell layout → cursor does not move, no crash

### Phase 3: Cross-Container
- **P3-1**: Hit-test at boundary between outline and embedded table → correct container resolved
- **P3-2**: Multi-level nesting (outline > table > outline) → cursor traverses all levels physically

### Phase 4: Label Selection
- **P4-1**: Click on label in `OutlineElement` → label selected (not null)
- **P4-2**: Arrow key navigation lands on label cell → focus ring visible on label

### Phase 5: Additional Keys
- **P5-1**: Home/End in table row → cursor jumps to first/last column
- **P5-2**: Page Down → cursor moves down by viewport height (not just scroll)
- **P5-3**: Page Up → cursor moves up by viewport height

### Cross-Cutting
- **X-1**: Focus ring pseudo-class toggles correctly on navigation
- **X-2**: TAB and arrow navigation produce different orders in hybrid layout (intentional)
- **X-3**: Null/empty JSON items in VirtualFlow → `hit()` handles gracefully

## Research Findings

### RF-001: FocusController Dead Code — Double Bug (Verified)

`TRAVERSAL_INPUT_MAP` is confirmed never installed — no `bind()` method exists. Secondary bug: `FocusTraversalNode` constructor (line 63) calls `parent.setCurrent()` (no-arg). When `parent` is `FocusController`, the no-arg overload (line 142) is empty — `current` is never set. For intermediate `FocusTraversalNode` parents, the no-arg overload (line 158) correctly delegates to `parent.setCurrent(this)`, so the bug only manifests for top-level containers whose parent is the FocusController. Fix: change `parent.setCurrent()` → `parent.setCurrent(this)` at line 63, which works correctly at all nesting levels.

### RF-002: Hit-Testing Infrastructure Exists (Verified)

Comprehensive hit-testing at all nesting levels:
- `VirtualFlow.hit(x, y)` → `CellHit` with cell index, cell reference, cell offset
- `LayoutContainer.hit(x, y, cells)` → first cell whose `node.contains(point)` is true
- Per-container `hit()` on `OutlineCell`, `Span`, `OutlineColumn`, `NestedCell`

**Gap**: `OutlineElement.hit()` only tests the data cell node, ignoring label/bullet children. Label clicks return `null`. Must be fixed for Phase 4 (label selection). Requires `LabelCell` adapter since labels are not `LayoutCell` instances.

**Gap**: No cross-VirtualFlow hit dispatch for hybrid layouts — must be built for Phase 3. Requires adding `hitScene(Point2D)` to `LayoutContainer` interface, cascading to all implementations.

**Gap**: `VirtualFlow.hit()` triggers `layout()` as a side effect (line 381). Must verify this is acceptable at navigation frequency.

### RF-003: ScrollHandler Blocks PAGE_UP/PAGE_DOWN (Verified)

`ScrollHandler` uses `InputMapTemplate.installOverride` (higher priority than `installFallback`). PAGE_UP/PAGE_DOWN only scroll viewport without moving cursor. A `FocusController.bind()` using `installFallback` would never see these keys. Solution: modify `ScrollHandler.scrollUp()`/`scrollDown()` to also move cursor after scrolling, preserving scroll behavior while adding cursor movement.

### RF-004: MultipleCellSelection Is Functional (Verified)

Full `MultipleSelectionModel<T>` implementation backed by `BitSet`. `focus(index)`, `select(row, focus)`, `clearSelection()` all work. `Cell.setFocus()` calls `node.requestFocus()`. The selection model requires zero changes — it just needs keyboard-driven callers once `FocusController` is wired.

### RF-005: Coordinate Transformation Available (Verified)

JavaFX `Node.localToScene()` / `Node.sceneToLocal()` available on all nodes. `VirtualFlow.cellToViewport(cell, x, y)` converts cell-local to viewport (parent) coordinates via `localToParent()` — note this bridges one level only, not to scene coordinates. Full scene-coordinate conversion requires chaining `localToParent()` calls or using `localToScene()` directly. No custom coordinate infrastructure needed — JavaFX platform provides the primitives.

### RF-006: KeyEvent Dispatch Requires Focus-Owning Node (Verified, Gate)

JavaFX `KeyEvent`s are delivered to the focus owner, not to parent layout panes. `AutoLayout` extends `AnchorPane` which never holds focus. `MouseHandler.bind()` works because mouse events bubble through the scene graph; `KeyEvent`s do not. The input map must be installed on `VirtualFlow` (the focus owner), following the `ScrollHandler` pattern at `VirtualFlow` line 221.

### RF-007: Layout Rebuild Invalidates All Scene State (Verified, Gate)

`AutoLayout.resize()` calls `autoLayout()` which replaces the entire control tree — `getChildren().setAll(node)` with `old.dispose()`. All `VirtualFlow` and cell instances are destroyed and recreated. Any cursor state stored as scene coordinates or cell references becomes stale. Cursor must be stored as `(dataItemIdentity, fieldPath)` and re-derived after each layout rebuild.

### RF-008: Off-Screen Navigation Requires Logical Fallback (Verified, Gate)

`VirtualFlow.hit(x, y)` operates only on visible cells. Off-screen positions return `HitAfterCells`/`HitBeforeCells`. `show(itemIdx)` requires an item index, which is not derivable from a scene point for non-instantiated cells. Solution: on off-screen hit result, fall back to index-based advancement (last visible index ± 1), call `show()` to materialize the target cell, then select it. This fallback is mandatory, not optional.

## References

- Bakke 2013, §4 (Interactive Features)
- T3: `kramer-gap-analysis-interactive-features`
