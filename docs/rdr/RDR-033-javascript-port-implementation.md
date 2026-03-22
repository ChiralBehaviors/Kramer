---
title: "JavaScript Port Implementation"
id: RDR-033
type: Feature
status: proposed
priority: P1
author: Hal Hildebrand
created: 2026-03-22
reviewed-by: pending
related: RDR-032, RDR-011
---

# RDR-033: JavaScript Port Implementation

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P1
- **Created**: 2026-03-22
- **Related**: RDR-032 (Feasibility Spike — closed), RDR-011 (Layout Protocol Extraction — closed)

---

## Problem Statement

RDR-032 validated that porting Kramer's autolayout algorithm to TypeScript/React is feasible. The PoC (34 tests) validated the basic pipeline shape: schema types, DP partitioner, greedy mode decision, and React DOM rendering of table/outline. It did NOT validate the exhaustive constraint solver, real browser measurement, style inset computation, or nested virtualization — those are Phase 1 and Phase 2 deliverables.

This RDR covers the full implementation: evolving the PoC into production-quality packages within the existing Kramer monorepo.

---

## Dependencies

- **RDR-032** (Feasibility Spike): CLOSED. PoC validates basic pipeline shape. Findings inform estimates.
- **RDR-011** (Layout Protocol Extraction): CLOSED.
- **LayoutView interface** (Kramer-49hy): DONE. Java-internal interface — all Java renderers accept `LayoutView` instead of `RelationLayout`. Note: `LayoutView` is JavaFX-coupled (returns `Label`, `TableHeader`, `LayoutCell<Region>`) and is NOT the portable cross-language protocol. The portable protocol is `LayoutDecisionNode` — a pure-data record tree with no framework types.
- **LayoutDecisionNode**: EXISTS. Pure Java record: `SchemaPath`, `MeasureResult`, `LayoutResult`, `CompressResult`, `HeightResult`, `ColumnSetSnapshot`. `MeasureResult.extractor` has `@JsonIgnore` — Jackson-serializable. This is the cross-language contract.

---

## Proposed Solution

### Portable Protocol: LayoutDecisionNode (not LayoutView)

The cross-language render-context protocol is `LayoutDecisionNode` — an immutable record tree capturing all layout decisions after the compute pipeline runs. It contains:

- `SchemaPath` — addressing key
- `MeasureResult` — 14 fields (label/data/max widths, cardinality, variable-length, convergence stats)
- `LayoutResult` — render mode (TABLE/OUTLINE/CROSSTAB), column widths
- `CompressResult` — justified width, column set snapshots, cell height
- `HeightResult` — resolved height, cardinality, column header height

All constituent types are pure records with no JavaFX imports. `LayoutView` remains as the Java-internal renderer interface; TypeScript renderers consume `LayoutDecisionNode` (or its TS equivalent types).

### Repository Structure

The TypeScript packages live under a self-contained `js/` subtree (RF-1). pnpm workspaces manage the JS monorepo. Maven `frontend-maven-plugin` with corepack runs JS tests.

