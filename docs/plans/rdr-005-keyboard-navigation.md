# RDR-005: Keyboard Navigation and Physical Cursor Model -- Implementation Plan

**RDR**: [RDR-005](../rdr/RDR-005-keyboard-navigation.md) (accepted 2026-03-09)
**Epic**: Kramer-lpi
**Feature branch**: `feature/rdr-005-keyboard-navigation`
**Module**: `kramer` (build: `mvn -pl kramer test`)

## Executive Summary

Implement keyboard navigation for Kramer's autolayout engine, progressing from
wiring up existing dead code through a full physical cursor model that follows
the Bakke 2013 paper's approach. The work is divided into 6 phases with clear
dependency ordering. Phase 6 (Accessibility) is deferred to a future RDR.

## Dependency Graph

```
Phase 1 (Kramer-e56)
    |
    v
Phase 2 (Kramer-1zk)
   / \
  v   v
Phase 3 (Kramer-6i0)    Phase 5 (Kramer-cy8)
  |
  v
Phase 4 (Kramer-fys)

Phase 6 (Kramer-8n2) -- deferred, no dependencies
```

**Critical path**: Phase 1 -> Phase 2 -> Phase 3 -> Phase 4
**Parallelizable**: Phase 5 can proceed in parallel with Phases 3+4 (both depend only on Phase 2)

---

## Phase 1: Wire Up Existing Navigation (Fix Dead Code)

**Bead**: Kramer-e56 | **Priority**: P1 | **Dependencies**: none
**Estimated size**: Small (~50 LOC across 4 files + tests)

### Problem

`FocusController.TRAVERSAL_INPUT_MAP` defines handlers for arrow keys, TAB,
SHIFT+TAB, and ENTER, but is never installed. Two bugs compound this:

1. No `bind()` method exists on FocusController (unlike MouseHandler)
2. `FocusTraversalNode` constructor line 63 calls `parent.setCurrent()` (no-arg)
   which is a no-op when parent is FocusController, so `current` stays null and
   all handlers guard with `if (current == null) return`

### Implementation Steps

1. **FocusTraversal.java** -- Add `bindKeyboard(Node node)` default method (no-op)
2. **FocusController.java** -- Implement `bindKeyboard(Node node)`:
   ```java
   public void bindKeyboard(Node vfNode) {
       InputMapTemplate.installFallback(TRAVERSAL_INPUT_MAP, this, c -> vfNode);
   }
   ```
   Update `unbind()` to track and uninstall per-node bindings.
3. **FocusTraversalNode.java** -- Override `bindKeyboard(Node node)` to delegate
   up: `parent.bindKeyboard(node)`. Fix line 63: change `parent.setCurrent()` to
   `parent.setCurrent(this)`.
4. **VirtualFlow.java** -- In constructor, after `scrollHandler` initialization
   (line 221), add: `parentTraversal.bindKeyboard(this);`
5. Write tests verifying the wiring works with mocked components.

### Files Changed

| File | Change |
|------|--------|
| `cell/control/FocusTraversal.java` | Add `bindKeyboard(Node)` default method |
| `cell/control/FocusController.java` | Implement `bindKeyboard(Node)`, update `unbind()` |
| `cell/control/FocusTraversalNode.java` | Fix setCurrent bug line 63, add `bindKeyboard` delegation |
| `flowless/VirtualFlow.java` | Call `parentTraversal.bindKeyboard(this)` in constructor |

### Test Strategy

**Test class**: `FocusControllerWiringTest.java`
(`kramer/src/test/java/com/chiralbehaviors/layout/cell/control/`)

| Test | Validates |
|------|-----------|
| `testBindInstallsInputMapOnVirtualFlow` | Input map installed via installFallback on VF node |
| `testSetCurrentBugFix_topLevelFTN` | FocusTraversalNode correctly sets current on FocusController when focused |
| `testSetCurrentBugFix_nestedFTN` | Nested FTN still works (regression guard) |
| `testArrowDownDispatches` | down() handler fires based on bias |
| `testArrowUpDispatches` | up() handler fires based on bias |
| `testTabTraversesNext` | TAB calls traverseCurrentNext |
| `testShiftTabTraversesPrevious` | SHIFT+TAB calls traverseCurrentPrevious |
| `testEnterActivates` | ENTER calls currentActivate |
| `testDisabledNodeSuppressesHandlers` | Handlers don't fire when node is disabled |

