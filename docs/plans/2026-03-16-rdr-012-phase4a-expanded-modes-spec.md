# RDR-012 Phase 4a: Expanded Solver Search Space — Design Specification

**Created**: 2026-03-16
**RDR**: docs/rdr/RDR-012-reactive-semantic-constraint-layout.md
**Phase**: 4a (Additional Modes in Solver Search Space)
**Depends on**: Phase 1 (constraint solver, ExhaustiveConstraintSolver), RDR-015 closed
**Status**: Design complete, pending Phase 1 validation

---

## 1. Overview

Phase 1 of RDR-012 replaces Kramer's greedy binary layout decision with an exhaustive constraint
solver that assigns TABLE or OUTLINE to each `Relation` node globally. The solver's search space
is 2^N binary assignments over N relation nodes.

Phase 4a extends the solver to include CROSSTAB as a third option in the search space for
eligible nodes — those with a configured `pivot-field` and non-empty pivot data. For such
nodes the solver considers TABLE, OUTLINE, and CROSSTAB simultaneously, making the mode
selection globally optimal across all three modes. Nodes without a configured `pivot-field`
remain binary (TABLE or OUTLINE), so the expansion is additive and backward-compatible.

BAR, BADGE, and SPARKLINE are `PrimitiveRenderMode` decisions computed in
`PrimitiveLayout.measure()` before the constraint solver runs. They are not solver variables
and are not addressed in Phase 4a. Future work (Phase 4 research) may revisit this boundary.

---

## 2. Current State

### 2.1 CROSSTAB is a Hard Override

CROSSTAB mode (RDR-015 Phase 3) is configured via `LayoutStylesheet` per `SchemaPath`:

```java
String pivot = stylesheet.getString(path, "pivot-field", "");
String value = stylesheet.getString(path, "value-field", "");
```

When `pivot-field` is non-empty, `RelationLayout.layout()` enters the CROSSTAB branch before the
solver decision. This is a **hard override** — the solver receives the node's width contribution
already resolved as CROSSTAB, not as a variable.

The Phase 1 constraint model explicitly documents this:

> "When a Relation has `renderMode == CROSSTAB` via LayoutStylesheet, this is treated as a
> **hard constraint override** — the solver skips that node entirely and uses the
> stylesheet-forced mode."

### 2.2 BAR/BADGE/SPARKLINE Are Pre-Solver

`PrimitiveLayout.measure()` runs before `ExhaustiveConstraintSolver`. Primitive render mode
determines the width returned by `PrimitiveLayout.width()`, which becomes an input to the solver
as a fixed numeric parameter. The solver sees post-mode-decision widths; it does not vary
primitive modes. This boundary is correct and is retained in Phase 4a.

### 2.3 ExhaustiveConstraintSolver

The Phase 1 solver iterates over all 2^N binary assignments for N relation nodes, prunes
infeasible assignments via hard constraint propagation, and selects the assignment maximizing
TABLE density. Its search space is strictly TABLE/OUTLINE per node. CROSSTAB nodes are excluded
from the enumeration.

---

## 3. Proposed Design

### 3.1 CROSSTAB Feasibility

A relation node is **CROSSTAB-eligible** when both conditions hold at measure time:

1. `stylesheet.getString(path, "pivot-field", "")` is non-empty
2. `measureResult.pivotStats() != null && !measureResult.pivotStats().pivotValues().isEmpty()`

Condition 2 confirms that the data produced at least one distinct pivot value. Nodes failing
either condition remain binary (TABLE/OUTLINE) in the solver.

### 3.2 Solver Variable Becomes Ternary for Eligible Nodes

Rather than a boolean `useTable` per node, the solver variable per node becomes:

```
RelationRenderMode: { TABLE, OUTLINE, CROSSTAB }
```

For the K nodes that are CROSSTAB-eligible, the variable has three values. For the remaining
N-K nodes, the variable has two values (TABLE, OUTLINE). The search space is:

```
3^K * 2^(N-K)
```

Example: N=10, K=3 nodes with pivot-field → 3^3 * 2^7 = 27 * 128 = 3456 assignments.
Compared to 2^10 = 1024 for the binary case. The expansion is modest when K is small.

### 3.3 RelationConstraint Extension

`RelationConstraint` (the per-node constraint record produced before solver enumeration) is
extended with a feasibility flag:

```java
record RelationConstraint(
    double tableWidth,       // existing: width when TABLE mode
    double outlineWidth,     // existing: width when OUTLINE mode
    double crosstabWidth,    // new: width when CROSSTAB mode; 0 if not eligible
    boolean crosstabEligible // new: true if pivot-field configured and pivotStats non-empty
) {}
```

When `crosstabEligible == false`, the solver does not generate CROSSTAB assignments for this
node. The `crosstabWidth` field is zero and ignored.

### 3.4 CROSSTAB Width Computation

