# RDR-028: Query Rewriter — Layer 1↔2 Bridge

## Metadata
- **Type**: Architecture
- **Status**: proposed
- **Priority**: P3
- **Created**: 2026-03-16
- **Related**: RDR-020 (GraphQL AST retention), RDR-026 (QueryState), RDR-018 (query-semantic stylesheet, Phase 3)

---

## Problem Statement

Kramer's expression pipeline (RDR-021) evaluates filter, formula, aggregate, and sort expressions **client-side** over already-fetched data. This works for small datasets but has two fundamental limitations:

1. **Scalability**: For datasets exceeding ~1000 rows, client-side filter/sort/aggregate is slow and memory-intensive. SIEUFERD's power comes from server-side execution — display changes rewrite the underlying SQL query.

2. **Correctness**: Client-side sort with cursor-based pagination sorts only the current page. Client-side filter reduces visible rows but does not fetch additional data to replace excluded rows. These are semantic gaps, not just performance gaps.

RDR-020 (GraphQL AST Retention) provides the infrastructure to retain the full `Document` AST and reconstruct modified queries. This RDR builds the `QueryRewriter` that bridges `QueryState` (Layer 2) to the GraphQL query (Layer 1) — pushing operations to the server where the backend supports them.

---

## Prior Art & Critique Findings Addressed

- **RDR-018 Critique C1**: Isomorphism is storage-only; execution model differs. This RDR addresses the execution gap by selectively pushing to server.
- **RDR-018 Critique C3**: Phase 3 requires GraphQlUtil redesign. Addressed by RDR-020 (AST retention). This RDR is a thin layer on top of RDR-020's `QueryBuilder`/`SchemaContext`.
- **RDR-018 Critique C4**: 3-4 SIEUFERD properties are backend-dependent (JOINON, COLUMNDEFINITION, REVERSEREF, EDITMODE). This RDR's capability model handles this explicitly.
- **RDR-018 Critique S4**: SORTORDER false equivalence (server-side ORDER BY ≠ client-side sort). This RDR pushes sort to server when supported, resolving the equivalence gap.
- **RDR-018 Critique S5**: Client-side scalability limitation. This RDR is the mitigation — push to server for scale.
- **Substantive Critique C4**: Scope collision with RDR-020 resolved. This RDR depends on RDR-020 Phase C (`QueryBuilder.reconstruct()`). Scope is `QueryRewriter` only — no AST retention or reconstruction infrastructure.
- **Substantive Critique S3**: Backend capability model is defined (see below).

---

## Dependencies

- **RDR-020 Phase C** (`QueryBuilder.reconstruct(SchemaContext, LayoutStylesheet)`): Must be implemented. Provides the query reconstruction infrastructure.
- **RDR-026** (QueryState): Must be implemented. Provides the typed mutation source.
- **RDR-020 Phase A** (`SchemaContext`, `fieldIndex`): Must be implemented. Provides the `SchemaPath ↔ Field` mapping.

---

## Proposed Solution

### Backend Capability Model

Not all GraphQL backends support the same arguments. A `ServerCapabilities` record declares what a specific backend supports, discovered via GraphQL schema introspection:

```java
public record ServerCapabilities(
    Map<SchemaPath, Set<PushableOperation>> fieldCapabilities
) {
    public enum PushableOperation {
        FILTER,     // field accepts a filter/where argument
        SORT,       // field accepts an orderBy/sort argument
        LIMIT,      // field accepts a limit/first argument
        AGGREGATE   // field accepts aggregate arguments
    }
}
```

**Discovery**: On connection to a GraphQL endpoint, introspect the schema's field argument definitions. If a Relation field (e.g., `orders`) has an argument named `filter`, `where`, or matching a configurable pattern, mark that path as `FILTER`-capable. Similarly for `orderBy`/`sort` → `SORT`. Store in `ServerCapabilities`.

**Fallback**: If introspection is unavailable or a field lacks the expected arguments, that operation remains client-side (existing behavior from RDR-021). The QueryRewriter never attempts to push an unsupported operation.