```
Kramer/
  test/fixtures/                Shared contract test fixtures (JSON)
  js/                           Maven module (packaging: pom), pnpm workspace root
    pnpm-workspace.yaml
    package.json                packageManager: "pnpm@10.x"
    pnpm-lock.yaml
    pom.xml                     frontend-maven-plugin with corepack
    packages/
      core/                     @kramer/core
        src/
          schema.ts             SchemaNode, Relation, Primitive, SchemaPath
          measure.ts            MeasurementStrategy protocol + implementations
          layout.ts             Layout decision, ExhaustiveConstraintSolver
          justify.ts            Two-pass width distribution
          compress.ts           ColumnSet packing + DP partitioner
          expression.ts         ExpressionParser, Expr AST, ExpressionEvaluator
          types.ts              LayoutDecisionNode, LayoutResult, CompressResult
        package.json
      measurement/              @kramer/measurement
        src/
          browser.ts            canvas.measureText() / DOM measurement
          approximate.ts        Character-width table for SSR
        package.json
      graphql/                  @kramer/graphql
        src/
          util.ts               Query string → schema tree
          expander.ts           AST field addition/removal
          introspector.ts       __schema introspection parsing
          rewriter.ts           Server-side operation push-down
          expr-to-graphql.ts    Expression → filter argument translation
        package.json
      react/                    @kramer/react
        src/
          AutoLayout.tsx        Top-level component with ResizeObserver
          Table.tsx             Nested table (headers, rows, cells)
          Outline.tsx           Outline (columns, elements, spans)
          ColumnHeader.tsx      Sort indicators, vertical rotation
        package.json
      react-ui/                 @kramer/react-ui
        src/
          FieldSelector.tsx     TreeView with visibility checkboxes
          FieldInspector.tsx     Property editor panel
          ContextMenu.tsx       Right-click sort/filter/aggregate menus
          ExpressionEditor.tsx  Inline expression editor
        package.json
  poc/kramer-js/                (retained as reference, eventually removed)
  kramer/                       (existing Java modules)
  kramer-ql/
  explorer/
  toy-app/
```

### Monorepo Tooling

- **pnpm workspaces** for package management and cross-package linking
- **vitest** for testing (validated in PoC)
- **tsup** or **vite library mode** for building publishable packages
- **TypeScript project references** for incremental compilation (`composite: true`, `workspace:*` protocol)
- **Maven frontend-maven-plugin** with corepack for CI integration (RF-1)

### Contract Tests

Shared JSON fixtures at `test/fixtures/` (repo root) ensure Java and TypeScript pipelines produce identical layout decisions:

```
test/fixtures/
  flat-3-primitives.json        Schema + data + width + expected LayoutDecisionNode
  nested-2-level.json           Schema + data + expected mode decisions
  threshold-switching.json      Width → expected mode per relation
```

**Fixture generation**: Extend Java `AutoLayoutResizeAdaptationTest` to serialize `LayoutDecisionNode` to JSON via Jackson after each layout pass. `MeasureResult.extractor` is already `@JsonIgnore`.

**Scope qualifier**: Fixtures exclude crosstab-mode nodes (crosstab is deferred to post-MVP). Comparisons skip `extractor` and function-typed fields.

Java `AutoLayoutResizeAdaptationTest` and TS `layout.test.ts` both read these fixtures. Divergence = failure in both.

---

## Implementation Phases

### Phase 1: Core Pipeline + Expression Language (4-5 weeks)

Evolve PoC `src/core/` into `@kramer/core` and `@kramer/measurement`:

- Port full `SchemaNode` hierarchy including fold, cardinality, sort fields
- Port `MeasureResult` with all 14 fields (P90, variable-length, convergence)
- Port `ExhaustiveConstraintSolver` (PoC uses greedy — must port exhaustive enumeration)
- Port `justifyColumn()` with un-rotation logic
- Port `ColumnPartitioner` with `dpOptimal()` and `greedy()`
- Port `CompressResult` with column set snapshots
- **Port expression language** (ExpressionParser → Expr AST → ExpressionEvaluator, ~400 LOC). This is pure computation with no platform dependencies, and Phases 3+4 depend on it (ExprToGraphQL, ExpressionEditor).
- Define TypeScript `LayoutDecisionNode` types matching Java records
- Extract `@kramer/measurement` with browser and approximate strategies
- **Generate contract test fixtures from Java** (extend AutoLayoutResizeAdaptationTest)

**Validation**: Contract test fixtures pass identically in Java and TypeScript. Browser measurement integration validated as a gate for Phase 2.

### Phase 2: React Renderer (4-6 weeks)

**Week 1: Virtualization spike** — validate @tanstack/virtual with a 2-level nested outline (outer column sets, inner table). If pattern maps cleanly, proceed. If not, budget additional 2-3 weeks for custom adapter.

