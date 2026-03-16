# RDR-016: Layout Stability & Incremental Update

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P2
- **Created**: 2026-03-15
- **Related**: RDR-009 (MeasureResult immutability), RDR-011 (LayoutDecisionTree), RDR-012 (streaming invalidation), SIEUFERD (SIGMOD 2016 §3.4)

## Problem Statement

Kramer has three distinct code paths that trigger layout work, but the current architecture conflates them. SIEUFERD (SIGMOD 2016) describes a stable layout approach: "The on-screen layout remains undisturbed by the automatic interruption and restarting of queries." This RDR addresses three concrete problems:

1. **Scroll position loss**: `Outline.updateItem()` calls `showAsFirst(0)` unconditionally, resetting scroll to top on every data update (this is Outline-specific; `NestedTable.updateItem()` does not reset scroll)
2. **Resize rebuilds the entire control tree**: `resize()` calls `autoLayout(data, width)` which calls `layout.autoLayout()` -> `layout()` + `compress()` + `calculateRootHeight()` + `buildControl()`, discarding and recreating all VirtualFlow instances
3. **Stylesheet changes null the layout**: The stylesheet listener nulls both `layout` and `measureResult`, then calls `autoLayout()` — a full remeasure + relayout even when only visual properties changed

---

## Current Architecture

`AutoLayout` has three entry paths that trigger work:

### Path 1: `resize(w, h)` — width change
```
resize(w, h):
  if (layoutWidth == w || w < 10 || h < 10): return
  layoutWidth = w
  autoLayout(data, w)
    if (layout == null): measure(data)    // only if no cached layout
    layout.autoLayout(w, height, ...)     // layout + compress + height + buildControl
    control = <new control tree>
    control.updateItem(data)
```
This always rebuilds the control tree. The `layout` (SchemaNodeLayout) is reused if non-null — only `clear()` + `layout()` + `compress()` + height + build runs, not measure. But the control tree is fully replaced.

### Path 2: `data.set(newData)` — data property change
```
setContent():
  if (control == null):
    Platform.runLater(() -> autoLayout(data, width))   // cold start: full pipeline
  else:
    control.updateItem(data)                           // HOT PATH: no relayout
  layout()                                             // JavaFX layout pass
```
The hot path (`control != null`) does NOT null layout, does NOT call `autoLayout()`, does NOT call `clear()`. It delegates directly to `control.updateItem(datum)` which reaches `Outline.updateItem()` or `NestedTable.updateItem()`. This is already a fast data-rebinding path.

### Path 3: Stylesheet change
```
stylesheets listener:
  model.setStyleSheets(newList, this)
  layout = null
  measureResult = null
  if (data != null): autoLayout()         // resets layoutWidth=0, schedules full pipeline
```
This is the only path that nulls `layout`, forcing a full remeasure on next pass.

### Path 4: `autoLayout()` public method
```
autoLayout():
  layoutWidth = 0.0                       // forces resize to re-enter
  Platform.runLater(() -> autoLayout(data, width))
```
Resets `layoutWidth` to 0.0 so the next `resize()` will not short-circuit. Any caching must coordinate with this reset.

### Key observations
- Data changes via `data.set()` already take a fast path when `control != null` — the original claim that "data change nulls layout" was incorrect
- The real scroll bug is in `Outline.updateItem()` only — `NestedTable.updateItem()` calls `rows.getItems().setAll(...)` without scroll reset
- `Outline.updateItem()` has an intentional comment ("Reset scroll to top after populating items"), so `showAsFirst(0)` is by design for initial population, but wrong for the update-existing-data case

---

## Proposed Design

### Phase 1: Scroll Preservation in Outline (LOW effort)

Fix `Outline.updateItem()` to preserve scroll position. `VirtualFlow.getFirstVisibleIndex()` returns `OptionalInt`, not `int`:

```java
// Before:
public void updateItem(JsonNode item) {
    List<JsonNode> list = SchemaNode.asList(item);
    items.setAll(list);
    // Reset scroll to top after populating items
    if (!items.isEmpty()) {
        showAsFirst(0);
    }
    ...
}

// After:
public void updateItem(JsonNode item) {
    OptionalInt savedIndex = getFirstVisibleIndex();
    List<JsonNode> list = SchemaNode.asList(item);
    items.setAll(list);
    if (!items.isEmpty()) {
        savedIndex.ifPresentOrElse(
            idx -> showAsFirst(Math.min(idx, items.size() - 1)),
            () -> showAsFirst(0)
        );
    }
    ...
}
```

Note: `getFirstVisibleIndex()` returns `OptionalInt.empty()` when no cells are visible (e.g., first population), so the fallback to `showAsFirst(0)` preserves the original design intent for initial load.

This fix is Outline-specific. `NestedTable.updateItem()` already takes the fast path without scroll reset — no fix needed there.

