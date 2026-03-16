# RDR-018: Query-Semantic LayoutStylesheet Extension

## Metadata
- **Type**: Architecture
- **Status**: proposed
- **Priority**: P2
- **Created**: 2026-03-15
- **Related**: RDR-009 (LayoutStylesheet), RDR-014 (hideIfEmpty, sort), RDR-015 (renderMode), RDR-019 (Tufte Sparkline Rendering), SIEUFERD (SIGMOD 2016 Table 2)

## Problem Statement

SIEUFERD (SIGMOD 2016) defines 17 per-field properties that unify query semantics and display configuration. Its central insight: "the complete structure of a query can be encoded in the schema of the query's own result." Modifying the display modifies the query.

Kramer's `LayoutStylesheet` (RDR-009) currently carries only rendering parameters (maxTablePrimitiveWidth, variableLengthThreshold, etc.). Extending it to carry query-semantic properties would move Kramer toward a data-manipulation tool. However, the full SIEUFERD interaction model — where display changes automatically rewrite and re-execute queries — requires architectural work beyond what LayoutStylesheet alone provides.

---

## Current State

RDR-009 defined `LayoutStylesheet`:

```java
public interface LayoutStylesheet {
    double getDouble(SchemaPath path, String property, double defaultValue);
    int getInt(SchemaPath path, String property, int defaultValue);
    String getString(SchemaPath path, String property, String defaultValue);
    PrimitiveStyle primitiveStyle(SchemaPath path);
    RelationStyle relationStyle(SchemaPath path);
}
```

This interface is generic enough to carry ANY per-path properties. The infrastructure exists — it just isn't populated with query-semantic properties.

**Interface extension required**: The `LayoutStylesheet` interface defined in RDR-009 declares `getDouble`, `getInt`, and `getString` methods but not `getBoolean`. Multiple RDRs require boolean properties (`hide-if-empty`, `visible`, `sparkline-band-visible`, etc.). Phase 1 must add `boolean getBoolean(SchemaPath path, String property, boolean defaultValue)` to the `LayoutStylesheet` interface. This is a one-line addition to the interface and `DefaultLayoutStylesheet`.

---

## Scope & Phasing

This RDR describes three phases that are **independent deliverables** with fundamentally different scopes and effort profiles. They should be treated as separate work items, each closeable on its own timeline:

- **Phase 1** (Display property consolidation): Low-medium effort. Consolidates existing RDR-014/015 properties under LayoutStylesheet. This is infrastructure work — it delivers no new user-visible features but establishes the property addressing foundation. Closeable independently.
- **Phase 2** (Client-side data manipulation): High effort. Requires a **sub-specification** for the expression language before implementation can begin. See "Expression Language Requirements" below. Cannot proceed without that specification.
- **Phase 3** (Query integration): High effort. Requires a **separate RDR** for `kramer-ql` architectural redesign — `GraphQlUtil.buildSchema()` must be redesigned to retain the graphql-java AST. See Phase 3 details below.

Phase 1 priority should be understood as infrastructure: it enables Phase 2 and Phase 3 but does not itself change user experience.

---

## SIEUFERD's 17-Property Model (Table 2, SIGMOD 2016)

