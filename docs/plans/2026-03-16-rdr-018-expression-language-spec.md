# RDR-018 Phase 2 Sub-Specification: Expression Language

- **RDR**: 018-P2
- **Bead**: Kramer-7x6
- **Date**: 2026-03-16
- **Status**: draft
- **Prerequisite for**: Phase 2 implementation (filter, formula, aggregate, cell-format properties)

---

## 1. Grammar (EBNF)

The expression language is an embedded sub-language within `LayoutStylesheet` property values. It is intentionally narrow: layout configuration, not general-purpose scripting.

```ebnf
expression      ::= or-expr

or-expr         ::= and-expr ( "||" and-expr )*
and-expr        ::= not-expr ( "&&" not-expr )*
not-expr        ::= "!" not-expr
               | compare-expr

compare-expr    ::= add-expr ( compare-op add-expr )?
compare-op      ::= "==" | "!=" | "<" | ">" | "<=" | ">="

add-expr        ::= mul-expr ( add-op mul-expr )*
add-op          ::= "+" | "-"

mul-expr        ::= unary-expr ( mul-op unary-expr )*
mul-op          ::= "*" | "/"

unary-expr      ::= "-" unary-expr
               | primary

primary         ::= literal
               | field-ref
               | function-call
               | "(" expression ")"

literal         ::= number-literal
               | string-literal
               | boolean-literal
               | null-literal

number-literal  ::= integer | decimal
integer         ::= DIGIT+
decimal         ::= DIGIT+ "." DIGIT+

string-literal  ::= '"' char* '"'
char            ::= any Unicode code point except '"' and '\', or escape-seq
escape-seq      ::= '\' ( '"' | '\' | 'n' | 't' | 'r' )

boolean-literal ::= "true" | "false"
null-literal    ::= "null"

field-ref       ::= "$" identifier
               | "${" field-path "}"
field-path      ::= identifier ( "." identifier )*

identifier      ::= ALPHA ( ALPHA | DIGIT | "_" )*
ALPHA           ::= [a-zA-Z_]
DIGIT           ::= [0-9]

function-call   ::= function-name "(" arg-list? ")"
arg-list        ::= expression ( "," expression )*
function-name   ::= "len" | "upper" | "lower" | "abs" | "round" | "if"
               | "sum" | "count" | "avg" | "min" | "max"
```

### Operator Precedence (highest to lowest)

| Level | Operators      | Associativity |
|-------|----------------|---------------|
| 7     | `!`, unary `-` | right         |
| 6     | `*`, `/`       | left          |
| 5     | `+`, `-`       | left          |
| 4     | `<`, `>`, `<=`, `>=`, `==`, `!=` | left |
| 3     | `&&`           | left          |
| 2     | `\|\|`           | left          |
| 1     | expression root | —            |

Parentheses override precedence at any level.

### Notes

- Whitespace (space, tab, newline) is ignored between tokens.
- No assignment, no statement sequences, no block syntax. Every expression is a single value-producing term.
- No user-defined functions. The function name set is closed.
- Aggregate functions (`sum`, `count`, `avg`, `min`, `max`) are syntactically identical to scalar functions but are semantically distinguished at the use-site (see Section 6).

---

## 2. Type System

### Value Types

| Type    | Description                                          |
|---------|------------------------------------------------------|
| NUMBER  | IEEE 754 double. Integers are a subset.              |
| STRING  | Unicode text.                                        |
| BOOLEAN | `true` or `false`.                                   |
| NULL    | Absence of a value. Propagates through most operations. |

There is no DATE type. Date comparison must use string comparison on ISO-8601 formatted fields, which sorts lexicographically correctly.

### JsonNode Materialization

`JsonNode` values are materialized to expression types on field read:

| JsonNode kind       | Expression type | Value                                      |
|---------------------|-----------------|--------------------------------------------|
| `IntNode`           | NUMBER          | numeric value                              |
| `LongNode`          | NUMBER          | numeric value (may lose precision >2^53)   |
| `DoubleNode`/`FloatNode` | NUMBER    | numeric value                              |
| `DecimalNode`       | NUMBER          | rounded to double                          |
| `TextNode`          | STRING          | text value                                 |
| `BooleanNode`       | BOOLEAN         | boolean value                              |
| `NullNode`          | NULL            |                                            |
| `MissingNode`       | NULL            | treated identically to NullNode            |
| `ArrayNode`         | NULL            | arrays are not scalars; resolves to NULL   |
| `ObjectNode`        | NULL            | objects are not scalars; resolves to NULL  |

