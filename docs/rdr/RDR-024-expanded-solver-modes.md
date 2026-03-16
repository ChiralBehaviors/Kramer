# RDR-024: Expanded Constraint Solver Modes

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P3
- **Created**: 2026-03-16
- **Related**: RDR-012 (constraint solver), RDR-015 (alternative rendering modes — CROSSTAB)

## Problem Statement

The Phase 1 constraint solver (RDR-012) assigns TABLE or OUTLINE to each Relation node globally, yielding a 2^N search space. CROSSTAB mode (RDR-015 Phase 3) is currently treated as a hard override: when a Relation has `pivot-field` configured in the `LayoutStylesheet`, the solver skips that node entirely and uses the stylesheet-forced mode. This means CROSSTAB is never evaluated against TABLE and OUTLINE as alternatives — the solver cannot choose CROSSTAB only when it produces a denser layout, nor can it fall back to TABLE when the crosstab would be too wide.

The result is that CROSSTAB is all-or-nothing: authors must manually configure it and accept whatever width it consumes. For schemas where CROSSTAB is beneficial at large widths but not at narrow ones, there is no adaptive behavior — the layout is either always a crosstab or never one, regardless of available space.

---

## Proposed Solution

Expand the solver's per-node variable from binary (TABLE, OUTLINE) to ternary (TABLE, OUTLINE, CROSSTAB) for nodes that are **CROSSTAB-eligible** at measure time. Non-eligible nodes remain binary. The search space becomes `3^K × 2^(N-K)` where K is the number of eligible nodes. For typical K << N this is a modest expansion.

### CROSSTAB eligibility

A Relation node is CROSSTAB-eligible when both conditions hold at measure time:

1. `stylesheet.getString(path, "pivot-field", "")` is non-empty.
2. `measureResult.pivotStats() != null && !measureResult.pivotStats().pivotValues().isEmpty()`.

Condition 2 confirms the data produced at least one distinct pivot value. Nodes failing either condition remain binary (TABLE/OUTLINE) in the solver and incur no search-space expansion.

### RelationConstraint extension

The record is extended by appending two fields. All seven existing fields are preserved:

```java
record RelationConstraint(
    // --- existing fields (unchanged) ---
    SchemaNode        node,             // the Relation node
    double            availableWidth,   // width budget at this node's position
    double            tableWidth,       // measured width in TABLE mode
    double            outlineWidth,     // measured width in OUTLINE mode
    boolean           hardCrosstab,     // true → stylesheet-forced CROSSTAB (Phase 1 behavior)
    RenderMode        forcedMode,       // non-null when mode is stylesheet-forced
    List<Integer>     childIndices,     // indices of direct Relation children in constraint list
    // --- new fields ---
    double            crosstabWidth,    // minimum feasibility width for CROSSTAB; 0 if not eligible
    boolean           crosstabEligible  // true iff pivot-field configured and pivotStats non-empty
) {}
```

Pseudocode is additive — field count goes from 7 to 9. Record construction at all existing call sites passes the two new fields last, defaulting to `(0.0, false)` for non-eligible nodes.

### hardCrosstab and crosstabEligible joint semantics

| `hardCrosstab` | `crosstabEligible` | Solver behavior |
|---|---|---|
| `true` | `false` | Pinned to CROSSTAB; excluded from enumeration (Phase 1 behavior retained). |
| `false` | `true` | Ternary solver variable: TABLE, OUTLINE, or CROSSTAB. |
| `true` | `true` | `hardCrosstab` wins; node is pinned and excluded from enumeration. Log a warning at constraint-generation time: `"[RDR-024] node {} has both hardCrosstab and crosstabEligible set; hardCrosstab takes precedence"`. |
| `false` | `false` | Binary solver variable: TABLE or OUTLINE only (existing behavior). |

Rationale: `hardCrosstab` represents explicit stylesheet intent; it is never overridden by the solver. `crosstabEligible` represents a solver-visible opportunity discovered at measure time.

