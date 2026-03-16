# RDR-020: GraphQL AST Retention in kramer-ql

## Metadata
- **Type**: Architecture
- **Status**: proposed
- **Priority**: P3
- **Created**: 2026-03-16
- **Related**: RDR-018 (Phase 3 prerequisite), RDR-009 (LayoutStylesheet/SchemaPath)

---

## Problem Statement

`GraphQlUtil.buildSchema()` in the `kramer-ql` module parses GraphQL query strings via
`graphql.parser.Parser.parse()` but discards the resulting `Document` AST immediately after
walking field names. Only field names and nesting structure are extracted into the
`Relation`/`Primitive` schema tree. The parsed `Document`, along with every `Field` node's
arguments, directives, alias, and pagination state, is lost.

This discard prevents:

- **Directive-based stylesheet hints**: `@hide`, `@render(mode: bar)`, etc. cannot be read
  from the AST if the AST does not survive past `buildSchema()`.
- **Argument preservation for query reconstruction**: Field arguments such as
  `orders(status: "active", first: 20)` are silently dropped. Re-executing a modified query
  (Phase 3 of RDR-018) is impossible without them.
- **Alias awareness for display names**: `Field.getAlias()` is never accessed, so aliased
  fields (`displayName: name`) cannot use the alias as the display label.
- **`FragmentSpread` support**: Both `buildSchema(Field, Set)` and
  `buildSchema(Relation, InlineFragment, Set)` throw `UnsupportedOperationException` when a
  `FragmentSpread` is encountered. Resolution of named fragments requires the full `Document`.
- **Cursor-based pagination (Relay spec)**: Pagination arguments (`first`, `after`,
  `last`, `before`) are field arguments in the AST. Without retaining the AST, the engine
  cannot advance a cursor or reconstruct a page-2 query.

RDR-018 Phase 3 explicitly calls for a separate RDR to cover this redesign. This is that RDR.

---

## Current State

### `buildSchema` overload inventory

`GraphQlUtil` exposes 8 public `buildSchema` overloads:

| Signature | Parses query string? | Discards Document? |
|-----------|---------------------|--------------------|
| `buildSchema(Field)` | No (receives Field directly) | N/A — Document already absent |
| `buildSchema(Field, Set<String>)` | No | N/A |
| `buildSchema(Relation, InlineFragment)` | No | N/A |
| `buildSchema(Relation, InlineFragment, Set<String>)` | No | N/A |
| `buildSchema(String)` | Yes | Yes — `Parser.parse(query)` result used inline, not stored |
| `buildSchema(String, Set<String>)` | Yes | Yes |
| `buildSchema(String, String)` | Yes | Yes |
| `buildSchema(String, String, Set<String>)` | Yes | Yes |

All four string-accepting overloads call `Parser.parse(query)` and immediately chain
`.getDefinitions()` without retaining the `Document` reference. There is no field, record,
or return value that carries the Document out of these methods.

### What is discarded at each call site

At `buildSchema(String, Set<String>)` (lines 140–177):

```
Parser.parse(query)           // Document created here
  .getDefinitions()           // Document reference dropped here
  .stream()
  ...
```

For every `Field` node visited by `buildSchema(Field, Set<String>)` (lines 90–110):

- `field.getArguments()` — never accessed
- `field.getDirectives()` — never accessed
- `field.getAlias()` — never accessed; `field.getName()` used for both schema name and display
- `field.getSelectionSet()` — accessed only to distinguish leaf vs. composite; content walked
  recursively but not stored

For `FragmentSpread` selections (line 106, line 131):

```java
} else if (selection instanceof FragmentSpread) {
    throw new UnsupportedOperationException(
        "Named fragment spreads are not supported; use inline fragments");
}
```

Named fragments require the `Document`'s fragment definitions map to resolve. Without the
`Document`, resolution is impossible and the exception is the only option.

### `evaluate()` is unaffected

