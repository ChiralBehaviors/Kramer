# RDR-022: LLM Annotation Pipeline

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P3
- **Created**: 2026-03-16
- **Related**: RDR-012 (constraint solver), RDR-009 (LayoutStylesheet), RDR-018 (query-semantic stylesheet)

## Problem Statement

The Phase 1 constraint solver (RDR-012) treats all Relation nodes as equally important when choosing TABLE vs OUTLINE assignments. A schema with a high-business-value `revenue` field receives the same density treatment as an `audit_id` field. The result is globally optimal in a geometric sense but not in a semantic sense: the solver cannot prefer TABLE for fields a human analyst considers critical and tolerate OUTLINE for fields that are merely present in the data.

Adding manual importance annotations to a `LayoutStylesheet` per field is possible but does not scale: a schema with 20 fields requires 20 hand-authored entries, and annotations must be re-authored when the schema evolves. What is needed is a build-time tool that reads the schema once and derives semantic importance automatically, storing the result in the same `LayoutStylesheet` override mechanism that already exists.

---

## Proposed Solution

A standalone Java CLI tool — the **annotation pipeline** — that accepts a `Relation` schema tree, calls an LLM once at build time, and writes semantic importance annotations as `LayoutStylesheet` overrides using the `@`-prefix property namespace. The constraint solver reads these annotations as soft-constraint weights when scoring TABLE/OUTLINE assignments.

The central design constraint: the LLM runs once per schema version, not per render. Runtime layout remains pure Bakke — deterministic, fast, and LLM-free. The LLM is an offline analysis tool, not a runtime dependency.

### Annotation properties

| Property | Values | Effect |
|----------|--------|--------|
| `@importance` | `high`, `medium`, `low` | Scales soft-constraint violation cost |
| `@prefer` | `table`, `outline` | Soft bias toward a rendering mode |
| `@canHide` | `true` | Marks field safe to omit under space pressure |

### Importance multipliers

| `@importance` | Cost multiplier |
|---------------|----------------|
| `high`        | 10              |
| `medium`      | 5               |
| `low`         | 1               |

These are initial estimates to be calibrated in Phase 2b validation. They scale the penalty incurred when a high-importance field is compressed or placed in a suboptimal rendering mode.

### Annotation lifecycle

1. **Build step**: run `annotation-tool --schema schema.json --model claude-haiku-3 --out .kramer-annotations.json`.
2. **Cache key**: SHA-256 of canonical schema JSON (fields sorted alphabetically, no whitespace). Written into the output file alongside annotations.
3. **Load**: `AnnotationLoader` compares stored hash against current schema hash. Match → load into `DefaultLayoutStylesheet` via `setOverride()`. Mismatch → log warning and fall back to uniform weights.
4. **Manual override**: `LayoutStylesheet.setOverride()` calls by the application take precedence over LLM annotations and survive schema evolution.
5. **LLM failure**: on any error (network, timeout, malformed JSON) the tool emits a full annotation map of `@importance: medium` for all fields and continues normally.

### Solver integration

Phase 2 modifies `ExhaustiveConstraintSolver` to accept an optional `AnnotationWeights` input:

```java
interface ConstraintSolver {
    Map<SchemaPath, RelationRenderMode> solve(
        List<RelationConstraint> constraints,
        double availableWidth,
        AnnotationWeights weights   // null → Phase 1 density objective
    );
}

record AnnotationWeights(
    Map<SchemaPath, Integer> importanceMultiplier,
    Map<SchemaPath, RelationRenderMode> preferredMode
) {}
```

When `weights` is null the solver falls back to the Phase 1 density-maximizing objective unchanged. Importance weights propagate upward through the schema tree: a Relation node's effective weight is the maximum multiplier among all descendants.

For N > 15, the greedy fallback used in Phase 1 is replaced by an ojAlgo LP relaxation with soft-constraint weights. The LP dependency (`org.ojalgo:ojalgo:55.0.1`, pure Java, Apache 2.0) lives in the new `kramer-annotation` submodule and does not enter the `kramer` core module.

### Module placement

New submodule `kramer-annotation` (depends on `kramer`; uses Jakarta RS HTTP client already available via `kramer-ql`). This keeps LLM HTTP client and LP solver out of the `kramer` core dependency graph.

---

## Alternatives Considered

### Alt A: Manual stylesheet annotations only

Extend `LayoutStylesheet` with documented `@importance` properties and require developers to author them by hand.

**Pros**: zero new dependencies, no LLM call, deterministic.

**Cons**: does not scale to large or frequently-evolving schemas; annotation burden falls entirely on the developer; misses the opportunity to derive importance from naming conventions and domain context that the LLM can read from field names and types.

**Rejected**: hand-authoring is the correct fallback (and is always available via `setOverride()`), but it is not a substitute for an automated pipeline when the schema has more than a handful of fields.

### Alt B: Heuristic rule-based importance scoring

Derive importance from field name patterns (e.g., `.*id$` → low, `revenue|amount|price` → high) and type information (UUID → low, Decimal → high) without any LLM call.

**Pros**: deterministic, no external service, fast.

**Cons**: heuristics are brittle and domain-specific; a field named `ref` in a billing schema is not the same as `ref` in a logistics schema; false positives and false negatives are hard to fix without understanding domain semantics; rules require ongoing maintenance as naming conventions change.

