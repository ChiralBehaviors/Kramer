---
title: "JavaScript Port Feasibility Spike"
id: RDR-032
type: Spike
status: accepted
priority: P2
author: Hal Hildebrand
created: 2026-03-22
accepted_date: 2026-03-22
reviewed-by: self
related: RDR-011, RDR-030
---

# RDR-032: JavaScript Port Feasibility Spike

## Metadata
- **Type**: Spike
- **Status**: accepted (2026-03-22)
- **Priority**: P2
- **Created**: 2026-03-22
- **Related**: RDR-011 (Layout Protocol Extraction), RDR-030 (Schema Navigation & Discovery)

---

## Problem Statement

Kramer's autolayout algorithm (Bakke InfoVis 2013) is implemented exclusively in Java/JavaFX. The web is the dominant deployment platform for data-dense UIs. A JavaScript port would make the algorithm accessible to web applications without requiring a Java backend or JavaFX runtime.

The question is not whether to port, but whether the architecture allows a clean port, what the effort looks like, and where the risks are.

---

## Scope

This is a **feasibility spike** — research only, no implementation. The deliverable is this document with findings that answer:

1. **Layer mapping**: for each Kramer architecture layer, what's the JS equivalent and how much translates directly?
2. **Size estimate**: grounded in actual `wc -l` counts of the Java source
3. **Risks & gaps**: what's hard, including architectural prerequisites?
4. **Proof of concept scope**: minimum slice to validate the critical assumptions
5. **Package structure**: `@kramer/core`, `@kramer/react`, etc.
6. **Dependency assessment**: build vs buy for JS libraries

---

## Actual Java Codebase Size

Measured via `wc -l` on Java source files:

| Package | LOC | Notes |
|---------|-----|-------|
| kramer core (total) | 19,458 | All packages in kramer module |
| — schema | 348 | SchemaNode, Relation, Primitive, SchemaPath |
| — expression | 1,167 | Parser, AST, Evaluator |
| — query | 1,981 | LayoutQueryState, InteractionHandler, FieldSelectorPanel, FieldInspectorPanel, ExpressionEditor |
| — style | 1,278 | Style, LabelStyle, PrimitiveStyle, RelationStyle |
| — cell | 3,041 | LayoutCell hierarchy, focus/selection |
| — flowless | 2,502 | Custom VirtualFlow (forked Flowless) |
| — outline + table | 1,490 | Outline, Span, OutlineColumn, OutlineElement, NestedTable, NestedRow, NestedCell, ColumnHeader |
| — layout engine | 4,823 | RelationLayout (1,580), PrimitiveLayout (708), AutoLayout (1,190), SchemaNodeLayout (359), ColumnSet (155), ColumnPartitioner (170), ExhaustiveConstraintSolver (270), + decision records |
| — other | 2,828 | DataSnapshot, MeasurementStrategy, search, etc. |
| kramer-ql | 2,044 | GraphQL integration |
| explorer | 1,793 | AutoLayoutController, StandaloneDemo, panels |
| **Grand total** | **23,295** | |

### Key structural fact

`RelationLayout.java` (1,580 LOC) is a monolith that straddles layers 2-6: measure, layout decision, justify, compress, AND buildControl are all in one class. The layout pipeline reads mutable instance fields (`useTable`, `columnSets`, `cellHeight`, `justifiedWidth`, etc.). This tight coupling means the "compute" and "render" phases are NOT cleanly separated — they share mutable state on `RelationLayout`.

---

## Architecture Layer Mapping

### Layer 1: Schema (348 LOC)

**Java**: sealed abstract `SchemaNode` with `Relation` (composite) and `Primitive` (leaf). `SchemaPath` is an immutable path record. Jackson `JsonNode` for data.

**JS equivalent**: TypeScript discriminated union or class hierarchy. Native JSON objects replace `JsonNode`. `SchemaPath` becomes a simple `string[]` wrapper.