`evaluate(WebTarget, QueryRequest)` and `evaluate(WebTarget, String)` send raw query strings
over HTTP. They do not interact with the parsed AST. These methods require no changes as part
of this RDR, though query reconstruction (Phase C) will affect how the query string is
assembled before passing to `evaluate`.

---

## Proposed Design

### Core principle

Retain the graphql-java `Document` alongside the `Relation` tree, and build a bidirectional
mapping between `SchemaPath` values (RDR-009) and graphql-java `Field` AST nodes. This
mapping is the foundation that unlocks directive extraction, alias display names, query
reconstruction, and `FragmentSpread` resolution.

### New type: `SchemaContext`

Introduce `SchemaContext` as a value object that packages the `Relation` schema tree with
its associated query metadata:

```java
public record SchemaContext(
    Relation schema,
    Document document,
    Map<SchemaPath, Field> fieldIndex,
    Map<SchemaPath, String> aliasIndex
) {
    /** Convenience: look up the Field AST node for a given schema path. */
    public Optional<Field> fieldAt(SchemaPath path) {
        return Optional.ofNullable(fieldIndex.get(path));
    }

    /** Returns the alias for a path, or the field name if no alias was present. */
    public String displayName(SchemaPath path) {
        return aliasIndex.getOrDefault(path, path.leaf());
    }
}
```

`SchemaContext` is immutable. It is produced by a new `buildContext()` family of methods
alongside the existing `buildSchema()` methods. The existing `buildSchema()` overloads are
preserved for backward compatibility.

### `buildContext()` API

```java
// Primary entry point — parses query string, returns full context
public static SchemaContext buildContext(String query);
public static SchemaContext buildContext(String query, Set<String> excludedFields);
public static SchemaContext buildContext(String query, String source);
public static SchemaContext buildContext(String query, String source, Set<String> excludedFields);
```

Internally, `buildContext()` calls `Parser.parse(query)`, retains the `Document`, builds the
`Relation` tree as before, and concurrently populates `fieldIndex` and `aliasIndex` via the
same recursive walk.

### `SchemaPath` ↔ `Field` index construction

During the recursive `buildSchema` walk, each visited `Field` is recorded in the index under
the `SchemaPath` constructed by the traversal. The traversal already carries enough
information (parent path + current field name) to build the path incrementally:

```
buildContext(document) {
    fields = top-level Fields from document (OperationDefinition selections)
    if fields.size == 1:
        root = buildRelation(fields[0], SchemaPath.root(), fieldIndex)
        // root is the child Relation directly
    else if operationName != null:
        root = new Relation(operationName)
        for each top-level Field f:
            key = f.getAlias() != null ? f.getAlias() : f.getName()
            path = new SchemaPath(key)
            fieldIndex.put(path, f)
            // f.getName() tracked for schema type resolution if needed
            root.addChild(buildRelation(f, path, fieldIndex))
    else:
        root = new QueryRoot()
        for each top-level Field f:
            key = f.getAlias() != null ? f.getAlias() : f.getName()
            path = new SchemaPath(key)
            fieldIndex.put(path, f)
            root.addChild(buildRelation(f, path, fieldIndex))
}

buildRelation(f, parentPath, fieldIndex) {
    key = f.getAlias() != null ? f.getAlias() : f.getName()
    path = parentPath.child(key)
    // NOTE: key (alias or name) is the data extraction key; f.getName() is the schema type name
    node = new Relation(key)   // or Primitive(key) if leaf
    fieldIndex.put(path, f)
    for each child Field c in f.getSelectionSet():
        node.addChild(buildRelation(c, path, fieldIndex))
    return node
    // fieldIndex path alignment: path mirrors the schema tree traversal exactly
}
```

