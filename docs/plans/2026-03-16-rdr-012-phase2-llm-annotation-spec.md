# RDR-012 Phase 2 Sub-Specification: LLM Annotation Pipeline

- **RDR**: 012-P2
- **Bead**: Kramer-d70
- **Date**: 2026-03-16
- **Status**: draft
- **Prerequisite for**: Phase 2 implementation (weighted objective in ExhaustiveConstraintSolver, ojAlgo LP solver for N>15)
- **Depends on**: Kramer-044 (Phase 1 constraint model complete)

---

## 1. Overview

The LLM annotation pipeline is a **build-time tool** that pre-processes a Kramer schema tree once and emits semantic importance metadata for each field. Annotations are stored as `LayoutStylesheet` overrides using the `@`-prefix property namespace. The constraint solver reads these annotations as soft constraint weights when optimizing table/outline mode assignments across the schema tree.

The central insight: LLM annotation runs once per schema version, not per render. Runtime layout remains pure Bakke — deterministic, fast, LLM-free. The LLM is an offline analysis tool, not a runtime dependency.

**Relationship to Phase 1**: Phase 1 (Kramer-044 through Kramer-m1i) implements the constraint solver with a density-maximizing objective (more TABLE = better). Phase 2 replaces that objective with a weighted one: fields annotated `@importance: high` incur greater cost when compressed or hidden. The solver's structure does not change — only the scoring function.

---

## 2. Annotation Lifecycle

### 2.1 Tool Position in Build

The annotation tool is a standalone Java CLI executable (not a JavaFX application, not a Maven plugin in Phase 2a). It is invoked manually or from a build script when the schema changes.

```
annotation-tool --schema schema.json --model claude-haiku-3 --out stylesheet-overrides.json
```

The tool writes a JSON file containing `Map<SchemaPath, Map<String, Object>>` entries. These are loaded into a `DefaultLayoutStylesheet` via `setOverride()` calls before the layout engine runs.

### 2.2 Input

The tool accepts a `Relation` schema tree in either form:
- Serialized JSON (canonical schema representation)
- GraphQL SDL string (tool infers schema from field definitions and types)

The canonical input format is a JSON object mirroring the `SchemaNode` tree:

```json
{
  "name": "order",
  "type": "Relation",
  "fields": [
    { "name": "id",      "type": "Primitive", "valueType": "UUID" },
    { "name": "revenue", "type": "Primitive", "valueType": "Decimal" },
    { "name": "customer","type": "Relation",  "fields": [...] }
  ]
}
```

### 2.3 Output

The tool writes a `Map<SchemaPath, Map<String, Object>>` where each key is a dot-separated schema path (e.g., `order.revenue`) and each value is a map of annotation properties:

```json
{
  "order.id":       { "@importance": "low",    "@canHide": true },
  "order.revenue":  { "@importance": "high",   "@prefer": "table" },
  "order.customer": { "@importance": "medium", "@prefer": "outline" }
}
```

This output is consumed by the layout system via `DefaultLayoutStylesheet.setOverride(SchemaPath, Map<String, Object>)`. Calling `setOverride()` bumps the stylesheet version, which causes the constraint solver to re-run on next layout pass.

### 2.4 Caching and Invalidation

Annotations are keyed by **schema hash**: SHA-256 of the canonical JSON Schema serialization (fields sorted alphabetically, no whitespace). This hash is written into the annotation output file alongside the annotations:

```json
{
  "_schemaHash": "e3b0c44298fc1c149afb...",
  "_annotatedAt": "2026-03-16T12:00:00Z",
  "_model": "claude-haiku-3",
  "annotations": { ... }
}
```

On load, the tool (or loader) compares the stored `_schemaHash` against the current schema hash. If they match, the cached annotations are used without re-calling the LLM. If they differ, annotations are invalidated and must be regenerated.

Manual stylesheet overrides (set via `LayoutStylesheet.setOverride()` directly, keyed by field path) are stored separately and take precedence over LLM-generated annotations. They are not invalidated by schema hash changes; they survive schema evolution.

### 2.5 Failure Handling

