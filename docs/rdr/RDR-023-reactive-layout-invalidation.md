# RDR-023: Reactive Layout Invalidation

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P3
- **Created**: 2026-03-16
- **Related**: RDR-012 (constraint solver), RDR-016 (layout stability / incremental update)

## Problem Statement

RDR-016 delivered the `LayoutDecisionKey` cache and `control.updateItem()` hot path. These ensure that data updates do not trigger full relayout when the schema and width are stable. However, two cache invalidation gaps remain:

1. **`frozenResult` not cleared on data change.** `PrimitiveLayout` holds a `frozenResult: MeasureResult` and a `frozenStylesheetVersion`. When new data arrives that is wider than the frozen p90, the layout does not adapt until the next stylesheet change. A price-feed update that pushes a column beyond its frozen width produces a visually truncated column with no automatic correction.

2. **`decisionCache` not re-evaluated on data change.** `AutoLayout.decisionCache` maps `LayoutDecisionKey(path, widthBucket, dataCardinality, stylesheetVersion)` → `LayoutResult`. The key has no data-content component. A data update that changes the p90 column width without changing cardinality hits the same cache key and returns a stale `LayoutResult` — potentially the wrong TABLE vs OUTLINE assignment.

The result is that RDR-016's streaming updates are visually correct only when new data values fall within the previously measured statistical range. Any out-of-range update silently produces a wrong layout. This is acceptable as a known limitation in RDR-016 but is not acceptable for production streaming use cases (monitoring dashboards, live query results).

RDR-012 Phase 3 addresses this by using the constraint solver to determine whether a data change actually shifts mode decisions, rather than conservatively assuming it does. The goal is `<16ms` relayout for point updates (values within existing statistical range) on a 20-node schema.

---

## Proposed Solution

Three logical steps are inserted into the `setContent()` hot path between receiving new data and calling `control.updateItem()`:

**Step A — Data-path invalidation.** Compare incoming `JsonNode` values against a snapshot from the previous call. For each `SchemaPath` whose value set has changed, clear the corresponding `PrimitiveLayout`'s `frozenResult`. Only changed leaves are targeted.

**Step B — Selective re-measure.** For each `PrimitiveLayout` whose `frozenResult` was cleared, invoke `measure()` on the new data. Capture the new p90 width. Collect the set of `PrimitiveLayout` nodes where `|new_p90 - old_p90| > EPSILON` (where EPSILON = 10.0, the same width bucket used by `LayoutDecisionKey`). A shift smaller than one bucket cannot change any constraint decision.

**Step C — Partial constraint re-solve.** If Step B returned a non-empty change set, find the lowest common ancestor (LCA) of the changed leaves in the schema tree. Re-solve only the constraint subtree rooted at the LCA with updated `RelationConstraint` records. If the solver returns the same mode assignment as cached, only `updateItem()` runs. If the assignment changed, rebuild only the affected subtree's control tree.

If Step B returns an empty set (no p90 widths shifted), skip the solver entirely and let RDR-016's `updateItem()` path handle the update as it does today.

### Data snapshot

`AutoLayout` retains a `Map<SchemaPath, List<String>>` — string-rendered values per primitive field, ordered by row position. Updated atomically at the end of each successful hot-path call. Keyed by `SchemaPath` (not field name) to handle schemas where the same name appears at multiple nesting levels.

### Outline sibling-lateral expansion

Outline column balancing creates lateral dependencies: when one field's width changes, sibling columns at the same outline tier may need rebalancing. Detection rule: if the changed `PrimitiveLayout`'s parent `RelationLayout` is in OUTLINE mode, expand the re-measure set to all sibling `PrimitiveLayout` nodes at the same level before running Step B. The LCA computation naturally captures the parent in Step C.

### Three complexity regimes

| Case | Behavior | Target latency |
|------|----------|---------------|
| (a) Point update, value within existing range | Step B returns empty; only `updateItem()` runs | <2ms |
| (b) Max-value removal/insertion (p90 shifts) | Re-measure affected path; partial re-solve | <16ms |
| (c) Outline sibling-lateral | Re-measure all siblings of changed field | O(siblings × rows) |