### QueryRewriter

Takes a `QueryState` change, the current `SchemaContext` (from RDR-020), and `ServerCapabilities`. Produces a modified `Document` with server-side arguments applied:

```java
public class QueryRewriter {
    private final ServerCapabilities capabilities;
    private final SchemaContext context;

    /**
     * Rewrite the query to push supported operations server-side.
     * Returns the modified Document, or the original if no server-side
     * operations are applicable.
     */
    public String rewrite(SchemaContext ctx, LayoutQueryState queryState) {
        // For each SchemaPath with a filter/sort expression:
        //   1. Check if the backend supports pushing that operation
        //   2. If yes: add/modify the field argument in the AST
        //   3. Null pushed client-side expressions inside suppressNotifications
        //   4. If no: leave for client-side evaluation (RDR-021 pipeline)
        // Use QueryBuilder.reconstruct(ctx, queryState) for the final query string
    }
}
```

### Pushable Operations

| QueryState property | GraphQL argument | Push condition |
|--------------------|--------------------|----------------|
| `filterExpression` | `filter` / `where` argument on Relation field | `FILTER` in capabilities |
| `sortExpression` / `sortFields` | `orderBy` / `sort` argument on Relation field | `SORT` in capabilities |
| `visible = false` | Remove field from selection set | Always (saves bandwidth) |
| `aggregateExpression` | Backend-specific aggregate argument | `AGGREGATE` in capabilities |
| `formulaExpression` | Not pushable (client-side only) | Never — formulas are virtual columns |

### Client-Side Fallback

When an operation is not pushed server-side, it remains in `QueryState` and is evaluated client-side by the existing expression pipeline (RDR-021). The pipeline runs regardless — pushed operations produce pre-filtered/sorted data that the client-side pipeline sees as already-processed.

**Double-evaluation prevention**: When an operation IS pushed server-side, the `QueryRewriter`
nulls the corresponding client-side expression on `LayoutQueryState` (e.g.,
`queryState.setFilterExpression(path, null)` for a pushed filter). The expression pipeline in
`RelationLayout.measure()` sees no expression and skips client-side evaluation. The original
expression is retained in `QueryRewriter` internal state for re-push on the next rewrite cycle.

This approach:
- Requires no new interface methods or `LayoutStylesheet` changes
- Respects the module boundary (`QueryRewriter` in `kramer-ql` writes to `LayoutQueryState`
  in `kramer` via its public API — no upward dependency)
- Uses the existing `suppressNotifications` batch API to null all pushed expressions in one
  notification cycle
- When the user clears a pushed filter, `QueryRewriter` detects the null and stops pushing,
  restoring client-side-only behavior

### Integration With kramer-ql

`GraphQlUtil.evaluate()` currently takes a query string and an endpoint. With RDR-020's `SchemaContext` and this RDR's `QueryRewriter`:

```
QueryState change
  → QueryRewriter.rewrite(document, queryState)
  → GraphQlUtil.evaluate(rewrittenDocument, endpoint)
  → new data
  → AutoLayout.setData(newData)  // triggers measure → layout → render
```

The `explorer` app orchestrates this loop. The `InteractionHandler` (RDR-027) triggers the rewrite when it applies an event to `QueryState`.

---

## SIEUFERD Properties: Server-Side Feasibility

| # | Property | Server-pushable? | Notes |
|---|----------|-----------------|-------|
| 2 | FILTER | Yes, if backend supports `where`/`filter` arg | Common in Relay, Hasura, PostGraphile |
| 3 | SORTORDER | Yes, if backend supports `orderBy` arg | Common in generated GraphQL APIs |
| 6 | JOINON | Backend-dependent | Requires schema to expose join relationships |
| 8 | COLUMNDEFINITION | No (client-side formula only) | Server-side computed columns are schema-dependent |
| 9 | INSTANTIATEDTABLE | Implicit (GraphQL type) | Already in schema |
| 12 | REVERSEREF | Backend-dependent | Only when schema exposes inverse fields |
| 13 | AGGREGATE | Backend-dependent | Some APIs support aggregate queries |