On any LLM failure (network error, timeout, malformed response, JSON parse error):

1. Log a warning: `"LLM annotation failed for schema {hash}: {reason}. Using uniform weights."`.
2. Emit a default annotation map: every field gets `{ "@importance": "medium" }`.
3. Write the uniform-weight output file normally, with `"_model": "fallback-uniform"` in metadata.
4. The solver proceeds with equal weights — functionally identical to Phase 1 behavior.

The constraint model is never blocked by LLM unavailability.

---

## 3. LLM Prompt Design

### 3.1 Input Format

The prompt presents the schema tree as an indented field list with types. This format is compact, unambiguous, and easy for the LLM to parse:

```
Analyze the following data schema and assign semantic importance to each field.

Schema (indented = nested):
  id: UUID
  revenue: Decimal
  created_at: Timestamp
  customer:
    customer_id: UUID
    name: String
    email: String
  line_items:
    sku: String
    quantity: Integer
    unit_price: Decimal
    total: Decimal
```

### 3.2 Requested Output

The prompt requests a JSON object with field paths as keys and importance levels as values. Full paths use dot notation for nested fields:

```
Return a JSON object mapping each field path to its importance metadata.
Use only the following keys:
  "@importance": "high" | "medium" | "low"
  "@prefer": "table" | "outline"    (optional — omit if no preference)
  "@canHide": true                   (optional — only for fields safe to omit)

Return only the JSON object. No explanation, no markdown fences.
```

### 3.3 Few-Shot Examples

Include three examples in the prompt before the actual schema. These establish the calibration the LLM should apply:

**Example 1 — Financial record**:
```
Schema:
  transaction_id: UUID
  amount: Decimal
  currency: String
  merchant: String
  timestamp: Timestamp
  audit_log_id: UUID

Output:
{
  "transaction_id":  { "@importance": "low",  "@canHide": true },
  "amount":          { "@importance": "high" },
  "currency":        { "@importance": "medium" },
  "merchant":        { "@importance": "high" },
  "timestamp":       { "@importance": "medium" },
  "audit_log_id":    { "@importance": "low",  "@canHide": true }
}
```

**Example 2 — Product catalog**:
```
Schema:
  sku: String
  name: String
  description: String
  price: Decimal
  stock_quantity: Integer
  internal_cost: Decimal
  created_by: UUID

Output:
{
  "sku":             { "@importance": "medium" },
  "name":            { "@importance": "high" },
  "description":     { "@importance": "medium", "@prefer": "outline" },
  "price":           { "@importance": "high" },
  "stock_quantity":  { "@importance": "high" },
  "internal_cost":   { "@importance": "low",  "@canHide": true },
  "created_by":      { "@importance": "low",  "@canHide": true }
}
```

**Example 3 — Nested relation**:
```
Schema:
  report_id: UUID
  title: String
  summary: String
  author:
    name: String
    department: String
    employee_id: UUID
  metrics:
    revenue: Decimal
    cost: Decimal
    margin: Decimal

Output:
{
  "report_id":            { "@importance": "low",  "@canHide": true },
  "title":                { "@importance": "high" },
  "summary":              { "@importance": "medium", "@prefer": "outline" },
  "author.name":          { "@importance": "medium" },
  "author.department":    { "@importance": "low" },
  "author.employee_id":   { "@importance": "low",  "@canHide": true },
  "metrics.revenue":      { "@importance": "high" },
  "metrics.cost":         { "@importance": "high" },
  "metrics.margin":       { "@importance": "high" }
}
```

### 3.4 Model Configuration

| Parameter   | Value                                  |
|-------------|----------------------------------------|
| temperature | 0 (deterministic)                      |
| max_tokens  | 1024 (sufficient for any schema ≤ 50 fields) |
| model       | configurable; default `claude-haiku-3` |
| timeout     | 30 seconds                             |

The model is configurable via CLI flag (`--model`) to allow cost/quality tradeoffs. Higher-quality models (claude-sonnet, gpt-4o) may produce better annotations for complex domain schemas at higher cost.

### 3.5 Response Validation

The tool validates the LLM response before accepting it:

