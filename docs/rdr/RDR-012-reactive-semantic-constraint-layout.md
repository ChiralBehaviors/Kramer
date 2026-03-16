# RDR-012: Reactive Semantic Constraint Layout

## Metadata
- **Type**: Research
- **Status**: proposed
- **Priority**: P2
- **Created**: 2026-03-15
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

These multipliers scale the cost incurred when a field is compressed, hidden, or placed in a suboptimal rendering mode. For example, hiding a `high` importance field costs 10× more than hiding a `low` importance field in the objective function.

**`@prefer` semantics**: `@prefer` is a **soft constraint**. It adds a cost penalty (equal to the field's importance multiplier) when the solver selects a different mode than preferred. If `@prefer` disagrees with the global optimum, the solver overrides the preference — the cost is simply factored into the global objective alongside all other constraints.

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
| Phase 1 | — | Standalone deliverable. No external dependencies beyond RDR-011. |
| Phase 2 | RDR-018 | Annotation properties coordinate with RDR-018's query-semantic stylesheet. |
| Phase 3 | RDR-016 | RDR-016 delivers streaming via decision caching first. Phase 3 enhances with constraint-aware partial re-solve. |
| Phase 4 | RDR-015 | Multi-mode rendering coordinates with RDR-015's alternative rendering modes. |

**Recommendation**: Deliver Phase 1 as a standalone contribution. Phases 2–4 are additive enhancements with explicit cross-references to their coordinating RDRs. Do not block Phase 1 on any other phase.

---

## Phased Implementation

### Phase 1: Constraint Model (depends on RDR-011)
- Define `ConstraintSet` record per schema node
- Generate constraints from schema structure (replicate current behavior as floor, then improve)
- Implement constraint solver via exhaustive enumeration for binary table/outline choices (no external solver dependency)
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

### Phase 4: Integration & Multi-Mode (coordinate with RDR-015)
- Add new mode options beyond table/outline (sparkline, badge, summary) — coordinate with RDR-015
- Wire soft/hard constraint UI for user customization
- Animated transitions between modes on data change

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

## Recommendation

This is a multi-quarter research effort. Phase 1 (constraint model) is the most valuable standalone deliverable — it replaces the greedy heuristic with a provably optimal solver while maintaining backward compatibility (greedy as floor, not target). Phases 2-4 build on Phase 1 incrementally. Recommend proceeding with Phase 1 after RDR-011 Phase 1 (LayoutDecisionTree extraction) is complete, as the constraint solver produces LayoutDecisions.

**Sequencing**: RDR-016 should be implemented before Phase 3 of this RDR. RDR-016 delivers streaming capability via decision caching without constraint solver dependency. Phase 3 then upgrades invalidation accuracy by replacing the decision cache with constraint-aware partial re-solve.

This work is publishable. The publishability argument is strongest when focused on the **constraint solver + schema interaction** alone (Phase 1): Bakke's schema-awareness is the enabling ingredient that makes automatic constraint generation tractable — no other system has this substrate. Phases 2-4 strengthen the contribution but are not required for the core novelty claim.
