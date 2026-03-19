---
title: "Expression Language for LayoutStylesheet"
id: RDR-021
type: Feature
status: closed
close_reason: implemented
priority: P3
author: Hal Hildebrand
created: 2026-03-16
accepted_date: 2026-03-16
closed_date: 2026-03-16
reviewed-by: self
---

# RDR-021: Expression Language for LayoutStylesheet

## Metadata
- **Type**: Feature
- **Status**: closed
- **Priority**: P3
- **Created**: 2026-03-16
- **Accepted**: 2026-03-16
- **Closed**: 2026-03-16
- **Reviewed-by**: self
- **Close-reason**: implemented
- **Related**: RDR-018 (Query-Semantic LayoutStylesheet, Phase 2 spec), RDR-014 (filter/sort properties), RDR-013 (statistical content width measurement)

---

## Problem Statement

`LayoutStylesheet` currently supports only static property values (strings, numbers, booleans). Users cannot express dynamic filter predicates, computed columns, or aggregates within a stylesheet. The expression language enables data-driven layout configuration — row filtering, virtual formula columns, group-level aggregation, and derived sort keys — beyond what static property overrides provide.

---

## Context

### Background

RDR-018 extended `LayoutStylesheet` with query-semantic property keys (`filter-expression`, `formula-expression`, `aggregate-expression`, `sort-expression`). These keys accept string values, but without an evaluator those strings are inert. The RDR-018 Phase 2 sub-specification (design doc `2026-03-16-rdr-018-expression-language-spec.md`) fully specifies the grammar, type system, scoping rules, and pipeline integration for the expression evaluator. RDR-021 is the implementation RDR that formalizes that spec for tracking and rationale purposes.

### Technical Environment

- Java 25 (sealed interfaces, pattern matching switch)
- Jackson `JsonNode` tree model for all data rows
- `LayoutStylesheet` property system (`getString` for expression retrieval by `SchemaPath`)
- `RelationLayout.measure()` as the integration point in the data pipeline
- `MeasureResult` caching in `RelationLayout` for layout-pass deduplication

---

## Research Findings

**RF-1: Design spec complete for parser and evaluator.**
The Phase 2 sub-specification defines the grammar (EBNF), type system, JsonNode materialization table, null-propagation rules, scoping rules, all four use cases (filter, formula, aggregate, sort), pipeline ordering, implementation strategy (recursive descent parser + sealed AST + pattern-matching evaluator), and risk mitigations. Four open questions (see below) must be resolved before pipeline wiring begins.

**RF-2: Recursive descent is the correct parser choice.**
An external parser generator (ANTLR, JavaCC, JEXL) would add a compile-time dependency for a grammar small enough to fit in one class. The expression grammar has six precedence levels and a closed function name set. Hand-written recursive descent produces a zero-dependency parser, keeps the codebase Spartan, and gives precise error positions in `ParseException`.

**RF-3: Pipeline ordering (filter → formula → aggregate → sort) matches relational semantics.**
This is the WHERE → projected/virtual columns → GROUP BY/aggregation → ORDER BY sequence from relational algebra. Evaluating filter before formula avoids computing formulas for excluded rows. Evaluating formulas before aggregates ensures aggregate functions can reference formula-derived fields. Evaluating formulas before RDR-013 statistical width sampling ensures width estimates for formula columns are correct.

---

## Proposed Solution

Implement the expression language as specified in the Phase 2 sub-specification. The spec is the authoritative design document; this RDR records the rationale, alternatives, risks, and implementation plan for project tracking.

### Grammar Summary

The expression language is an embedded sub-language within `LayoutStylesheet` string property values. It is intentionally narrow: layout configuration, not general-purpose scripting.

Operator precedence (highest to lowest): unary `!`/`-`, `*`/`/`, `+`/`-`, comparisons, `&&`, `||`. Full EBNF in the spec.

Built-in functions: `len`, `upper`, `lower`, `abs`, `round`, `if` (scalar); `sum`, `count`, `avg`, `min`, `max` (aggregate).

Field references use a `$` prefix: `$name` or `${field.path}` for nested access. Bare identifiers are not field references; this prevents collision with keywords and function names.

### Type System