1. Parse as JSON object. On failure → log and fall back to uniform weights.
2. Each key must be a valid dot-separated field path present in the input schema. Unknown paths are logged and discarded (not treated as errors).
3. Each `@importance` value must be `"high"`, `"medium"`, or `"low"`. Invalid values → replace with `"medium"`, log warning.
4. `@prefer` value must be `"table"` or `"outline"` if present. Invalid → discard the key, log warning.
5. Fields absent from the LLM response receive default `@importance: "medium"`.

No exceptions are thrown for partial responses. The tool produces a complete annotation map for all schema fields regardless of LLM output completeness.

---

## 4. Annotation-to-Constraint Mapping

### 4.1 Importance Multipliers

`@importance` maps to a soft constraint violation cost multiplier used in the solver's objective function:

| `@importance` | Cost multiplier |
|---------------|----------------|
| `high`        | 10             |
| `medium`      | 5              |
| `low`         | 1              |

**These multipliers are initial estimates and must be calibrated during Phase 2 validation.** The ratios (10:5:1) are chosen to give high-importance fields clear priority without entirely suppressing low-importance fields from influencing layout. Empirical layout comparison on sample datasets is required before treating these as settled values.

### 4.2 Objective Function

Phase 2 replaces the Phase 1 density-maximizing objective with a weighted objective:

```
minimize: sum over all Relation nodes N of
  importance_weight(N) * mode_violation_cost(N, assigned_mode)
```

Where:

- `importance_weight(N)` = the cost multiplier for the highest-importance field in N's subtree (propagated upward: a parent containing a high-importance child inherits `high` weight).
- `mode_violation_cost(N, TABLE)` = 0 if TABLE fits within available width; `importance_weight(N) * overflow_pixels` if TABLE would overflow.
- `mode_violation_cost(N, OUTLINE)` = `importance_weight(N)` (constant penalty for choosing outline over table, scaled by importance — high-importance fields incur more cost for density loss).

The objective minimizes total weighted cost, making the solver prefer TABLE for high-importance fields and tolerate OUTLINE more readily for low-importance fields.

### 4.3 `@prefer` Semantics

`@prefer` is a **soft constraint**. It adds a cost penalty equal to the field's importance multiplier when the solver assigns a different mode than preferred:

```
if assigned_mode != preferred_mode:
    cost += importance_weight(N)
```

`@prefer` does not override the global optimum. The solver may choose a different mode if the global cost is lower. The preference simply biases the solver toward the declared mode, weighted by importance.

### 4.4 Importance Propagation

Importance weights propagate upward through the schema tree. A `Relation` node's effective weight is the maximum `@importance` multiplier among all its direct fields and all fields in descendant subtrees:

```
effectiveWeight(node) = max(
  importanceMultiplier(node's own @importance),
  max over all children c of effectiveWeight(c)
)
```

This ensures that a deeply nested high-importance field causes its ancestor Relation nodes to be treated as high-importance in the objective. Without propagation, the solver might choose OUTLINE at an ancestor level, hiding the high-importance descendant entirely.

### 4.5 `@canHide` Semantics

`@canHide: true` marks a field as safe to omit from the layout when space is critically constrained. In the Phase 2 constraint model:

- A `@canHide: true` field has its violation cost multiplied by 0.1 (effectively treated as a tenth of its stated importance for the purpose of hiding decisions).
- `@canHide` does not affect table/outline mode decisions directly; it is reserved for a future Phase where the solver can choose to hide individual fields rather than just change their rendering mode.
- In Phase 2, `@canHide` is parsed and stored but does not yet affect the solver's objective. It is stored as metadata for Phase 4c (Constraint UI) use.

---

## 5. Integration with ExhaustiveConstraintSolver

### 5.1 Phase 1 Baseline

Phase 1 (`ExhaustiveConstraintSolver`) uses a density-maximizing objective: maximize the count of TABLE assignments, with depth-first tiebreaking. It operates correctly without any annotations; all nodes are treated as equally important.

### 5.2 Phase 2 Weighted Objective

