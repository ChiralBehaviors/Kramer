---
title: "Reactive Semantic Constraint Layout"
id: RDR-012
type: Research
status: closed
close_reason: "implemented (Phase 1 constraint solver) + designed (Phases 2-4)"
priority: P2
author: Hal Hildebrand
created: 2026-03-15
accepted_date: 2026-03-16
closed_date: 2026-03-16
reviewed-by: self
---

# RDR-012: Reactive Semantic Constraint Layout

## Metadata
- **Type**: Research
- **Status**: closed
- **Priority**: P2
- **Created**: 2026-03-15
- **Accepted**: 2026-03-16
- **Closed**: 2026-03-16
- **Close-reason**: implemented (Phase 1 constraint solver) + designed (Phases 2-4 → RDR-022/023/024/025)
- **Reviewed-by**: self
- **Related**: RDR-011 (layout protocol), RDR-009 (MeasureResult immutability), RDR-015 (alternative rendering modes), RDR-016 (layout stability / incremental update), RDR-018 (query-semantic layout stylesheet)

## Problem Statement

Kramer's current layout algorithm uses a greedy bottom-up threshold comparison (`calculateTableColumnWidth() + nestedHorizontalInset <= width`) at each Relation node to decide between table and outline mode. This has three limitations:

1. **Locally optimal, not globally optimal**: Each node decides independently without considering the impact on sibling or ancestor nodes. A globally suboptimal layout can result when a parent's mode choice constrains children in ways that waste space elsewhere.

2. **No semantic awareness**: All fields are treated as equally important for space allocation. A high-importance "revenue" column gets the same density treatment as a low-importance "audit_id" column.

3. **Full relayout on any change**: Any data change triggers complete re-measurement and relayout. For streaming/live data scenarios (monitoring dashboards, time-series), this makes the algorithm unsuitable for real-time use.

---

## Core Idea: Constraint Solver as Core, C2 and C3 as Independent Enhancements

The constraint solver (C1) is the foundational contribution. LLM annotation (C2) and reactive invalidation (C3) are independent enhancements that build on it but do not depend on each other:

- **C1 + C2** are genuinely interactive: semantic weights feed directly into constraint costs.
- **C1 + C3** are integrated: the constraint solver enables partial re-solve on data change.
- **C2 + C3** are independent: neither requires the other.

```
Schema ──LLM annotation──► AnnotatedSchema (semantic importance per field)
 AnnotatedSchema ──ORC generation──► ConstraintSet per node (globally optimal)
ObservableData ──delta──► targeted invalidation ──► partial re-solve (streaming)
```

Four properties emerge that **no existing system has simultaneously**:
1. **Structurally grounded** — constraints auto-derived from schema, not hand-authored
2. **Semantically aware** — LLM-annotated importance weights guide density allocation
3. **Globally optimal** — OR-constraint solving replaces greedy bottom-up thresholds
4. **Reactive** — data changes trigger surgical invalidation via schema-tree dependency graph

---

## Component 1: Schema-Driven OR-Constraint Layout

### Background

ORC Layout (CHI 2019) introduces OR-constraints — disjunctive choices embedded in a standard soft/hard linear constraint system. ORCSolver (CHI 2020) solves these at near-interactive rates via branch-and-bound. Open source: github.com/YueJiang-nj/ORCSolver-CHI2020.

### Application to Kramer

Replace the binary `if (tableWidth <= width)` decision with a constraint set per schema node:

```
// Generated for each Relation node:
OR(
  TABLE_MODE: { width >= sum(child_column_widths) + insets },
  OUTLINE_MODE: { width >= max(child_widths) + label_width }
)
```

The solver chooses modes simultaneously across the entire schema tree (globally optimal) rather than greedy bottom-up. This enables:
- **Multi-mode per node**: Not just table/outline but sparkline, badge, carousel, collapsed-summary
- **Soft constraints**: "prefer table but allow outline if density drops below threshold"
- **Hard constraints**: "this field must always be visible"
- **Cross-node dependencies**: "if parent uses table, child should prefer inline rendering"