### CROSSTAB width computation

```
crosstabWidth = rowHeaderWidth + pivotCount × pivotColumnWidth + insets
```

Where `rowHeaderWidth` is the sum of non-pivot, non-value child column widths; `pivotCount` is `measureResult.pivotStats().pivotCount()`; `pivotColumnWidth` is the value field's `MeasureResult.columnWidth()`; `insets` are the same as TABLE mode. This mirrors the formula already used by `layoutCrosstab()` at render time, computed eagerly during constraint generation so the solver can prune without triggering a render pass.

`crosstabWidth` is a **minimum feasibility bound**. The constraint checks `crosstabWidth <= availableWidth`; if more width is available the CROSSTAB layout degrades gracefully (pivot columns expand proportionally) without requiring a re-solve.

### Feasibility and pruning

A CROSSTAB assignment for node i is feasible when `crosstabWidth(i) <= availableWidth(i)`. When all three modes are infeasible, the solver uses the same fallback as Phase 1 (greedy for N > 15). No new failure modes are introduced.

The greedy fallback is extended to emit CROSSTAB for eligible nodes: when a node has `crosstabEligible == true` and `!hardCrosstab`, the greedy pass tries CROSSTAB first (if feasible), then TABLE, then OUTLINE — matching the solver objective ordering. This ensures the greedy fallback and the exhaustive solver are consistent in their mode preferences.

### Solver objective

The Phase 1 objective — maximize TABLE assignments, depth-first lexicographic tie-break — is extended to rank CROSSTAB equally with TABLE (both produce column-oriented layouts). When TABLE and CROSSTAB are both feasible and equally ranked, TABLE is preferred by default. A `prefer-mode` stylesheet property (values: `table`, `crosstab`) may be added post-implementation to let users express explicit preference.

The objective is computed as:

```
tableCount   = count of nodes assigned TABLE in a candidate assignment
crosstabCount = count of nodes assigned CROSSTAB in a candidate assignment
score = 2 * (tableCount + crosstabCount) + tableCount
```

The coefficient-2 term ranks any column-oriented assignment (TABLE or CROSSTAB) above any OUTLINE. The `+ tableCount` secondary term breaks ties by preferring TABLE over CROSSTAB when they are both feasible. Assignments are ranked by descending score; equal scores use depth-first lexicographic order (same as Phase 1 tie-break).

Rationale: treating CROSSTAB equal to TABLE in the primary term avoids over-preferring CROSSTAB for small pivot counts where the sparse matrix is not actually denser than a table.

### Enumeration change

The Phase 1 bit-vector enumeration is replaced with a **mixed-radix enumeration** parameterized by per-node mode count (2 or 3). The enumeration remains exhaustive. The N > 15 greedy fallback is retained unchanged. N counts all Relation nodes; CROSSTAB-eligible nodes do not lower the fallback threshold.

**Mixed-radix threshold adjustment**: When K > 0 eligible nodes are present, the effective search-space budget grows as `3^K × 2^(N-K)`. To prevent the fallback threshold from silently allowing an over-large search, either:
- Lower the threshold by K when K > 0: `effectiveThreshold = 15 - K`; or
- Add an explicit performance test: for K=5, N=15 (≈ 249K assignments with pruning), the test must complete in under 1ms. The test fails if this bound is violated, flagging that the threshold must be reduced.

The Phase 4a-T2 implementation must choose one strategy and document the decision.

---

## Alternatives Considered

### Alt A: Keep CROSSTAB as a hard override; add a width-based degradation guard

When `pivot-field` is configured and the CROSSTAB width exceeds available width, degrade to TABLE rather than forcing CROSSTAB at any cost. This requires no solver change — just a guard in `RelationLayout.layout()`.

**Pros**: zero solver complexity; the degradation guard is a few lines; solves the most common pain point (crosstab too wide).

