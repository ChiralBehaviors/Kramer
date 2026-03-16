# RDR-017: In-Layout Search & Navigation

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P2
- **Created**: 2026-03-15
- **Related**: RDR-005 (keyboard navigation), RDR-009 (stylesheet data structure / SchemaPath), Related Worksheets (CHI 2011)

## Problem Statement

Kramer has keyboard navigation (`FocusController`, `FocusTraversal`, `MultipleCellSelection`) but no search capability. Users cannot find specific data values within a rendered layout. The Related Worksheets paper (CHI 2011) describes "Firefox-style search" that moves the cursor to the next matching cell string within the current view.

For large datasets rendered via `VirtualFlow` (where not all cells are visible), finding a specific value requires manual scrolling. This is the simplest high-value interaction feature missing from Kramer.

---

## Current State

The `cell.control` subpackage provides:
- `FocusController` — manages focused cell across the layout
- `FocusTraversal` — tree of traversal groups (one per VirtualFlow)
- `MultipleCellSelection` — selection model with single/range selection
- Arrow key navigation between cells via `FocusTraversal.right()`, `left()`, `up()`, `down()`

These components handle cell-to-cell movement but have no concept of "find cell matching predicate."

### FocusController Public API (actual surface)

The following methods are public or interface-visible on `FocusController`:
- `bindKeyboard(Node)` — installs keyboard input map on a node
- `unbind()` — removes keyboard bindings and clears cursor state
- `activate()`, `edit()` — no-ops in the base implementation
- `isCurrent()`, `isCurrent(FocusTraversalNode)` — focus identity checks
- `propagate(SelectionEvent)` — event propagation (returns false)
- `select(LayoutContainer)`, `selectNoFocus(LayoutContainer)` — container selection (no-ops)
- `selectNext()`, `selectPrevious()` — traversal (no-ops)
- `setCurrent()`, `setCurrent(FocusTraversalNode)` — sets current focus node
- `resetCursorState()` — clears cursor state
- `recoverCursor(VirtualFlow)` — recovers cursor position after layout rebuild

**Notable**: There is no `focus(cell)` method. The internal `selectCellAt(VirtualFlow, int)` method (line 389) is **private**. It handles both selection model update and cursor state derivation. Programmatic focus-to-index requires either exposing `selectCellAt` as public or introducing a new `navigateTo(VirtualFlow, int)` method.

### CursorState.fieldPath

The `CursorState` record has a `fieldPath` field of type `String`, but `selectCellAt` always passes `null` for it with the comment "not yet implemented; requires schema-aware lookup." This means nested field-level navigation is deferred work.

### ENTER Key Conflict

`FocusController.TRAVERSAL_INPUT_MAP` consumes `keyPressed(ENTER)` for `currentActivate()`. Any search binding that uses Enter must account for this — the TRAVERSAL_INPUT_MAP is installed as a fallback on every node that calls `bindKeyboard()`.

---

## Proposed Design

### Search Model

```java
public class LayoutSearch {
    private final FocusController<?> focusController;
    private final SchemaNode root;
    private String query = "";
    private boolean caseSensitive = false;
    private boolean wrapAround = true;

    public void setQuery(String query) { this.query = query; }
    public String getQuery() { return query; }

    /** Find next cell matching current query, starting from current focus */
    public Optional<SearchResult> findNext() { ... }

    /** Find previous cell matching current query */
    public Optional<SearchResult> findPrevious() { ... }

    /** Count total matches for current query (for "3 of 17" display) */
    public int countMatches(JsonNode data) { ... }
}

record SearchResult(
    SchemaPath path,      // which field (from root to matched primitive)
    int rowIndex,         // which data row at the outermost relation
    String matchedValue,  // the cell content that matched
    int matchStart,       // character offset of match within value
    int matchLength       // length of match
) {}
```

`SchemaPath` (from RDR-009, implemented in `com.chiralbehaviors.layout.SchemaPath`) is a `record SchemaPath(List<String> segments)` providing immutable addressing of schema nodes. It already exists in the codebase and is suitable for identifying matched fields.

For search navigation into nested structures, the `SearchResult` also needs row indices at each nesting level. Define a supplementary path type:

```java
record NavigationPath(List<NavigationStep> steps) {}
record NavigationStep(SchemaPath field, int rowIndex) {}
```

This pairs each relation in the path with the row index to scroll to, enabling the VirtualFlow chain navigation described in Phase 2b.

### Search Algorithm

Search traverses the JSON data in **schema depth-first order** — the canonical traversal order for hybrid rendering where the same data may render as table or outline depending on width:

```
For each data row (depth-first over schema tree):
    For each Primitive field (in schema child order):
        If field value contains query (respecting caseSensitive flag):
            Return SearchResult(path, rowIndex, value, offset, length)
```

