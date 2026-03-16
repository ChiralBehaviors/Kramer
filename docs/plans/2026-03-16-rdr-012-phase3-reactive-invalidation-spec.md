# RDR-012 Phase 3: Reactive Invalidation Design Spec

**Date**: 2026-03-16
**RDR**: docs/rdr/RDR-012-reactive-semantic-constraint-layout.md
**Bead**: Kramer-5gv
**Status**: design (not yet scheduled)
**Depends on**: Kramer-044 (Phase 1 constraint model), RDR-016 closed (LayoutDecisionKey cache live)

---

## 1. Overview

Phase 3 enables partial re-solve when data changes, instead of full relayout. When a data
update affects only some `PrimitiveLayout` column widths, only the affected subtree's
constraints are re-solved by the `ExhaustiveConstraintSolver`. Unchanged subtrees reuse
their cached `LayoutResult`.

The baseline established by RDR-016 delivers streaming capability via decision caching and
direct `control.updateItem()` rebinding. Phase 3 is an enhancement over that baseline:
once the constraint solver exists (Phase 1), partial re-solve replaces conservative cache
invalidation with constraint-aware surgical invalidation — the solver can determine whether
a data change actually shifts mode decisions, rather than conservatively assuming it does.

The goal is `<16ms` relayout for point updates on a 20-node schema (case (a): values
within existing statistical range).

---

## 2. Current State

### 2.1 frozenResult invalidation

`PrimitiveLayout` holds two fields that together implement convergence-based caching:

```
private MeasureResult  frozenResult;
private long           frozenStylesheetVersion = -1;
```

`frozenResult` is set when the p90 width stabilizes across consecutive measure cycles
(convergence detection, Kramer-16k). Once frozen, `PrimitiveLayout` returns the frozen
`MeasureResult` without re-measuring.

Invalidation today is triggered by **stylesheet version change only**:
- When `frozenStylesheetVersion != currentStylesheetVersion`, the frozen result is
  discarded and re-measure runs.
- Data changes via `AutoLayout.setContent()` → `control.updateItem()` rebind visible
  cells but **do not** clear `frozenResult`. If the data at a path has grown wider than
  the frozen p90, the layout will not adapt until the next stylesheet change or explicit
  `autoLayout()` call.

### 2.2 Data change path

`AutoLayout.setContent()` (triggered by `data` property change):

```
if (control == null):
    Platform.runLater(() -> autoLayout(data, width))   // cold start
else:
    control.updateItem(data)                           // HOT PATH: no remeasure
layout()                                               // JavaFX layout pass
```

The hot path calls `control.updateItem()`, which delegates to
`Outline.updateItem()` / `NestedTable.updateItem()`. These call `items.set()` for
changed rows (RDR-016 Phase 2 list diff) but they do not re-invoke `measure()`.

### 2.3 LayoutDecisionKey cache

`AutoLayout.decisionCache` maps `LayoutDecisionKey(path, widthBucket, dataCardinality,
stylesheetVersion)` → `LayoutResult`. The key has no data-content component. A data
update that does not change cardinality will produce a cache hit on the same key, and the
cached `LayoutResult` will be returned — even if column widths have shifted.

### 2.4 Summary: the gap

| Mechanism | Cleared by stylesheet change | Cleared by data change |
|-----------|------------------------------|------------------------|
| `frozenResult` in `PrimitiveLayout` | Yes (`frozenStylesheetVersion` check) | No |
| `decisionCache` in `AutoLayout` | Yes (listener clears map) | No |
| `control.updateItem()` rebind | N/A — runs on data change | Yes (visual rebind only) |

Phase 3 must close both gaps: `frozenResult` must be clearable per data-path, and
`decisionCache` must be re-evaluated when underlying `PrimitiveLayout` p90 widths change.

---

## 3. Proposed Design

### 3.1 Core mechanism

Three logical steps on each data update:

**Step A — Data-path invalidation**: When `setContent()` receives new data, compare
incoming `JsonNode` values against the snapshot from the previous `setContent()` call.
For each `SchemaPath` whose value set has changed, clear the corresponding
`PrimitiveLayout`'s `frozenResult`. This targets only the leaves that actually changed.

**Step B — Selective re-measure**: For each `PrimitiveLayout` whose `frozenResult` was
cleared in Step A, invoke `measure()` on the new data. Capture the new p90 width.
Compare it against the previously frozen p90 (stored before clearing). Collect the set of
`PrimitiveLayout` nodes where `|new_p90 - old_p90| > EPSILON`.

**Step C — Partial constraint re-solve**: If the set from Step B is non-empty, re-solve
the constraint subtree rooted at the lowest common ancestor of all changed
`PrimitiveLayout` nodes. Pass updated `RelationConstraint` records (with the new
`tableWidth` values derived from the updated column widths) to `ExhaustiveConstraintSolver`.
If the solver returns the same mode assignment as the cached assignment, skip
`buildControl()` — only `updateItem()` is needed. If the assignment changed, rebuild
the control tree for the affected subtree only.