Evolve PoC `src/react/` into `@kramer/react`:

- `<AutoLayout>` with full resize pipeline, convergence short-circuit, decision caching
- `<NestedTable>` with column headers, sort indicators, vertical header rotation
- `<Outline>` with column sets, multicolumn packing, vertical relation labels
- `<NestedRow>` / `<NestedCell>` for table row rendering
- `<ColumnHeader>` with sort click handling
- Virtualization via `@tanstack/virtual` (headless, `measureElement` pattern per RF-2)
  - Inner tables: non-virtualized for <50 rows, fixed-height inner virtualizer otherwise
- CSS modules matching Java `default.css` class structure

**Validation**: StandaloneDemo course catalog data renders with table↔outline switching. Visual parity is not required (font metrics differ); structural parity is (same mode decisions for same widths).

### Phase 3: GraphQL Integration (2 weeks)

Port `kramer-ql` to `@kramer/graphql`:

- `graphql` npm package for parsing + AST manipulation
- `QueryExpander` — addField/removeField using graphql-js `visit()` + `print()`
- `TypeIntrospector` — parse `__schema` introspection response
- `QueryRewriter` — inject sort/filter arguments
- `ExprToGraphQL` — expression AST → GraphQL filter arguments (depends on expression language from Phase 1)
- `RelayConnectionDetector` — cursor pagination detection

**Validation**: Round-trip tests (modify → serialize → parse → compare) pass.

### Phase 4: Interaction UI (2-3 weeks)

Port interaction layer to `@kramer/react-ui`:

- `LayoutQueryState` — per-field overrides (sort, filter, formula, aggregate, visibility)
- `InteractionHandler` — dispatches `LayoutInteraction` events, undo/redo
- `<FieldSelector>` — TreeView with visibility checkboxes, state badges
- `<FieldInspector>` — property editor panel
- `<ContextMenu>` — right-click menus via `@radix-ui/react-context-menu`
- `<ExpressionEditor>` — inline expression editor (depends on expression language from Phase 1)
- Keyboard shortcuts (Cmd+Z undo, Cmd+Shift+F field selector, etc.)

**Validation**: Full interactive demo matching Java StandaloneDemo functionality.

---

## Scope Exclusions

- **Flowless VirtualFlow port**: Use `@tanstack/virtual` instead. If nested patterns don't map, build thin adapter (estimated 2-3 weeks contingency in Phase 2).
- **Crosstab rendering**: Defer to post-MVP. Table + outline cover primary use cases. Contract test fixtures exclude crosstab-mode nodes.
- **JavaFX CSS port**: Rewrite styles in standard CSS/CSS modules.
- **Java codebase changes**: Contract test fixture generation only. No algorithm changes.
- **Server-side rendering**: `@kramer/core` runs in Node, but SSR rendering is deferred.
- **`@kramer/dom` (vanilla renderer)**: Deferred. React renderer validates the protocol; a second renderer target can be added post-MVP to validate generality.

---

## Size Estimate (revised per gate critique)

| Package | TS LOC (est) | Effort | Phase |
|---------|-------------|--------|-------|
| `@kramer/core` (incl. expression) | ~4,100 | 4-5 weeks | 1 |
| `@kramer/measurement` | ~300 | included in Phase 1 | 1 |
| Contract test fixture generation (Java) | ~200 | included in Phase 1 | 1 |
| `@kramer/react` | ~4,000 | 4-6 weeks (+2-3 contingency) | 2 |
| `@kramer/graphql` | ~1,500 | 2 weeks | 3 |
| `@kramer/react-ui` | ~800 | 2-3 weeks | 4 |
| **Total** | **~10,900** | **~12-17 weeks** (+ contingency) | |

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Nested virtualization (VirtualFlow-per-column-set) | **Critical** | Week 1 spike in Phase 2; 2-3 week contingency for custom adapter |
| Contract test drift between Java and TS | High | Shared fixtures at `test/fixtures/`, CI runs both |
| Browser measurement fidelity vs Java | High | Phase 1 validates browser measurement as gate for Phase 2 |
| Font metric differences across browsers | Medium | Algorithm adapts by design; structural parity, not visual parity |
| Bundle size growth across 5 packages | Medium | Tree-shakeable exports; core is pure computation |
| pnpm + Maven integration | Low | Corepack pattern validated; Keycloak reference (RF-1) |