Properties not listed are display-only (no server-side component).

---

## Scope Exclusions

- **GraphQL mutation support (EDITMODE)**: Requires full mutation infrastructure. Separate RDR.
- **JOINON implementation**: Requires backend-specific join syntax. Not standardized across GraphQL implementations. Deferred.
- **Subscription-based live data**: GraphQL subscriptions for streaming updates. Coordinate with RDR-023 (reactive invalidation). Separate effort.
- **Custom argument naming**: v1 uses a fixed set of recognized argument names (`filter`, `where`, `orderBy`, `sort`, `first`, `limit`). Configurable argument mapping deferred.

---

## Implementation Plan

### Phase 1: ServerCapabilities Discovery (kramer-ql module)
- Introspect GraphQL schema for field arguments
- Build `ServerCapabilities` record
- Unit tests against sample schemas (Relay-style, Hasura-style, bare)

### Phase 2: QueryRewriter Core (kramer-ql module)
- `QueryRewriter.rewrite()` for filter and sort push-down
- Visible-false field removal from selection set
- Integration with RDR-020's `QueryBuilder.reconstruct()`
- Unit tests: rewrite produces correct Document AST modifications

### Phase 3a: AutoLayoutController SchemaContext Retention (explorer module)

Currently `AutoLayoutController.setData()` at line 339 calls
`GraphQlUtil.buildContext(...).schema()` — the `SchemaContext` is discarded immediately.
This phase retains it as controller state for use by the rewriter.

- Add `private SchemaContext schemaContext` field to `AutoLayoutController`
- Update `setData()` to store the full context: `this.schemaContext = GraphQlUtil.buildContext(...)`
  then `layout.setRoot(schemaContext.schema())`
- Thread `ServerCapabilities` into the controller from the connection lifecycle
  (`ActiveState.setEndpoint()` or `AutoLayoutController` constructor)
- Tests: verify `schemaContext` is non-null after `setData()`

### Phase 3b: Change-Driven Re-fetch Path (explorer module)

Add a re-fetch listener to `LayoutQueryState` that is distinct from the existing
re-layout listener (`layout.autoLayout()`).

- Register a second change listener on `layoutQueryState`:
  ```
  layoutQueryState.addChangeListener(() -> {
      if (queryRewriter != null && schemaContext != null) {
          String rewritten = queryRewriter.rewrite(schemaContext, layoutQueryState);
          activeQuery.fetchAsync(rewritten);
      } else {
          layout.autoLayout();
      }
  });
  ```
- Coordinate with the existing GraphiQL WebEngine query submission path — user-typed
  queries from the editor should bypass the rewriter and use the raw query string
- Prevent re-entrant fetch loops with a `fetchInProgress` guard flag:
  1. Set `fetchInProgress = true` BEFORE calling `queryRewriter.rewrite()` (which
     nulls client-side expressions inside `suppressNotifications`, triggering a
     change notification — the guard must already be active when this fires)
  2. The change listener checks `if (fetchInProgress) return;` at the top
  3. `fetchAsync()` submits the rewritten query to the background executor
  4. The `Platform.runLater` callback in `fetchAsync` calls `setData()`, then
     clears `fetchInProgress = false` at the end of the callback
  This ensures the guard is held across the full rewrite → fetch → setData cycle,
  preventing the null-expression batch notification from triggering a second fetch.

### Phase 3c: Double-evaluation prevention + End-to-end test

- Implement the null-expression push-down mechanism in `QueryRewriter`:
  when pushing filter/sort server-side, null the client-side expression via
  `queryState.setFilterExpression(path, null)` inside `suppressNotifications`
- Retain original expressions in `QueryRewriter` state for re-push
- End-to-end test: interaction event → QueryState → rewrite → fetch → layout
  (requires stub GraphQL server or mock WebTarget)

---

## Research Findings

### RF-1: RDR-020 Phase C blocker resolved (Confidence: HIGH)