CROSSTAB width at a given node depends on data collected during `measure()` via `PivotStats`.
The formula:

```
crosstabWidth = rowHeaderWidth + pivotCount * pivotColumnWidth + insets
```

Where:
- `rowHeaderWidth` — sum of column widths for non-pivot, non-value child Primitives (the row
  identifier fields). These are fixed widths from child `MeasureResult.columnWidth()`.
- `pivotCount` — `measureResult.pivotStats().pivotCount()`.
- `pivotColumnWidth` — width of the value field's column (from the value field's
  `MeasureResult.columnWidth()`). All pivot columns receive equal width (equal-share allocation
  matching the crosstab layout implementation in RDR-015).
- `insets` — `style.getNestedHorizontalInset()`, same as TABLE mode.

This computation mirrors what `layoutCrosstab(double width)` already computes at render time.
The difference is that Phase 4a computes it eagerly during constraint generation so the solver
can evaluate CROSSTAB feasibility without triggering a render pass.

### 3.5 Feasibility Predicate

A CROSSTAB assignment for node i is feasible when:

```
crosstabWidth(i) <= availableWidth(i)
```

where `availableWidth(i)` is the width available to node i under the parent's current assignment
(same propagation as TABLE/OUTLINE feasibility in Phase 1). When `crosstabWidth > availableWidth`,
the CROSSTAB assignment is pruned — it is not enumerated.

If all three modes (TABLE, OUTLINE, CROSSTAB) are infeasible for a node at the current
available width, the solver uses the same fallback as Phase 1: the assignment is globally
infeasible and the solver widens the search by relaxing parent constraints (or falls back to
greedy for N > 15).

### 3.6 Solver Objective

The Phase 1 objective is: maximize TABLE assignments, depth-first lexicographic tie-break.

Phase 4a extends the objective to: maximize "dense" assignments where density rank is:
`CROSSTAB >= TABLE > OUTLINE`. CROSSTAB and TABLE are treated as equal density (both produce
column-oriented layouts). If CROSSTAB and TABLE are both feasible for a node, TABLE is preferred
by default. A stylesheet property `prefer-mode` (values: `table`, `crosstab`) may be added in a
future sub-phase to let users express a preference.

Rationale for CROSSTAB >= TABLE: CROSSTAB pivots N rows into M columns, producing a denser
layout for high-cardinality pivot data. Treating it as equal to TABLE avoids over-preferring
CROSSTAB for cases where pivot count is small and the sparse matrix is not actually denser.

### 3.7 Solver Algorithm Change

The Phase 1 solver iterates over binary assignments represented as bit vectors of length N.
Phase 4a replaces the bit vector with a base-3/2 mixed-radix enumeration:

```
for each assignment in mixed-radix(modes per node):
    if feasible under constraint propagation:
        evaluate objective
        update best
```

The enumeration remains exhaustive; no LP solver is introduced in Phase 4a. The N > 15 fallback
(greedy) is retained unchanged. Adding CROSSTAB-eligible nodes does not change the fallback
threshold — N counts all relation nodes, not just eligible ones.

---

## 4. PrimitiveRenderMode Integration

BAR, BADGE, and SPARKLINE (RDR-019) affect the width returned by `PrimitiveLayout.width()`.
These modes are decided in `PrimitiveLayout.measure()` prior to constraint generation. The
solver receives widths that already reflect the chosen primitive mode.

No change to this boundary is proposed in Phase 4a. The solver treats primitive widths as
fixed inputs — it does not enumerate primitive mode combinations.

Future research (tentatively Phase 4d, not scoped here): extend the solver to vary primitive
modes jointly with relation modes. This would expand the search space to
`3^K * 2^(N-K) * 3^P` where P is the number of Primitives with non-TEXT modes available.
For P=5 and N=10, K=2 this is 3^7 * 2^8 = 2187 * 256 = ~559K — still tractable. This is a
research topic and is explicitly out of scope for Phase 4a.

---

## 5. Implementation Plan

### Phase 4a-T1: Extend RelationConstraint with CROSSTAB Feasibility

**Scope**: `RelationConstraint` record, constraint generation in `ExhaustiveConstraintSolver`.

1. Add `crosstabWidth` and `crosstabEligible` fields to `RelationConstraint`.
2. In constraint generation: check `stylesheet.getString(path, "pivot-field", "")` and
   `measureResult.pivotStats()` to set `crosstabEligible`.
3. Compute `crosstabWidth` using the row-header + pivot-column formula (§3.4).
4. Add unit tests: eligible node produces correct `crosstabWidth`; non-eligible node has
   `crosstabEligible == false` and `crosstabWidth == 0`.

**Acceptance**: `RelationConstraint` carries correct CROSSTAB feasibility for schemas with and
without pivot-field configured. All Phase 1 tests continue to pass (no behavioral change for
non-eligible nodes).

### Phase 4a-T2: Extend ExhaustiveConstraintSolver for Ternary Decisions