| # | Property | Scope | Category | Kramer Mapping | Feasibility |
|---|----------|-------|----------|----------------|-------------|
| 1 | VISIBLE | Both | Display | `visible` boolean (new) | Achievable |
| 2 | FILTER | Both | Query | `filter` Predicate (new) | Client-side approximation |
| 3 | SORTORDER | Both | Query | `sortFields` (RDR-014) | Client-side approximation |
| 4 | FORMULA | Primitive | Query | `formula` Expression (new) | Client-side approximation |
| 5 | FORMAT | Both | Display | `renderMode` (RDR-015) | Achievable |
| 6 | JOINON | Relation | Query | `joinField` (new, for kramer-ql) | Backend-dependent |
| 7 | HIDEIFEMPTY | Relation | Display | `hideIfEmpty` (RDR-014) | Achievable |
| 8 | COLUMNDEFINITION | Relation | Query | `columnDef` (new) | Backend-dependent |
| 9 | INSTANTIATEDTABLE | Relation | Query | GraphQL type (exists in schema) | Achievable |
| 10 | COLLAPSEDUPLICATEROWS | Relation | Display | `collapseDuplicates` (new) | Achievable (requires spec) |
| 11 | TUPLESORT | Relation | Display | `stableSort` (RDR-014) | Achievable |
| 12 | REVERSEREF | Relation | Query | N/A | Backend-dependent (only when schema exposes reverse field) |
| 13 | AGGREGATE | Primitive | Query | `aggregate` function (new) | Client-side approximation |
| 14 | CELLFORMAT | Primitive | Display | `cellFormat` pattern (new) | Achievable (requires type inference) |
| 15 | COLUMNWIDTH | Both | Display | Exists (justifiedWidth) | Achievable |
| 16 | FROZEN | Both | Display | `frozen` boolean (new) | Achievable (requires interaction model) |
| 17 | EDITMODE | Both | Interaction | `editable` boolean (new, future) | Requires mutation infrastructure |

**Feasibility notes:**
- **JOINON**: Standard GraphQL has no client-side join. Requires the backend schema to expose join relationships. Cannot be implemented purely in Kramer.
- **COLUMNDEFINITION**: Injecting computed columns requires server-side schema support. Kramer can approximate with client-side formulas (Phase 2), but this is not equivalent.
- **REVERSEREF**: Only possible when the GraphQL schema exposes reverse/inverse relationship fields. Not a general capability.
- **EDITMODE**: Requires full GraphQL mutation infrastructure — mutation schemas, optimistic updates, conflict resolution. A substantial effort beyond property addition.
- **SORTORDER**: SIEUFERD's SORTORDER modifies `ORDER BY` (server-side). Kramer sorts client-side, which produces incorrect results when combined with cursor-based pagination (only the current page is sorted, not the full result set). Correct sort-with-pagination requires Phase 3 server-side integration.

---

## Proposed Design

### Phase 1: Display Property Consolidation

Properties that control HOW data is shown without changing WHAT data is shown:

```java
// LayoutStylesheet property keys:
"visible"              // boolean: show/hide field (default: true)
"render-mode"          // enum: text|bar|badge|crosstab (RDR-015)
"hide-if-empty"        // boolean: hide parent with empty children (RDR-014)
"sort-fields"          // string: comma-separated field names (RDR-014)
```

> **Note**: sparkline render-mode is deferred to RDR-019 (Tufte Sparkline Rendering). Add to property registry when RDR-019 is accepted.

These are pure display concerns — they modify rendering without touching the data source.

Phase 1 should create a `LayoutPropertyKeys` constant class holding all registered keys from the central LayoutStylesheet Property Registry (see `docs/rdr/RDR-ROADMAP.md`). This class serves as the single source of truth for property key strings, preventing typo-driven mismatches across RDR-014, RDR-015, and future property consumers. Property naming follows kebab-case convention.

**Deferred from Phase 1:**
- `collapse-duplicates` — requires its own specification: what constitutes a "duplicate" (exact match? key fields?), how groups render, interaction with sort order. Recommend separate design document.
- `cell-format` — printf-style formatting on weakly-typed `JsonNode` values requires type inference. Moved to Phase 2 where type coercion is addressed.
- `frozen` — prevents user from hiding/moving a field, but no column-hide or column-reorder UI currently exists. Deferred to post-Phase 3 when an interaction model is established.

### Phase 2: Data Manipulation Properties

Properties that transform data before layout:

```java
"filter"               // string: predicate expression (e.g., "> 100", "contains 'active'")
"formula"              // string: expression referencing sibling fields (e.g., "sum(amount)")
"aggregate"            // enum: sum|avg|count|min|max|none
"cell-format"          // string: printf-style format pattern (moved from Phase 1)
```