**Cons**: degradation is always TABLE (never OUTLINE); the solver cannot weight CROSSTAB against TABLE globally; a schema with one CROSSTAB-eligible node and several TABLE-constrained siblings cannot be jointly optimized; the Phase 1 constraint model has no CROSSTAB awareness at all, so the hard override remains a blind spot.

**Rejected**: the degradation guard is correct and should be added regardless (it already appears in RDR-015's implementation notes). But it does not give the solver global awareness of CROSSTAB's width contribution. For schemas where CROSSTAB and TABLE compete for the same horizontal budget, the solver must consider them simultaneously.

### Alt B: Introduce a separate CROSSTAB solver phase after the TABLE/OUTLINE solve

Run the Phase 1 binary solver first to get TABLE/OUTLINE assignments. Then, as a second pass, evaluate each TABLE node that has `pivot-field` configured and replace TABLE with CROSSTAB if CROSSTAB is feasible and denser.

**Pros**: does not change the Phase 1 solver; the second pass is a simple post-processing loop; search space growth is zero.

**Cons**: two-pass greedy is not globally optimal — a second-pass swap of a TABLE node to CROSSTAB may make a previously feasible sibling TABLE assignment infeasible; the solver has no opportunity to jointly optimize across the two passes; the second pass cannot un-choose OUTLINE in favor of a CROSSTAB that the first pass missed.

**Rejected**: post-processing greedy patching re-introduces the locally-optimal, globally-suboptimal problem that Phase 1 was designed to eliminate. If the benefit of the constraint solver is global optimality, a two-pass architecture partially undoes that benefit for any schema with CROSSTAB-eligible nodes.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Search space growth for large K | Medium | K (eligible nodes) is always small — `pivot-field` is intentional configuration; for K=5, N=15: 3^5 × 2^10 ≈ 249K assignments, tractable with pruning |
| CROSSTAB width is data-dependent (pivot count changes with data) | Medium | Any data change that triggers re-measure also regenerates `RelationConstraint` records; no additional invalidation required |
| CROSSTAB vs TABLE preference ambiguity for small pivot counts | Low | Default TABLE tie-break handles this correctly; `prefer-mode` property deferred to post-implementation |
| Mixed-radix enumeration breaks tree-structured pruning invariant | Medium | Verify that enumeration order preserves breadth-first-by-depth property required for correct child pruning; unit test mixed-radix enumeration exhaustiveness independently |

---

## Implementation Plan

### Phase 4a-T1: Extend RelationConstraint with CROSSTAB feasibility

- Add `crosstabWidth` and `crosstabEligible` fields to `RelationConstraint`
- In constraint generation: check `stylesheet.getString(path, "pivot-field", "")` and `measureResult.pivotStats()`
- Compute `crosstabWidth` using the row-header + pivot-column formula
- Unit tests: eligible node → correct `crosstabWidth`; non-eligible node → `crosstabEligible == false`, `crosstabWidth == 0`
- Acceptance: all Phase 1 tests pass (no behavioral change for non-eligible nodes)

### Phase 4a-T2: Extend ExhaustiveConstraintSolver for ternary decisions

- Replace bit-vector enumeration with mixed-radix enumeration parameterized by per-node mode count
- Extend feasibility pruning to CROSSTAB: prune when `crosstabWidth > availableWidth`
- Extend objective: CROSSTAB ranked equal to TABLE; tie-break prefers TABLE
- Retain N > 15 greedy fallback unchanged
- Unit tests: single eligible node (CROSSTAB when fits, TABLE when CROSSTAB does not, OUTLINE when neither); mixed schema 3^2 × 2^3 = 72 assignments; N=15 with K=5 performance (<1ms with pruning)

### Phase 4a-T3: Integration tests with mixed TABLE/OUTLINE/CROSSTAB schemas

- 3-level schema: root Relation (no pivot), child A (pivot-field, 4 distinct values), child B (no pivot)
- Test case A: width admits CROSSTAB for A → solver assigns CROSSTAB
- Test case B: width too narrow for CROSSTAB but admits TABLE → solver assigns TABLE
- Test case C: neither CROSSTAB nor TABLE fits → solver assigns OUTLINE
- Verify density: CROSSTAB for A in test case A is denser than TABLE (measurable via `RelationLayout.layout()` return comparison)

---

## Finalization Gate

### 1. Has the problem been validated with real user scenarios?

Partially. The hard-override limitation of Phase 1 for CROSSTAB is confirmed by RDR-015's implementation notes and the Phase 1 solver scope description in RDR-012: "When a Relation has `renderMode == CROSSTAB` via LayoutStylesheet, this is treated as a hard constraint override — the solver skips that node entirely." The adaptive behavior gap (CROSSTAB only when it fits) has not been validated in a user study. It is analytically correct that the current design cannot choose between CROSSTAB and TABLE at runtime based on available width.

### 2. Is the solution the simplest that could work?

Yes. The change is confined to `RelationConstraint` (two new fields) and `ExhaustiveConstraintSolver` (mixed-radix enumeration replacing bit-vector, feasibility check extended by one branch). No new solver algorithm is introduced; no new dependencies are added. The N > 15 greedy fallback and the overall solver interface are unchanged.

### 3. Are all assumptions verified or explicitly acknowledged?

Verified: `PivotStats` is present in `MeasureResult` (RDR-015 closed); `pivotStats.pivotCount()` is available at constraint generation time. The crosstab width formula (`rowHeaderWidth + pivotCount × pivotColumnWidth + insets`) mirrors the existing `layoutCrosstab()` implementation.

Acknowledged gaps: cardinality-based tie-breaking between CROSSTAB and TABLE for dense vs sparse pivot data is deferred to post-implementation calibration. The mixed-radix enumeration must be verified to preserve the tree-structured pruning invariant from Phase 1; this is a correctness risk that must be addressed during Phase 4a-T2 implementation.

### 4. What is the rollback strategy if this fails?

The `crosstabEligible` field on `RelationConstraint` defaults to `false` for all existing nodes. If Phase 4a-T2 (mixed-radix enumeration) introduces regressions in binary-only schemas, revert the solver to bit-vector enumeration — `RelationConstraint.crosstabEligible == false` for all nodes means the mixed-radix enumerator degenerates to the Phase 1 bit-vector case, so the two implementations are equivalent and the rollback is a single-class revert.

### 5. Are there cross-cutting concerns with other RDRs?

- **RDR-015**: `PivotStats`, `pivotCount()`, and the CROSSTAB branch in `RelationLayout.layout()` are live (RDR-015 closed). Phase 4a reads `pivotStats` from `MeasureResult` without modifying how it is collected.
- **RDR-012**: Phase 4a is Phase 4 of RDR-012. Phase 1 (binary constraint solver) must be complete and passing before ternary enumeration is introduced.
- **RDR-023 (Phase 3 reactive invalidation)**: If reactive invalidation is implemented before Phase 4a, the partial re-solve path must account for CROSSTAB-eligible nodes. The `RelationConstraint.crosstabEligible` field is transparent to the LCA computation and subtree re-solve; no additional coordination is required.

---

## Success Criteria

- [ ] All Phase 1 binary-only `ConstraintSolverTest` cases pass unchanged
- [ ] Single CROSSTAB-eligible node: solver selects CROSSTAB when it fits, TABLE when CROSSTAB does not, OUTLINE when neither fits
- [ ] Mixed schema (K=2 eligible, 3 non-eligible): mixed-radix enumeration covers all 3^2 × 2^3 = 72 assignments
- [ ] N=15 with K=5 performance: search space ≈ 249K assignments completes in <1ms with pruning
- [ ] Integration test case A: CROSSTAB assignment produces measurably denser layout than TABLE for the same node
- [ ] No regression for non-eligible schemas: `crosstabEligible == false` for all nodes produces identical results to Phase 1