**Portability**: **Direct translation**. Pure data structures, no platform dependency.

### Layer 2: Measurement (spread across PrimitiveLayout 708 + RelationLayout 1,580 + Style 391)

**Java**: `PrimitiveLayout.measure()` computes P90 widths by measuring text via `LabelStyle.width()` → JavaFX `Text.getLayoutBounds()`. `RelationLayout.measure()` aggregates children. `MeasureResult` captures statistics. `Style.textWidth()` (lines 97-105) uses `javafx.scene.text.Text` and `Shape.intersect()` directly. The `Style` constructor (lines 281-344) bootstraps by constructing live JavaFX scene graph nodes, calling `applyCss()` and `layout()` to extract computed insets and heights.

**Existing abstraction**: A `MeasurementStrategy` interface already exists (37 LOC) with `ConfiguredMeasurementStrategy` (72 LOC) — but its coverage is incomplete. `Style.textWidth()` still calls JavaFX directly, and style initialization requires a live JavaFX scene for CSS computation.

**JS equivalent**: Text measurement via `canvas.measureText()` or hidden `<span>` + `getBoundingClientRect()` in browser. Style initialization would inject hidden DOM nodes and read computed styles — more involved than "just use browser CSS."

**Portability**: **Needs platform adapter AND completing the existing abstraction**. The interface exists but JavaFX leaks through.

**Risk**: **Medium-high**. Browser text measurement is synchronous but requires DOM access. Style bootstrap needs rethinking for browser. Cannot measure during SSR without approximation.

### Layer 3: Layout Decision (ExhaustiveConstraintSolver 270 + RelationConstraint 79 + parts of RelationLayout)

**Java**: `RelationLayout.layout()` decides table vs outline. `ExhaustiveConstraintSolver` enumerates render-mode assignments. `readableTableWidth()` threshold.

**JS equivalent**: Direct port for the solver and constraint types. The layout decision logic in `RelationLayout.layout()` needs extraction from the monolith.

**Portability**: The solver and constraint types (~350 LOC) are **direct translation**. The decision logic embedded in `RelationLayout` requires decomposition.

### Layer 4: Width Distribution (embedded in RelationLayout)

**Java**: `RelationLayout.justifyColumn()` — two-pass minimum-guarantee distribution. `effectiveChildWidth()`, `minimumChildWidth()`.

**JS equivalent**: Direct port. Pure arithmetic.

**Portability**: **Direct translation** once extracted from `RelationLayout`. ~150 LOC of pure logic.

### Layer 5: Column Packing (ColumnSet 155 + ColumnPartitioner 170 + parts of RelationLayout)