**Formula scope**: Per SIEUFERD's design, a formula's aggregate scope is determined by its position in the schema hierarchy. A `SUM(amount)` field that is a child of `Customer` computes per-customer totals. A `SUM(amount)` field at the root computes the grand total. The schema tree IS the GROUP BY.

**Data pipeline integration**:

```
extractFrom(datum)
  -> sort (Phase 1 display property)
  -> filter (Phase 2 data property)
  -> compute formulas (Phase 2 data property)
  -> aggregate (Phase 2 data property)
  -> measure children
```

**Important limitation**: All data manipulation in Phase 2 is client-side. The full dataset is fetched from the server before filtering, sorting, or aggregating. For large datasets (>1000 rows), this provides display convenience but not query optimization. Phase 3 addresses this by pushing operations to the server.

#### Expression Language Requirements

Phase 2 requires a sub-specification before implementation. The following must be defined in a separate design document or sub-RDR:

1. **Grammar** (EBNF): Formal syntax for filter predicates and formula expressions.
2. **Field reference scoping rules**: How expressions reference sibling fields, parent fields, and child aggregates. Resolution rules for ambiguous names.
3. **Null handling semantics**: Behavior when `JsonNode` values are null or missing. Three-valued logic vs. null-coalescing.
4. **Type coercion strategy**: How `JsonNode` text/number/boolean values are coerced for comparison and arithmetic. Error handling for type mismatches.
5. **JsonNode materialization for virtual fields**: How computed formula results are represented as `JsonNode` values and made available to downstream layout.
6. **Formula-vs-aggregate distinction**: Clear rules for when an expression is evaluated per-row (formula) vs. across rows (aggregate). Whether a single expression can mix both.

This sub-specification is a **prerequisite** for Phase 2 implementation.

### Phase 3: Query Integration (Separate RDR Required)

Phase 3 aims to close the loop: visual manipulation -> stylesheet change -> query modification -> new data -> updated layout. This is the SIEUFERD endgame.

**However, Phase 3 requires a redesign of `GraphQlUtil`, not merely an extension.** The current `buildSchema()` implementation (`GraphQlUtil.java`) walks the graphql-java `Field` AST to extract field names and nesting structure, but **discards**:
- Field arguments (e.g., `orders(status: "active")`)
- Directives
- Aliases
- Pagination parameters
- The original graphql-java `Document` AST

Query reconstruction from stylesheet changes is impossible without retaining this information. `buildSchema()` would need to be redesigned to preserve the graphql-java AST alongside the `SchemaNode` tree, maintaining a bidirectional mapping between stylesheet paths and query AST nodes.

**Additional considerations:**
- GraphQL pagination is cursor-based per the Relay specification (`first`/`after`, `last`/`before`), not `LIMIT`/`OFFSET`. Query-level virtual scrolling must use cursor-based pagination, not the `LIMIT`/`OFFSET` model referenced in earlier drafts.
- Pushing filter and sort to the server requires the GraphQL backend to support those as field arguments — this is schema-dependent, not guaranteed.

**Recommendation**: Phase 3 should be its own RDR focused on `kramer-ql` architectural redesign, covering AST retention, bidirectional mapping, and cursor-based pagination integration.

---

## LayoutStylesheet as Property Addressing Layer

`LayoutStylesheet` + `SchemaPath` provides the **storage and addressing layer** for SIEUFERD-inspired properties. The structural correspondence:

| SIEUFERD | Kramer (after this RDR) |
|----------|------------------------|
| Nested relational schema | SchemaNode tree (Relation/Primitive) |
| Per-field property table | LayoutStylesheet per SchemaPath |
| Schema path identifier | SchemaPath record |

Phase 1 delivers display property consolidation under this addressing scheme. Phase 2 delivers client-side data manipulation — a weaker model than SIEUFERD's server-side approach, since all data is still fetched before client-side processing. Phase 3, if implemented via a separate `kramer-ql` redesign RDR, would begin to close the query-execution gap by pushing operations to the server.