If the set from Step B is empty (no p90 widths shifted beyond epsilon), skip the solver
entirely and let RDR-016's `control.updateItem()` path handle the update (visual rebind
only, no layout work).

### 3.2 Epsilon

The epsilon threshold for "p90 width changed enough to matter" should be the same 10px
width bucket used by `LayoutDecisionKey` (i.e., `EPSILON = 10.0`). A p90 shift smaller
than one width bucket cannot change the bucket-keyed cache result, so it cannot affect
any constraint decision.

### 3.3 Data snapshot storage

`AutoLayout` must retain a snapshot of the previous data to enable comparison in Step A.
The snapshot is a `Map<SchemaPath, List<String>>` — the string-rendered values per
primitive field, ordered by row position. It is updated atomically at the end of each
successful `setContent()` cycle that runs the full hot path.

The snapshot is keyed by `SchemaPath` (not field name) to handle schemas where the same
field name appears at multiple nesting levels.

---

## 4. Schema-Tree Dependency Graph

### 4.1 Structure

The schema tree is already the dependency graph. Each `PrimitiveLayout` is a leaf. Each
`RelationLayout` is an interior node whose constraint decision depends on the p90 widths
of its descendant `PrimitiveLayout` nodes.

Dependency direction: `PrimitiveLayout` → parent `RelationLayout` → grandparent
`RelationLayout` → ... → root.

A width change at a leaf propagates upward. The question is: at what ancestor does the
mode decision *potentially* flip? The answer is: any `RelationLayout` whose
`tableWidth` (sum of child column widths) straddles the `availableWidth` threshold after
the update.

### 4.2 Propagation algorithm

Given the set of changed `PrimitiveLayout` nodes (from Step B):

1. Walk each changed leaf upward to the root, collecting all ancestor `RelationLayout`
   nodes.
2. Form the union of these ancestor sets. This is the set of `RelationLayout` nodes whose
   constraint inputs have changed.
3. Find the **lowest common ancestor** (LCA) of the changed `PrimitiveLayout` nodes.
   The subtree rooted at the LCA is the minimal re-solve scope.
4. Collect `RelationConstraint` records for all `RelationLayout` nodes in this subtree.
   Substitute updated `tableWidth` values for nodes whose descendant p90 widths changed.
5. Invoke `ExhaustiveConstraintSolver.solve()` on the updated constraint list with the
   subtree's available width.
6. Compare the returned mode assignments against the cached assignments for the same
   subtree nodes.

### 4.3 Sibling-lateral dependencies in outline mode

Outline column balancing creates lateral dependencies that the simple ancestor-path model
does not capture. When one primitive's column width changes, sibling columns in the same
outline tier may need rebalancing because outline assigns equal column widths across peers.

Consequence: for any `PrimitiveLayout` change within a `RelationLayout` rendering in
OUTLINE mode, the invalidation scope must include **all sibling `PrimitiveLayout` nodes**
at the same level, not just the changed one. The LCA computation in step 3 handles this
naturally (the LCA will be the parent `RelationLayout`), but the re-measure in Step B
must include all siblings, not just the changed leaf.

Detection: after Step A path invalidation, if the parent `RelationLayout` is in OUTLINE
mode, expand the re-measure set to all children of that parent.

### 4.4 Three complexity regimes

These regimes are inherited from RDR-012 RF-2 and must each be handled explicitly:

**(a) Point update, value within existing statistical range** — `frozenResult` remains
valid; only the visual cell value changes. `ContentWidthStats` min/max/p90 are unaffected.
Step B finds no p90 shift. Step C is skipped. RDR-016 `updateItem()` handles the update.
This is the expected case for streaming numeric updates (e.g., price feeds, sensor data).
Target latency: O(visible cells), typically <2ms.

**(b) Max-value removal or insertion** — The removed/inserted item was or becomes the
widest string at its path. `ContentWidthStats.p90Width` changes. Step B detects the shift.
The parent `RelationLayout` must re-solve. If the p90 crosses the width-bucket threshold
for `LayoutDecisionKey`, the mode assignment may change and `buildControl()` may run for
the affected subtree. This cannot be made O(log N) without auxiliary data structures
(augmented heaps keyed on rendered width); such structures are out of scope for Phase 3
and would be a Phase 4 concern. The fallback is to re-scan all items at the affected path
level to recompute p90.

**(c) Outline sibling-lateral** — Handled by the expansion rule in section 4.3. The
re-measure scope broadens to all siblings; re-solve runs for the parent. Complexity is
O(sibling_count × row_count) for the re-measure.