### Coercion Rules

Implicit coercion is applied only in arithmetic and comparison contexts where types are mixed. Coercion never happens silently in a way that conceals a likely authoring error — when in doubt, resolve to NULL.

**Arithmetic (`+`, `-`, `*`, `/`, unary `-`)**:
- NUMBER op NUMBER → NUMBER.
- STRING op NUMBER → attempt `Double.parseDouble(string)`. If parse fails, result is NULL.
- NUMBER op STRING → same, symmetric.
- STRING op STRING with `+` → string concatenation (the only string arithmetic).
- STRING op STRING with `-`, `*`, `/` → NULL.
- Any operand NULL → NULL (see Section 2.4).
- BOOLEAN in arithmetic → NULL.

**Comparison (`==`, `!=`, `<`, `>`, `<=`, `>=`)**:
- NUMBER vs NUMBER → numeric comparison.
- STRING vs STRING → lexicographic comparison.
- BOOLEAN vs BOOLEAN → equality only; `<`, `>`, `<=`, `>=` produce NULL.
- Mixed NUMBER/STRING → attempt numeric coercion of the string. If parse fails, fall back to string representation of the number for string comparison. Implementation note: this is intentionally predictable for filter authors; use explicit `len()` or casting patterns if precise behavior is needed.
- NULL == NULL → `true` (see Section 2.4).
- NULL == anything-non-null → `false`.
- NULL with `<`, `>`, `<=`, `>=` → `false`.

**Boolean contexts (`&&`, `||`, `!`)**:
- BOOLEAN operands only. Any non-BOOLEAN, non-NULL value coerced: NUMBER non-zero → `true`, zero → `false`; STRING non-empty → `true`, empty → `false`. NULL in boolean context → `false` (not NULL propagation; see rationale below).
- Rationale: filter predicates produce boolean results. Propagating NULL through `&&`/`||` would silently exclude rows whose fields are missing, which is a subtle and common source of user confusion. Treating NULL as false in boolean context makes the behavior explicit: a row with a missing field that is referenced in a filter will not match the filter. Authors who want to include such rows must write `$field == null || $field > 0` explicitly.

### Null Propagation Rules

| Context                    | Rule                                             |
|----------------------------|--------------------------------------------------|
| Arithmetic with NULL       | Result is NULL                                   |
| `+` with NULL operand      | NULL (no string-concat fallback for nulls)       |
| Comparison NULL == NULL    | `true`                                           |
| Comparison NULL != non-null | `true`                                          |
| Comparison NULL < / > / <= / >= | `false`                                   |
| `&&` / `\|\|` with NULL       | NULL treated as `false`                          |
| `!` on NULL                | `true` (null is falsy, negation of falsy is true) |
| `if(NULL, b, c)`           | evaluates `c` branch (condition is falsy)        |
| `len(NULL)`                | NULL                                             |
| `upper(NULL)` / `lower(NULL)` | NULL                                          |
| `abs(NULL)` / `round(NULL)` | NULL                                            |
| `sum` / `avg` aggregate over set containing NULLs | NULLs excluded from the set |
| `count()` aggregate        | counts non-null rows only                        |
| `min` / `max` over all-null set | NULL                                        |

---

## 3. Scoping Rules

### Field References

A field reference `$name` or `${field.path}` resolves against the **current row's `JsonNode`**. The current row is the `JsonNode` element being processed at the time the expression is evaluated — a single array element for filter and formula expressions, the full data set for aggregate expressions (see Section 6).

**Simple reference**: `$revenue` resolves to `row.get("revenue")`. If the key is absent, the result is NULL (MissingNode).

**Nested reference**: `${customer.address.city}` resolves by walking: `row.get("customer").get("address").get("city")`. Each step that encounters a non-ObjectNode (including MissingNode or NullNode) short-circuits to NULL without error. This makes nested path traversal safe for sparse or heterogeneous data.