Alias handling: the `Relation`/`Primitive` node is created with
`f.getAlias() != null ? f.getAlias() : f.getName()` as the data extraction key — this is the
key used to look up values in the JSON response (the server returns data keyed by alias when
an alias is present). `f.getName()` is stored separately in `aliasIndex` (or a `typeNameIndex`)
for schema type tracking when needed. This is the inverse of the naive approach: the alias is
the correct data key; the field name tracks the schema type. `aliasIndex` records the alias
for use by display/rendering layers as well.

### Phase structure

Phase A and Phase B are prerequisites for Phase C and Phase D2. Phase C cannot proceed
without Phase B's directive schema. Phase D1 (defect fix) can proceed in parallel with
Phase C immediately after Phase A. Phase D2 (new feature) requires Phase C.

#### Phase A: Retain Document, build SchemaPath ↔ Field index

Deliverable: `SchemaContext` record, `buildContext()` overloads, index construction, tests.

Scope:
- `SchemaContext` record with `document`, `fieldIndex`, `aliasIndex`
- `buildContext()` methods (string-accepting; Field-accepting overloads are internal)
- Recursive index construction integrated into existing walk
- Alias display name resolution via `aliasIndex`
- Unit tests: round-trip that every `SchemaPath` in a built schema has a corresponding `Field`
  entry; alias round-trip; argument preservation (not used yet, but verify non-null)

Phase A does NOT change `buildSchema()`. Existing callers are unaffected.

#### Phase B: Directive extraction

Deliverable: `DirectiveReader` utility; wiring of `@hide` and `@render` into `LayoutStylesheet`.

Scope:
- Define the directive vocabulary: `@hide` (boolean, maps to `visible=false`),
  `@render(mode: String)` (maps to `render-mode` property in RDR-015),
  `@hideIfEmpty` (maps to `hide-if-empty` in RDR-014)
- `DirectiveReader` reads `Field.getDirectives()` from the `fieldIndex` for a given
  `SchemaPath` and produces per-path stylesheet overrides
- Integration: when a `SchemaContext` is present, a directive-aware `LayoutStylesheet`
  wrapper applies directive-derived overrides before falling through to the base stylesheet.
  `DirectiveAwareStylesheet` lives in **kramer-ql** (not kramer): it imports both
  `LayoutStylesheet` (from kramer) and `SchemaContext` (from kramer-ql). Placing it in kramer
  would create a forward dependency from the core module into the GraphQL integration module,
  which is prohibited by the module dependency direction.
- The directive namespace (`@hide`, `@render`, `@hideIfEmpty`) must be documented as
  client-side only; servers that validate directive declarations will reject these unless the
  schema declares them. Queries intended for servers with strict directive validation must
  strip client directives before submission.

Phase B depends on Phase A (requires `fieldIndex`) and on RDR-018 Phase 1 (requires
`LayoutPropertyKeys` constants to be defined).

#### Phase C: Query reconstruction

Deliverable: `QueryBuilder` that reconstructs a `Document` (and serializable query string)
from a modified `SchemaContext` — specifically, from field visibility changes applied via
`LayoutStylesheet`.

Scope:
- `QueryBuilder.reconstruct(SchemaContext, LayoutStylesheet)` — walks the schema tree,
  skips fields where `visible=false`, preserves field arguments and non-client directives,
  serializes to a query string using graphql-java's `AstPrinter`
- Re-execution: reconstructed query string passed to existing `evaluate()` — no change to
  the HTTP layer
- Cursor-based pagination: `first`/`after` arguments on connection fields (Relay spec) are
  preserved during reconstruction and can be updated via `SchemaContext` mutation before
  reconstruction. This is the mechanism for page-advance: update the cursor argument on the
  relevant field, reconstruct, re-execute.
- Arguments that are NOT pagination-related must be preserved verbatim from the original
  `Field` node in `fieldIndex`

Phase C depends on Phase B (directive vocabulary must be stable before reconstruction logic
is built) and on RDR-018 Phase 2 (the `visible` property must be wired before reconstruction
has meaningful work to do).