### Phase 2: List Diffing for Data Updates (MEDIUM effort)

The existing `updateItem()` implementations in both `Outline` and `NestedTable` already rebind data via `items.setAll(list)`. This is already the "rebindData" pattern. The concrete optimization is to avoid the bulk `setAll()` allocation:

```java
// In Outline.updateItem() and NestedTable.updateItem():
List<JsonNode> incoming = SchemaNode.asList(item);
if (incoming.size() == items.size()) {
    // Patch in-place: only replace changed entries
    for (int i = 0; i < incoming.size(); i++) {
        if (!incoming.get(i).equals(items.get(i))) {
            items.set(i, incoming.get(i));
        }
    }
} else {
    items.setAll(incoming);
}
```

This avoids:
- Bulk `ObservableList.setAll()` triggering a full list-change event
- `SchemaNode.asList()` allocations for unchanged rows (VirtualFlow cell recycling handles the rest)
- Unnecessary cell factory invocations in VirtualFlow for rows that haven't changed

### Phase 3: Layout Decision Caching for Resize (MEDIUM effort)

Cache the `LayoutResult` (not just a boolean) per Relation node, keyed by `(SchemaPath, widthBucket)`:

```java
record LayoutDecisionKey(SchemaPath path, int widthBucket) {
    static LayoutDecisionKey of(SchemaPath path, double width) {
        return new LayoutDecisionKey(path, (int)(width / 10));
    }
}

// In AutoLayout or a dedicated LayoutCache:
Map<LayoutDecisionKey, LayoutResult> decisionCache = new HashMap<>();
```

`LayoutResult` already captures the full sub-tree layout state: `relationMode`, `primitiveMode`, `useVerticalHeader`, `tableColumnWidth`, `columnHeaderIndentation`, `constrainedColumnWidth`, and recursive `childResults`. Caching only a boolean would miss child layout state (e.g., `useVerticalHeader` changes, `columnHeaderIndentation` shifts).

**SchemaPath reliability**: SchemaPath is currently set only during `measure()` (see `RelationLayout.measure()` line 400-407 and `AutoLayout.measure()` line 135). If measure is bypassed by cache hit, SchemaPath may be null. SchemaPath derivation must be separated from the measure phase — derive statically from `SchemaNode` tree topology at construction time, not as a measure side-effect. This requirement is elevated to a standalone deliverable tracked in the RDR-ROADMAP.md execution plan (Wave 3, Step 9). It is a prerequisite for the LayoutResult caching strategy in Phase 3 and benefits all RDRs that use SchemaPath-addressed LayoutStylesheet lookups (RDR-013, 014, 015, 017, 018, 019).

On resize: if schema and width bucket are unchanged, restore `LayoutResult` state and skip `layout()` + `compress()`. The `buildControl()` phase still runs (since the control tree is width-dependent), but the expensive decision-making is eliminated.

### Phase 4: Convergent Width (Statistical Integration)

When combined with RDR-013 (statistical content-width measurement), converged widths provide an additional stability guarantee: once `ContentWidthStats.converged == true`, new data values cannot change the column width. This creates a natural "settle" phase:

```
First N data updates: widths may adjust (statistical convergence)
After convergence: widths frozen, only cell content updates
```

### Phase 5: Decoupled Header Rendering (SIEUFERD §3.4)

Render table headers immediately after layout decisions, before data arrives:
- Show column structure from schema
- Placeholder cells for pending data
- Data fills in progressively as it arrives (streaming/pagination)

SIEUFERD quote: "for fields not present in the old result, we show a placeholder icon."

**Forward dependency**: This phase depends on RDR-011 (LayoutDecisionTree, proposed) for schema-only header construction. It also requires a queuing/batching mechanism for streaming data arrival (see thread-safety risk below).

---

## Data-Only Change Detection

The mechanism for detecting "data-only change" vs "schema/style change":

```java
// In AutoLayout, the guard is implicit:
// - data.set() listener → setContent() → control.updateItem() when control != null
// - Schema change → setRoot() (currently no listener, but root.set() could null layout)
// - Stylesheet change → listener nulls layout, calls autoLayout()

// Explicit guard for future use:
boolean isDataOnlyChange = (root.get() == previousRoot && layout != null);
```

Currently, `root` property changes do NOT null `layout` — only the stylesheet listener does. If schema changes are introduced (e.g., `rootProperty().addListener(...)` in a consumer), `layout` must be nulled to force remeasure. This should be documented as a contract: `root` property change must invalidate `layout`.

**Implementation note**: A listener should be added to the `root` property that nulls `layout` and `measureResult`, matching the existing stylesheet listener behavior. This prevents the silent contract violation when `setRoot()` is called (e.g., by future RDR-018 Phase 3 query integration). Until this listener is added, this is a known limitation.

---

## Implementation