**Key insight**: Search operates on DATA (`JsonNode`), not on rendered cells. This means:
- Search finds matches in non-visible (virtualized) rows
- Search works identically regardless of table/outline rendering mode
- No dependency on VirtualFlow cell materialization

### Focus Navigation

When a match is found:

**Phase 2a (flat/top-level):**
1. Scroll the outermost VirtualFlow via `VirtualFlow.show(index)` (minimal scroll, not `showAsFirst` which forces to top)
2. Select the matching row via the exposed navigation method (see Prerequisites)
3. Highlight the matching text within the cell

**Phase 2b (nested VirtualFlow chain):**
After `outerVf.show(index)`, the inner VirtualFlow is **not materialized until the next JavaFX pulse**. Synchronous traversal of a nested VirtualFlow chain is not possible. The navigation must chain asynchronously:

```java
void navigateToMatch(NavigationPath path) {
    navigateStep(path, 0);
}

void navigateStep(NavigationPath path, int depth) {
    if (depth >= path.steps().size()) return;
    NavigationStep step = path.steps().get(depth);
    VirtualFlow<?> vf = resolveFlowForDepth(depth);
    vf.show(step.rowIndex());
    // Inner VirtualFlow materializes on next pulse
    Platform.runLater(() -> navigateStep(path, depth + 1));
}
```

Each level of nesting requires one `Platform.runLater()` hop. A 3-level nested layout requires 3 pulses to fully navigate.

### Keyboard Binding

Following Firefox convention:
- `Ctrl+F` / `Cmd+F` — open search bar
- `F3` — find next (exclusively; Enter is NOT used for find-next because `FocusController.TRAVERSAL_INPUT_MAP` consumes `keyPressed(ENTER)` for `currentActivate()`)
- `Shift+F3` — find previous
- `Enter` within search TextField — handled by TextField's own `onAction` handler to trigger find-next (TextField consumes the event before it reaches the TRAVERSAL_INPUT_MAP fallback)
- `Escape` — close search bar, return focus to last matched cell

### Search Bar UI

Minimal floating bar at top or bottom of `AutoLayout`:

```java
public class SearchBar extends HBox {
    private final TextField searchField;
    private final Label matchCount;  // "3 of 17"
    private final Button nextButton;
    private final Button prevButton;
}
```

Styled via co-located CSS (`search-bar.css`).

---

## Prerequisites

Before Phase 2 can begin, the following must be in place:

1. **Expose `selectCellAt` or equivalent** — `FocusController.selectCellAt(VirtualFlow, int)` is currently private. Either:
   - Make it package-private/public, or
   - Add a new public `navigateTo(VirtualFlow<?> vf, int index)` method that delegates to `selectCellAt`

   This is a small change (~5 lines) but is a hard dependency for programmatic search navigation.

2. **Use `show(int)` not `showAsFirst(int)`** — `show(int)` (line 525 of VirtualFlow) uses `MinDistanceTo` which scrolls minimally. `showAsFirst(int)` uses `StartOffStart` which forces the target to the viewport top. Search navigation should use `show(int)` to avoid disorienting jumps.

---

## Implementation

### Phase 1: Data-level search (LOW effort)
- Implement `LayoutSearch` with `setQuery()`, `findNext()`, `findPrevious()`, `countMatches()`
- Search operates on `JsonNode` data using `SchemaNode` structure
- Traversal order: schema depth-first
- Returns `SearchResult` without any UI changes

### Phase 2a: Top-level VirtualFlow navigation (LOW-MEDIUM effort)
- Expose `FocusController.navigateTo(VirtualFlow, int)` (prerequisite)
- On `SearchResult`: call `VirtualFlow.show(rowIndex)` then `navigateTo(vf, rowIndex)`
- Works for flat layouts and the outermost level of nested layouts

### Phase 2b: Nested VirtualFlow chain navigation (MEDIUM-HIGH effort)
- Requires `NavigationPath` with row indices at each nesting level
- Async coordination via `Platform.runLater()` chaining — one pulse per nesting depth
- Depends on `CursorState.fieldPath` being implemented (currently always `null`)
- Requires a way to resolve the VirtualFlow at each nesting depth from the schema path

### Phase 3: Search bar UI (LOW effort)
- Floating `SearchBar` in `AutoLayout`
- Keyboard binding: Ctrl+F opens, F3/Shift+F3 navigate, Escape closes
- Enter in search TextField triggers find-next via `onAction` handler (avoids ENTER key conflict)
- Match count display

### Phase 4: Match highlighting (MEDIUM effort)
- Highlight matched text within the focused cell
- Requires converting `Primitive` cell content from plain `Label` to `TextFlow` with styled `Text` runs
- This is more involved than a CSS pseudo-class approach because the match position is substring-level — TextFlow must split the cell text into pre-match, match, and post-match `Text` nodes
- `Style.LayoutObserver` could apply highlighting without core layout changes, but the `TextFlow` conversion itself touches the cell rendering pipeline

---

## Integration with Explorer App