**Rejected**: rule matching can serve as the fallback within the annotation tool when the LLM is unavailable (i.e., it is better than pure uniform weights), but it is not sufficient as the primary mechanism. The LLM provides domain-context awareness that no set of syntactic rules can replicate.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| LLM cost per schema | Low | ~$0.01/schema at haiku rates; one-time per version; `--max-fields 100` guard |
| Annotation staleness after schema evolution | Medium | Hash mismatch logged at load time; fallback to uniform weights; recommend checking annotations into VCS |
| Weight miscalibration (10:5:1 ratios) | Medium | Expose as configurable parameters; validate on sample datasets during Phase 2b |
| ojAlgo dependency size (~6 MB) | Low | Confined to `kramer-annotation` submodule; not in `kramer` core |
| LP relaxation rounding error for N > 15 | Low | Feasibility constraints are per-node (no coupling); LP relaxation is exact for this structure; post-rounding repair pass guaranteed to terminate |

---

## Implementation Plan

### Phase 2a: Annotation CLI tool

- `AnnotationTool` CLI main entry point
- `SchemaSerializer` — converts `Relation` tree to indented field-list prompt format
- `LlmAnnotationClient` interface + `AnthropicAnnotationClient` implementation (HTTP via Jakarta RS)
- `AnnotationResponse` record with validation: unknown paths discarded, invalid importance replaced with `medium`, missing fields default to `medium`
- `AnnotationLoader` — reads `.kramer-annotations.json`, validates schema hash, calls `DefaultLayoutStylesheet.setOverride()`
- Few-shot prompt with three calibration examples (financial, product catalog, nested report)
- Model: configurable (`--model`), default `claude-haiku-3`, temperature 0, 30s timeout

### Phase 2b: Weighted objective in ExhaustiveConstraintSolver

- Add `AnnotationWeights` parameter to `ConstraintSolver.solve()` (null-safe)
- Replace Phase 1 density count with weighted violation cost sum
- Importance propagation: `effectiveWeight()` = max multiplier across descendant subtree
- `@prefer` soft penalty integrated into score

### Phase 2c: ojAlgo LP solver for N > 15

- `OjAlgoConstraintSolver` implementing `ConstraintSolver`
- Binary-relaxed LP: `x_i ∈ [0,1]`, minimize weighted violation cost, feasibility per node
- Post-rounding feasibility repair (O(N), guaranteed termination)
- `ExhaustiveConstraintSolver` delegates to `OjAlgoConstraintSolver` when N > 15
- **Prerequisite gate**: validate ojAlgo with a trivial LP before writing formulation code

---

## Finalization Gate

### 1. Has the problem been validated with real user scenarios?

Partially. The uniform-weight limitation of Phase 1 is analytically demonstrable: given a schema where `revenue` and `audit_id` are siblings under a Relation node that barely fits in TABLE mode, the Phase 1 solver treats them identically. Whether users perceive the difference requires a Phase 2b validation study on sample datasets. The annotation pipeline is proposed as a build-time integration point; its end-to-end value is not yet confirmed by a user study.

### 2. Is the solution the simplest that could work?

Yes. The annotation tool is a thin CLI wrapper around one LLM HTTP call and one JSON write. The solver change is a null-safe parameter addition to an existing interface. The ojAlgo LP (Phase 2c) is confined to N > 15, which the Phase 1 exhaustive enumerator already cannot handle optimally. No new layout phases are introduced.

### 3. Are all assumptions verified or explicitly acknowledged?

Verified: `DefaultLayoutStylesheet.setOverride()` bumps stylesheet version, which triggers constraint re-solve automatically via the existing `frozenResult` invalidation mechanism (RDR-009/RDR-018). The `@` prefix namespace does not collide with existing LayoutStylesheet structural properties.

Acknowledged gaps: importance multiplier ratios (10:5:1) are estimates; LLM annotation quality for domain-specific schemas is medium-high confidence based on informal testing; formal calibration is a Phase 2b deliverable.

### 4. What is the rollback strategy if this fails?

The annotation pipeline is additive. If Phase 2b weighted solver produces worse layouts than Phase 1 uniform weights, pass `weights=null` to restore Phase 1 behavior. The `AnnotationWeights` null-safe path is a first-class code path, not a temporary escape hatch. The `kramer-annotation` submodule can be removed without affecting `kramer` core.

### 5. Are there cross-cutting concerns with other RDRs?

- **RDR-009**: `@importance`, `@prefer`, `@canHide` properties must be registered in the central LayoutStylesheet Property Registry. The `@` prefix namespace is established in RDR-012 Phase 2.
- **RDR-012**: This RDR is Phase 2 of RDR-012. Phase 1 (constraint solver) must be complete before the weighted objective can be validated.
- **RDR-018**: Phase 2 annotation properties interact with query-semantic stylesheet layer. The `@` namespace avoids collision with structural properties defined in RDR-018.

---

## Success Criteria

- [ ] Annotation CLI produces identical output across 3 runs for the same schema (temperature=0 determinism)
- [ ] Weighted solver produces measurably better layouts than uniform-weight solver on at least 2 annotated sample datasets
- [ ] All Phase 1 `ConstraintSolverTest` cases pass unchanged with `weights=null`
- [ ] ojAlgo trivial LP test passes before LP formulation is written (prerequisite gate)
- [ ] N=16 weighted assignment: LP solver respects importance weights (high-importance fields prefer TABLE)
- [ ] Hash mismatch produces warning and uniform fallback; no silent stale-annotation use
- [ ] LLM failure produces uniform-weight output and continues without exception