#### Phase D1: FragmentSpread resolution (defect fix)

Deliverable: `FragmentSpread` resolution; named fragment spreads no longer throw.

Scope:
- Replace the two `UnsupportedOperationException` throw sites with resolution against
  `Document.getDefinitionsOfType(FragmentDefinition.class)`
- `FragmentDefinition` fields are inlined into the parent `Relation` during schema building;
  the `fieldIndex` maps the inlined fields under their resolved paths

Phase D1 depends on Phase A (requires `Document` for fragment resolution). It can proceed
in parallel with Phase C. This is a defect fix — `FragmentSpread` throws unconditionally
today (RF-3); fixing it is not gated on any new feature.

#### Phase D2: Relay cursor pagination (new feature)

Deliverable: Cursor management for Relay-style connections.

Scope:
- Relay connection pattern recognition: detect `edges { node { ... } }` + `pageInfo` shape
  and provide a cursor-advance API (`SchemaContext.withCursor(SchemaPath, String cursor)`)
  that returns a new `SchemaContext` with the updated `after` argument

Phase D2 depends on Phase A and Phase C (cursor advance requires query reconstruction to
produce the next-page query string). Phase D2 is a new feature and should not block delivery
of Phase D1.

Phase D1 can proceed immediately after Phase A. Phase D2 requires Phase C.

---

## Alternatives Considered

### Alt A: Embed directives as JSON annotations in a separate config file

Directive intent (hide field, change render mode) could be expressed in a separate JSON or
YAML config file keyed by schema path strings, loaded alongside the query. The query string
itself would remain unmodified.

**Rejected**: Splits query semantics across two files with no enforced synchronization. If
the query adds or renames a field, the config file silently applies to a path that no longer
exists. Directives embedded in the query string are structurally coupled to the field they
annotate. The separate config approach also does not address `FragmentSpread` resolution or
argument preservation — those require the AST regardless. This alternative solves only the
directive problem, and solves it less reliably.

### Alt B: Re-parse the query string every time directives are needed

Instead of retaining the `Document`, re-parse the original query string on demand when a
caller needs directive data. `GraphQlUtil` would store the query string, not the `Document`.

**Rejected**: `Parser.parse()` is not free — it tokenizes and constructs a full AST. In a
layout system that re-layouts on every resize event, re-parsing per-resize would be
measurable overhead. The `Document` is approximately 1–2 KB of heap per typical Kramer query
(schema trees are 5–20 nodes per RDR-009). Retaining it is negligible. The re-parse approach
also produces a different object identity each time, making `fieldIndex` map lookups
unreliable unless the map is rebuilt on each re-parse, which defeats the purpose.

### Alt C: Use a custom query language instead of GraphQL

Replace the GraphQL query input to `buildSchema` with a Kramer-specific query DSL that
natively carries the information currently lost (display hints, arguments, aliases). The
graphql-java parser would be used only for server communication, not for schema building.

