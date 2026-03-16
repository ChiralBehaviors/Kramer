# RDR-016: Layout Stability & Incremental Update

## Metadata
- **Type**: Feature
- **Status**: closed
- **Priority**: P2
- **Created**: 2026-03-15
- **Accepted**: 2026-03-15
- **Closed**: 2026-03-16
- **Close-reason**: implemented
- **Reviewed-by**: self
- **Related**: RDR-009 (MeasureResult immutability), RDR-010 (CSS integration remediation), RDR-011 (LayoutDecisionTree), RDR-012 (streaming invalidation), SIEUFERD (SIGMOD 2016 §3.4)

## Problem Statement

Kramer has three distinct code paths that trigger layout work, but the current architecture conflates them. SIEUFERD (SIGMOD 2016) describes a stable layout approach: "The on-screen layout remains undisturbed by the automatic interruption and restarting of queries." This RDR addresses three concrete problems:

1. **Scroll position loss**: `Outline.updateItem()` calls `showAsFirst(0)` unconditionally, resetting scroll to top on every data update (this is Outline-specific; `NestedTable.updateItem()` does not reset scroll)
2. **Resize rebuilds the entire control tree**: `resize()` calls `autoLayout(data, width)` which calls `layout.autoLayout()` -> `layout()` + `compress()` + `calculateRootHeight()` + `buildControl()`, discarding and recreating all VirtualFlow instances
3. **Stylesheet changes null the layout**: The stylesheet listener nulls both `layout` and `measureResult`, then calls `autoLayout()` — a full remeasure + relayout even when only visual properties changed (Optimizing stylesheet-change handling requires classifying CSS properties as visual-only vs layout-affecting; deferred to RDR-010 resolution. This RDR addresses Problems 1 and 2.)

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
- Bulk `ObservableList.LIST_UPDATED` event and per-cell cellFactory reinvocations for unchanged rows
- Unnecessary cell factory invocations in VirtualFlow for rows that haven't changed

### Phase 3: Layout Decision Caching for Resize (MEDIUM effort)

Cache the `LayoutResult` (not just a boolean) per Relation node, keyed by `(SchemaPath, widthBucket)`:

```java
record LayoutDecisionKey(SchemaPath path, int widthBucket, int dataCardinality) {
    static LayoutDecisionKey of(SchemaPath path, double width, int itemCount) {
        return new LayoutDecisionKey(path, (int)(width / 10), itemCount);
    }
}

// In AutoLayout or a dedicated LayoutCache:
Map<LayoutDecisionKey, LayoutResult> decisionCache = new HashMap<>();
```

`LayoutResult` already captures the full sub-tree layout state: `relationMode`, `primitiveMode`, `useVerticalHeader`, `tableColumnWidth`, `columnHeaderIndentation`, `constrainedColumnWidth`, and recursive `childResults`. Caching only a boolean would miss child layout state (e.g., `useVerticalHeader` changes, `columnHeaderIndentation` shifts).

Cache is invalidated when data cardinality changes. Phase 3 alone provides resize-only caching; full data-change caching requires Phase 4 (RDR-013 convergence).

**SchemaPath reliability**: SchemaPath is currently set only during `measure()` (see `RelationLayout.measure()` line 400-407 and `AutoLayout.measure()` line 135). If measure is bypassed by cache hit, SchemaPath may be null. SchemaPath derivation must be separated from the measure phase — derive statically from `SchemaNode` tree topology at construction time, not as a measure side-effect. This requirement is elevated to a standalone deliverable tracked in the RDR-ROADMAP.md execution plan (Wave 3, Step 9). It is a prerequisite for the LayoutResult caching strategy in Phase 3 and benefits all RDRs that use SchemaPath-addressed LayoutStylesheet lookups (RDR-013, 014, 015, 017, 018, 019).

On resize: if schema, width bucket, and data cardinality are unchanged, restore `LayoutResult` state and skip `layout()` + `compress()`. The `buildControl()` phase still runs (since the control tree is width-dependent), but the expensive decision-making is eliminated.

### Phase 4: Convergent Width (Statistical Integration)

When combined with RDR-013 (statistical content-width measurement), converged widths provide an additional stability guarantee: once `ContentWidthStats.converged == true`, new data values cannot change the column width. This creates a natural "settle" phase:

```
First N data updates: widths may adjust (statistical convergence)
After convergence: widths frozen, only cell content updates
```

Decoupled header rendering (streaming data fill) is out of scope; tracked as a dependency on RDR-011 resolution.

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

---

## Risks and Considerations