### FXAT enforcement

`setContent()` must begin with a JavaFX Application Thread assertion. If called from a background thread (e.g., a GraphQL subscription callback), wrap the entire update in `Platform.runLater()` and return. The snapshot comparison and `frozenResult` mutation are not thread-safe; the FXAT guard makes this safe by construction.

---

## Alternatives Considered

### Alt A: Conservative full-invalidation on every data change

On any `setContent()` call, clear all `frozenResult` fields and the entire `decisionCache`, then let the next layout pass re-measure everything from scratch.

**Pros**: simple, always correct, no snapshot logic.

**Cons**: defeats the entire purpose of RDR-016's caching investment; full re-measure of a 20-node schema with 1000 rows is O(20 × 1000) string measurements per update — well above 16ms for streaming data at even modest update rates; loses scroll position on every update (the scroll-preservation work from RDR-016 Phase 1 would be meaningless).

**Rejected**: this is the pre-RDR-016 behavior. The goal is to improve on it, not to restore it selectively.

### Alt B: Per-stylesheet-version invalidation with version bumping on data change

Bump the stylesheet version counter on every `setContent()` call. The existing `frozenStylesheetVersion` check in `PrimitiveLayout` would then clear `frozenResult` automatically, and the `decisionCache` stylesheet-version listener would clear the cache.

**Pros**: reuses the existing invalidation mechanism with one-line change; no new snapshot infrastructure.

**Cons**: bumping stylesheet version triggers a full re-measure of all `PrimitiveLayout` nodes on every data update, not just the changed ones — equivalent to Alt A in cost; also invalidates purely visual CSS properties that have no relationship to the data change; the stylesheet version is a semantic versioning mechanism for style changes, not a data-change signal.

**Rejected**: conflates two independent invalidation axes (style changes vs data changes). The existing version mechanism is correct and should remain for style changes only.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| False invalidation from `JsonNode` comparison | Low | Compare `JsonNode.asText()` (bounded by rendered string length) rather than structural equality; consistent with how `PrimitiveLayout` measures |
| Per-field vs per-row granularity | Low | Per-field is correct for p90-based `frozenResult`; per-row would over-invalidate for large datasets |
| Off-FX-thread data updates | High | Enforce FXAT assertion at start of `setContent()`; wrap in `Platform.runLater()` if violated |
| Snapshot memory overhead | Low | 200K strings for 20-field × 10K rows ≈ 4 MB; acceptable; high-row threshold configurable in a future phase |
| Step A cost on unchanged datasets | Medium | `detectChangedPaths()` on an unchanged dataset must be benchmarked to confirm <1ms for 20-field / 1000-row schema; if not, make comparison lazy |
| Regression at `setContent()` hot path | Medium | Case (a) must short-circuit after `detectChangedPaths()` returns empty; benchmark confirms case (a) adds no visible latency |

---

## Implementation Plan

### Phase 3a: Data-path `frozenResult` invalidation

- Add `Map<SchemaPath, List<String>> dataSnapshot` to `AutoLayout`
- Add `void clearFrozenResult()` package-accessible mutator to `PrimitiveLayout`
- Add `Set<SchemaPath> detectChangedPaths(JsonNode newData)` to `AutoLayout`
- Invalidate on mismatch; update snapshot on each successful hot-path completion
- TDD: single-field update; multi-field update; same-value no-op; new row appended (full rebuild)

### Phase 3b: Selective re-measure

- Add `Map<SchemaPath, Double> remeasureChanged(Set<SchemaPath> changedPaths, JsonNode data)` to `AutoLayout`
- OUTLINE sibling expansion: if parent is OUTLINE, add all siblings to re-measure set before invoking `measure()`
- Return only paths where `|new_p90 - old_p90| > 10.0`
- TDD: value within bucket (empty map); value crosses bucket boundary; outline sibling expansion; shrink below old p90 but within bucket (empty map)

### Phase 3c: Partial constraint re-solve