The `$` prefix is syntactically required on all field references. Bare identifiers are not field references — they are an error. This prevents accidental collisions with keywords (`true`, `false`, `null`) and function names.

### Parent Scope

Parent scope is **not accessible**. Each expression evaluates exclusively in the context of one row. There is no `$$parent` syntax, no cross-row variable binding, no access to sibling rows.

Rationale: parent-scope access creates implicit coupling between expressions at different schema levels. Aggregate functions (`sum`, `avg`, etc.) cover the cross-row case explicitly and with clear semantics.

### Function Scope

Built-in functions are globally available. There is no namespace prefix. If a future function name conflicts with a field name, the `$` prefix on field references avoids ambiguity.

### Formula Columns

A formula expression on a `Primitive` node at path `P` may reference sibling fields (other `Primitive` nodes at the same level in the schema) by their names. It may not reference itself (circular). It may not reference the virtual field it is producing by name in another expression until that expression is evaluated (forward references permitted between siblings, but evaluation order is topological; see Section 7).

---

## 4. Use Cases

### 4.1 Filter Expression

**Property key**: `filter-expression`
**Result type**: BOOLEAN (if result is not BOOLEAN after coercion, treated as false)
**Scope**: per-row predicate

A row where the filter expression evaluates to `false` (or NULL, which is falsy) is excluded from layout. Rows where the expression evaluates to `true` are included.

```
$revenue > 0 && $status != "cancelled"
```

```
${customer.tier} == "premium" || $lifetime_value >= 10000
```

```
$quantity != null && $unit_price != null
```

The filter runs after `hideIfEmpty` resolution and before child measure. See Section 5 for pipeline position.

### 4.2 Formula Expression

**Property key**: `formula-expression`
**Result type**: any (NUMBER, STRING, BOOLEAN, or NULL depending on expression)
**Scope**: per-row computation, result materialized as a virtual field

A formula expression replaces the field value at the target `Primitive` path with the computed result. The original `JsonNode` for that path (if any) is ignored; the formula result is materialized as a new `JsonNode` and injected into the row's value map before downstream layout.

```
$price * $quantity
```

```
upper($last_name) + ", " + $first_name
```

```
if($discount_rate > 0, $price * (1 - $discount_rate), $price)
```

Formula results become available to subsequent expressions in the same row's evaluation pass. Evaluation order is topological (see Section 7).

### 4.3 Aggregate Expression

**Property key**: `aggregate-expression`
**Result type**: NUMBER (for sum/avg/min/max), NUMBER (for count), or as produced by the expression
**Scope**: across all rows in the current group, produces a single value

Aggregate expressions use the aggregate function set (`sum`, `count`, `avg`, `min`, `max`). These functions receive an expression that is evaluated per-row, then reduced.

```
sum($revenue)
```

```
avg($unit_price)
```

```
count()
```

```
sum($price * $quantity)
```

An aggregate expression may combine scalar operations with an aggregate:

```
sum($revenue) / count()
```

Per SIEUFERD, the grouping scope is determined by the `Primitive`'s position in the schema hierarchy. A `sum($revenue)` on a `Primitive` that is a child of a `Relation` for `Customer` computes per-customer totals. The schema tree is the GROUP BY — no explicit grouping syntax is needed.

An expression that mixes aggregate and non-aggregate calls at the same level is an error caught at parse time: `sum($revenue) + $bonus` is illegal because `$bonus` is ambiguous in an aggregate context (it has no per-group singular value unless it is itself aggregated or constant across the group).

### 4.4 Sort Expression

**Property key**: `sort-expression`
**Result type**: NUMBER, STRING, or BOOLEAN (compared using natural ordering for the type)
**Scope**: per-row computation used as the sort key

A sort expression computes a derived value per row that replaces the raw field value for comparison purposes during sort. The sort direction (ascending/descending) is controlled by `sort-fields` property convention (prefix `-` for descending), not within the expression itself.

```
abs($delta)
```

```
lower($last_name)
```

```
$priority * -1
```

Sort expressions are evaluated after filter and formula passes but before the final sort step. This ensures formula-derived fields are available as sort keys.

---

## 5. Evaluation Context

### Property Storage

Expressions are stored as `String` values in `LayoutStylesheet` properties, keyed by `SchemaPath`. The property keys are:

| Property key           | Use case             | Section |
|------------------------|----------------------|---------|
| `filter-expression`    | Row inclusion filter | 4.1     |
| `formula-expression`   | Virtual column value | 4.2     |
| `aggregate-expression` | Group-level summary  | 4.3     |
| `sort-expression`      | Sort key derivation  | 4.4     |

These keys are registered in `LayoutPropertyKeys` alongside the Phase 1 display keys. Absence of a key means the feature is inactive for that path; the default behavior (no filtering, no formula, etc.) applies.

### Pipeline Position

The full data pipeline in `RelationLayout.measure()`, with Phase 2 additions, follows relational semantics (WHERE → projected/computed columns → GROUP BY/aggregation → ORDER BY):

```
extractFrom(datum)          // raw JsonNode extraction
  → filter-expression       // per-row boolean predicate; false rows excluded
  → formula-expression      // per-row virtual field computation; results materialized
  → aggregate-expression    // cross-row reduction; produces summary rows or values
  → sort-expression         // per-row sort key; then sort by sort-fields order
  → measure children        // layout downstream
```

**Position of filter**: after `hideIfEmpty` resolution (which operates on the schema node, not data rows), before child measure. This ensures child measure never sees excluded rows.

**Position of formula**: after filter so that formulas are not evaluated for excluded rows. Before aggregate so that aggregate functions can reference formula-derived fields.

**Position of aggregate**: after formula, before sort. Sort must operate on the final set of values (including aggregated ones).

**Position of sort**: final, after all data transformations.

### Expression Compilation

Expressions are parsed to an AST (see Section 6) once per unique expression string per `SchemaPath`, at first use. Compiled ASTs are cached in the expression evaluator. Cache invalidation follows the same stylesheet-version key used by `AutoLayout.decisionCache` — when `LayoutStylesheet.getVersion()` increments, compiled expression caches are cleared.

### Integration Point

`RelationLayout.measure()` currently calls `extractFrom(datum)` and applies sort/filter based on `Relation` field values. Phase 2 adds an `ExpressionEvaluator` component that is:

- Injected via the `Style` factory (which already receives `LayoutStylesheet`)
- Invoked after extraction, in the order described above
- Stateless per expression (all state lives in the AST + the per-row `JsonNode` context)
- Returns a modified list of `JsonNode` rows (filter removes elements; formula mutates elements; aggregate folds elements to a summary)

---

## 6. Implementation Strategy

### Phase 2a: Parser

**Approach**: Recursive descent parser. No parser generator dependency (ANTLR, JavaCC, etc.). This keeps the dependency footprint zero and matches the Spartan design philosophy of the project.

**Input**: expression string (from `LayoutStylesheet.getString(path, "filter-expression", null)`)
**Output**: AST rooted at an `Expr` sealed interface

**AST node types**:

```
Expr (sealed)
  ├── Literal(Object value)         // NUMBER (Double), STRING, BOOLEAN, NULL
  ├── FieldRef(List<String> path)   // $name or ${a.b.c}
  ├── BinaryOp(Op op, Expr left, Expr right)
  ├── UnaryOp(Op op, Expr operand)
  ├── FunctionCall(String name, List<Expr> args)
  └── AggregateCall(String fn, Expr arg)   // sum/count/avg/min/max
```

`AggregateCall` is distinguished from `FunctionCall` at parse time by checking whether the function name is in the aggregate function set. This allows the evaluator to dispatch correctly without runtime introspection.

**Lexer**: single-pass tokenizer producing a token stream before parsing. Tokens:

```
NUMBER, STRING, BOOLEAN, NULL, IDENT, FIELD_REF, FIELD_PATH_REF,
PLUS, MINUS, STAR, SLASH, BANG, AMP_AMP, PIPE_PIPE,
EQ, NEQ, LT, GT, LTE, GTE,
LPAREN, RPAREN, COMMA, EOF
```

**Error handling**: parse errors produce a `ParseException` with position (char offset) and message. The exception is caught at the `LayoutStylesheet` read site; expressions that fail to parse are treated as absent (the pipeline stage is skipped), and the error is logged at WARN level via SLF4J. This prevents a malformed expression from crashing layout.