| Risk | Severity | Mitigation |
|------|----------|------------|
| Stale `LayoutResult` cache produces wrong layout | Medium | Invalidate cache when schema changes, stylesheet changes, width changes by > 1 bucket, or data cardinality changes |
| SchemaPath null when measure bypassed by cache | High | Derive SchemaPath from SchemaNode tree topology at construction, not during measure |
| `autoLayout()` public method resets `layoutWidth=0.0` | Medium | Cache invalidation must clear decision cache when `autoLayout()` is called |
| Thread-safety: `data.set()` from background thread | High | `data` is a JavaFX property — `setContent()` listener fires on calling thread. All `data.set()` calls must be on the JavaFX Application Thread (`Platform.runLater()`). Phase 5 streaming requires a dedicated batching queue that drains on the FX thread |
| Structural data changes (added/removed fields) with list diff | Medium | Detect structural change (size mismatch), fall back to `items.setAll()` |
| Scroll position restoration when item count decreases | Low | Clamp to valid range via `Math.min(idx, items.size() - 1)` |
| root property change without layout invalidation causes stale layout for new schema | High | Add root.addListener() that nulls layout and measureResult, mirroring stylesheet listener. Must precede RDR-018 Phase 3. |

## Alternatives Considered

### Alt A: Full Reactive/Observable Data Model