Four value types: NUMBER (IEEE 754 double), STRING (Unicode), BOOLEAN, NULL. `JsonNode` values are materialized to expression types on field read. Null propagates through arithmetic; in boolean contexts (`&&`, `||`, `!`) NULL is treated as false rather than propagating, so a missing field in a filter predicate causes the row to be excluded rather than silently propagating through the expression.

### AST Node Types

```
Expr (sealed)
  ├── Literal(Object value)
  ├── FieldRef(List<String> path)
  ├── BinaryOp(Op op, Expr left, Expr right)
  ├── UnaryOp(Op op, Expr operand)
  ├── FunctionCall(String name, List<Expr> args)
  └── AggregateCall(String fn, Optional<Expr> arg)
```

`AggregateCall` is distinguished from `FunctionCall` at parse time. This allows the evaluator to dispatch correctly using Java 25 pattern matching switch without runtime name inspection. `count()` takes no argument; its `arg` is `Optional.empty()`. All other aggregate functions require exactly one argument expression.

### Evaluator

Per-row evaluation (filter, formula, sort): tree-walking visitor over the sealed `Expr` hierarchy. Short-circuit evaluation for `&&` and `||`. Division by zero produces NULL (not exception) for safe formula use with zero-valued denominators.

Aggregate evaluation: map the argument expression over all rows, reduce by the aggregate function. NULLs are excluded from aggregate input sets (`count` counts non-null rows only).

### Expression Compilation and Caching

Expressions are parsed to AST once per unique string per `SchemaPath`, at first use. Compiled ASTs are cached in `ExpressionEvaluator`. Cache invalidation follows `LayoutStylesheet.getVersion()` — when the stylesheet version increments, expression caches are cleared, consistent with `AutoLayout.decisionCache` invalidation. `LayoutStylesheet.getVersion()` confirmed present (research-3, verified against LayoutStylesheet.java interface). `DefaultLayoutStylesheet` increments version on `setOverride`/`clearOverrides`.

### Pipeline Integration

`RelationLayout.measure()` additions:

1. Read `filter-expression` for the current `SchemaPath`. Compile (or retrieve from cache). Evaluate per row; exclude rows where result is false or null.
2. Read `formula-expression` for each child `Primitive` path. Evaluate formulas in topological order over the filtered row set. Materialize results as `ObjectNode` field overrides.
3. Read `aggregate-expression` for any child `Primitive` marked as aggregate. Presence of aggregate-expression on a SchemaPath is sufficient to mark that Primitive as aggregate-mode. No separate marker property needed. Evaluate aggregate over post-formula rows; hold result for rendering once the `aggregate-position` property is defined (see Open Question 3). Step C delivers computation, not rendering.
4. Read `sort-expression` per row (if present) to derive sort keys. Apply sort.

`ExpressionEvaluator` is injected via the `Style` factory, which already receives `LayoutStylesheet`. It is stateless per expression; all state is in the cached AST and the per-row `JsonNode` context.

---

## Alternatives Considered

**Alt A: Use an existing expression library (MVEL, SpEL, JEXL).**
Each library adds a transitive dependency of 100–500 KB with its own security surface. JEXL exposes reflection-based property access; SpEL can invoke arbitrary Spring beans. None of the three has a closed function set. For a layout framework, importing a general-purpose scripting library to evaluate filter predicates is a disproportionate dependency. Rejected.

**Alt B: JavaScript via GraalVM polyglot.**
Full general-purpose scripting at the cost of a 50 MB+ runtime dependency. The security boundary between JavaScript expressions and the host JVM is complex to enforce correctly. Explicitly prohibited in the spec (Section 7 risk mitigation). Rejected.

**Alt C: Static stylesheet values only (current state).**
No implementation cost, but filter predicates, formula columns, and aggregates are impossible. Users must pre-process data before passing it to `AutoLayout`, coupling data access logic to layout configuration. This is the current limitation that motivates the feature. Rejected as the do-nothing baseline.

---

## Trade-offs and Risks

### Expression Injection

**Risk**: expressions are user-supplied strings stored in `LayoutStylesheet`.