---

## 5. Implementation Plan

### Phase 3a: Data-path frozenResult invalidation

**Scope**: Add data-change tracking to `AutoLayout` and connect it to `PrimitiveLayout`.

**New state in `AutoLayout`**:
- `Map<SchemaPath, List<String>> dataSnapshot` — string values per primitive path from
  the last completed hot-path `setContent()`.

**New method in `PrimitiveLayout`**:
- `void clearFrozenResult()` — sets `frozenResult = null`. Exists today as internal
  state; needs a package-accessible mutator for `AutoLayout` to call.

**New method in `AutoLayout`** (called from within `setContent()` hot path):
- `Set<SchemaPath> detectChangedPaths(JsonNode newData)` — walks the schema tree,
  extracts string values for each `Primitive` path, compares against `dataSnapshot`.
  Returns the set of paths where the value list differs.

**Invalidation**: for each `SchemaPath` in the returned set, resolve the corresponding
`PrimitiveLayout` from the schema tree and call `clearFrozenResult()`.

**Snapshot update**: after invalidation, update `dataSnapshot` with the new values.

**Tests (TDD first)**:
1. Single-field update: change one value; assert only that path's `frozenResult` cleared.
2. Multi-field update: change two paths; assert both cleared, unrelated paths untouched.
3. Same-value update: same data; assert no `frozenResult` cleared.
4. New row appended: new data is a longer array; assert all paths' `frozenResult` cleared
   (size mismatch triggers full snapshot rebuild, consistent with RDR-016 list-diff
   fallback behavior).

### Phase 3b: Selective re-measure

**Scope**: Re-measure only `PrimitiveLayout` nodes whose `frozenResult` was cleared in
Phase 3a. Detect which p90 widths actually shifted.

**New method in `AutoLayout`**:
- `Map<SchemaPath, Double> remeasureChanged(Set<SchemaPath> changedPaths, JsonNode data)`
  — for each changed `SchemaPath`, resolves the `PrimitiveLayout`, invokes `measure()`,
  computes the new p90 from `ContentWidthStats`, and returns a map of
  `SchemaPath → new_p90` for paths where `|new_p90 - old_p90| > EPSILON`.

The old p90 is read from the snapshot captured before `clearFrozenResult()` was called;
the caller is responsible for saving it before clearing.

**Outline sibling expansion**: before invoking `measure()`, check if the changed
`PrimitiveLayout`'s parent `RelationLayout` renders in OUTLINE mode. If so, add all
sibling `PrimitiveLayout` nodes to the re-measure set.

**Tests (TDD first)**:
1. Value changes but stays in same p90 bucket: method returns empty map.
2. Value grows beyond bucket boundary: method returns entry with new p90.
3. Outline sibling expansion: change one field in an OUTLINE relation; assert all
   siblings are re-measured.
4. Value shrinks below old p90 but not below bucket boundary: empty map.

### Phase 3c: Partial constraint re-solve

**Scope**: Re-solve only the subtree rooted at the LCA of changed `PrimitiveLayout`
nodes. Reuse cached `LayoutResult` for unchanged subtrees.

**New method in `AutoLayout`**:
- `boolean partialResolve(Map<SchemaPath, Double> changedP90s)` — computes LCA,
  collects `RelationConstraint` records for the LCA subtree with updated `tableWidth`
  values, invokes `constraintSolver.solve()`, compares result against cached assignments,
  returns `true` if any mode assignment changed.

**If `partialResolve` returns `false`**: call `control.updateItem(data)` only
(visual rebind, no control tree rebuild).

**If `partialResolve` returns `true`**: rebuild the control subtree for the LCA node
only. The root-level `LayoutResult` is reused; only the affected subtree's `buildControl()`
is called.

**decisionCache interaction**: after partial re-solve, the cache entries for the affected
subtree nodes are updated with the new `LayoutResult`. Root and sibling subtrees retain
their existing cache entries.

**Tests (TDD first)**:
1. No p90 changes: `partialResolve` not called; only `updateItem()` runs.
2. P90 changes but mode assignment unchanged: `partialResolve` returns false;
   only `updateItem()` runs.
3. P90 changes and mode assignment flips: `partialResolve` returns true;
   affected subtree `buildControl()` is called.
4. Multiple leaves change in different subtrees: LCA is root; full solver re-runs
   (degenerate case but must not crash).
5. Hard-override node in changed subtree: solver respects `hardOverride=true` flag;
   forced mode never changes.

---

## 6. Risks

### 6.1 False invalidation from JsonNode equality

Detecting "data changed" requires comparing old and new `JsonNode` values per field.
`JsonNode.equals()` performs deep structural equality — this is correct but has cost
proportional to the value's subtree size for complex nodes.