Replace the current pull-and-rebind pattern with a fully reactive model where `SchemaNode` subtrees observe `JsonNode` properties directly (conceptually similar to RDR-012's streaming invalidation proposal).

**Pros:**
- Data changes propagate automatically without any explicit `updateItem()` call chain
- Scroll position stability falls out naturally — only changed nodes fire
- Aligns with JavaFX property binding idioms

**Cons:**
- `JsonNode` is a Jackson immutable value tree, not a property graph; wrapping it requires a parallel mutable property layer
- The indirection layer (Jackson model → JavaFX property model) adds significant allocation pressure on every data update
- Schema-driven layouts already derive structure from `SchemaNode` topology, not from runtime observation — a reactive layer on top of an already-computed layout adds conceptual weight without benefit
- RDR-012 was explicitly deferred as "too speculative"; adopting its core mechanism here re-opens a rejected scope

**Rejection reason:** The existing `setContent()` hot path already achieves the same effect (no remeasure, no relayout on data change) without a reactive model. The scroll bug is a one-line fix; the list-diff optimization is a local change to `updateItem()`. Full reactivity would add a large new abstraction for a problem that is already 90% solved with targeted fixes.

---

### Alt B: Debounced Resize with No Caching

Instead of caching `LayoutResult` decisions, simply debounce `resize()` calls (e.g., coalesce rapid resize events and fire layout only after a 50–100 ms quiet period).

**Pros:**
- Trivial implementation: one `Timeline` or `PauseTransition` in `AutoLayout`
- No cache invalidation logic
- No SchemaPath refactoring dependency

**Cons:**
- Debouncing introduces visible lag: the layout visibly lags behind the drag handle, which is jarring in interactive contexts
- Does not reduce the cost of the layout pipeline itself — merely defers it; a single fire at the end of a resize drag still runs the full measure + layout + compress + buildControl chain
- Provides no benefit for the scroll-preservation or list-diffing problems
- Addresses resize thrashing only during interactive drag; programmatic width changes (e.g., from a splitter binding) may still fire frequently

**Rejection reason:** Debouncing trades correctness for performance in a way that degrades the perceived responsiveness of the UI. Phase 3's `LayoutDecisionKey` cache delivers the same resize-thrashing benefit without lag, and is composable with the other phases. Debouncing could serve as a complementary micro-optimization on top of Phase 3 but is insufficient as a standalone strategy.

---

### Alt C: Immutable Control Tree Reuse via Structural Sharing

Cache the entire JavaFX control sub-tree (the output of `buildControl()`) and reuse nodes across resize and data updates, similar to React's virtual DOM diffing. When width changes, diff the new `LayoutResult` against the cached one and patch only changed branches.

**Pros:**
- Could eliminate `buildControl()` cost on resize when layout decisions are unchanged
- Structural sharing would prevent `VirtualFlow` instances from being discarded on every resize, preserving their internal scroll state implicitly

**Cons:**
- JavaFX nodes carry mutable state (focus, hover, CSS pseudo-classes, animation state) that is hard to diff safely
- `VirtualFlow` instances have significant internal state (visible cell range, estimated height, scroll offset); re-parenting them between scenes or layout passes can cause rendering artifacts
- The diff algorithm itself has non-trivial cost and complexity; for Kramer's schema-driven layout, the `LayoutResult` equality check in Phase 3 is a far cheaper proxy for "same structural decisions"
- Requires deep changes to `Style.buildControl()` and all four cell types (`VerticalCell`, `HorizontalCell`, `AnchorCell`, `RegionCell`)

**Rejection reason:** JavaFX's rendering model is not designed for node reuse across layout passes the way the browser DOM is. The mutable state attached to live nodes makes safe diffing error-prone. Phase 3's `LayoutResult` cache achieves the key benefit (skip redundant decision computation) without touching the control tree construction path. Full structural sharing is a larger architectural change that belongs in a dedicated RDR if benchmarks show `buildControl()` to be the dominant cost after Phase 3.

---

## Success Criteria

1. Scroll position preserved across data updates in Outline
2. Data update with unchanged schema does NOT trigger relayout (already true via `setContent()` hot path — verify no regressions)
3. Resize with same width bucket skips layout decision phase (Phase 3)
4. Column widths stable after convergence (with RDR-013)
5. No visual artifacts from stale layout decisions
6. Measurable latency reduction: list-diff update < 2ms for 100-row change (vs current `setAll` path)
7. All existing tests pass

## Finalization Gate

### 1. Has the problem been validated with real user scenarios?

Yes, with one caveat. The scroll-reset bug (Phase 1) is validated by code inspection: `Outline.updateItem()` unconditionally calls `showAsFirst(0)` on every data update, which is confirmed by the explicit comment in the source ("Reset scroll to top after populating items"). Any user who scrolls down in an Outline view and then receives a data refresh will experience the regression. The data-update hot path (already working) is validated by the code path analysis in RF-1: `setContent()` branches on `control != null` and calls `updateItem()` directly without relayout. The resize-rebuild behavior (Phase 3) is validated by tracing `resize()` → `autoLayout()` → `buildControl()` in the current `AutoLayout` implementation. No explicit user study or profiling session is recorded in the research artifacts, but each of the three concrete problems is grounded in direct code evidence rather than speculation.

### 2. Is the solution the simplest that could work?

Yes, within each phase. Phase 1 is a single `OptionalInt` guard wrapping the existing `showAsFirst(0)` call — it preserves the original design intent for first population and adds scroll preservation for subsequent updates. Phase 2 replaces a bulk `setAll()` with an in-place patch loop; it uses only existing `ObservableList` API. Phase 3 introduces one `record` (`LayoutDecisionKey`) and one `HashMap` cache with a clear invalidation contract tied to the existing stylesheet listener and `autoLayout()` public method. The alternatives considered (Alt A: reactive model, Alt B: debounce, Alt C: structural sharing) are all more complex for equivalent or lesser benefit. The one non-trivial prerequisite — separating `SchemaPath` derivation from the measure phase — is a genuine structural dependency that cannot be simplified away without undermining cache key reliability, and it has been elevated to a tracked roadmap item rather than silently assumed.

### 3. Are all assumptions verified or explicitly acknowledged?

The following assumptions are verified by code inspection:

- `VirtualFlow.getFirstVisibleIndex()` returns `OptionalInt` (not `int`) — verified; the fix uses `OptionalInt` correctly.
- `NestedTable.updateItem()` does not reset scroll — verified (RF-4); the Phase 1 fix is Outline-specific by design.
- The data-change fast path does not null `layout` — verified (RF-1); the original problem statement was corrected in the Current Architecture section.
- `LayoutResult` captures full sub-tree state sufficient for cache key comparison — verified (RF-3).

The following assumptions are acknowledged but not fully verified:

- `SchemaPath` is currently null when measure is bypassed — stated as a risk with mitigation (derive statically from `SchemaNode` topology), but the refactoring is deferred to a roadmap item. Phase 3 cannot be implemented safely until this is resolved.
- Thread-safety of `data.set()` — documented as a constraint (must be on the JavaFX Application Thread) but no enforcement mechanism is proposed beyond documentation.
- The `root` property change must null `layout` — identified as a known limitation; a listener is proposed but not yet added.

### 4. What is the rollback strategy if this fails?

Each phase is independently reversible:

- **Phase 1 (scroll preservation):** The change is a three-line guard in `Outline.updateItem()`. If the `OptionalInt` clamping causes regressions (e.g., scroll restoration to a stale index after structural data changes), the guard can be removed and the original `showAsFirst(0)` unconditional call restored. The behavior reverts exactly to the current state.
- **Phase 2 (list diffing):** The in-place patch loop in `updateItem()` is guarded by a size equality check; mismatched sizes already fall through to `items.setAll()`. If the per-element equality check causes incorrect cell rendering (e.g., `JsonNode.equals()` false negatives), replace the patch loop with `items.setAll()` unconditionally, restoring prior behavior.
- **Phase 3 (layout decision caching):** The `LayoutDecisionKey` map is consulted only after the `SchemaPath` refactoring is complete. If the cache produces stale decisions, the cache can be disabled by returning `Optional.empty()` from the lookup, which causes the existing full layout pipeline to run unconditionally. The `HashMap` instance can remain in place as dead code until root-caused and re-enabled.
- **Phases 4–5:** These depend on RDR-013 and RDR-011 respectively. If either dependency is abandoned, Phases 4–5 are simply not implemented; no existing behavior is changed.

There is no shared mutable state introduced by this RDR that would make rollback of one phase affect others.

### 5. Are there cross-cutting concerns with other RDRs?

Several significant cross-cutting concerns exist:

- **RDR-009 (MeasureResult immutability):** The `LayoutResult` caching in Phase 3 assumes that `MeasureResult` instances are stable across layout passes. RDR-009's immutability guarantee is a prerequisite for safe cache key construction — a mutable `MeasureResult` would invalidate the assumption that the same `SchemaPath` + width bucket maps to the same layout decision.
- **RDR-011 (LayoutDecisionTree):** Phase 5 (decoupled header rendering) depends directly on RDR-011 providing a schema-only tree structure from which headers can be constructed before data arrives. Without RDR-011, Phase 5 has no clean place to derive header structure.
- **RDR-012 (streaming invalidation):** The thread-safety constraint documented here (all `data.set()` calls must be on the FX thread) is directly relevant to RDR-012's streaming data delivery. Phase 5's queuing/batching mechanism will need to integrate with whatever streaming model RDR-012 adopts.
- **RDR-013 (statistical content-width measurement):** Phase 4's convergent-width stability guarantee is a direct integration point. The `ContentWidthStats.converged` flag from RDR-013 would be used in Phase 4 to short-circuit the layout pipeline. These two RDRs should be implemented in sequence.
- **RDR-018 (query/stylesheet integration):** The `root` property change contract documented in the Data-Only Change Detection section is directly relevant to RDR-018's Phase 3 query integration, where `setRoot()` may be called dynamically. The proposed `root` property listener (nulling `layout` and `measureResult`) must be in place before RDR-018 Phase 3 is implemented.
- **SchemaPath refactoring (roadmap item):** The decision to derive `SchemaPath` statically from `SchemaNode` topology rather than as a measure side-effect is a prerequisite for Phase 3 and a cross-cutting benefit for RDR-013, 014, 015, 017, 018, and 019, all of which use SchemaPath-addressed LayoutStylesheet lookups.

## Research Findings

### RF-1: Data-Change Fast Path Already Exists (Confidence: HIGH)
`AutoLayout.setContent()` already delegates to `control.updateItem(datum)` when `control != null`, bypassing measure/layout/compress/build entirely. The original RDR incorrectly claimed data changes null the layout. The actual bottleneck for data updates is: (a) `Outline.updateItem()` resetting scroll, (b) `items.setAll()` allocation overhead, (c) `SchemaNode.asList()` per-update allocation.

### RF-2: VirtualFlow Already Supports Incremental Update (Confidence: HIGH)
The existing VirtualFlow implementation recycles cells on `items.setAll()`. It does NOT rebuild the VirtualFlow container — it updates visible cells in-place. The bottleneck for resize is the outer pipeline (layout/compress/build), not the VirtualFlow update.

### RF-3: LayoutResult Captures Full Sub-Tree State (Confidence: HIGH)
`LayoutResult` records `relationMode` (`RelationRenderMode`), `primitiveMode` (`PrimitiveRenderMode`), `useVerticalHeader`, `tableColumnWidth`, `columnHeaderIndentation`, `constrainedColumnWidth`, and recursive `childResults`. This is sufficient to restore layout decisions without recomputation. A boolean cache would miss critical state like child `useVerticalHeader` rotations and `columnHeaderIndentation` shifts.

### RF-4: NestedTable Is Already Scroll-Stable (Confidence: HIGH)
`NestedTable.updateItem()` calls `rows.getItems().setAll(SchemaNode.asList(item))` without any scroll manipulation. The scroll bug is Outline-specific.

### RF-5: Cursor Recovery Bug Fixed (Confidence: HIGH)
The unbind()-before-recoverCursor() bug (016-research-3) was fixed prior to this RDR. AutoLayout.java now saves cursorState before unbind().

## Recommendation

Phase 1 (scroll preservation in Outline) is immediately implementable and delivers the most user-visible improvement with minimal risk. Phase 2 (list diffing) is a straightforward performance optimization. Phase 3 (layout decision caching) requires SchemaPath refactoring but delivers the resize performance win. Phases 4-5 build on other RDRs and can proceed later. Recommend implementing Phases 1 and 2 together as they are independent of other RDRs and address the two concrete bottlenecks in the data-update path.