**Rejected**: Breaks the graphql-java ecosystem entirely. The `explorer` and `toy-app`
modules accept user-entered GraphQL strings from real GraphQL endpoints. A custom DSL cannot
be typed by users familiar with GraphQL. The directive-on-field syntax that GraphQL already
provides (`field @hint`) is the natural extension point — there is no need to invent a DSL
when GraphQL's extension mechanism exists and graphql-java's AST already models it.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| graphql-java `Document` memory overhead | Low | Schema trees are 5–20 nodes. A `Document` for a typical Kramer query is ~1–2 KB. The overhead is negligible at the application scale of `explorer`/`toy-app`. Monitor if queries grow significantly larger. |
| Client directives rejected by strict GraphQL servers | Medium | Phase B must strip client-only directives (`@hide`, `@render`, `@hideIfEmpty`) from the query string before submission to `evaluate()`. `QueryBuilder` (Phase C) handles this as part of reconstruction. Until Phase C exists, Phase B callers must manually strip directives or use a pre-stripping `evaluate()` wrapper. |
| Query reconstruction correctness | High | AST round-trips through `AstPrinter` must preserve all server-visible semantics: field arguments, non-client directives, aliases, fragments. Test coverage must include round-trip equality for the full query, not just the schema tree. |
| `SchemaPath` uniqueness for `fieldIndex` keys | Medium | RDR-009 RF-3 confirmed that field names are not unique within schema trees (e.g., `name` appears at 7+ levels). `SchemaPath` uses the full path from root, which IS unique. The `fieldIndex` must use full-path keys, not leaf names. This is already the `SchemaPath` design. |
| `FragmentSpread` inlining changes schema tree shape | Medium | Named fragments can be used at multiple sites. Inlining a fragment at two different paths creates two schema subtrees with identical field names but different `SchemaPath` keys. This is correct behavior but must be verified with test cases. |
| Phase C depends on RDR-018 Phase 2 being stable | Medium | If the `visible` property wiring changes between Phase B and Phase C, `QueryBuilder.reconstruct()` must be updated. Coordination point: freeze the `visible` property key before starting Phase C. |

---

## Implementation Plan

### Phase A: Retain Document, build SchemaPath ↔ Field index
- Define `SchemaContext` record in `kramer-ql`
- Refactor the recursive walk inside `buildSchema(String, Set<String>)` into an
  index-populating variant; expose as `buildContext(String, Set<String>)`
- Schema node creation: use `f.getAlias() ?? f.getName()` as the data extraction key
  (the key used to look up values in the JSON response). Store `f.getName()` separately
  for schema type tracking. `aliasIndex` records the resolved display key per path.
- Tests: verify `fieldIndex` covers all paths in the built `Relation` tree; verify
  `displayName()` returns alias where set; verify `Field.getArguments()` is non-null and
  accessible via `fieldIndex`
- Backward compatibility: all existing `buildSchema()` callers work unchanged

### Phase B: Directive extraction
- Define directive vocabulary constants (annotation names and argument keys)
- Implement `DirectiveReader.overridesFor(SchemaContext, SchemaPath)` returning
  `Map<String, Object>` of property key → value
- Implement `DirectiveAwareStylesheet` wrapping a base `LayoutStylesheet`, consulting
  `DirectiveReader` for per-path overrides (client directives take lowest precedence —
  explicit stylesheet overrides win)
- Tests: query with `@hide` on a field produces `visible=false` for that path; query with
  `@render(mode: "bar")` produces `render-mode=bar`; non-client directives are ignored by
  `DirectiveReader`
- Integration test: `buildContext()` + `DirectiveAwareStylesheet` + `AutoLayout` renders
  correctly with directive-driven visibility

### Phase C: Query reconstruction
- Implement `QueryBuilder.reconstruct(SchemaContext, LayoutStylesheet)` using graphql-java
  `AstPrinter` for serialization
- Strip client-only directive names before printing (configurable set of directive names to
  strip)
- Cursor-advance API: `SchemaContext.withCursor(SchemaPath connectionPath, String cursor)`
  returns a new `SchemaContext` with the `after` argument updated on the relevant `Field`
- Tests: round-trip equality (parse → reconstruct → compare); hidden field omission;
  argument preservation; cursor update produces correct `after` value in reconstructed query
- Integration test: reconstruct + `evaluate()` returns data consistent with the modified query

### Phase D1: FragmentSpread resolution (defect fix)
- Remove both `throw new UnsupportedOperationException(...)` sites
- Implement `resolveFragment(FragmentSpread, Document)` that looks up
  `Document.getDefinitionsOfType(FragmentDefinition.class)` and inlines selections
- `fieldIndex` receives inlined fields under their resolved `SchemaPath`
- Tests: fragment spread round-trip; multi-site fragment spread (same fragment at two paths)