`QueryBuilder.reconstruct(SchemaContext, LayoutStylesheet)` exists at
`kramer-ql/.../QueryBuilder.java:51`. Walks Document AST, filters hidden fields,
strips client directives, serializes via `AstPrinter.printAst()`. 8 tests pass.

### RF-2: LayoutQueryState → LayoutStylesheet bridge works (Confidence: HIGH)

`LayoutQueryState` implements `LayoutStylesheet` at line 33. `getBoolean()` delegates
to inner `DefaultLayoutStylesheet`. Can be passed directly to `QueryBuilder.reconstruct()`
as the stylesheet argument — no adapter needed.

### RF-3: No existing sort/filter argument patterns in test queries (Confidence: HIGH)

No test queries use `orderBy`, `filter`, `where`, or `sort` arguments. Only `agency(id: $id)`,
`users(limit: 10)`, and Relay cursor args exist. New test fixtures needed.

### RF-4: SchemaContext.fieldAt().getArguments() works for argument access (Confidence: HIGH)

`SchemaContext.fieldAt(path).get().getArguments()` returns `List<Argument>`. Confirmed by
test at `SchemaContextTest.java:123-125`. Rewriter can inspect existing field arguments.

### RF-5: Explorer integration gap — SchemaContext is transient (Confidence: HIGH)

`AutoLayoutController.setData()` at line 339 builds `SchemaContext` but only extracts
`.schema()` — the full context is discarded. The change listener at line 274 only triggers
`autoLayout()` (re-layout), not re-fetch. Rewriter needs: (1) retained SchemaContext as
controller state, (2) change-listener-driven re-fetch path.

### RF-6: Expression-to-GraphQL translation feasible for simple cases (Confidence: HIGH)

`Expr` sealed hierarchy supports pattern-matching translation. `BinaryOp(EQ/NEQ/LT/GT/LTE/GTE)`
maps to `_eq/_neq/_lt/_gt/_lte/_gte`. `AND/OR` maps to `_and/_or`. Arithmetic, `ScalarCall`,
and `AggregateCall` cannot be pushed down — require "pushability check" before translation.

### RF-7: withCursor() fully implemented (Confidence: HIGH)

`SchemaContext.withCursor(SchemaPath, String)` at line 72 returns new immutable SchemaContext
with updated `after` argument in both `fieldIndex` and `Document` AST. 5 tests pass.

### RF-8: Sort field → orderBy mapping is format-dependent (Confidence: HIGH)

`sortFields` stored as `"name"` or `"-name"`. No sort-to-argument logic exists. Translation
depends on backend: Hasura (`order_by: {name: desc}`), PostGraphile (`orderBy: NAME_DESC`),
generic (`{field: "name", direction: "DESC"}`). v1 fixed-format approach is reasonable.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Argument naming varies across GraphQL implementations | High | Fixed recognized names in v1; configurable in follow-on |
| Introspection disabled on some endpoints | Medium | Graceful fallback to all-client-side |
| Push-down produces different results than client-side | Medium | Test equivalence for simple cases; document edge cases |
| Double-evaluation prevention via null-expression push | Medium | QueryRewriter nulls client-side expression via public LayoutQueryState API; retains original internally for re-push (Phase 3c) |
| SchemaContext discarded in AutoLayoutController | Medium | Phase 3a: retain as controller state before rewriter wiring |
| ~~RDR-020 Phase C not yet implemented~~ | ~~Blocking~~ | **Resolved** — Phase C implemented 2026-03-17 |

---

## Success Criteria

- [ ] `ServerCapabilities` correctly identifies pushable operations from GraphQL schema introspection
- [ ] Filter push-down produces equivalent results to client-side filtering for supported backends
- [ ] Sort push-down produces correct global sort (not page-local) for supported backends
- [ ] Visible-false fields are removed from selection set, reducing payload size
- [ ] Client-side fallback works seamlessly when server push is unavailable
- [ ] No double-evaluation: pushed operations are not re-evaluated client-side
- [ ] Existing tests pass with QueryRewriter in the pipeline (backward compatible)