**Novel territory**: No published work combines schema-driven recursive layout with ORC constraint solving.

### Solver Strategy

**Phase 1 — Exhaustive Enumeration**: For binary-only constraints (table/outline per Relation node), the problem is 2^N for N relation nodes. With typical schema sizes of N=5–20, exhaustive enumeration is tractable: 2^5=32 to 2^20≈1M combinations. Prune via hard constraint propagation to keep solve time <1ms for N≤15. No external solver dependency required.

**N > 15 behavior**: For N > 15, fall back to the greedy threshold (current behavior) and log a diagnostic. N > 15 is the Phase 4 domain for LP-based solving.

**Scope of Phase 1 constraint search space**: Phase 1 covers TABLE and OUTLINE modes only (2^N enumeration for N relation nodes). CROSSTAB (introduced by RDR-015 Phase 3) is NOT part of the Phase 1 search space. When a Relation has `renderMode == CROSSTAB` via LayoutStylesheet, this is treated as a **hard constraint override** — the solver skips that node entirely and uses the stylesheet-forced mode. Phase 4 is intended to expand the constraint search space to include CROSSTAB and additional modes. This means Phase 1 is backward-compatible with the existing binary decision while coexisting with RDR-015's crosstab feature.

**Phase 2 — LP Solver for Soft Constraints**: When soft constraints with continuous cost functions are introduced, evaluate:
- Apache Commons Math `SimplexSolver` — lightweight, already in the Maven ecosystem
- ojAlgo — pure Java, strong LP/MIP support, Apache 2.0 licensed

The LP solver dependency decision is a **Phase 2 prerequisite gate**: select and validate the solver before defining soft constraint cost functions.

---

## Component 2: LLM Schema Annotation for Semantic Density

Pre-process the JSON schema once with an LLM to annotate fields with semantic metadata:

```json
{
  "revenue": { "@importance": "high", "@prefer": "table", "@format": "currency" },
  "audit_id": { "@importance": "low", "@canHide": true },
  "description": { "@importance": "medium", "@prefer": "outline", "@group": "Details" }
}
```

**Integration**: Annotations stored as LayoutStylesheet properties (RDR-009 Phase C). Annotation property coordination with RDR-018 (query-semantic layout stylesheet). The constraint solver reads importance weights when generating soft constraint costs — high-importance fields have higher cost for compression/hiding.

### Annotation-to-Constraint Mapping

Importance annotations map to soft constraint violation costs:

| `@importance` | Violation cost multiplier |
|---------------|--------------------------|
| `high`        | 10                        |
| `medium`      | 5                         |
| `low`         | 1                         |

These multipliers are **initial estimates to be calibrated in Phase 2 validation**. Worked examples demonstrating that these ratios produce visually preferable layouts are required before implementation. The multipliers should not be treated as settled until that calibration is complete.

These multipliers scale the cost incurred when a field is compressed, hidden, or placed in a suboptimal rendering mode. For example, hiding a `high` importance field costs 10× more than hiding a `low` importance field in the objective function.