### Phase D2: Relay cursor pagination (new feature)
- Relay connection detection: `RelayConnectionDetector.isConnection(Relation)` heuristic
  (presence of `edges` child with `node` grandchild + `pageInfo` sibling)
- `SchemaContext.withCursor(SchemaPath connectionPath, String cursor)` returns a new
  `SchemaContext` with the `after` argument updated on the relevant `Field`
- Tests: Relay connection shape detection; `withCursor()` on a connection path produces
  correct `after` value in reconstructed query (via Phase C)

---

## Research Findings

### RF-1: AST discard is confirmed (Confidence: HIGH)

All four string-accepting `buildSchema` overloads call `Parser.parse(query)` and immediately
chain `.getDefinitions()` without storing the `Document` reference. This is directly visible
in `GraphQlUtil.java` lines 144, 185. There is no `Document` field on `GraphQlUtil`,
`QueryRoot`, or any `Relation` subclass.

### RF-2: Field.getArguments(), getDirectives(), getAlias() are never accessed (Confidence: HIGH)

The recursive walk in `buildSchema(Field, Set<String>)` (lines 90–110) accesses only
`field.getName()` and `field.getSelectionSet()`. The three other relevant accessors
(`getArguments()`, `getDirectives()`, `getAlias()`) are not called anywhere in
`GraphQlUtil.java`.

### RF-3: FragmentSpread throws unconditionally (Confidence: HIGH)

Lines 106 and 131 both throw `UnsupportedOperationException` with the message
"Named fragment spreads are not supported; use inline fragments." Resolution requires the
`Document` (to look up `FragmentDefinition` by name). Phase A's Document retention is the
prerequisite.

### RF-4: evaluate() is independent of parsed AST (Confidence: HIGH)

`evaluate(WebTarget, QueryRequest)` (lines 206–221) and `evaluate(WebTarget, String)`
(lines 223–234) operate only on the `QueryRequest.query` string and HTTP responses. They have
no coupling to the AST. Phase C's query reconstruction feeds its output string into the
existing `evaluate()` — no changes to the HTTP layer are required.

### RF-5: Reference from RDR-018 research (Confidence: HIGH)

RDR-018 research finding RF-3 (confirmed HIGH confidence): "GraphQlUtil.buildSchema() walks
graphql-java Field nodes and extracts only field names and nesting. Arguments, directives,
aliases, pagination parameters, and the original Document are all discarded. Phase 3 requires
retaining this information, which is a redesign of the kramer-ql data flow."

018-research-6 (referenced in RDR-018 Phase 3): "GraphQlUtil still discards AST. Phase 3
prerequisite unchanged."

---

## Finalization Gate

### 1. Has the problem been validated with real user scenarios?

The discard is confirmed by direct code inspection (RF-1, RF-2, RF-3). The consequences
(inability to use directives, reconstruct queries, or resolve named fragments) are structural
deficiencies, not speculative. The `UnsupportedOperationException` for `FragmentSpread` is a
present-day failure mode that users of named fragments will encounter immediately.

The directive-based stylesheet workflow and query reconstruction scenarios have not been
validated against real user workflows — they are design-phase projections. Phase A
(Document retention) and Phase D (FragmentSpread) address confirmed deficiencies; Phases B
and C address speculative but well-grounded future capabilities.

### 2. Is the solution the simplest that could work?

For Phase A, yes. `SchemaContext` is a thin record wrapping existing types. `buildContext()`
is a minimal extension of `buildSchema()`. The index construction is a single-pass addition
to the existing recursive walk.

For Phase B, approximately. `DirectiveReader` is a simple map from directive name to property
key. The alternative (configuring directive behavior externally) is more complex than reading
the AST that is already retained by Phase A.

For Phase C, the simplest approach is graphql-java's own `AstPrinter` for serialization —
no custom printer. The `reconstruct()` method copies the AST, applies visibility-driven
pruning, and prints. This is minimal.