The gap from Kramer's current architecture to SIEUFERD's full query model is smaller than building from scratch, but it is not merely a property-addition exercise — Phase 3 in particular requires significant architectural investment in `kramer-ql`.

---

## Interaction Model (Future, Post-Phase 3)

With query-semantic properties on LayoutStylesheet, direct manipulation becomes possible:
- **Right-click column header** -> sort ascending/descending (sets `sort-fields`)
- **Right-click column header** -> filter (sets `filter`)
- **Right-click column header** -> hide (sets `visible = false`)
- **Drag column border** -> resize (sets explicit width override)
- **Right-click relation** -> toggle hide-if-empty
- **Right-click numeric field** -> show as bar chart (sets `render-mode = bar`)

Each interaction modifies the `LayoutStylesheet`, triggering re-layout. If kramer-ql is active, the stylesheet change also modifies the GraphQL query (Phase 3), creating the SIEUFERD feedback loop.

**Note**: These interaction examples (right-click menus, drag gestures) depend on an interaction framework that does not currently exist in Kramer. Building this framework is a separate effort, likely post-Phase 3.

---

## Implementation Strategy

**Phase 1** (LOW-MEDIUM effort, after RDR-009 Phase C):
1. Define property key constants for display properties
2. Wire `visible` through existing LayoutStylesheet
3. Ensure RDR-014 and RDR-015 properties route through LayoutStylesheet

**Phase 2** (HIGH effort, after expression language sub-specification):
1. Complete expression language sub-specification (see requirements above)
2. Implement filter predicate parsing and evaluation
3. Implement formula computation with hierarchical scoping
4. Implement aggregate functions
5. Implement `cell-format` with type coercion
6. Wire into data pipeline in RelationLayout.measure()

**Phase 3** (HIGH effort, separate RDR):
1. Redesign `GraphQlUtil.buildSchema()` to retain graphql-java AST
2. Build bidirectional mapping between SchemaPath and query AST nodes
3. Design GraphQL query modification API in kramer-ql
4. Map stylesheet property changes to GraphQL query AST modifications
5. Implement query re-execution on stylesheet change
6. Handle cursor-based pagination (Relay spec: `first`/`after`) tied to VirtualFlow

---

## Dependencies

- **Hard**: RDR-009 Phase C (LayoutStylesheet interface and SchemaPath) — the foundation
- **Soft**: RDR-014 (hideIfEmpty, sort — these become Phase 1 properties)
- **Soft**: RDR-015 (renderMode — this becomes a Phase 1 property)
- **Soft**: RDR-011 (Layout Protocol — query properties route through same LayoutDecisionTree)

---

## Alternatives Considered

### Alt A: Extend Relation/Primitive Schema Objects Directly

Embed all display and query-semantic properties directly on `Relation` and `Primitive` — no stylesheet indirection. Each schema node would carry its own `visible`, `hide-if-empty`, `render-mode`, `sort-fields`, etc. as first-class fields.

**Pros:**
- No indirection: layout code reads properties directly from the node it is processing.
- Trivial serialization: schema trees are already Jackson-serializable; adding fields costs nothing.
- Type-safe: properties are typed fields, not string-keyed map lookups with defaults.

**Cons:**
- Kills reuse: the same underlying schema cannot be rendered differently for different views or users without cloning the entire tree.
- Couples data shape to presentation: `Relation` and `Primitive` are data-model types; burdening them with display state violates separation of concerns.
- Per-path addressing becomes awkward: to change one field's `render-mode` at runtime, you must locate and mutate the schema node rather than replacing a stylesheet entry.
- Phase 3 cannot work: query reconstruction requires a stable schema tree against which stylesheet diffs can be mapped to query AST nodes. If the schema tree IS the stylesheet, there is no stable referent.

**Rejection reason:** Conflates schema structure with presentation policy. The whole point of `LayoutStylesheet` is to allow the same schema tree to be presented differently without cloning or mutating it. This was the motivation for RDR-009.