Tests use Mockito to verify method invocations without needing a running JavaFX
scene. The FocusController methods are exercised directly (they're private, so
tests call them via reflection or by directly setting `current` and invoking).

### Success Criteria

- [ ] `FocusController.bindKeyboard(Node)` installs input map on given node
- [ ] `FocusTraversalNode` constructor sets `current` correctly for top-level FTNs
- [ ] Arrow keys produce logical navigation (existing bias model)
- [ ] TAB/SHIFT+TAB traverse containers
- [ ] ENTER activates the focused cell
- [ ] `mvn -pl kramer test` passes with all new tests
- [ ] Existing tests remain green

---

## Phase 2: Hybrid Cursor Model

**Bead**: Kramer-1zk | **Priority**: P1 | **Dependencies**: Kramer-e56 (Phase 1)
**Estimated size**: Large (~200 LOC across 4 files + tests)

### Problem

The existing navigation uses a logical bias-based model that follows schema
structure, not visual position. In hybrid layouts (outline + table), pressing
DOWN may jump to a schema-adjacent container rather than the visually adjacent
cell. The paper specifies a physical cursor model where arrow keys move a point
in the visual layout.

### Implementation Steps

1. **CursorState.java** (NEW) -- Define record in `cell.control` package:
   ```java
   public record CursorState(
       Object dataIdentity,    // JSON node identity or array index
       String fieldPath,       // schema path to focused field
       Point2D scenePosition,  // cached, re-derived after relayout
       Bounds cellBounds       // cached, used for step size
   ) {}
   ```