For Phase D, yes. Fragment inlining is the standard resolution strategy. The Relay pagination
cursor API is a narrowly scoped addition to `SchemaContext`.

### 3. Are all assumptions verified or explicitly acknowledged?

Verified:
- `Document` is not retained anywhere in `kramer-ql` — confirmed (RF-1)
- `getArguments()`, `getDirectives()`, `getAlias()` are not accessed — confirmed (RF-2)
- `FragmentSpread` throws unconditionally — confirmed (RF-3)
- `evaluate()` does not use the AST — confirmed (RF-4)

Acknowledged as unverified:
- graphql-java's `AstPrinter` round-trips all argument and directive syntax without loss.
  This is expected from the library but has not been tested against Kramer's specific query
  shapes. Phase C tests must include full round-trip cases.
- Client directive names do not conflict with server-side directives in any GraphQL endpoint
  used by `explorer` or `toy-app`. If a target server declares `@hide` with a different
  semantics, stripping behavior could produce unexpected results. The strip-list must be
  configurable.
- graphql-java `Document` heap size for realistic Kramer queries is negligible (estimated
  1–2 KB). This estimate is based on typical schema tree sizes but has not been profiled.

### 4. What is the rollback strategy if this fails?

Phase A rollback: `buildContext()` is additive. If it causes issues, revert to
`buildSchema()` everywhere. No behavior change to existing code.

Phase B rollback: `DirectiveAwareStylesheet` is a wrapper. Remove the wrapper and pass the
base stylesheet directly. Directive behavior disappears; everything else works.

Phase C rollback: `QueryBuilder` is a standalone utility with no required callers until
wired into the interaction layer. If query reconstruction proves unreliable, stop calling it.
`evaluate()` continues to accept raw query strings.

Phase D1 rollback: Restoring the `UnsupportedOperationException` is a one-line revert. Users
who relied on the exceptions as a guard are unaffected.

Phase D2 rollback: `withCursor()` is additive to `SchemaContext`. Remove the method and
`RelayConnectionDetector`. No behavior change to non-Relay callers.

### 5. Are there cross-cutting concerns with other RDRs?

- **RDR-018 Phase 1**: Phase B of this RDR requires `LayoutPropertyKeys` constants (defined
  in RDR-018 Phase 1). Phase B must not land before RDR-018 Phase 1 is implemented, or the
  directive → property key mapping will use raw strings.
- **RDR-018 Phase 2**: Phase C of this RDR requires the `visible` property to be wired into
  the layout pipeline before `QueryBuilder` has meaningful work to do (hidden fields must
  be excluded from the reconstructed query). Coordinate the `visible` property key before
  starting Phase C.
- **RDR-009 (SchemaPath)**: `fieldIndex` must use full `SchemaPath` keys — confirmed correct
  by RDR-009 RF-3 which established that leaf field names are not unique within schema trees.
- **RDR-016 (Layout Stability / Incremental Update)**: Query reconstruction (Phase C)
  produces a new query string that triggers a full re-fetch and re-measure cycle. This must
  be coordinated with any incremental-update logic from RDR-016 to ensure incremental
  caches are invalidated when the query changes.
- **RDR-014 (hideIfEmpty, sort)** and **RDR-015 (renderMode)**: These properties are the
  target mapping destinations for Phase B directives (`@hideIfEmpty`, `@render`). The
  property key strings must be finalized in RDR-014/015 implementations before Phase B
  maps directives to them.
- **AutoLayout.decisionCache invalidation (Phase B)**: Phase B depends on the
  `AutoLayout.decisionCache` invalidation fix in which `LayoutDecisionKey` includes a
  `stylesheetVersion` field (implemented). When Phase B's `DirectiveAwareStylesheet`
  changes the effective stylesheet for a path, the version change automatically invalidates
  cached layout decisions for that path. No additional cache invalidation logic is required
  in Phase B, provided the `stylesheetVersion` mechanism is in place before Phase B lands.