---

### Alt B: External JSON/YAML Config File Loaded at Startup

Replace `LayoutStylesheet` with a static JSON or YAML config file that maps SchemaPath-like dotted strings to property bags. File is loaded once at startup; no runtime interface.

**Pros:**
- Familiar: most developers understand "put your config in a file."
- No Java interface to implement: any app can supply a YAML file.
- Human-editable without recompile.

**Cons:**
- Static binding: config is fixed at startup. There is no mechanism to adjust properties at runtime (user interaction, dynamic data) without restarting.
- No type safety: property keys are strings in the file and strings in the reader. Typos fail silently at the default value.
- Breaks Phase 3: query re-execution requires the stylesheet to be a live, mutable object that the interaction layer can write to. A file loaded at startup cannot participate in a reactive update loop.
- Path resolution is undefined: dotted strings in YAML have no inherent connection to `SchemaPath`. A custom parser is required anyway, which is essentially reimplementing `LayoutStylesheet`.
- Forces an external file dependency on every Kramer consumer, even those that have no configuration needs.

**Rejection reason:** Static config files are suitable for initialization but cannot support the reactive manipulation model required by Phase 2 interaction and Phase 3 query rewriting. The `LayoutStylesheet` interface is already a superset of this capability and is already wired.

---

### Alt C: GraphQL Directive-Based Configuration

Embed display and query-semantic properties as GraphQL directives on fields in the query document. Example: `orders @hideIfEmpty @sortBy(fields: "createdAt") { ... }`. `GraphQlUtil.buildSchema()` would parse directives into schema node properties.

**Pros:**
- Co-locates data shape and presentation in one artifact (the query).
- No separate stylesheet file or object to manage.
- Directives survive round-trips through GraphQL tooling.
- Natural fit for Phase 3: the query IS the schema, and directives on fields are already part of the graphql-java AST.

**Cons:**
- Requires Phase 3 to be in place first: `GraphQlUtil.buildSchema()` currently discards all directives (RF-3). Reading directives is impossible until the AST is retained, which is the Phase 3 prerequisite.
- Couples presentation to query text: to change a column's `render-mode` the user must edit the query string, not manipulate a UI control.
- Directive namespace pollution: custom directives require server-side schema declarations in production GraphQL environments; ad-hoc directives may break strict validators.
- Cannot represent properties that have no query analog (e.g., column width, cell format patterns). Directives are a reasonable home for query-semantic properties but an awkward home for pure display properties.
- No current Kramer consumer uses directives. Adopting this as the primary mechanism requires a directive specification before any Phase 1 work can start, inverting the recommended phase order.

**Rejection reason:** Directive-based configuration is a viable long-term complement to `LayoutStylesheet` for query-semantic properties in Phase 3, but it cannot replace the stylesheet for display properties, and it cannot function before Phase 3's AST-retention redesign. Adopting it as the primary approach now would block Phase 1 on Phase 3 prerequisites.

---

## Research Findings

### RF-1: LayoutStylesheet Is Already Generic (Confidence: HIGH)
The `LayoutStylesheet` interface from RDR-009 uses `getDouble/getInt/getString` with arbitrary property keys. Adding query-semantic properties requires zero interface changes — just new property keys and consumers.

### RF-2: Hierarchical Scoping Is Free (Confidence: HIGH)
SIEUFERD's formula scoping (aggregate by position in hierarchy) maps directly to Kramer's schema tree traversal. A SUM formula on a Primitive that is a child of a Relation automatically aggregates within that Relation's data rows — the traversal IS the GROUP BY.

### RF-3: GraphQlUtil Discards Query Structure (Confidence: HIGH)
`GraphQlUtil.buildSchema()` walks graphql-java `Field` nodes and extracts only field names and nesting. Arguments, directives, aliases, pagination parameters, and the original `Document` are all discarded. Phase 3 requires retaining this information, which is a redesign of the `kramer-ql` data flow, not an incremental extension.