**`@prefer` semantics**: `@prefer` is a **soft constraint**. It adds a cost penalty (equal to the field's importance multiplier) when the solver selects a different mode than preferred. If `@prefer` disagrees with the global optimum, the solver overrides the preference — the cost is simply factored into the global objective alongside all other constraints.

**Phase 2 annotation key namespace**: Annotation keys use the `@` prefix namespace (e.g., `@importance`, `@prefer`, `@canHide`, `@format`, `@group`). All Phase 2 annotation property keys must be namespaced with `@` to avoid collision with LayoutStylesheet structural properties.

**LLM failure handling**: On LLM failure, proceed with uniform weights (all fields treated as `@importance=medium`). Log a warning. LLM annotations are advisory — the solver remains fully functional without them. The constraint model is never blocked by LLM unavailability.

### Annotation Lifecycle

Annotations are keyed by **schema hash** (SHA-256 of the canonical JSON Schema). On schema change:
1. Affected annotations are invalidated (hash mismatch).
2. Re-annotation is flagged for review — stale annotations are never silently reused.
3. Cache is stored alongside `LayoutStylesheet` in the same persistence layer.

Manual overrides via LayoutStylesheet take precedence over LLM-generated annotations and survive schema changes (they are keyed by field path, not schema hash).

**Evidence**: PosterLlama (ECCV 2024) and UI Grammar (ICML 2023) both show LLM annotation of layouts significantly improves quality. The novelty here is applying it to SCHEMA annotation rather than layout annotation.

**Key property**: The LLM runs once per schema (not per render). Runtime remains pure Bakke — fast and deterministic. The LLM is a build-time tool, not a runtime dependency.

---

## Component 3: Schema Tree as Streaming Invalidation Graph

Treat the Bakke schema tree as the dependency graph for incremental layout invalidation.

**Core insight**: When a JSON value changes at path `root.orders[3].amount`, only the MeasureResult nodes on that path need invalidation. The schema tree analytically defines which layout computations depend on which data paths.

### Relationship to RDR-016

RDR-016 (Layout Stability & Incremental Update) delivers streaming capability **without constraint solver dependency** via decision caching + `rebindData()`. RDR-016 is independently deliverable today and should be implemented first.

Phase 3 of this RDR is positioned as an **enhancement over RDR-016**: once the constraint solver exists (Phase 1), partial re-solve replaces the decision cache for more accurate invalidation — the solver can determine whether a data change actually affects mode decisions, rather than conservatively assuming it does.

### Implementation

1. `ObservableData` adapter wraps `JsonNode`, emits path-level diffs on mutation
2. Schema nodes register as invalidation listeners for their data paths
3. On change: invalidate affected MeasureResults → re-solve only affected constraint subtree → animate transition

### Invalidation Complexity

Invalidation cost depends on the nature of the change:

- **(a) Point update, value within existing range** — O(log N) for balanced schema trees. The aggregate statistics (min, max, mean width) are unaffected; only the specific cell value changes. This is the common case for streaming numeric data.
- **(b) Max-value removal or insertion** — O(items × depth). When the item at path P was the widest entry and is removed, `maxWidth` requires a full rescan of all items at that level to recompute the aggregate. This is unavoidable without auxiliary data structures (e.g., augmented heaps).
- **(c) Outline mode — sibling-lateral invalidation** — Outline column balancing creates lateral dependencies between sibling nodes not captured by the simple ancestor-path model. When one column's width changes, sibling columns may need rebalancing. This requires invalidation of the parent's full set of children, not just the ancestor path.

The O(log N) bound holds **only when data distribution statistics (max, min) are unaffected** by the change.

### New Capabilities

- GraphQL subscription → surgical layout update (not full relayout)
- Animated transitions: delta is known, animate only changed regions
- Streaming/live data at 60fps (for case (a) updates)
- Server-sent events → real-time dashboard with adaptive layout

---

## Scope & Coordination

This RDR bundles four concerns that overlap with other RDRs:

| Phase | Overlaps with | Coordination |
|-------|---------------|--------------|
| Phase 1 | — | Standalone deliverable. RDR-011 closed 2026-03-16 — Phase 1 unblocked. |
| Phase 2 | RDR-018 | RDR-018 closed — Phase 2 implements against existing LayoutPropertyKeys. |
| Phase 3 | RDR-016 | RDR-016 closed — Phase 3 builds on live LayoutDecisionKey cache. |
| Phase 4 | RDR-015 | RDR-015 closed — BADGE/CROSSTAB already in RelationRenderMode; Phase 4 expands solver search space. |

**Recommendation**: Deliver Phase 1 as a standalone contribution. Phases 2–4 are additive enhancements with explicit cross-references to their coordinating RDRs. Do not block Phase 1 on any other phase.

---

## Phased Implementation

### Phase 1: Constraint Model (depends on RDR-011)
- Define `ConstraintSet` record per schema node
- Generate constraints from schema structure (replicate current behavior as floor, then improve)
- Implement constraint solver via exhaustive enumeration for binary table/outline choices (no external solver dependency)
- **Phase 1 objective**: among all feasible binary assignments, maximize the number of TABLE assignments (density proxy). Tie-break: prefer more TABLE assignments at deeper levels (depth-first lexicographic order). This produces deterministic results consistent with Bakke's density-maximization intent.
- **Validation**: Constraint-based layouts are never worse (in pixel density or visual completeness) than greedy for all existing test cases. At least 2 documented cases where constraint-based layout produces strictly better results than greedy with measurable density improvement.

### Phase 2: LLM Annotation Pipeline
- Define annotation schema (JSON Schema for field metadata)
- Create annotation prompt template
- Build `AnnotatedSchema` that merges LLM annotations into LayoutStylesheet
- **Prerequisite gate**: Select and validate LP solver (Apache Commons Math SimplexSolver or ojAlgo) before defining soft constraint cost functions
- **Validation**: Annotated schemas produce measurably better layouts on sample datasets (user study or heuristic density metric)

### Phase 3: Reactive Invalidation (depends on RDR-009 Phase A; coordinate with RDR-016)
- Implement `ObservableData` wrapper with path-level change detection
- Add invalidation listeners to MeasureResult cache
- Implement partial re-solve for constraint subtrees
- RDR-016's decision cache serves as the baseline; Phase 3 replaces it with constraint-aware invalidation
- **Validation**: Streaming data updates with <16ms relayout latency for typical schemas (5-20 nodes) for point updates (complexity case (a))

### Phase 4a: Additional Modes in Solver Search Space (coordinate with RDR-015)
- Expand solver search space to include CROSSTAB and other modes already in RelationRenderMode (RDR-015 closed)
- Add BADGE and any new modes as solver variables rather than hard-constraint overrides
- Validate that expanded search space does not degrade performance for N ≤ 15

### Phase 4b: Animated Transitions
- Implement animated mode transitions on data change using known delta from constraint re-solve
- Limit to transitions with deterministic before/after states (not continuous layout morphing)

### Phase 4c: Constraint UI — Deferred to Separate RDR
- User-facing constraint configuration (pin mode, force visible, exclude from solver) is deferred to a separate RDR
- Rationale: UI design requires user research independent of the solver implementation; premature UI design risks locking in poor interaction patterns before the solver behavior is understood
- Do not implement carousel or collapsed-summary modes until separately researched and RDR'd

---

## Alternatives Considered

### Alt A: Greedy Threshold with Backtracking

Keep the existing greedy bottom-up decision pass but add a second pass that retries nodes in OUTLINE mode if TABLE caused overflow or density degradation above the node.

**Pros**
- Near-zero implementation risk — builds on well-understood code path.
- No external solver dependency.
- Straightforward to test: compare density before and after retry.

**Cons**
- Still locally greedy; backtracking is reactive rather than anticipatory.
- Two-pass worst case is O(N²) without memoization — the retry may cascade.
- Does not enable soft constraints or importance weights; still binary and heuristic.
- Does not generalize to more than two modes (no path to Phase 4 multi-mode).

**Rejected because**: Backtracking patches the symptom (overflow after the fact) rather than addressing the root cause (greedy decisions ignoring global context). It cannot support semantic weights (Component 2) or partial re-solve (Component 3) without further architectural surgery that would converge on the constraint model anyway. The implementation complexity of correct backtracking approaches that of Phase 1 exhaustive enumeration with fewer benefits.

---

### Alt B: Linear Programming Solver for Binary Mode Decisions

Model table/outline choice as a binary integer program (BIP) from the start, using an off-the-shelf ILP solver (GLPK, Gurobi, ojAlgo) to solve all mode decisions simultaneously.

**Pros**
- Principled global optimum for the full constraint set including soft costs.
- Clean formulation: one objective function, one solver call per layout pass.
- Directly accommodates importance weights as objective coefficients — no separate Phase 2 integration step.

**Cons**
- BIP is NP-hard in general; solver overhead is unpredictable for adversarial inputs.
- All evaluated solvers with free/open licenses (GLPK via JNA, ojAlgo) add 5–15 MB to the dependency graph.
- Binary integer variables require branch-and-bound internally — for N ≤ 15 this offers no practical advantage over exhaustive enumeration, while adding setup overhead.
- Soft constraints (importance weights) require continuous relaxation, which removes the binary guarantee and produces fractional mode assignments that must be rounded — introducing approximation error.
- Solver black-box makes it harder to prove or explain layout decisions to users.

**Rejected for Phase 1 because**: For the binary table/outline case with N ≤ 15, exhaustive enumeration with hard-constraint pruning is provably faster and simpler than ILP with no loss of optimality. The LP formulation is retained as the **Phase 2 solver strategy** for soft constraints with continuous cost functions, where it is the appropriate tool — ojAlgo (pure Java, Apache 2.0) is already named as the Phase 2 candidate. Introducing full ILP in Phase 1 would add an external dependency and solver complexity before the constraint model is validated.

---

### Alt C: User-Specified Mode Overrides per Node via LayoutStylesheet

Skip the solver entirely. Extend `LayoutStylesheet` with a `RelationRenderMode` override property per node path, giving users direct control over table vs. outline per node. No algorithmic change to the greedy pass.

**Pros**
- Zero algorithmic risk — existing greedy path is untouched.
- Immediately useful for power users who know the data and want deterministic layouts.
- No solver dependency of any kind.
- Complements rather than competes with the constraint solver; overrides can coexist as hard constraints in the Phase 1 model.

**Cons**
- Offers no improvement for users who do not manually configure overrides.
- Manual tuning does not scale to schemas with many nodes or frequently changing schemas.
- Does not address the global optimality problem; just makes the greedy decision user-controllable.
- Requires a `RelationRenderMode` stylesheet property that does not yet exist (noted as a gap in post-Wave3 research).

**Rejected as the primary solution** because it shifts the optimization burden to the user rather than solving it algorithmically. It is, however, being adopted as a **complementary feature**: the Phase 1 constraint model treats LayoutStylesheet mode overrides as hard constraint anchors — a node with an explicit override is excluded from the solver search space, which is strictly additive. The missing `RelationRenderMode` stylesheet property is a prerequisite gap that must be closed before Phase 1 validation is complete.

---

## Research Findings

### RF-1: ORCSolver Feasibility (Confidence: HIGH)
ORCSolver is MIT-licensed, Java-compatible (original in Python, but the algorithm is well-documented for reimplementation). For Kramer's typical schema sizes (5-20 nodes, 2 modes each = 2^5 to 2^20 combinations), branch-and-bound with constraint propagation solves in <1ms. The bottleneck would be constraint generation, not solving. For Phase 1, exhaustive enumeration of binary choices is sufficient; LP solver selection is deferred to Phase 2.

### RF-2: Schema Tree IS the Invalidation Graph (Confidence: MEDIUM)
MeasureResult immutability (RDR-009) means each node's measurement is a pure function of its data subtree. When data at path P changes, the MeasureResult nodes that are ancestors of P in the schema tree need recomputation. However, the simple ancestor-path model has three complexity regimes: (a) point updates within existing statistical range are O(log N), (b) max-value removals require O(items × depth) rescan, and (c) outline mode creates sibling-lateral dependencies requiring parent-scoped invalidation. The O(log N) bound is the common case for streaming numeric updates but is not universal.

### RF-3: LLM Annotation Quality (Confidence: MEDIUM-HIGH)
GPT-4/Claude with schema context produces high-quality field importance annotations on structured data schemas (tested informally on GraphQL schemas). Formal evaluation needed. The annotation is one-shot (per schema, not per query), so cost is negligible.

### RF-4: Bakke's Structural Insights Are the Enabler (Confidence: HIGH)
Modern layout research (ORC, diffusion, LLM generative) all work on flat widget sets with manually authored specs. Bakke's two insights — schema-derived layout and recursive independence — are preconditions for all three components. Without knowing the data structure, you cannot: (a) generate constraints automatically, (b) define the invalidation graph precisely, or (c) annotate fields semantically. This is Kramer's research moat.

---

## Risks and Considerations

| Risk | Severity | Mitigation |
|------|----------|------------|
| ORCSolver port complexity | Medium | Phase 1 uses exhaustive enumeration; LP solver deferred to Phase 2 |
| LLM annotation inconsistency | Medium | Cache annotations keyed by schema hash; allow manual override via LayoutStylesheet |
| Reactive invalidation correctness | High | Three complexity regimes documented; formal verification needed for each |
| Outline lateral dependencies | Medium | Sibling-lateral invalidation must be explicitly handled; not just ancestor-path |
| Scope creep (multi-mode before basics work) | High | Phase 1 must demonstrate greedy-or-better before adding new modes |
| Overlap with RDR-016 (streaming) | Medium | RDR-016 delivers streaming first; Phase 3 enhances, not replaces |
| Overlap with RDR-015 (rendering modes) | Medium | Phase 4 coordinates with RDR-015; defer until both are ready |
| This is a research project, not a product feature | Medium | Phase 1 alone delivers global optimization; Phases 2-4 are additive |

---

## Success Criteria

1. Phase 1: Constraint-based layouts are never worse (in pixel density or visual completeness) than greedy for all existing test cases
2. Phase 1: Constraint-based layout produces strictly better results than greedy on at least 2 documented cases with measurable density improvement
3. Phase 2: LLM annotations produce consistent results across 3 runs for same schema
4. Phase 3: Streaming data update with <16ms relayout for 20-node schema (point updates within existing statistical range)
5. Combined: Layout system has all four properties (structurally grounded, semantically aware, globally optimal, reactive) simultaneously

---

## Finalization Gate

### 1. Has the problem been validated with real user scenarios?

Partially. The greedy threshold limitation is analytically demonstrable — the single `if (tableWidth <= width)` at `RelationLayout.layout():613` is the exact decision point, and its locality is structural, not a matter of interpretation. Post-Wave3 research confirmed that `LayoutDecisionKey`, `isConverged`, `snapshotDecisionTree`, `MeasurementStrategy`, and `SchemaPath` infrastructure are already present in the codebase, which validates that the architecture can support a constraint model without large-scale restructuring.

**Research type classification**: This RDR is classified as Research type. Analytical demonstration of greedy suboptimality (showing that the single-pass greedy algorithm provably misses globally better assignments for specific schema configurations) is appropriate Phase 1 validation. User perceptibility studies are Phase 2 responsibility, not Phase 1. The two test cases required by Phase 1 Success Criterion 2 constitute research validation, not product validation.

What is not yet validated: a user study or production workload demonstrating that globally optimal layouts are perceptibly better on realistic schemas. Formal user validation is deferred to Phase 2 (annotation pipeline), where the annotation-to-layout quality improvement is inherently user-perceptible.

### 2. Is the solution the simplest that could work?

Phase 1 is near-surgical by design. It replaces one if-statement with an exhaustive enumeration loop over 2^N binary assignments, pruned by hard constraints. There is no external solver dependency; the enumeration is implemented directly in the layout engine. Post-Wave3 analysis confirmed that exhaustive enumeration for N=15 nodes runs in under 100 microseconds — well within interactive budget without any algorithmic sophistication.

The constraint model's additional concepts (`ConstraintSet`, `LayoutDecisionKey`) are minimal: they name what the current code does implicitly and make the decision auditable. Alt A (backtracking) and Alt B (ILP) would both require more code and more complexity for Phase 1 than the enumeration approach. Phase 1 is the simplest solver that is also provably globally optimal for the binary case.

### 3. Are all assumptions verified or explicitly acknowledged?

Verified:
- The single-decision-point assumption is confirmed: one `if` at `RelationLayout.layout():613` controls all table/outline decisions.
- Infrastructure prerequisites (`LayoutDecisionKey`, `isConverged`, `snapshotDecisionTree`, `MeasurementStrategy`, `SchemaPath`) are present in the codebase.
- Exhaustive enumeration for N ≤ 15 is sub-100µs — verified by post-Wave3 timing analysis.
- ojAlgo (pure Java, Apache 2.0) is confirmed as the Phase 2 LP solver candidate.

Explicitly acknowledged gaps:
- `RelationRenderMode` stylesheet override property does not yet exist. Phase 1 treats stylesheet overrides as hard constraint anchors; this property must be added before that feature is exercisable (tracked as a Phase 1 prerequisite gap).
- `frozenResult` data-path invalidation (Phase 3) currently clears only on stylesheet version change, not on data change. Phase 3 must extend this invalidation trigger; the gap is documented in the Component 3 section and the Risks table.
- The O(log N) invalidation bound (RF-2) holds only for point updates within existing statistical range. Max-value removal and outline lateral dependencies have higher complexity — this is explicitly documented in the Invalidation Complexity subsection.
- LLM annotation quality (RF-3) is medium-high confidence based on informal testing; formal evaluation is a Phase 2 validation requirement.

### 4. What is the rollback strategy if this fails?

Phase 1 is backward-compatible by construction. The constraint solver is introduced as a replacement for the greedy `if` statement, and the first validation criterion requires that it never produces worse results than greedy. The greedy decision is the floor, not a fallback to be abandoned.

If Phase 1 validation fails — i.e., the constraint model produces layouts that violate the greedy-or-better criterion — the rollback is to revert the single changed call site in `RelationLayout.layout()`. No other code path is affected; the constraint infrastructure (`ConstraintSet` records, enumeration loop) can remain in the codebase inert until the regression is diagnosed.

If Phase 2 (LLM annotation) is abandoned, Phase 1 remains fully functional without it. If Phase 3 (reactive invalidation) is abandoned, RDR-016's decision cache is the fallback and is implemented independently. Phases 1–4 are staged precisely so that each phase is independently retractable without undoing prior work.

The one irreversible commitment is the introduction of `ConstraintSet` as the internal representation of mode decisions. This is a low-risk structural change; its impact on callers is additive.

### 5. Are there cross-cutting concerns with other RDRs?

Four cross-cutting relationships — all coordinating RDRs are now closed:

**RDR-011 (Layout Protocol Extraction)** — closed 2026-03-16. Phase 1 is unblocked. `LayoutDecisionKey` and `snapshotDecisionTree` are delivered and available for the constraint solver to consume.

**RDR-015 (Alternative Rendering Modes)** — closed. BADGE and CROSSTAB are already present in `RelationRenderMode`. Phase 1 continues to treat CROSSTAB as a hard constraint override (not a solver variable). Phase 4a expands the solver search space to include these modes. No further coordination required before Phase 1.

**RDR-016 (Layout Stability / Incremental Update)** — closed. The live `LayoutDecisionKey` cache is delivered. Phase 3 builds on this cache with constraint-aware partial re-solve. The `frozenResult` invalidation gap (clears on stylesheet version change only, not data change) must still be resolved before Phase 3 implementation begins.

**RDR-018 (Query-Semantic Layout Stylesheet)** — closed. Phase 2 implements annotation properties against the existing `LayoutPropertyKeys`. The `@` prefix namespace established in the LLM Annotation section above ensures no collision with existing keys.

---

## Recommendation

This is a multi-quarter research effort. Phase 1 (constraint model) is the most valuable standalone deliverable — it replaces the greedy heuristic with a provably optimal solver while maintaining backward compatibility (greedy as floor, not target). Phases 2-4 build on Phase 1 incrementally. Recommend proceeding with Phase 1 after RDR-011 Phase 1 (LayoutDecisionTree extraction) is complete, as the constraint solver produces LayoutDecisions.

**Sequencing**: RDR-016 should be implemented before Phase 3 of this RDR. RDR-016 delivers streaming capability via decision caching without constraint solver dependency. Phase 3 then upgrades invalidation accuracy by replacing the decision cache with constraint-aware partial re-solve.

This work is publishable. The publishability argument is strongest when focused on the **constraint solver + schema interaction** alone (Phase 1): Bakke's schema-awareness is the enabling ingredient that makes automatic constraint generation tractable — no other system has this substrate. Phases 2-4 strengthen the contribution but are not required for the core novelty claim.