Phase 2 modifies `ExhaustiveConstraintSolver` to accept an optional `AnnotationWeights` input alongside the `List<RelationConstraint>`:

```
interface ConstraintSolver {
    Map<SchemaPath, RelationRenderMode> solve(
        List<RelationConstraint> constraints,
        double availableWidth,
        AnnotationWeights weights  // null → Phase 1 density objective
    );
}
```

When `weights` is null (no annotations available), the solver falls back to the Phase 1 density-maximizing objective. This preserves backward compatibility and ensures the annotation pipeline is a purely additive enhancement.

`AnnotationWeights` is a value record:

```
record AnnotationWeights(
    Map<SchemaPath, Integer> importanceMultiplier,  // 1, 5, or 10
    Map<SchemaPath, RelationRenderMode> preferredMode  // @prefer values
)
```

The solver computes `effectiveWeight(node)` at solve time by walking the constraint list and propagating weights upward as described in section 4.4.

### 5.3 N > 15 Behavior

For N ≤ 15: exhaustive enumeration continues to work. Phase 2 changes the scoring function, not the enumeration strategy. The weighted objective replaces the density count: each candidate assignment is scored by summing weighted violation costs across all nodes.

For N > 15: the greedy fallback used in Phase 1 does not incorporate annotation weights. Phase 2 upgrades the N > 15 path to use the ojAlgo LP solver with soft constraint weights. See section 5.4.

The N ≤ 15 / N > 15 boundary remains at 15 in Phase 2. This boundary is subject to recalibration in Phase 2 validation if weighted enumeration proves slower than unweighted enumeration.

### 5.4 ojAlgo LP Solver for N > 15

**Prerequisite gate**: Before writing any LP formulation code, validate that ojAlgo integrates cleanly into the `kramer` module pom.xml without conflict. The validation test is: add the dependency, write a trivial LP (minimize x+y subject to x+y>=1, x>=0, y>=0), confirm it solves to x=0, y=1 or x=1, y=0.

**Dependency**: `org.ojalgo:ojalgo:55.0.1` (pure Java, Apache 2.0).

**Formulation**: Relaxed LP (not integer) with binary relaxation:

```
Variables:
  x_i ∈ [0, 1] for each non-hard-override Relation node i
  (x_i = 1 → TABLE, x_i = 0 → OUTLINE)

Objective (minimize weighted violation):
  minimize sum_i [ w_i * (tableWidth_i + inset_i - width) * x_i
                 + w_i * preferOutlinePenalty_i * x_i
                 + w_i * preferTablePenalty_i * (1 - x_i) ]

Constraints:
  For each node i:
    tableWidth_i * x_i + nestedInset_i * x_i <= width  (feasibility)
    0 <= x_i <= 1

Hard overrides:
  x_i = 1 for TABLE-forced nodes
  x_i = 0 for OUTLINE/CROSSTAB-forced nodes
```

The LP relaxation produces fractional x_i values. Rounding rule: x_i >= 0.5 → TABLE, x_i < 0.5 → OUTLINE. After rounding, verify feasibility: if any TABLE assignment violates its width constraint, flip to OUTLINE. This post-rounding feasibility repair is O(N) and guaranteed to terminate.

The LP solver is not used for N ≤ 15 in Phase 2. The exhaustive enumerator remains the primary solver for small schemas.

---

## 6. Caching and Invalidation

### 6.1 Annotation Cache

Annotations are stable per schema version. Cache aggressively:

- **Cache key**: SHA-256 of canonical schema JSON (fields sorted, no whitespace, no data values).
- **Cache location**: alongside the `LayoutStylesheet` persistence file; conventionally `.kramer-annotations.json` in the project directory.
- **Cache lifetime**: indefinite until schema hash changes.

### 6.2 Invalidation Triggers

| Event | Action |
|-------|--------|
| Schema field added/removed | Hash mismatch → annotations invalidated, re-annotation required |
| Schema field renamed | Hash mismatch → annotations invalidated |
| Schema field type changed | Hash mismatch → annotations invalidated |
| Manual override via setOverride() | Annotation for that path superseded; LLM annotation for that path ignored |
| stylesheet version bumped | Solver re-runs; annotations remain valid if schema unchanged |