---

## Research Findings

### RF-1: pnpm + Maven Integration
**Status**: verified
**Source**: Research agent, Keycloak project analysis

Use **corepack** (not `install-node-and-pnpm` which is unreliable). Pattern: `install-node-and-corepack` → `corepack enable pnpm` → `corepack pnpm install` → `corepack pnpm run -r test`. Self-contained `js/` subtree recommended — pnpm-workspace.yaml and lockfile isolated from Java modules. Requires `packageManager` field in root package.json. Keycloak is the canonical real-world example.

**Design implication**: `packages/` lives under `js/` Maven module. PoC config migrates to corepack goals.

### RF-2: @tanstack/virtual Nested Virtualization
**Status**: verified
**Source**: Research agent, TanStack Virtual API analysis

Nested virtualization works via two patterns: (1) non-virtualized inner tables for small datasets (<50 rows) with outer `measureElement` auto-remeasuring via ResizeObserver, (2) fixed-height inner virtualizers for large datasets. Headless API gives full DOM/CSS control — best fit for Kramer. `estimateSize` can use Kramer's structural measurements as initial estimates. react-window eliminated (requires pre-computed sizes). react-virtuoso is simpler fallback.

**Design implication**: Phase 2 Week 1 spike validates @tanstack/virtual with nested pattern before committing. Non-virtualized inner tables initially; add inner virtualization only if performance requires it.

### RF-3: Cross-Language Contract Test Fixtures
**Status**: verified
**Source**: Research agent, golden-file testing analysis

JSON fixtures capture: schema + data + width + expected `LayoutDecisionNode` tree. Generate from Java: `LayoutDecisionNode` is a pure record, `MeasureResult.extractor` is `@JsonIgnore` — Jackson-serializable. Fixtures live at `test/fixtures/` (repo root). CI runs both — divergence = failure in both languages.

**Design implication**: Phase 1 starts by generating fixtures from Java test suite. Fixtures exclude crosstab-mode nodes.

### RF-4: LayoutDecisionNode is the Portable Protocol
**Status**: verified
**Source**: Gate critique, codebase inspection

`LayoutDecisionNode` is a pure Java record tree with no JavaFX types. All constituent records (`LayoutResult`, `CompressResult`, `MeasureResult`, `HeightResult`, `ColumnSetSnapshot`, `SchemaPath`) are framework-free. `MeasureResult.extractor` (a `Function<JsonNode, JsonNode>`) is `@JsonIgnore`. This is the cross-language contract — NOT `LayoutView` (which returns JavaFX `Label`, `TableHeader`, `LayoutCell<Region>`).

**Design implication**: TypeScript types mirror `LayoutDecisionNode` records. `LayoutView` stays as Java-internal interface; it is not ported.

---

## Success Criteria

- [ ] `@kramer/core` passes contract test fixtures identically to Java
- [ ] Expression language evaluates same inputs/outputs as Java
- [ ] Phase 2 Week 1 spike validates @tanstack/virtual nested pattern
- [ ] `@kramer/react` renders course catalog data with table↔outline switching
- [ ] `@kramer/graphql` round-trip tests pass (modify → serialize → parse → compare)
- [ ] `@kramer/react-ui` provides interactive sort/filter/visibility with undo/redo
- [ ] `mvn test` from repo root runs both Java (1034+) and TypeScript tests
- [ ] `pnpm test` from `js/` root runs all TS package tests
- [ ] Demo app deployable as static site (Vite build)