**Validation at parse time**:
- Aggregate functions used outside an aggregate-expression context: WARN and treat as absent.
- Non-aggregate expressions in aggregate-expression context where aggregate function is required: WARN and treat as absent.
- Mixed aggregate/non-aggregate at same expression level (e.g., `sum($x) + $y`): WARN and treat as absent.
- Formula self-reference (expression at path P references the field at P): detected during wiring, not parse time. See Section 7.

### Phase 2b: Evaluator

**Input**: compiled `Expr` AST + current row `JsonNode` (or row set for aggregate)
**Output**: typed value (`Double`, `String`, `Boolean`, `null`)

**Design**: visitor over the `Expr` sealed hierarchy. Java pattern matching (`switch` on sealed type) is appropriate here and aligns with the Java 25 codebase.

**Per-row evaluation** (filter, formula, sort-expression):

```java
Object evaluate(Expr expr, JsonNode row) {
    return switch (expr) {
        case Literal(var v)            -> v;
        case FieldRef(var path)        -> resolveField(row, path);
        case BinaryOp(var op, var l, var r) -> evalBinary(op, evaluate(l, row), evaluate(r, row));
        case UnaryOp(var op, var e)    -> evalUnary(op, evaluate(e, row));
        case FunctionCall(var fn, var args) -> evalFunction(fn, args, row);
        case AggregateCall(_, _)       -> throw new EvalException("aggregate in row context");
    };
}
```

**Aggregate evaluation** (aggregate-expression):

```java
Object evaluateAggregate(AggregateCall call, List<JsonNode> rows) {
    // map call.arg over rows, reduce by call.fn
    // null exclusion per Section 2.4
}
```

**Short-circuit evaluation**:
- `&&`: if left is false (or null→false), right is not evaluated.
- `||`: if left is true, right is not evaluated.

**Division by zero**: `x / 0` → NULL (not an exception). This makes formula expressions safe in the presence of zero-valued denominators without requiring authors to write defensive `if($denom != 0, $x / $denom, null)` in every formula. The NULL result will propagate to the rendered cell as an empty value.

### Phase 2c: Wiring into RelationLayout.measure()

`RelationLayout.measure()` receives a `List<JsonNode>` rows. The Phase 2 pipeline addition:

1. Read `filter-expression` from stylesheet for the current `SchemaPath`. If present and non-null, compile (or retrieve from cache) and evaluate per row. Collect passing rows.
2. Read `formula-expression` for each child `Primitive` path. For each row, evaluate formulas in topological order (formulas that reference no other formulas first, then those that reference already-evaluated formulas). Materialize results as `ObjectNode` field overrides. Produce an updated row list.
3. Read `aggregate-expression` for any child `Primitive` marked as aggregate. Evaluate aggregate over the post-formula row list. Inject aggregate result as a summary row or column value per the schema position.
4. Proceed with sort (using `sort-expression` if present, otherwise `sort-fields`).

Steps 1–4 replace the current filter/sort logic in `RelationLayout.measure()`. The existing `Relation.isHideIfEmpty()` and `Relation.getSortFields()` reads are replaced by Phase 1 stylesheet reads before this pipeline runs.

---

## 7. Risks

### Expression Injection

**Risk**: expressions are user-supplied strings stored in `LayoutStylesheet`. A malicious expression could attempt code execution.

**Mitigation**: the expression language has no I/O, no reflection, no dynamic dispatch, no access to Java APIs. The evaluator is a pure tree-walking interpreter over a closed AST type set. There is no general-purpose scripting runtime invocation, no class loading, no dynamic code execution path. Injection into the expression language can only produce a wrong value, not code execution. Input sanitization at the parser level (character set validation, length cap of 4096 characters) is recommended as defense-in-depth.

**Do not**: use `javax.script.ScriptEngine`, Nashorn, GraalVM polyglot, or any general-purpose scripting runtime for expression evaluation. The parser and evaluator must be custom and fully bounded.

### Performance

**Risk**: expressions evaluated per-row during `RelationLayout.measure()`. For n rows, evaluation is O(n * expression-complexity). Measure is called on layout pass; JavaFX layout passes can be frequent.