### Phase 1: Scroll preservation (LOW effort, no dependencies)
- Fix `showAsFirst(0)` in `Outline.updateItem()` using `OptionalInt` from `getFirstVisibleIndex()`
- Preserve original `showAsFirst(0)` behavior for first population (empty OptionalInt)

### Phase 2: List diffing (MEDIUM effort, no dependencies)
- Replace `items.setAll()` with incremental patching in `Outline.updateItem()` and `NestedTable.updateItem()`
- Benchmark: measure cell factory invocations before/after

### Phase 3: Layout decision caching (MEDIUM effort)
- Add `LayoutDecisionKey` and `Map<LayoutDecisionKey, LayoutResult>` cache
- Separate SchemaPath derivation from measure phase
- Coordinate cache invalidation with `autoLayout()` public method (which resets `layoutWidth = 0.0`)
- Invalidate cache when stylesheet listener fires (layout nulled)

### Phase 4: Convergent integration with RDR-013 (LOW effort, after RDR-013)
- When all ContentWidthStats converged, mark layout as "settled"
- Settled layout skips layout+compress on resize, goes straight to buildControl

### Phase 5: Decoupled headers (MEDIUM effort, after RDR-011)
- Separate header rendering from data rendering
- Headers built from LayoutDecisionTree (RDR-011)
- Data fills cells asynchronously via queuing mechanism

---

## Risks and Considerations

| Risk | Severity | Mitigation |
|------|----------|------------|
| Stale `LayoutResult` cache produces wrong layout | Medium | Invalidate cache when schema changes, stylesheet changes, or width changes by > 1 bucket |
| SchemaPath null when measure bypassed by cache | High | Derive SchemaPath from SchemaNode tree topology at construction, not during measure |
| `autoLayout()` public method resets `layoutWidth=0.0` | Medium | Cache invalidation must clear decision cache when `autoLayout()` is called |
| Thread-safety: `data.set()` from background thread | High | `data` is a JavaFX property — `setContent()` listener fires on calling thread. All `data.set()` calls must be on the JavaFX Application Thread (`Platform.runLater()`). Phase 5 streaming requires a dedicated batching queue that drains on the FX thread |
| Structural data changes (added/removed fields) with list diff | Medium | Detect structural change (size mismatch), fall back to `items.setAll()` |
| Scroll position restoration when item count decreases | Low | Clamp to valid range via `Math.min(idx, items.size() - 1)` |

## Success Criteria

1. Scroll position preserved across data updates in Outline
2. Data update with unchanged schema does NOT trigger relayout (already true via `setContent()` hot path — verify no regressions)
3. Resize with same width bucket skips layout decision phase (Phase 3)
4. Column widths stable after convergence (with RDR-013)
5. No visual artifacts from stale layout decisions
6. Measurable latency reduction: list-diff update < 2ms for 100-row change (vs current `setAll` path)
7. All existing tests pass

## Research Findings

### RF-1: Data-Change Fast Path Already Exists (Confidence: HIGH)
`AutoLayout.setContent()` already delegates to `control.updateItem(datum)` when `control != null`, bypassing measure/layout/compress/build entirely. The original RDR incorrectly claimed data changes null the layout. The actual bottleneck for data updates is: (a) `Outline.updateItem()` resetting scroll, (b) `items.setAll()` allocation overhead, (c) `SchemaNode.asList()` per-update allocation.

### RF-2: VirtualFlow Already Supports Incremental Update (Confidence: HIGH)
The existing VirtualFlow implementation recycles cells on `items.setAll()`. It does NOT rebuild the VirtualFlow container — it updates visible cells in-place. The bottleneck for resize is the outer pipeline (layout/compress/build), not the VirtualFlow update.

### RF-3: LayoutResult Captures Full Sub-Tree State (Confidence: HIGH)
`LayoutResult` records `relationMode` (`RelationRenderMode`), `primitiveMode` (`PrimitiveRenderMode`), `useVerticalHeader`, `tableColumnWidth`, `columnHeaderIndentation`, `constrainedColumnWidth`, and recursive `childResults`. This is sufficient to restore layout decisions without recomputation. A boolean cache would miss critical state like child `useVerticalHeader` rotations and `columnHeaderIndentation` shifts.

### RF-4: NestedTable Is Already Scroll-Stable (Confidence: HIGH)
`NestedTable.updateItem()` calls `rows.getItems().setAll(SchemaNode.asList(item))` without any scroll manipulation. The scroll bug is Outline-specific.

## Recommendation

Phase 1 (scroll preservation in Outline) is immediately implementable and delivers the most user-visible improvement with minimal risk. Phase 2 (list diffing) is a straightforward performance optimization. Phase 3 (layout decision caching) requires SchemaPath refactoring but delivers the resize performance win. Phases 4-5 build on other RDRs and can proceed later. Recommend implementing Phases 1 and 2 together as they are independent of other RDRs and address the two concrete bottlenecks in the data-update path.