**Mitigation**: the expression language has no I/O, no reflection, no dynamic dispatch, no access to Java APIs. The evaluator is a pure tree-walking interpreter over a closed sealed AST type set. Injection can only produce a wrong value, not code execution. Input sanitization at the parser level (character set validation, 4096-character length cap) is added as defense-in-depth. The spec explicitly prohibits use of `javax.script.ScriptEngine`, Nashorn, GraalVM polyglot, or any general-purpose scripting runtime.

### Performance

**Risk**: expressions are evaluated per-row during `RelationLayout.measure()`, which is invoked on JavaFX layout passes.

**Mitigations**: AST compilation is cached per expression string (parsing happens once). Per-row evaluation is O(1) per expression evaluation; row-level allocation cost depends on formula overlay strategy (see Open Question 2). If deepCopy per row is required, cost is O(rows × formulas). The dominant cost is the existing sort (O(k log k)); expression evaluation adds a constant factor per row. `MeasureResult` caching in `RelationLayout` absorbs repeated measure calls at the same width; expression evaluation is embedded in `MeasureResult` and is not re-executed at the same width. Datasets exceeding ~10,000 rows will be perceptibly slow on client-side evaluation; this is a known Phase 2 limitation. Very large datasets are a Phase 3 (server-side push) concern.

### Circular Formula References

**Risk**: formula for field A references `$B`, formula for field B references `$A`.

**Mitigation**: at wiring time in `RelationLayout.measure()`, construct a directed dependency graph over formula fields. A cycle causes all formulas in the cycle to be treated as absent (raw `JsonNode` value used as fallback), with a WARN log naming the cycle. Detection is O(F²) where F is the number of formula fields per relation (typically < 10 in practice). Self-reference is the degenerate cycle case and is detected by the same graph traversal without special-casing. Cycle detection results are cached alongside the AST cache, per stylesheet version.

### Aggregate/Row Context Mismatch

**Risk**: `aggregate-expression` property contains no aggregate function call, or `formula-expression` contains an aggregate call.

**Mitigation**: at wiring time, classify each compiled expression. If `aggregate-expression` AST contains no `AggregateCall` node, log WARN and treat as absent. If `formula-expression` AST contains an `AggregateCall` node, log WARN and treat as absent. Checks run once per expression per stylesheet version.

### RDR-013 Ordering Interaction

**Risk**: statistical content width measurement (RDR-013) samples `JsonNode` values for width estimation. Formula results produce new values that affect measured widths. If RDR-013 sampling runs before formulas are computed, width estimates for formula columns will be wrong.

**Required constraint**: formula evaluation must complete before statistical width sampling begins. The pipeline position defined above (formulas evaluated early in `RelationLayout.measure()`, before child measure) satisfies this requirement, provided RDR-013's sampling hook is in the child measure path. This dependency must be validated during Phase 2c wiring.

### Cross-Cutting Behavioral Notes

**`hideIfEmpty` and `filter-expression` ordering**: `filter-expression` runs after `hideIfEmpty`. A filter that excludes all rows does NOT trigger `hideIfEmpty` re-evaluation. This is expected behavior: `hideIfEmpty` reflects the pre-filter presence of data; `filter-expression` is a view-level exclusion applied on top. `hideIfEmpty` is evaluated inside `RelationLayout.measure()` at line ~701, before the filter-expression insertion point at line ~720. The ordering is intra-measure, not pre-measure.

**RDR-016 compatibility**: Any `MeasureResult` schema changes introduced by RDR-016 (layout stability / incremental update) must be reviewed for compatibility with expression result embedding. Expression evaluation results (filtered row sets, formula overlays, aggregate values) are stored alongside `MeasureResult`; a structural change to that record requires a coordinated update here.

**Data-change invalidation**: RDR-023 (Reactive Layout Invalidation, implemented) clears `frozenResult` on data change via `DataSnapshot` comparison. Expression results cached in `MeasureResult` are invalidated through the same path — when data changes, `frozenResult` is cleared, `measure()` re-runs, and expressions re-evaluate against fresh data.

### Open Questions (Deferred to Implementation)

The following are deferred and must be resolved before Phase 2c wiring begins:

1. **`cell-format` interaction with formula results**: printf-style format patterns must match the runtime type of the formula result. A wrong format code produces `IllegalFormatException`; catch and log, render raw value as fallback.
2. **Formula column `JsonNode` injection strategy**: formula results must be overlaid on the row's `ObjectNode`. If the row `JsonNode` is immutable, a copy-on-write overlay or mutable copy is required. The choice has memory implications for large datasets.
3. **Aggregate result render position**: a single summary value per group — footer row, column header annotation, or standalone cell — is determined by a separate `aggregate-position` property (not specified here). The expression language spec must leave room for this.
4. **`sort-expression` vs `sort-fields` precedence**: if both are present on the same `SchemaPath`, `sort-expression` should take precedence with `sort-fields` as tiebreaker. Document in `LayoutPropertyKeys` javadoc during Phase 2c.

---

## Implementation Plan

### Step A: Parser

- Implement single-pass tokenizer producing the token stream specified in the design spec.
- Implement recursive descent parser producing sealed `Expr` AST. `count()` is parsed as `AggregateCall(fn="count", arg=Optional.empty())`; all other aggregate functions require one argument expression.
- `AggregateCall` distinguished from `FunctionCall` at parse time.
- `ParseException` with char offset and message on parse error.
- Parse-time validations: aggregate/non-aggregate context mismatch (warn + absent), mixed aggregate/scalar at expression root (warn + absent).
- Unit tests: expression strings → expected AST structure; error cases → `ParseException` messages.

### Step B: Evaluator

- Implement `ExpressionEvaluator` with per-row evaluate (`Expr`, `JsonNode` → `Object`) and aggregate evaluate (`AggregateCall`, `List<JsonNode>` → `Object`).
- Java 25 pattern matching switch over sealed `Expr` hierarchy.
- Short-circuit `&&` / `||`. Division by zero → NULL.
- `JsonNode` materialization per the spec's type table.
- Coercion rules: arithmetic, comparison, boolean contexts per spec.
- Null propagation per spec's null propagation table.
- AST cache keyed by expression string, invalidated by `LayoutStylesheet.getVersion()`.
- Unit tests: eval against `ObjectNode` fixtures; null propagation cases; aggregate reduction; cycle detection.

### Step C: Pipeline Wiring into RelationLayout.measure()

- Inject `ExpressionEvaluator` via `Style` factory.
- Add filter pass after `extractFrom(datum)`: read `filter-expression`, evaluate per row, collect passing rows.
- Add formula pass: read `formula-expression` for each child `Primitive` path, build dependency graph, detect cycles (warn + skip cycle members), evaluate in topological order, materialize as `ObjectNode` overlays.
- Add aggregate pass: read `aggregate-expression` for aggregate-marked `Primitive` paths, evaluate over post-formula rows.
- Add sort-expression pass before sort: read `sort-expression`, evaluate per row as sort key.
- Validate formula-before-RDR-013-sampling ordering.
- Resolve the four open questions listed above.
- Integration tests: end-to-end pipeline with filter, formula, aggregate, sort; verify `MeasureResult` caching does not re-evaluate expressions at the same width.

---

## Finalization Gate

Before closing this RDR as implemented:

1. Does the implementation match the grammar exactly as specified in the design spec, including operator precedence and null propagation rules?
2. Are all four expression use cases (filter, formula, aggregate, sort) covered by integration tests with real `JsonNode` data?
3. Have the four open questions been resolved and documented in `LayoutPropertyKeys` javadoc or a follow-on RDR?
4. Has the RDR-013 ordering constraint been verified — formulas evaluated before statistical width sampling in the measure pipeline?
5. Is the injection risk mitigation (no `ScriptEngine`, no GraalVM polyglot, length cap enforced) verified in code review?

---

## Success Criteria

- [ ] Filter expressions (`filter-expression`) exclude rows where the predicate is false or null, and include rows where it is true.
- [ ] Formula expressions (`formula-expression`) produce computed values that appear in layout cells and are accessible as sort keys in the same pipeline pass.
- [ ] Aggregate expressions (`aggregate-expression`) produce summary values across all rows in the current group.
- [ ] No external runtime dependencies are added (no ANTLR, MVEL, SpEL, JEXL, GraalVM, Nashorn).
- [ ] Expression parse errors produce a `ParseException` with a char offset and human-readable message; malformed expressions are treated as absent (pipeline stage skipped) without crashing layout.
- [ ] All existing tests pass with Step C wiring in place.