**Java**: `ColumnSet.compress()` with `ColumnPartitioner` (DP-optimal painter's partition). `PrimitiveLayout.compress()` sets justified widths.

**JS equivalent**: Direct port. Pure algorithms.

**Portability**: **Direct translation**. ~325 LOC.

### Layer 6: Rendering (outline 667 + table 823 + cell 3,041 + flowless 2,502 = ~7,033 LOC)

**Java**: `RelationLayout.buildOutline()` → `Outline` (VirtualFlow), `OutlineCell`, `Span`, `OutlineColumn`, `OutlineElement`. `RelationLayout.buildNestedTable()` → `NestedTable`, `NestedRow`, `NestedCell`, `ColumnHeader`. All JavaFX nodes. The `buildControl()` method reads directly from `RelationLayout`'s mutable instance fields — `NestedTable`'s constructor takes `(int childCardinality, RelationLayout layout, ...)`, coupling the renderer to the layout object.

**Critical architectural gap**: `LayoutDecisionNode` (from RDR-011) is a **diagnostic snapshot**, not a render protocol. `snapshotDecisionTree()` is called post-hoc for caching and inspection. No renderer consumes it. To enable pluggable renderers, a **render-context record** must be designed that captures everything `buildControl()` reads from `RelationLayout` — this is prerequisite architectural work not currently accounted for.

**JS equivalent**: **Full rewrite required.** Options:
- **React components**: `<Outline>`, `<NestedTable>`, `<OutlineElement>` → straightforward mapping once render-context exists.
- **Vanilla DOM**: `document.createElement()` tree. Manual virtualization (harder).
- **Pluggable**: Core produces render-context record; renderer is a separate package.

**Risk**: **High**. The compute↔render coupling in `RelationLayout` must be broken before a clean port is possible. The flowless VirtualFlow (2,502 LOC) has no direct JS equivalent — `@tanstack/virtual` handles simple cases but the Kramer pattern of nested VirtualFlows per column set has no off-the-shelf JS solution.

### Layer 7: Interaction (query package 1,981 LOC)

**Java**: `LayoutQueryState`, `FieldState` (14-field record), `InteractionHandler` (undo/redo), `LayoutInteraction` (12-variant sealed hierarchy), `InteractionMenuFactory`, `ColumnSortHandler`, `FieldSelectorPanel`, `FieldInspectorPanel`, `ExpressionEditor`.

**JS equivalent**: Core state logic (~800 LOC: LayoutQueryState, FieldState, InteractionHandler, LayoutInteraction) is framework-agnostic — direct port. UI panels (~1,100 LOC: FieldSelectorPanel, FieldInspectorPanel, ExpressionEditor, InteractionMenuFactory) are JavaFX and need rewrite per framework.

**Portability**: Core state is **direct translation**. UI panels are **renderer-specific rewrite**.

### Layer 8: Expression Language (1,167 LOC)

**Java**: `ExpressionParser` → `Expr` AST → `ExpressionEvaluator`. Field references, arithmetic, comparisons, aggregates.

**JS equivalent**: Direct port. Parser combinators work identically in TS.

**Portability**: **Direct translation**.

### Layer 9: GraphQL Integration (2,044 LOC)

**Java**: `GraphQlUtil` (434), `QueryExpander` (292), `QueryRewriter` (355), `TypeIntrospector` (136), `SchemaIntrospector` (64), `SchemaContext` (146), `ExprToGraphQL` (221), `DirectiveAwareStylesheet` (88), `RelayConnectionDetector` (42), `QueryBuilder` (130), `QueryRoot` (37), `ServerCapabilities` (39), `DirectiveReader` (60).

**JS equivalent**: `graphql` npm package provides parsing, AST manipulation, `print()`, and visitor utilities. `ExprToGraphQL` (expression → GraphQL filter argument translation) creates a cross-layer dependency between expression language (Layer 8) and query rewriting — both must be ported together. `RelayConnectionDetector` is relevant since Relay pagination conventions are common in JS GraphQL ecosystems.

**Portability**: **Direct translation** with some simplification from graphql-js utilities. All 2,044 LOC need porting.

### Layer 10: CSS Styling (Style 391 + co-located .css files)

**Java**: JavaFX CSS with co-located `.css` files. Custom `-fx-*` properties. `Style` class applies stylesheets.

**JS equivalent**: Native browser CSS — but `Style.java` (391 LOC) is deeper than "just use browser CSS." It initializes by constructing JavaFX scene graph nodes, calling `applyCss()` and `layout()` to extract computed insets and heights. The browser equivalent must inject hidden DOM nodes and read `getComputedStyle()`. This is non-trivial and interacts with the measurement risk (Layer 2).

**Portability**: CSS files are **rewrite** (JavaFX syntax → standard CSS). Style bootstrapping is **redesign** — same concept (measure invisible nodes) but different API.

---

## Portability Summary

| Category | Java LOC | Portability | TS LOC (est) |
|----------|----------|-------------|-------------|
| **Direct port** (schema, expression, solver, partitioner, decision types, query state logic) | ~3,500 | Copy + syntax translation | ~2,500 |
| **Port with extraction** (justify, compress, layout decision logic embedded in RelationLayout) | ~1,800 | Extract from monolith, then translate | ~1,200 |
| **Platform adapter** (measurement, style bootstrap) | ~1,700 | Redesign interface + browser impl | ~800 |
| **Full rewrite** (rendering: cell, outline, table, flowless, UI panels) | ~8,500 | New code for browser/React | ~4,000 |
| **GraphQL** (cross-layer, framework-neutral) | 2,044 | Direct translation | ~1,500 |
| **Other** (data snapshot, search, etc.) | ~5,750 | Mixed; evaluate per file | ~2,000 |
| **Total** | ~23,295 | | ~12,000 |

**Direct port candidates: ~25% of codebase** (not 60% as originally claimed). The remaining 75% requires extraction, redesign, or full rewrite.

---

## Architectural Prerequisite: Render-Context Record

Before a pluggable renderer is possible, `RelationLayout.buildControl()` must be decoupled from mutable layout state. Currently the renderer reads ~15 fields directly from `RelationLayout`:

- `useTable`, `useCrosstab`, `columnSets`, `resolvedCardinality`
- `cellHeight`, `justifiedWidth`, `columnHeaderHeight`, `labelWidth`
- `tableColumnWidth`, `columnHeaderIndentation`, `columnWidth`
- `children` (list of SchemaNodeLayout), `style` (RelationStyle)
- `extractor` (data extraction function), `sortComparator`

A **render-context record** (TypeScript interface or Java record) must capture this state so `buildControl()` can be driven from data rather than from the layout object. This can be designed as a new Java record first (without breaking existing code), then used as the portable protocol for the TS port.

**Effort**: ~1-2 weeks of architectural work, not counted in the previous estimate.

---

## Size Estimate (Revised)

| Package | Source Java LOC | TS LOC (est) | Effort | Notes |
|---------|----------------|-------------|--------|-------|
| Architectural prerequisite (render-context record) | — | ~200 | 1-2 weeks | Design + Java refactor, then TS interface |
| `@kramer/core` (schema, expression, solver, partitioner, justify, compress) | ~5,300 | ~3,700 | 3-4 weeks | Extract from RelationLayout monolith + translate |
| `@kramer/measurement` (browser measurement strategy) | ~500 | ~300 | 1 week | Style bootstrap redesign |
| `@kramer/graphql` (full kramer-ql port) | 2,044 | ~1,500 | 2 weeks | Including ExprToGraphQL, RelayConnectionDetector |
| `@kramer/react` (renderer: table, outline, virtualization) | ~7,033 | ~4,000 | 4-6 weeks | Full rewrite; nested virtualization is hardest part |
| `@kramer/react-ui` (interaction panels) | ~1,100 | ~800 | 2 weeks | Framework-specific UI |
| **Total (React path)** | **~16,000** | **~10,500** | **~13-17 weeks** | |

Rough effort: **13-17 weeks** (3-4 months) for a senior TS developer. Previous estimate of 4-6 weeks was based on fabricated LOC counts and did not account for the architectural prerequisite or the RelationLayout decomposition work.

---

## Risks & Gaps

| Risk | Severity | Mitigation |
|------|----------|------------|
| RelationLayout monolith must be decomposed before clean port | **Critical** | Design render-context record first; refactor Java side as prerequisite |
| Text measurement + Style bootstrap requires browser DOM | High | MeasurementStrategy exists but is incomplete; complete it in Java first, then port |
| Nested VirtualFlow pattern (multiple per column set) has no JS equivalent | High | @tanstack/virtual for simple cases; may need custom implementation for nested pattern |
| Effort estimate uncertainty (±30%) | High | PoC validates critical path; revise estimate after PoC |
| Expression → GraphQL cross-layer dependency | Medium | Port expression and GraphQL together |
| Server-side rendering (SSR) | Medium | Core runs in Node; measurement needs approximation without DOM |
| Browser text measurement varies across platforms | Medium | Accept platform-specific measurements; algorithm adapts by design |
| Maintaining parity with Java version | High | Shared test fixtures (JSON schema + data + expected decisions) as contract tests |

### Biggest Gap: Compute↔Render Coupling

The `RelationLayout` monolith mixes computation and rendering. `buildControl()` reads mutable fields set during the compute phases. A clean port requires designing a render-context record that captures this state, enabling renderers to work from data rather than from the layout object. This is prerequisite work that benefits both the JS port AND the Java codebase (better separation of concerns).

---

## Proof of Concept Scope (Revised)

Two slices to validate the critical assumptions:

### Slice 1: Flat table (validates measurement + basic rendering)
1. Schema types in TypeScript (Relation, Primitive, SchemaPath)
2. Measure a flat schema (3 primitives) using DOM text measurement
3. Layout + justify pipeline producing render-context data
4. React component that renders a `<table>` from the render-context
5. Resize: re-run pipeline on `window.resize`

### Slice 2: Nested outline (validates the hard parts)
1. Nested schema (2-level relation, ~6 leaf primitives)
2. Constrained width that forces the solver to choose outline for the outer relation and table for the inner
3. Compress with DP column partitioner
4. React renderer producing nested outline + inner table
5. Verify mode switches at the threshold width

Slice 2 is critical — a flat-3-primitive PoC validates almost nothing about the portability of the complex cases.

**Estimated PoC effort**: **5-8 days** for both slices.

---

## Package Structure

```
@kramer/core          — schema, measure protocol, layout pipeline, render-context record
@kramer/graphql       — GraphQL query → schema, QueryExpander, TypeIntrospector,
                        ExprToGraphQL, RelayConnectionDetector
@kramer/react         — React renderer (table, outline, crosstab components)
@kramer/react-ui      — React interaction panels (field selector, inspector, menus)
@kramer/dom           — vanilla DOM renderer (no framework)
@kramer/measurement   — browser DOM measurement strategy (separate for SSR exclusion)
```

Core has zero browser/framework dependencies. Renderers depend on core + their framework.

---

## Dependency Assessment

| Need | Build vs Buy | Recommendation |
|------|-------------|----------------|
| Virtualized lists (simple) | Buy | `@tanstack/virtual` — handles non-uniform row heights |
| Nested virtualization | Build | No off-the-shelf solution for VirtualFlow-per-column-set pattern |
| GraphQL parsing | Buy | `graphql` (reference implementation) |
| Expression parsing | Build | Small, custom grammar; no library matches exactly |
| State management | Build | Simple class with change listeners; no external dependency needed |
| Text measurement | Build | `canvas.measureText()` or DOM measurement; thin wrapper |
| Context menus | Buy (React) | `@radix-ui/react-context-menu` or similar |
| CSS | Build | Standard CSS modules or CSS-in-JS |

---

## Conclusion

The port is **feasible but larger than initially estimated**. The Kramer architecture has a clear conceptual separation between computation and rendering, but the implementation couples them through `RelationLayout`'s mutable state. A render-context record must be designed as a prerequisite, which benefits both the JS port and the Java codebase.

~25% of the codebase (schema, expression language, solver, query state logic) translates directly. ~75% requires extraction from the monolith, platform adapter redesign, or full rewrite. Total estimated effort is **13-17 weeks** for a React-based port, with 1-2 weeks of Java-side architectural work as a prerequisite.

**Recommendation**: Start with the architectural prerequisite (render-context record design) in the Java codebase. Then run the 2-slice PoC (5-8 days) to validate measurement, layout, and nested rendering before committing to the full port.

---

## Success Criteria

- [x] Layer mapping covers all 10 architecture layers
- [x] Size estimate grounded in actual `wc -l` counts
- [x] Risk assessment with severity and mitigation
- [x] Architectural prerequisite (render-context record) identified
- [x] Proof of concept scope defined (2 slices: flat + nested)
- [x] Package structure and dependency assessment complete