Stale annotations are **never silently reused**. If the hash does not match:
1. Log a warning: `"Schema hash mismatch: cached annotations are stale. Re-run annotation tool."`.
2. Fall back to uniform weights.
3. Do not attempt to merge or partially apply stale annotations.

### 6.3 Version Interaction with Solver

`DefaultLayoutStylesheet.setOverride()` bumps the stylesheet version. The layout engine's `frozenResult` invalidation mechanism clears cached `MeasureResult` nodes on stylesheet version change (established by RDR-009/RDR-018). Loading new annotations via `setOverride()` therefore automatically triggers solver re-evaluation on the next layout pass. No additional invalidation plumbing is required for annotation loading.

### 6.4 Build-Time Re-Annotation

The annotation tool should be re-run when:
- Schema changes (detected by hash mismatch on load).
- Domain semantics change (e.g., a previously unimportant field becomes business-critical). This requires manual re-run; the tool has no way to detect domain semantic changes automatically.

A recommended workflow:
1. Check in `.kramer-annotations.json` to version control alongside the schema.
2. Re-run the annotation tool as part of the schema update process.
3. Review annotation diffs in code review (LLM-generated annotations are readable and reviewable).

---

## 7. Implementation Plan

### Phase 2a: Annotation CLI Tool

**Bead**: Kramer-d70 (first deliverable)

**Scope**:
- `AnnotationTool` CLI class (main entry point)
- `SchemaSerializer` — converts `Relation` tree to the indented field-list prompt format
- `LlmAnnotationClient` interface + `AnthropicAnnotationClient` implementation (HTTP via Jakarta RS, already a project dependency)
- `AnnotationResponse` record — parsed and validated LLM response
- `AnnotationLoader` — reads `.kramer-annotations.json`, validates schema hash, calls `DefaultLayoutStylesheet.setOverride()`
- `AnnotationWeights` record (for Phase 2b consumption)

**Module placement**: New submodule `kramer-annotation` (depends on `kramer`, depends on `kramer-ql` for Jakarta RS client). Kept out of `kramer` core to avoid adding LLM HTTP client as a runtime dependency of the layout engine.

**Test coverage**:
- `AnnotationToolTest`: mock LLM response → verify `setOverride()` called with correct paths and values
- `SchemaSerializerTest`: relation tree → prompt format string, assert field ordering and indentation
- `AnnotationResponseValidatorTest`: malformed JSON, missing fields, invalid importance values → verify fallback behavior and log warnings
- `AnnotationLoaderTest`: hash match → load annotations; hash mismatch → warn + uniform fallback

### Phase 2b: Weighted Objective in ExhaustiveConstraintSolver

**Bead**: Kramer-d70 (second deliverable, same bead)

**Scope**:
- Add `AnnotationWeights` parameter to `ConstraintSolver.solve()` (null-safe — null → Phase 1 objective)
- Modify `ExhaustiveConstraintSolver` scoring: replace density count with weighted violation cost sum
- Importance propagation: `effectiveWeight()` computed once at solve entry from `AnnotationWeights` map
- `@prefer` soft penalty integrated into score

**Test coverage**:
- `WeightedConstraintSolverTest`: annotated schema where greedy (and Phase 1 unweighted) would choose OUTLINE for a high-importance field but weighted solver keeps it TABLE by accepting worse treatment of a low-importance sibling
- Regression: all Phase 1 `ConstraintSolverTest` cases pass with `weights=null`

### Phase 2c: ojAlgo LP Solver for N > 15

**Bead**: Kramer-d70 (third deliverable, same bead)

**Prerequisite gate**: ojAlgo integration validated (trivial LP test passes) before any LP formulation code is written.

**Scope**:
- `OjAlgoConstraintSolver` implementing `ConstraintSolver`
- LP formulation as specified in section 5.4
- Post-rounding feasibility repair
- `ExhaustiveConstraintSolver` delegates to `OjAlgoConstraintSolver` when N > 15 (replaces current greedy fallback)
- `pom.xml` addition: `org.ojalgo:ojalgo:55.0.1`