**Mitigations**:
- AST compilation is cached per expression string. Parsing (O(expression-length)) happens once.
- Expression evaluation is O(1) per row — no allocation of new collections per row, no re-parsing.
- If the dataset has k rows, the filter pass is O(k). Formula pass is O(k * f) where f is the number of formula fields. Aggregate pass is O(k). Sort is O(k log k). The dominant cost is the existing sort; expression evaluation adds a constant factor.
- `MeasureResult` caching in `RelationLayout` absorbs repeated measure calls at the same width. Expression evaluation results are embedded in the `MeasureResult`; the cache prevents re-evaluation on subsequent layout passes at the same width.
- For datasets exceeding ~10,000 rows, client-side evaluation will be perceptibly slow. This is a known limitation of Phase 2's client-side model. The recommendation at that scale is Phase 3 (server-side push). No special mitigation is added to Phase 2 for very large datasets.

### Circular Formula References

**Risk**: formula for field A references `$B`, formula for field B references `$A`. Evaluating either formula would loop.

**Detection**: at the time formulas are wired in `RelationLayout.measure()`, construct a directed dependency graph over formula fields. If the graph contains a cycle, all formulas in the cycle are rejected: they are treated as absent (the field falls back to its raw `JsonNode` value), and a WARN is logged naming the cycle. Detection is O(F²) where F is the number of formula fields per relation; F is small in practice (typically <10).

**Detection timing**: dependency graph construction happens once per `SchemaPath` per stylesheet version, at the same time as AST compilation. Cycle detection results are cached alongside the AST cache.

**Self-reference**: a formula at path P that references `$name` where `name` is P's own field name is a self-reference. This is the degenerate cycle case and is detected by the same graph algorithm without special-casing.

### Aggregate/Row Context Mismatch

**Risk**: an `aggregate-expression` property contains a non-aggregate expression (no aggregate function call), or a `formula-expression` contains an aggregate call. Either is a semantic error that is not syntactically detectable.

**Mitigation**: at wiring time (before first evaluation), classify each compiled expression: if `aggregate-expression` property's AST contains no `AggregateCall` node, log WARN and treat the expression as absent. If `formula-expression` AST contains an `AggregateCall` node, log WARN and treat as absent. These checks run once per expression per stylesheet version.

### RDR-013 Ordering Interaction

**Risk**: statistical content width measurement (RDR-013) runs during the measure phase. Formula results produce new `JsonNode` values that affect measured content widths. If RDR-013 runs before formulas are computed, width estimates for formula columns will be wrong.

**Required**: formula evaluation must complete before statistical width sampling begins. The Phase 2 pipeline position (formulas evaluated early in `RelationLayout.measure()`, before child measure) satisfies this requirement, provided RDR-013's sampling hook is in the child measure path. This dependency must be validated during Phase 2c wiring.

---

## Open Questions (Deferred)

The following questions are explicitly deferred and must be resolved before Phase 2c implementation begins:

1. **`cell-format` interaction**: printf-style format patterns on formula results require type inference or explicit type in the format string. The `%d` vs `%f` vs `%s` distinction maps poorly to `Double` vs integer numeric values from `JsonNode`. Consider restricting `cell-format` to `String.format`-compatible patterns and requiring the author to match the format code to the field type. A wrong format code produces a runtime `IllegalFormatException`; catch and log, render raw value as fallback.

2. **Formula column JsonNode injection**: formula results must be injected into the row's `ObjectNode` for downstream layout to read them. If the row `JsonNode` is immutable (Jackson `JsonNode` is designed to be read-only in many configurations), injection requires wrapping: create an `ObjectNode` with the formula results overlaid on the original fields. The wrapping strategy (copy-on-write overlay vs. mutable copy) has memory implications for large datasets.

3. **Aggregate result representation**: when an aggregate expression produces a summary value for a `Primitive` in a `Relation`, how is it rendered? As a footer row? As a column header annotation? As a standalone cell at the top? This is a layout question, not an expression language question, but the expression language spec needs to leave room for the answer. The aggregate result is a single `JsonNode` value; where it appears in the layout is determined by a separate `aggregate-position` property (not specified here).

4. **Sort expression vs sort-fields interaction**: if both `sort-expression` and `sort-fields` are present on the same `SchemaPath`, which takes precedence? Recommendation: `sort-expression` takes precedence; `sort-fields` is used as a tiebreaker. This should be documented in `LayoutPropertyKeys` javadoc when Phase 2 is implemented.