For scalar primitives (numbers, strings), `JsonNode.equals()` is O(1). For array
primitives (a `Primitive` whose data is a JSON array), it is O(array length). The
comparison is bounded: it runs only for `Primitive` leaf nodes, not for relation
arrays (those are compared structurally by the list-diff in RDR-016 Phase 2).

Mitigation: compare the string representation of each row's field value
(`JsonNode.asText()`) rather than full structural equality. This is consistent with how
`PrimitiveLayout` measures width (it measures the rendered text, not the JSON structure).
The string comparison is O(string length), which is bounded by the maximum rendered
width — at most a few hundred characters for any practical data value.

### 6.2 Per-field vs per-row invalidation granularity

The proposed design invalidates per-field (per `SchemaPath`). The alternative is
per-row: when row N changes, invalidate all `PrimitiveLayout` nodes for row N.

Per-field granularity is correct for the p90-based `frozenResult` mechanism: the p90
is a field-level aggregate over all rows. A change to row 3's `amount` field matters only
if it shifts the field-level p90, not because row 3 specifically is important.

Per-row would over-invalidate for large datasets: updating one cell would invalidate all
field layouts for that row, even if the p90 for each field is unaffected.

The per-field approach is adopted. The comparison is: for each `SchemaPath`, collect the
list of string values across all rows and compare to the snapshot. This is an O(rows)
comparison per field, bounded by data size.

### 6.3 Concurrency: data updates on non-FX Application Thread

`AutoLayout` extends `AnchorPane` (a JavaFX `Region`). All mutations to its state and
to JavaFX scene-graph nodes must occur on the FX Application Thread (FXAT).

`data` is a `SimpleObjectProperty<JsonNode>`. Its listener (`setContent()`) fires on
whichever thread calls `data.set()`. If called from a background thread (e.g., a
GraphQL subscription callback or `CompletableFuture` continuation), the listener fires
off the FXAT, which violates JavaFX threading rules and will produce
`IllegalStateException` on scene graph mutations.

RDR-016 Phase 3 already flags this: "Thread-safety of `data.set()` — High — All
`data.set()` calls must be on FXAT."

Phase 3 enforcement mechanism:

- `setContent()` must begin with an FXAT assertion: if `!Platform.isFxApplicationThread()`,
  wrap the entire update in `Platform.runLater()` and return.
- This guard should be added regardless of Phase 3 (it is a correctness fix); it is
  called out here because Phase 3 makes the consequences of off-thread calls more severe
  (snapshot comparison and frozenResult mutation are not thread-safe).

The snapshot (`Map<SchemaPath, List<String>>`) is mutable state on `AutoLayout`. It
must only be read and written on the FXAT. No concurrent access is possible once the
FXAT guard is in place.

### 6.4 Snapshot memory overhead

`dataSnapshot` stores one `List<String>` per `SchemaPath`, each containing one string
per row. For a 20-field schema with 10,000 rows, this is 200,000 strings. At an average
of 10 characters each (UTF-16, 2 bytes), this is ~4 MB — acceptable for typical use.

For schemas with very long string values or very high row counts, memory pressure could
be significant. A configurable snapshot threshold (e.g., skip snapshot beyond 100,000
total values) is a Phase 4 concern. Phase 3 does not need it for the 20-node / <16ms
target.

### 6.5 Regression risk at setContent hot path

The current `setContent()` hot path is:
```
control.updateItem(data)
layout()
```

Phase 3 inserts `detectChangedPaths()` + conditional `remeasureChanged()` +
conditional `partialResolve()` before `control.updateItem()`. This lengthens the
hot path even for case (a) updates (the common case).

The case (a) path must short-circuit after `detectChangedPaths()` returns an empty set
(no values changed). The cost of `detectChangedPaths()` on an unchanged dataset must be
benchmarked and kept well under 1ms for a 20-field / 1000-row schema.

If `detectChangedPaths()` cost exceeds 1ms, the snapshot comparison must be made
lazy: trigger on a separate property observer rather than in the synchronous hot path.
This is a Phase 3 implementation detail to be resolved during TDD.

---

## Validation Criteria (from RDR-012 Phase 3)

- Streaming data update with `<16ms` relayout latency for typical schemas (5–20 nodes)
  for point updates (complexity case (a)).
- `frozenResult` is not cleared for paths whose data did not change.
- A p90 shift that does not cross a width-bucket boundary does not trigger
  `buildControl()`.
- A p90 shift that flips a mode assignment rebuilds only the affected subtree's control,
  not the full tree.
- All Phase 1 success criteria (greedy-or-better constraint solving) remain satisfied
  after Phase 3 integration.
- All RDR-016 success criteria (scroll preservation, list-diff update, decision cache)
  remain satisfied after Phase 3 integration.
- All existing tests pass.