**Test coverage**:
- `OjAlgoConstraintSolverTest`: N=16 schema → LP solver produces valid assignment (all TABLE nodes within width constraint)
- Weighted N=16: high-importance fields prefer TABLE over low-importance fields under width pressure
- `ExhaustiveConstraintSolverTest` N=16 delegation: confirm ojAlgo path is taken (via solver type check or logging)

---

## 8. Risks

### R1: LLM Cost Per Schema

Estimated $0.005–$0.015 per schema with claude-haiku-3 (assuming 500–1500 tokens input + 200 tokens output). Cost is incurred once per schema version, not per render or per user session. For a project with 10 schemas, total cost is under $0.15. Cost is negligible at this scale.

Risk materializes if: schemas are very large (>100 fields), annotation tool is accidentally invoked in a loop, or model pricing changes. Mitigation: enforce a `--max-fields 100` guard in the CLI, log cost estimate before calling the LLM, cache aggressively.

### R2: Annotation Staleness on Schema Evolution

When a schema field is renamed, removed, or added, the schema hash changes and annotations are invalidated. The tool must be re-run. If re-run is forgotten, the system falls back to uniform weights — correct behavior, but without the semantic weighting benefit.

Risk materializes if: annotation re-run is not integrated into the schema update workflow. Mitigation: log a clear warning on hash mismatch at layout startup; document the re-run requirement in the tool's help text; recommend checking annotations into version control alongside schema.

### R3: Weight Calibration

The multipliers (high=10, medium=5, low=1) are initial estimates. If miscalibrated, the solver may consistently prefer TABLE for technically low-importance fields (multipliers too flat) or over-constrain the layout in favor of a single high-importance field at the expense of overall usability (multipliers too steep).

Risk mitigation: treat multipliers as configurable parameters, not constants. Expose them as `AnnotationWeights` constructor parameters (defaulting to 10/5/1). Validate on sample datasets during Phase 2b implementation. The calibration validation is a Phase 2 success criterion: annotated schemas must produce measurably better layouts on sample datasets.

### R4: ojAlgo Dependency Size and Compatibility

ojAlgo 55.0.1 is approximately 6 MB. It introduces a non-trivial dependency into the build. Being pure Java (no JNI), it avoids native library issues but adds to the distribution size.

Risk mitigation: place ojAlgo in the new `kramer-annotation` submodule only. The `kramer` core module must not depend on ojAlgo. If the LP solver is needed at runtime (future use), it is added to `kramer` at that time via a deliberate decision, not by accident. Validate Apache 2.0 license compatibility with project license (both Apache 2.0 — no conflict).

### R5: LP Relaxation Rounding Error

The continuous LP relaxation with post-rounding may produce suboptimal assignments. A fractional solution x_i = 0.5 rounded to TABLE might violate feasibility, requiring flip to OUTLINE — which may itself be suboptimal relative to other assignment combinations.

Risk mitigation: the feasibility repair pass is correct (never produces infeasible assignments). The quality of the rounded solution is bounded by the integrality gap of the LP relaxation. For this specific constraint structure (each constraint involves only one x_i), the LP relaxation is exact (no integrality gap) — the feasibility constraints are per-node, not coupling constraints. Document this in code comments. If coupling constraints are added in future phases, this assumption must be revisited.

---

## Success Criteria (Phase 2)

- [ ] Annotation CLI tool produces consistent output across 3 runs for the same schema (temperature=0 determinism).
- [ ] Weighted solver produces measurably better layouts than uniform-weight solver on at least 2 annotated sample datasets (quantified by density metric or field-visibility score).
- [ ] All Phase 1 `ConstraintSolverTest` cases pass unchanged with `weights=null`.
- [ ] ojAlgo integration validated: trivial LP test passes before LP formulation is written.
- [ ] N=16 weighted assignment: LP solver respects importance weights (high-importance fields prefer TABLE).
- [ ] Annotation staleness: hash mismatch produces warning and uniform fallback, no silent stale-annotation use.
- [ ] LLM failure produces uniform-weight output and continues without exception.