- Add `boolean partialResolve(Map<SchemaPath, Double> changedP90s)` to `AutoLayout`
- Compute LCA of changed leaves; collect `RelationConstraint` records for LCA subtree with updated `tableWidth`
- Compare solver result against cached assignments; return `true` only if any mode assignment changed
- `false` → `control.updateItem()` only; `true` → rebuild LCA subtree only
- Update `decisionCache` for affected subtree nodes; root and sibling subtrees retain existing cache entries
- TDD: no p90 changes (no solver call); p90 changes but mode unchanged (no rebuild); mode flips (rebuild); multiple changed leaves in different subtrees (LCA = root, full re-solve); hard-override node respected

---

## Finalization Gate

### 1. Has the problem been validated with real user scenarios?

Partially. The `frozenResult` gap is confirmed by code inspection: `PrimitiveLayout` invalidates only on `frozenStylesheetVersion` mismatch, not on data change (documented in RDR-016 §Current Architecture). The `decisionCache` gap is confirmed by the key definition: `LayoutDecisionKey(path, widthBucket, dataCardinality, stylesheetVersion)` has no data-content component. Any streaming use case that produces out-of-range values (price feed exceeding historical max, sensor reading outside prior p90) will silently produce a wrong layout. No user study measuring the perceptibility of stale layouts has been conducted.

### 2. Is the solution the simplest that could work?

Yes. Steps A–C are minimal additions to the existing hot path. Step A is a map comparison. Step B re-invokes existing `measure()` logic. Step C re-invokes the existing constraint solver on a subtree. No new layout phases are introduced. The EPSILON threshold reuses the existing width bucket constant from `LayoutDecisionKey`.

### 3. Are all assumptions verified or explicitly acknowledged?

Verified: RDR-016's `LayoutDecisionKey` cache is live (RDR-016 closed). `frozenResult` invalidation is stylesheet-version-only (confirmed by code inspection). `JsonNode.asText()` is O(string length), bounded for practical data values.

Acknowledged gaps: snapshot memory overhead for very large datasets (>100K rows) is not yet measured; threshold-based truncation is a future concern. The LCA computation assumes well-formed schema tree topology (no cycles); this is structurally guaranteed by the sealed `SchemaNode` hierarchy. The `detectChangedPaths()` cost for large datasets must be benchmarked during Phase 3a TDD.

### 4. What is the rollback strategy if this fails?

Each phase is independently reversible. Phase 3a: remove `dataSnapshot`, `clearFrozenResult()`, and `detectChangedPaths()` — the hot path reverts to the RDR-016 state. Phase 3b: remove `remeasureChanged()` — unchanged from Phase 3a. Phase 3c: remove `partialResolve()` — data changes continue to use `updateItem()` without solver re-evaluation, same as RDR-016. No shared state is introduced between phases.

### 5. Are there cross-cutting concerns with other RDRs?

- **RDR-016**: Phase 3 builds on RDR-016's `LayoutDecisionKey` cache and `updateItem()` hot path. All RDR-016 success criteria must remain satisfied after Phase 3 integration.
- **RDR-012**: Phase 3 requires the constraint solver (Phase 1 of RDR-012) to be operational. Phase 3 is a Phase 1 consumer; it cannot be implemented before Phase 1 is complete.
- **RDR-013**: `ContentWidthStats.p90Width` is the width value compared against EPSILON. Phase 3 depends on RDR-013's statistical width measurement being in place.
- **RDR-009**: `frozenStylesheetVersion` mechanism is defined in RDR-009's `MeasureResult` immutability contract. Phase 3 adds a second invalidation axis without modifying the existing stylesheet-version axis.

---

## Success Criteria

- [ ] Point update (value within existing statistical range) produces `<16ms` relayout latency on a 20-node schema
- [ ] `frozenResult` is not cleared for paths whose data did not change
- [ ] p90 shift that does not cross a 10px width-bucket boundary does not trigger `buildControl()`
- [ ] p90 shift that flips a mode assignment rebuilds only the affected subtree, not the full control tree
- [ ] All Phase 1 constraint solver success criteria remain satisfied after Phase 3 integration
- [ ] All RDR-016 success criteria (scroll preservation, list-diff update, decision cache) remain satisfied
- [ ] All existing tests pass