The `explorer` module (`AutoLayoutExplorer`) is the primary beneficiary. Add search bar to the explorer toolbar or as a floating overlay. The `SearchBar` fires events that `AutoLayoutExplorer` routes to `LayoutSearch` on the active `AutoLayout` instance.

---

## Research Findings

### RF-1: Data-Level Search Is Mode-Independent (Confidence: HIGH)

Because search operates on `JsonNode` data, not on rendered cells, the same search works regardless of whether a field is rendered as table, outline, bar chart, or crosstab. This is architecturally clean — search is a data concern, not a rendering concern.

### RF-2: FocusController Requires API Extension for Programmatic Focus (Confidence: HIGH)

`FocusController` has **no public method** for programmatic focus-to-index. The internal `selectCellAt(VirtualFlow, int)` (line 389) handles selection model update and cursor state derivation but is private. The actual public surface is: `bindKeyboard`, `unbind`, `activate`, `isCurrent`, `propagate`, `select`, `setCurrent(FocusTraversalNode)`, `recoverCursor(VirtualFlow)`, `resetCursorState`.

To support search navigation, either `selectCellAt` must be promoted to public visibility, or a new `navigateTo(VirtualFlow<?>, int)` method must be added. This is a small prerequisite change.

### RF-3: VirtualFlow Scrolling for Non-Visible Matches — Flat Case Only (Confidence: HIGH)

`VirtualFlow.show(int)` (line 525) scrolls to materialize a target row using `MinDistanceTo`, which minimizes scroll distance. This works well for top-level (flat) navigation. However, for **nested** VirtualFlows, calling `show(index)` on the outer flow does not synchronously materialize the inner flow's cells — that happens on the next JavaFX layout pulse. Nested navigation requires async coordination.

### RF-4: SchemaPath Exists and Is Suitable (Confidence: HIGH)

`SchemaPath` was implemented as part of RDR-009 (`com.chiralbehaviors.layout.SchemaPath`). It is a `record SchemaPath(List<String> segments)` with `child(String)` for building paths and `leaf()` for the terminal segment. This is sufficient for identifying which field a search match belongs to. For navigation, the supplementary `NavigationPath`/`NavigationStep` types pair schema paths with row indices.

SchemaPath exists in the codebase (implemented as part of RDR-009) but is currently set only during `measure()`. For search to address results by SchemaPath at any time, SchemaPath must be construction-time-derived. This lifecycle change is tracked as a standalone deliverable in RDR-ROADMAP.md.

---

## Risks and Considerations

| Risk | Severity | Mitigation |
|------|----------|------------|
| Search on large datasets (>10K rows) may be slow | Low | Search is O(rows x fields) string matching; even 10K x 20 fields is fast |
| Nested VirtualFlow materialization is async | High | Phase 2b uses `Platform.runLater()` chaining; one pulse per nesting depth. Cannot be solved synchronously. |
| ENTER key consumed by FocusController TRAVERSAL_INPUT_MAP | Medium | Use F3 exclusively for find-next outside search bar. Enter within TextField is consumed by its own onAction handler before reaching the fallback input map. |
| Focus model may not support focusing into nested tables | Medium | FocusTraversal handles nested groups, but CursorState.fieldPath is always null ("not yet implemented"). Phase 2b depends on this being completed. |
| TextFlow conversion for match highlighting | Medium | Phase 4 requires converting Primitive cells from Label to TextFlow, touching the cell rendering pipeline. Flag as MEDIUM effort, not LOW. |

## Success Criteria

1. Ctrl+F opens search bar in AutoLayout
2. Typing a query finds and focuses the first matching cell
3. F3 advances to next match; Shift+F3 goes to previous
4. Match count shows "N of M" accurately
5. Search works across table and outline modes identically
6. Non-visible (virtualized) matches are scrolled into view (top-level)
7. Escape closes search and returns focus to last match
8. All existing tests pass (search is additive)
9. (Phase 2b) Nested VirtualFlow chain navigation reaches correct cell within 1 pulse per nesting depth

## Recommendation

Phase 1 (data search) and Phase 3 (search bar UI) are LOW effort and have no blockers — recommend implementing together as the initial deliverable.

Phase 2a (top-level navigation) is LOW-MEDIUM effort with a small prerequisite (exposing `selectCellAt` or adding `navigateTo`). Implement immediately after Phase 1+3.

Phase 2b (nested VirtualFlow chain navigation) is MEDIUM-HIGH effort due to async coordination, the unimplemented `CursorState.fieldPath`, and the need to resolve VirtualFlow instances at each nesting depth. Defer until keyboard navigation (RDR-005) completes the fieldPath work.

Phase 4 (match highlighting via TextFlow) is MEDIUM effort due to the Label-to-TextFlow conversion in the cell rendering pipeline.

Overall: Phases 1+3+2a are straightforward and deliver most of the user-visible value. Phase 2b and 4 are follow-on work with real complexity.