2. **FocusController.java** -- Add `cursorState` field (volatile). Rewrite
   `down()`, `up()`, `left()`, `right()` to:
   - Compute new position: current scene position + step (cellBounds height/width)
   - Hit-test the new position via `hitScene()` (Phase 3 adds cross-container;
     for Phase 2, use VirtualFlow's local `hit()` via coordinate conversion)
   - On cell hit: select target cell, update cursorState
   - On off-screen hit (HitAfterCells/HitBeforeCells): fall back to index-based
     advancement -- get last/first visible index +/- 1, call `show(nextIdx)` to
     scroll, then select the materialized cell
   - Store cellBounds when selecting a cell for step size computation

3. **FocusController.java** -- Add method to update cursor from data identity
   after layout rebuild. Requires access to the VirtualFlow to resolve identity
   to cell index.

4. **AutoLayout.java** -- Add layout-complete callback: after `autoLayout()`
   replaces the control tree (line 194), invoke a callback that allows
   FocusController to re-derive cursor position from stored
   `(dataIdentity, fieldPath)`.

5. **VirtualFlow.java** -- Expose `getFirstVisibleIndex()` and
   `getLastVisibleIndex()` as public methods (currently accessed via
   `cellPositioner` which is private). Add `getItemCount()` convenience method.

### Files Changed

| File | Change |
|------|--------|
| NEW `cell/control/CursorState.java` | Record definition |
| `cell/control/FocusController.java` | cursorState field, rewritten arrow handlers, recovery method |
| `AutoLayout.java` | Layout-complete callback, notify FocusController |
| `flowless/VirtualFlow.java` | Public first/last visible index, item count accessors |

### Test Strategy

**Test classes**: `CursorStateTest.java`, `FocusControllerNavigationTest.java`

| Test | Validates |
|------|-----------|
| `testCursorStateRecordFields` | Record construction and field access |
| `testMoveDownPhysical` | sceneY += cellBounds.height, hit-test resolves next cell |
| `testMoveUpPhysical` | sceneY -= cellBounds.height |
| `testMoveLeftPhysical` | sceneX -= cellBounds.width |
| `testMoveRightPhysical` | sceneX += cellBounds.width |
| `testOffScreenFallbackDown` | HitAfterCells triggers index+1 advancement + show() |
| `testOffScreenFallbackUp` | HitBeforeCells triggers index-1 advancement |
| `testCursorRecoveryAfterRelayout` | Identity-based restoration after autoLayout() |
| `testEmptyLayoutNavigation` | No crash on empty item list |
| `testSingleCellNavigation` | No movement, no crash |
| `testCursorStateNullBeforeFirstNavigation` | cursorState starts null |

### Success Criteria

- [ ] Arrow keys move cursor by physical position (cellBounds step size)
- [ ] On-screen hit-testing selects the correct cell
- [ ] Off-screen navigation triggers auto-scroll via `show()` and selects materialized cell
- [ ] Cursor recovers to the same data item after layout rebuild (resize)
- [ ] CursorState stores identity + position correctly
- [ ] Empty and single-cell layouts handle gracefully (no crash)
- [ ] `mvn -pl kramer test` passes

---

## Phase 3: Cross-Container Hit Dispatch

**Bead**: Kramer-6i0 | **Priority**: P2 | **Dependencies**: Kramer-1zk (Phase 2)
**Estimated size**: Medium (~100 LOC across 7 files + tests)

### Problem

Phase 2's cursor model uses single-VirtualFlow hit-testing. In hybrid layouts
(outline containing a table, or vice versa), cursor movement must cross
container boundaries. The hit-test needs to traverse the containment hierarchy
using scene coordinates.

### Implementation Steps

1. **LayoutContainer.java** -- Add default `hitScene(Point2D scenePoint)`:
   ```java
   default Hit<C> hitScene(Point2D scenePoint) {
       Point2D local = getNode().sceneToLocal(scenePoint);
       if (local == null || !getNode().contains(local)) return null;
       return hit(local.getX(), local.getY());
   }
   ```

2. **VirtualFlow.java** -- Override `hitScene` to handle the `layout()` side
   effect consideration (the existing `hit()` calls `layout()` at line 381).
   Verify performance is acceptable at navigation frequency.

3. **OutlineCell.java, OutlineColumn.java, NestedCell.java, Span.java,
   OutlineElement.java** -- Verify the default `hitScene` works correctly for
   each. Override only if coordinate transformation needs customization.

4. **AutoLayout.java** -- Add `hitSceneRoot(Point2D scenePoint)` that walks
   child containers recursively, calling `hitScene()` on each until one returns
   a non-null cell hit.

5. **FocusController.java** -- Update arrow handlers from Phase 2 to use
   `hitSceneRoot()` instead of single-VirtualFlow hit-testing.

### Files Changed

| File | Change |
|------|--------|
| `cell/LayoutContainer.java` | Add `hitScene(Point2D)` default method |
| `flowless/VirtualFlow.java` | Override `hitScene` if needed |
| `outline/OutlineCell.java` | Verify/override hitScene |
| `outline/OutlineColumn.java` | Verify/override hitScene |
| `outline/Span.java` | Verify/override hitScene |
| `outline/OutlineElement.java` | Verify/override hitScene |
| `table/NestedCell.java` | Verify/override hitScene |
| `AutoLayout.java` | Add `hitSceneRoot()` dispatch |
| `cell/control/FocusController.java` | Use hitSceneRoot for cross-container nav |

### Test Strategy

**Test class**: `HitSceneTest.java`

| Test | Validates |
|------|-----------|
| `testHitSceneCoordinateTransform` | sceneToLocal + hit delegation returns correct cell |
| `testHitSceneOutsideBounds` | Returns null for points outside container |
| `testHitSceneCrossContainerBoundary` | Hit across outline/table boundary resolves correct container |
| `testHitSceneMultiLevelNesting` | Nested outline > table > outline traversal |
| `testRootDispatchWalksChildren` | AutoLayout.hitSceneRoot finds correct child |
| `testHitSceneLayoutSideEffect` | VirtualFlow.hit() layout() call is safe at nav frequency |

### Success Criteria

- [ ] `hitScene(Point2D)` correctly converts scene coords and delegates to `hit()`
- [ ] Cross-container boundary resolution works for outline/table hybrid layouts
- [ ] Multi-level nesting (3+ levels) traverses correctly
- [ ] Root dispatch from AutoLayout finds the correct container
- [ ] No performance regression from layout() side effect in VirtualFlow.hit()
- [ ] `mvn -pl kramer test` passes

---

## Phase 4: Cell-Level Selection (LabelCell Adapter)

**Bead**: Kramer-fys | **Priority**: P2 | **Dependencies**: Kramer-6i0 (Phase 3)
**Estimated size**: Small-Medium (~80 LOC across 3 files + tests)

### Problem

Labels in `OutlineElement` and `NestedCell` are plain JavaFX `Label` nodes, not
`LayoutCell` instances. `OutlineElement.hit()` only checks `cell.getNode().contains()`
and ignores label/bullet children. Clicking on or navigating to a label returns null.

### Implementation Steps

1. **LabelCell.java** (NEW) -- Adapter in `cell` package implementing
   `LayoutCell<Label>`:
   - Wraps a `Label` node
   - Implements `updateItem()`, `activate()`, `dispose()`
   - Supports focus and selection pseudo-class toggling
   - Does not implement `LayoutContainer` (labels are leaf nodes)

2. **OutlineElement.java** -- Rewrite `hit(double x, double y)` to check all
   children (label, bullet, data cell) not just the data cell:
   - Create `LabelCell` wrappers for label and bullet nodes
   - Check `label.getNode().contains()` and `bullet.getNode().contains()` in
     addition to existing `cell.getNode().contains()`
   - Return the appropriate `Hit<LayoutCell<?>>` for whichever node is hit
   - Store LabelCell wrappers as fields (created in constructor)

3. **NestedCell.java** -- If NestedCell contains label nodes (check layout code),
   add LabelCell wrappers to hit resolution. May not be needed if NestedCell only
   contains child LayoutCell instances.

### Files Changed

| File | Change |
|------|--------|
| NEW `cell/LabelCell.java` | LayoutCell adapter wrapping Label |
| `outline/OutlineElement.java` | Rewrite hit() for label/bullet coverage, store LabelCell fields |
| `table/NestedCell.java` | Add LabelCell if applicable |

### Test Strategy

**Test class**: `LabelCellTest.java`

| Test | Validates |
|------|-----------|
| `testLabelCellImplementsLayoutCell` | Interface contract fulfilled |
| `testLabelCellUpdateItem` | updateItem sets text on wrapped Label |
| `testLabelCellFocusPseudoClass` | Focus pseudo-class toggles correctly |
| `testLabelCellSelectionPseudoClass` | Selection pseudo-class toggles |
| `testOutlineElementHitOnLabel` | hit() returns LabelCell for label click |
| `testOutlineElementHitOnBullet` | hit() returns LabelCell for bullet click |
| `testOutlineElementHitOnDataCell` | hit() still returns data cell (regression) |
| `testOutlineElementHitOutside` | hit() returns null for miss |

### Success Criteria

- [ ] LabelCell correctly wraps Label with LayoutCell interface
- [ ] OutlineElement.hit() returns non-null for label and bullet hits
- [ ] Focus ring pseudo-class toggles on LabelCell
- [ ] Arrow key navigation can land on label cells
- [ ] Existing data cell hit behavior unchanged (regression test)
- [ ] `mvn -pl kramer test` passes

---

## Phase 5: Additional Keys (Home/End/PageUp/PageDown Cursor)

**Bead**: Kramer-cy8 | **Priority**: P2 | **Dependencies**: Kramer-1zk (Phase 2)
**Estimated size**: Small (~40 LOC across 2 files + tests)

### Problem

Home/End keys are not defined. PAGE_UP/PAGE_DOWN only scroll the viewport
(via ScrollHandler's `installOverride`) without moving the cursor. The RDR
specifies cursor movement for all of these.

### Implementation Steps

1. **FocusController.java** -- Add Home/End to `TRAVERSAL_INPUT_MAP`:
   - Home: move cursor to leftmost cell in current row (sceneX = row left edge)
   - End: move cursor to rightmost cell in current row (sceneX = row right edge)
   - Implement `home()` and `end()` private methods using hit-testing at row edges

2. **ScrollHandler.java** -- Modify `scrollUp()` and `scrollDown()` to also
   move the cursor after scrolling. This requires ScrollHandler to have a
   reference to FocusController or a cursor-movement callback:
   - Add `Runnable afterScrollUp` and `Runnable afterScrollDown` callbacks
   - VirtualFlow passes lambdas that invoke cursor movement on FocusController
   - Alternative: ScrollHandler accepts a `Consumer<Direction>` for post-scroll
     cursor movement

3. **Document** in code comments that TAB/SHIFT+TAB intentionally retains
   logical traversal order per platform conventions.

### Files Changed

| File | Change |
|------|--------|
| `cell/control/FocusController.java` | Add Home/End to input map, home()/end() methods |
| `flowless/ScrollHandler.java` | Add post-scroll cursor movement callback |
| `flowless/VirtualFlow.java` | Wire cursor movement callback to ScrollHandler |

### Test Strategy

**Test class**: `AdditionalKeysTest.java`

| Test | Validates |
|------|-----------|
| `testHomeMovesToFirstColumn` | Cursor jumps to leftmost cell in row |
| `testEndMovesToLastColumn` | Cursor jumps to rightmost cell in row |
| `testHomeInSingleColumnRow` | No movement, no crash |
| `testEndInSingleColumnRow` | No movement, no crash |
| `testPageDownMovesCursor` | After scroll, cursor position updates |
| `testPageUpMovesCursor` | After scroll backward, cursor position updates |
| `testPageDownAtEnd` | At last item, no crash |
| `testPageUpAtStart` | At first item, no crash |

### Success Criteria

- [ ] Home key moves cursor to first column in current row
- [ ] End key moves cursor to last column in current row
- [ ] PAGE_DOWN scrolls viewport AND moves cursor down by viewport height
- [ ] PAGE_UP scrolls viewport AND moves cursor up by viewport height
- [ ] TAB/SHIFT+TAB behavior unchanged (logical order)
- [ ] Edge cases (single column, start/end of data) handled gracefully
- [ ] `mvn -pl kramer test` passes

---

## Phase 6: Accessibility (Deferred)

**Bead**: Kramer-8n2 | **Priority**: P3 | **Dependencies**: none
**Status**: Tracking only -- no implementation in this epic

### Tracked Work

When ready, create a separate RDR covering:
- Set `AccessibleRole.TABLE_CELL` / `AccessibleRole.LIST_ITEM` on selectable nodes
- Add `AccessibleAttribute.TEXT` for cell content announcements
- Verify `node.requestFocus()` interaction with platform accessibility
- Test with screen readers (VoiceOver on macOS)

---

## Risk Register

| Risk | Impact | Mitigation |
|------|--------|------------|
| JavaFX test infrastructure lacks UI interaction support | Tests limited to unit/mock level | Use Mockito for handler verification; defer integration tests to manual validation |
| Thread safety of CursorState | Race conditions on FX thread | Mark cursorState volatile; all updates on FX application thread |
| Platform.runLater() timing for cursor recovery | Cursor recovery races with layout | Schedule recovery in same runLater block as autoLayout(), or add dedicated callback |
| VirtualFlow.hit() calls layout() as side effect | Performance at navigation frequency | Profile; extract layout-skip path if hit frequency is too high |
| Layout rebuild destroys all cell references | Stale cell references in CursorState | Store data identity, not cell reference; re-derive cell from identity after rebuild |

## Parallel Execution Opportunities

- Phase 5 can be developed in parallel with Phase 3 and Phase 4 once Phase 2 is
  complete. Both branches depend only on Phase 2's cursor model.
- Within Phase 3, the hitScene implementations on individual container classes
  are independent and can be worked on simultaneously.
- Test development for each phase can begin as soon as the phase's interface
  contracts are defined, before implementation is complete (TDD).

## Bead Summary

| Bead ID | Phase | Type | Priority | Blocked By |
|---------|-------|------|----------|------------|
| Kramer-lpi | Epic | epic | P1 | -- |
| Kramer-e56 | 1 | task | P1 | -- |
| Kramer-1zk | 2 | task | P1 | Kramer-e56 |
| Kramer-6i0 | 3 | task | P2 | Kramer-1zk |
| Kramer-fys | 4 | task | P2 | Kramer-6i0 |
| Kramer-cy8 | 5 | task | P2 | Kramer-1zk |
| Kramer-8n2 | 6 | chore | P3 | -- |
