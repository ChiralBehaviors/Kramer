---
title: "Keyboard Navigation and Physical Cursor Model"
id: RDR-005
type: Feature
status: draft
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

Even if wired up, the navigation model diverges from the paper:
- **Paper**: cursor follows physical position in the visual layout, stored as a point
- **Kramer**: cursor follows logical schema structure via bias-based traversal, stored as a `FocusTraversalNode` reference

The paper also specifies cell-level selection granularity (labels independently selectable), Home/End navigation, and Page Up/Down as cursor movement — none of which exist.

## Context

### Dead Code Inventory

| Component | Status | Location |
|-----------|--------|----------|
| `TRAVERSAL_INPUT_MAP` | Defined, never installed | `FocusController.java:54-91` |
| `FocusController.setCurrent()` handlers | Called by input map only | `FocusController.java:111-180` |
| `FocusController.current` | Always null (double bug) | `FocusTraversalNode.java:63` calls no-arg `setCurrent()` |
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

## Proposed Solution

### Phase 1: Wire Up Existing Navigation

1. Add `bind()` method to `FocusController` (parallel to `MouseHandler.bind()`)
2. Call `bind()` from `AutoLayout` or `VirtualFlow` initialization
3. Test that arrow keys produce any navigation at all

### Phase 2: Physical Cursor Model

Replace the bias-based traversal with a point-based model:

1. Store cursor as `Point2D cursorPosition` on `FocusController`
2. On arrow key, compute new position by moving in the pressed direction by one cell height/width
3. Hit-test the new position to find the containing cell
4. If no cell at new position, scan further in the pressed direction until a cell is found or edge is reached

```java
// Pseudocode for physical navigation
void moveDown() {
    Point2D newPos = new Point2D(cursorPosition.getX(),
                                  cursorPosition.getY() + currentCellHeight);
    LayoutCell<?> target = hitTest(newPos);
    if (target != null) {
        select(target);
        cursorPosition = newPos;
    }
}
```

### Phase 3: Cell-Level Selection

1. Make labels independently selectable in `OutlineElement.hit()` and `NestedCell.hit()`
2. Add label cells to the hit-test resolution

### Phase 4: Additional Keys

1. Home/End: move cursor to left/right edge of current row
2. Page Up/Down: move cursor by viewport height (not just scroll)

## Implementation Plan

### Phase 1: Wire Up Existing Navigation (Fix Dead Code)
1. Add `FocusController.bind(Node)` — call `InputMapTemplate.installFallback(TRAVERSAL_INPUT_MAP, this, c -> node)`
2. Fix `FocusTraversalNode` constructor: change `parent.setCurrent()` → `parent.setCurrent(this)` at line 63
3. Call `controller.bind(this)` from `AutoLayout` initialization
4. Test that arrow keys produce logical navigation with existing bias model

### Phase 2: Physical Cursor Model
5. Add `cursorPosition: Point2D` field to `FocusController` (scene coordinates)
6. Rewrite arrow key handlers to use physical hit-testing via existing `VirtualFlow.hit()` + `LayoutContainer.hit()`
7. Build cross-VirtualFlow hit dispatch for hybrid layouts (root-level `AutoLayout` → child containers via `localToScene()`/`sceneToLocal()`)
8. Use `VirtualFlow.show(itemIdx)` to auto-scroll when cursor moves off-screen

### Phase 3: Cell-Level Selection
9. Fix `OutlineElement.hit()` to include label/bullet nodes in hit test
10. Add label cells to `NestedCell.hit()` resolution

### Phase 4: Additional Keys
11. Add Home/End to `TRAVERSAL_INPUT_MAP` — move cursor to left/right edge of current row
12. Modify `ScrollHandler` to move cursor after scrolling for PAGE_UP/PAGE_DOWN (option b from RF-003)
13. Add tests for all key bindings in hybrid layouts

## Trade-offs

- **Physical model is more complex**: requires hit-testing on every key press
- **Physical model is more correct**: handles merged cells, hybrid layouts, and column sets naturally
- **Logical model could be kept as fallback**: if hit-testing fails (edge of layout), fall back to logical traversal

## Test Plan

- **Scenario**: Arrow keys in pure table → **Verify**: cursor moves to adjacent cell
- **Scenario**: Arrow keys in hybrid layout → **Verify**: cursor crosses outline/table boundary physically
- **Scenario**: Home/End in table row → **Verify**: cursor jumps to first/last column
- **Scenario**: Page Down → **Verify**: cursor moves down by viewport height, not just scroll

## Research Findings

### RF-001: FocusController Double Bug (Verified)

`TRAVERSAL_INPUT_MAP` is confirmed never installed — no `bind()` method exists. Secondary bug: `FocusTraversalNode` constructor calls `parent.setCurrent()` (no-arg overload, does nothing) instead of `parent.setCurrent(this)`. Even if the input map were wired, `FocusController.current` would remain `null` and all handlers would no-op. Fix requires both: (1) add `bind()` following `MouseHandler.bind()` pattern, (2) fix `setCurrent()` dispatch in `FocusTraversalNode.java:63`.

### RF-002: Hit-Testing Infrastructure Exists (Verified)

Comprehensive hit-testing at all nesting levels:
- `VirtualFlow.hit(x, y)` → `CellHit` with cell index, cell reference, cell offset
- `LayoutContainer.hit(x, y, cells)` → first cell whose `node.contains(point)` is true
- Per-container `hit()` on `OutlineCell`, `Span`, `OutlineColumn`, `NestedCell`

**Gap**: `OutlineElement.hit()` only tests the data cell node, ignoring label/bullet children. Label clicks return `null`. Must be fixed for Phase 3 (label selection).

**Gap**: No cross-VirtualFlow hit dispatch for hybrid layouts — must be built for Phase 2.

### RF-003: ScrollHandler Blocks PAGE_UP/PAGE_DOWN (Verified)

`ScrollHandler` uses `InputMapTemplate.installOverride` (higher priority than `installFallback`). PAGE_UP/PAGE_DOWN only scroll viewport without moving cursor. A `FocusController.bind()` using `installFallback` would never see these keys. Recommended: modify `ScrollHandler` to also move cursor after scrolling (option b), preserving scroll behavior while adding cursor movement.

### RF-004: MultipleCellSelection Is Functional (Verified)

Full `MultipleSelectionModel<T>` implementation backed by `BitSet`. `focus(index)`, `select(row, focus)`, `clearSelection()` all work. `Cell.setFocus()` calls `node.requestFocus()`. The selection model requires zero changes — it just needs keyboard-driven callers once `FocusController` is wired.

### RF-005: Coordinate Transformation Available (Verified)

JavaFX `Node.localToScene()` / `Node.sceneToLocal()` available on all nodes. `VirtualFlow.cellToViewport(cell, x, y)` converts cell-local to viewport coordinates. No custom coordinate infrastructure needed — JavaFX platform provides the bridge for cross-container physical navigation.

## References

- Bakke 2013, §4 (Interactive Features)
- T3: `kramer-gap-analysis-interactive-features`