**Scope**: `ExhaustiveConstraintSolver` enumeration loop, objective function.

1. Replace bit-vector enumeration with mixed-radix enumeration parameterized by per-node mode
   count (2 or 3).
2. Extend feasibility pruning to CROSSTAB: prune when `crosstabWidth > availableWidth`.
3. Extend objective: density-rank CROSSTAB equal to TABLE; tie-break prefers TABLE by default.
4. Retain N > 15 greedy fallback unchanged.
5. Unit tests:
   - Single CROSSTAB-eligible node: solver selects CROSSTAB when it fits, TABLE when CROSSTAB
     does not fit, OUTLINE when neither fits.
   - Mixed schema (2 eligible, 3 non-eligible): correct mixed-radix enumeration covers all
     3^2 * 2^3 = 72 assignments.
   - N=15 performance: search space 3^5 * 2^10 = 243 * 1024 ~= 249K assignments, must
     complete in < 1ms with pruning.

**Acceptance**: Solver produces globally optimal TABLE/OUTLINE/CROSSTAB assignments. No
regression on Phase 1 binary-only schemas.

### Phase 4a-T3: Integration Tests with Mixed TABLE/OUTLINE/CROSSTAB Schemas

**Scope**: `kramer` module test suite.

1. Construct a 3-level schema: root Relation (no pivot), child Relation A (pivot-field
   configured, data has 4 distinct pivot values), child Relation B (no pivot).
2. Test case A: available width admits CROSSTAB for A — solver assigns CROSSTAB to A.
3. Test case B: available width too narrow for CROSSTAB but admits TABLE for A — solver
   assigns TABLE to A.
4. Test case C: available width admits neither CROSSTAB nor TABLE for A — solver assigns
   OUTLINE to A.
5. Verify pixel density: CROSSTAB assignment for A in test case A produces a denser layout
   than TABLE (measurable via `RelationLayout.layout()` return value comparison).

**Acceptance**: All three test cases pass. Density improvement is measurable for test case A.

---

## 6. Risks

### 6.1 Search Space Growth

3^N for N=15 is ~14.3M combinations. In practice, K (eligible nodes) will be much smaller
than N — pivot-field is an intentional configuration, not a default. For K=5 and N=15, the
space is 3^5 * 2^10 = 249K assignments. With constraint propagation pruning (a parent-infeasible
assignment prunes all children), the effective enumerated count is typically an order of
magnitude smaller.

The N > 15 greedy fallback is retained. If K is large and the pruned search space exceeds the
time budget, Phase 4a falls back to the greedy path for the entire schema, logging a diagnostic.
A future LP-based solver (Phase 2 of RDR-012) handles the high-N case correctly.

### 6.2 CROSSTAB Width is Data-Dependent

`crosstabWidth` depends on `pivotStats.pivotCount()`, which reflects the distinct pivot values
present in the data at measure time. For streaming or frequently updated data:

- If the pivot value count changes between measure cycles, `RelationConstraint.crosstabWidth`
  becomes stale.
- Mitigation: any data change that triggers re-measure (per the existing `AutoLayout.setData()`
  contract and RDR-015 §Sparse Matrix Handling) also regenerates `RelationConstraint` records.
  No additional invalidation mechanism is required beyond what RDR-015 and RDR-016 already
  establish.

### 6.3 CROSSTAB Preference Ambiguity

The objective treats CROSSTAB and TABLE as equal density. For data with small pivot counts
(1-2 pivot values), CROSSTAB produces sparser output than TABLE. The default TABLE tie-break
handles this correctly — CROSSTAB is selected only when explicitly preferred or when TABLE is
infeasible. However, for dense pivot data (e.g., 12-column crosstab vs 3-column table), the
density comparison should favor CROSSTAB. A cardinality-based tiebreak rule may be needed;
this is left for Phase 4a post-implementation calibration.

### 6.4 Mixed-Radix Enumeration Implementation Risk

Replacing a bit-vector with a mixed-radix counter is a mechanical change, but the pruning
logic assumes a specific enumeration order (breadth-first by node depth). Verify that the
mixed-radix enumeration preserves the tree-structured pruning invariant from Phase 1.
Incorrect ordering invalidates the pruning and may produce suboptimal assignments without
failing tests.

---

## Appendix: Phase 4a Scope Boundary

The following are explicitly out of scope for Phase 4a:

- Primitive mode (BAR/BADGE/SPARKLINE) as solver variables — see §4.
- LP-based solver for large N — Phase 2 of RDR-012.
- User-facing constraint configuration UI — deferred to separate RDR (Phase 4c).
- Animated transitions on mode change — Phase 4b of RDR-012.
- `prefer-mode` stylesheet property for CROSSTAB/TABLE tie-breaking — post-implementation.
- SPARKLINE as a solver variable — depends on RDR-019 and the schema-layer extension it
  introduces.