### RF-4: 7-Layer Architecture Is Analytical, Not Committed (Confidence: MEDIUM)
The 7-layer architecture identified in earlier synthesis (Data Source -> Query Model -> Schema Tree -> Measure -> Layout Decisions -> Rendering -> Interaction) is a useful analytical framework for understanding where properties fit. It is not a committed architectural design or implementation plan.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Expression language design scope | High | Requires sub-specification before Phase 2 implementation |
| Formula evaluation performance | Medium | Formulas evaluate during measure phase, cached in MeasureResult |
| Query modification correctness (Phase 3) | High | Separate RDR; extensive test coverage; start with simple cases |
| Over-engineering before simpler RDRs deliver value | High | Phase 1 is consolidation only; no new features |
| Client-side filtering requires full data fetch | Medium | For large datasets (>1000 rows), Phase 2 provides display convenience but not query optimization. Phase 3 addresses this by pushing filter/sort to the server |
| Several SIEUFERD properties are backend-dependent | Medium | JOINON, COLUMNDEFINITION, REVERSEREF require specific GraphQL schema support; cannot be implemented purely client-side |

---

## Success Criteria

1. All Phase 1 display properties (`visible`, `render-mode`, `hide-if-empty`, `sort-fields`) configurable per SchemaPath
2. RDR-014 and RDR-015 properties unified under LayoutStylesheet
3. Expression language sub-specification completed (prerequisite for Phase 2)
4. Filter predicates correctly filter data before layout (Phase 2)
5. Formula fields compute correct aggregates with hierarchical scoping (Phase 2)
6. (Phase 3, separate RDR) GraphQlUtil redesigned to retain AST; stylesheet changes trigger query re-execution
7. All existing tests pass at each phase

---

## Finalization Gate

### 1. Has the problem been validated with real user scenarios?

Partially. The core thesis — that Kramer needs per-path display properties addressable at runtime — is validated by the concrete requirements in RDR-014 (hide-if-empty, sort-fields) and RDR-015 (render-mode). Both features are stalled because there is no agreed property delivery mechanism; routing them through `LayoutStylesheet` is the unblocking step. However, Phase 2 (client-side data manipulation) and Phase 3 (query rewriting) have not been validated against real user workflows. The interaction model described in this RDR is speculative — no user has operated a prototype. Phase 1 is justified by existing blocked work; Phases 2 and 3 should be gated on user validation before investment.

### 2. Is the solution the simplest that could work?

For Phase 1, yes. The `LayoutStylesheet` interface is already fully wired (`getBoolean` has been confirmed as present post-Wave2 research). Adding property key constants and routing RDR-014/015 properties through the existing interface is minimal work. The alternatives (Alt A: schema object extension, Alt B: static config file, Alt C: GraphQL directives) all require more structural change for Phase 1 scope.

For Phase 2, not yet — the expression language sub-specification has not been written. Without it, any Phase 2 implementation risks over- or under-engineering the evaluator. The simplest Phase 2 path cannot be confirmed until the expression grammar is constrained.

For Phase 3, no: Phase 3 is architecturally complex by necessity. The simplest design has not been determined; that determination requires the separate `kramer-ql` redesign RDR called for in this document.

### 3. Are all assumptions verified or explicitly acknowledged?

The following assumptions have been verified by post-Wave2 research:

- `LayoutStylesheet` interface is fully wired and `getBoolean` exists — **verified**.
- `GraphQlUtil.buildSchema()` discards the graphql-java AST (Phase 3 blocker) — **verified** (RF-3).

The following assumptions remain unverified and are explicitly acknowledged:

- `hide-if-empty` and `sort-fields` are still on the `Relation` object rather than routed through `LayoutStylesheet`. Phase 1 assumes these can be migrated without breaking existing consumers. Migration path not validated.
- The `visible` property does not yet exist anywhere in the codebase. Phase 1 assumes it can be added as a pure stylesheet property with no schema-object changes. This is likely true but unconfirmed against the layout pipeline.
- The layout decision cache does not incorporate stylesheet version. Any Phase 1 property that influences cached layout decisions (e.g., `visible`) will silently return stale results until the cache invalidation strategy is extended to key on stylesheet identity. This is an unresolved correctness risk for Phase 1.
- Phase 2 expression language is entirely unspecified. All Phase 2 design is provisional.
- Phase 3 assumes graphql-java's AST is retainable and that AST nodes can be mapped bidirectionally to `SchemaPath` values. This has not been prototyped.

### 4. What is the rollback strategy if this fails?

**Phase 1 rollback**: Property constants and `LayoutStylesheet` routing are additive. If Phase 1 properties cause regressions, removing them is a one-PR revert. RDR-014 and RDR-015 implementations can revert to direct Relation/Primitive fields temporarily. No data migration required.

**Phase 2 rollback**: Client-side filter/formula/aggregate are purely additive to the data pipeline. If the expression evaluator is defective, the pipeline step can be disabled (default: no expression registered). Data is unaffected; only the computed display columns disappear.

**Phase 3 rollback**: Phase 3 is a redesign of `GraphQlUtil`, not an extension. It would live in a separate module/branch. A failed Phase 3 does not affect Phase 1 or Phase 2. The `kramer-ql` module can remain at its current design indefinitely while Phases 1 and 2 deliver value in display and client-side manipulation.

**Worst-case**: The `LayoutStylesheet` property addressing model proves insufficient (e.g., path resolution semantics are wrong). Fallback is Alt A (schema object extension), which was rejected for architectural reasons but is always mechanically available as a last resort.

### 5. Are there cross-cutting concerns with other RDRs?

Yes, several:

- **RDR-009 (LayoutStylesheet)**: Phase 1 directly depends on RDR-009 Phase C being complete. The `getBoolean` method confirmed present by Wave2 research satisfies this. However, the layout decision cache (introduced by RDR-009 or a related optimization) does not incorporate stylesheet version. If cache invalidation is not extended before Phase 1 `visible` property is wired, stylesheet changes will not reliably trigger re-layout.
- **RDR-014 (hideIfEmpty, sort)**: `hide-if-empty` and `sort-fields` are currently on the `Relation` object. Phase 1 consolidates them under `LayoutStylesheet`. This migration must be coordinated with RDR-014 implementation; whichever lands first sets the precedent and may require a follow-up migration.
- **RDR-015 (renderMode)**: Same concern as RDR-014. `render-mode` migration to `LayoutStylesheet` must be coordinated with RDR-015 implementation.
- **RDR-011 (Layout Protocol Extraction)**: If RDR-011 extracts the layout decision tree into a protocol, Phase 1 property routing must be consistent with whatever interface RDR-011 defines. Risk of interface incompatibility if the two RDRs land in sequence without coordination.
- **RDR-016 (Layout Stability / Incremental Update)**: If the layout stability work introduces or modifies a decision cache, the stylesheet-version invalidation concern becomes acute. Phase 1 `visible` property correctness depends on cache invalidation keying on stylesheet identity.
- **RDR-019 (Tufte Sparkline Rendering)**: `sparkline` render-mode is explicitly deferred to RDR-019. When RDR-019 is accepted, its render-mode value must be added to the `LayoutPropertyKeys` constants registry defined in Phase 1. No conflict, but requires coordination at the time of RDR-019 implementation.

---

## Recommendation

Phase 1 is a consolidation of RDR-014 and RDR-015 under LayoutStylesheet — it is infrastructure work that should follow their implementation. Phase 2 (data manipulation) is the transformative feature that turns Kramer into a data exploration tool, but it requires an expression language sub-specification first. Phase 3 (query integration) requires a separate RDR for `kramer-ql` architectural redesign to retain the graphql-java AST. Each phase is independently valuable and should proceed on its own timeline. The full SIEUFERD closed-loop model depends on all three phases plus a yet-to-be-designed interaction framework.
