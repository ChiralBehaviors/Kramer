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

RDR-032 validated that porting Kramer's autolayout algorithm to TypeScript/React is feasible. The PoC (34 tests, core pipeline + React renderer) proved the architecture works. This RDR covers the full implementation: evolving the PoC into production-quality packages within the existing Kramer monorepo.

---

## Dependencies

- **RDR-032** (Feasibility Spike): CLOSED. PoC validates approach. Findings inform all estimates.
- **RDR-011** (Layout Protocol Extraction): CLOSED. `LayoutView` interface extracted (Kramer-49hy).
- **LayoutView interface**: DONE. All Java renderers accept `LayoutView` instead of `RelationLayout`.

---

## Proposed Solution

### Repository Structure

The TypeScript packages live in the existing Kramer repo alongside the Java modules. pnpm workspaces manage the JS monorepo. Maven `frontend-maven-plugin` runs JS tests as part of the build.

```
Kramer/
  pnpm-workspace.yaml
  packages/
    core/                 @kramer/core
      src/
        schema.ts         SchemaNode, Relation, Primitive, SchemaPath
        measure.ts        MeasurementStrategy protocol + implementations
        layout.ts         Layout decision, constraint solver
        justify.ts        Two-pass width distribution
        compress.ts       ColumnSet packing + DP partitioner
        types.ts          LayoutView, LayoutResult, CompressResult
      test/
      package.json
    measurement/          @kramer/measurement
      src/
        browser.ts        canvas.measureText() / DOM measurement
        approximate.ts    Character-width table for SSR
      package.json
    graphql/              @kramer/graphql
      src/
        util.ts           Query string → schema tree
        expander.ts       AST field addition/removal
        introspector.ts   __schema introspection parsing
        rewriter.ts       Server-side operation push-down
        expr-to-graphql.ts Expression → filter argument translation
      package.json
    react/                @kramer/react
      src/
        AutoLayout.tsx    Top-level component with ResizeObserver
        Table.tsx         Nested table (headers, rows, cells)
        Outline.tsx       Outline (columns, elements, spans)
        ColumnHeader.tsx  Sort indicators, vertical rotation
      package.json
    react-ui/             @kramer/react-ui
      src/
        FieldSelector.tsx TreeView with visibility checkboxes
        FieldInspector.tsx Property editor panel
        ContextMenu.tsx   Right-click sort/filter/aggregate menus
        ExpressionEditor.tsx Inline expression editor
      package.json
  poc/kramer-js/          (retained as reference, eventually removed)
  kramer/                 (existing Java modules)
  kramer-ql/
  explorer/
  toy-app/
```

### Monorepo Tooling

- **pnpm workspaces** for package management and cross-package linking
- **vitest** for testing (already validated in PoC)
- **tsup** or **vite library mode** for building publishable packages
- **TypeScript project references** for incremental compilation
- **Maven frontend-maven-plugin** for CI integration (`mvn test` runs JS tests)

### Contract Tests

Shared JSON fixtures ensure the Java and TypeScript pipelines produce identical layout decisions for the same input:

```
test/fixtures/
  flat-3-primitives.json        Schema + data + expected LayoutResult
  nested-2-level.json           Schema + data + expected mode decisions
  threshold-switching.json      Width → expected mode per relation
```

The Java `AutoLayoutResizeAdaptationTest` and the TS `layout.test.ts` both read these fixtures. A layout decision divergence between Java and TS is a test failure in both.

---

## Implementation Phases

### Phase 1: Core Pipeline (3-4 weeks)

Evolve the PoC `src/core/` into `@kramer/core`:

- Port full `SchemaNode` hierarchy including fold, cardinality, sort fields
- Port `MeasureResult` with all 14 fields (P90, variable-length detection, convergence)
- Port `ExhaustiveConstraintSolver` (currently PoC uses greedy)
- Port `justifyColumn()` with un-rotation logic
- Port `ColumnPartitioner` with both `dpOptimal()` and `greedy()` (PoC has `dpPartition`)
- Port `CompressResult` with column set snapshots
- Define `LayoutView` interface matching the Java version
- Extract `@kramer/measurement` with browser and approximate strategies

**Validation**: Contract test fixtures pass identically in Java and TypeScript.

### Phase 2: React Renderer (4-6 weeks)

Evolve the PoC `src/react/` into `@kramer/react`:

- `<AutoLayout>` with full resize pipeline, convergence short-circuit, decision caching
- `<NestedTable>` with column headers, sort indicators, vertical header rotation
- `<Outline>` with column sets, multicolumn packing, vertical relation labels
- `<NestedRow>` / `<NestedCell>` for table row rendering
- `<ColumnHeader>` with sort click handling
- Virtualization via `@tanstack/virtual` for large datasets
- CSS modules matching the Java `default.css` class structure

**Validation**: StandaloneDemo course catalog data renders identically to Java version.

### Phase 3: GraphQL Integration (2 weeks)

Port `kramer-ql` to `@kramer/graphql`:

- `graphql` npm package for parsing + AST manipulation
- `QueryExpander` — addField/removeField using graphql-js `visit()` + `print()`
- `TypeIntrospector` — parse `__schema` introspection response
- `QueryRewriter` — inject sort/filter arguments
- `ExprToGraphQL` — expression AST → GraphQL filter arguments
- `RelayConnectionDetector` — cursor pagination detection

**Validation**: Round-trip tests (modify → serialize → parse → compare) pass.

### Phase 4: Interaction UI (2-3 weeks)

Port interaction layer to `@kramer/react-ui`:

- `LayoutQueryState` — per-field overrides (sort, filter, formula, aggregate, visibility)
- `InteractionHandler` — dispatches `LayoutInteraction` events, undo/redo
- `<FieldSelector>` — TreeView with visibility checkboxes, state badges
- `<FieldInspector>` — property editor panel
- `<ContextMenu>` — right-click menus via `@radix-ui/react-context-menu`
- `<ExpressionEditor>` — inline expression editor
- Keyboard shortcuts (Cmd+Z undo, Cmd+Shift+F field selector, etc.)

**Validation**: Full interactive demo matching Java StandaloneDemo functionality.

### Phase 5: Expression Language (1 week)

Port `kramer/expression` to `@kramer/core`:

- `ExpressionParser` → `Expr` AST
- `ExpressionEvaluator` with LRU cache
- Field references, arithmetic, comparison, aggregates

**Validation**: Expression evaluation tests pass with same inputs/outputs as Java.

---

## Scope Exclusions

- **Flowless VirtualFlow port**: Use `@tanstack/virtual` instead of porting the 2,502 LOC custom VirtualFlow. If nested virtualization patterns don't map cleanly, build a thin adapter.
- **Crosstab rendering**: Defer to post-MVP. Table + outline cover the primary use cases.
- **JavaFX CSS port**: Rewrite styles in standard CSS/CSS modules. No JavaFX syntax translation.
- **Java codebase changes**: Beyond maintaining `LayoutView` interface parity, no Java modifications.
- **Server-side rendering**: `@kramer/core` runs in Node, but SSR rendering is deferred.

---

## Size Estimate (from RDR-032, revised)

| Package | TS LOC (est) | Effort | Phase |
|---------|-------------|--------|-------|
| `@kramer/core` | ~3,700 | 3-4 weeks | 1 |
| `@kramer/measurement` | ~300 | included in Phase 1 | 1 |
| `@kramer/react` | ~4,000 | 4-6 weeks | 2 |
| `@kramer/graphql` | ~1,500 | 2 weeks | 3 |
| `@kramer/react-ui` | ~800 | 2-3 weeks | 4 |
| Expression language (in core) | ~400 | 1 week | 5 |
| **Total** | **~10,700** | **~13-17 weeks** | |

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Nested virtualization (VirtualFlow-per-column-set) | High | @tanstack/virtual for simple cases; thin custom adapter if needed |
| Contract test drift between Java and TS | High | Shared JSON fixtures, CI runs both |
| Font metric differences across browsers | Medium | Algorithm adapts by design; visual parity not required |
| Bundle size growth across 5 packages | Medium | Tree-shakeable exports; core is pure computation |
| pnpm + Maven integration complexity | Low | frontend-maven-plugin proven in PoC |

---

## Success Criteria

- [ ] `@kramer/core` passes contract test fixtures identically to Java
- [ ] `@kramer/react` renders StandaloneDemo course catalog data with table↔outline switching
- [ ] `@kramer/graphql` round-trip tests pass (modify → serialize → parse → compare)
- [ ] `@kramer/react-ui` provides interactive sort/filter/visibility with undo/redo
- [ ] `mvn test` from repo root runs both Java (1034) and TypeScript tests
- [ ] `pnpm test` from repo root runs all TS package tests
- [ ] Demo app deployable as static site (Vite build)
