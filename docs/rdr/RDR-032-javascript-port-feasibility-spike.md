---
title: "JavaScript Port Feasibility Spike"
id: RDR-032
type: Spike
status: proposed
priority: P2
author: Hal Hildebrand
created: 2026-03-22
reviewed-by: pending
related: RDR-011, RDR-030
---

# RDR-032: JavaScript Port Feasibility Spike

## Metadata
- **Type**: Spike
- **Status**: proposed
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
2. **Size estimate**: rough LOC/effort for core, first renderer, and GraphQL integration
3. **Risks & gaps**: what's hard?
4. **Proof of concept scope**: minimum slice to validate the approach
5. **Package structure**: `@kramer/core`, `@kramer/react`, etc.
6. **Dependency assessment**: build vs buy for JS libraries

---

## Architecture Layer Mapping

### Layer 1: Schema (SchemaNode, Relation, Primitive, SchemaPath)

**Java**: sealed abstract `SchemaNode` with `Relation` (composite) and `Primitive` (leaf). `SchemaPath` is an immutable path record. Jackson `JsonNode` for data.

**JS equivalent**: TypeScript discriminated union or class hierarchy. Native JSON objects replace `JsonNode`. `SchemaPath` becomes a simple `string[]` wrapper.

**Portability**: **Direct translation**. Pure data structures, no platform dependency. ~200 LOC Java → ~150 LOC TS.

### Layer 2: Measurement (measure phase)

**Java**: `PrimitiveLayout.measure()` computes P90 widths by measuring text via `LabelStyle.width()` → JavaFX `Text.getLayoutBounds()`. `RelationLayout.measure()` aggregates children. `MeasureResult` captures statistics.

**JS equivalent**: Text measurement via hidden `<span>` + `getBoundingClientRect()` in browser. Server-side: approximate via character-width tables or headless browser.

**Portability**: **Needs platform adapter**. The measurement INTERFACE is portable, but the implementation requires a `MeasurementStrategy` abstraction:
```typescript
interface MeasurementStrategy {
  textWidth(text: string, font: string, fontSize: number): number;
  lineHeight(font: string, fontSize: number): number;
}
```

**Risk**: Browser text measurement is synchronous (good) but requires DOM access. Cannot measure during SSR without approximation. **Medium risk**.

### Layer 3: Layout Decision (layout phase + constraint solver)

**Java**: `RelationLayout.layout()` decides table vs outline. `ExhaustiveConstraintSolver` enumerates render-mode assignments. `readableTableWidth()` threshold. `LayoutResult` and `LayoutDecisionNode` capture decisions.

**JS equivalent**: Direct port. Pure arithmetic and combinatorial logic. No platform dependency.

**Portability**: **Direct translation**. ~500 LOC Java → ~400 LOC TS. The `LayoutDecisionNode` tree is already a portable data protocol.

### Layer 4: Width Distribution (justify phase)

**Java**: `RelationLayout.justifyColumn()` — two-pass minimum-guarantee distribution. `effectiveChildWidth()`, `minimumChildWidth()`.

**JS equivalent**: Direct port. Pure arithmetic.

**Portability**: **Direct translation**. ~200 LOC.

### Layer 5: Column Packing (compress phase)

**Java**: `ColumnSet.compress()` with `ColumnPartitioner` (DP-optimal painter's partition). `PrimitiveLayout.compress()` sets justified widths.

**JS equivalent**: Direct port. Pure algorithms.

**Portability**: **Direct translation**. ~300 LOC including DP partitioner.

### Layer 6: Rendering (buildControl phase)

**Java**: `RelationLayout.buildOutline()` → `Outline` (VirtualFlow), `OutlineCell`, `Span`, `OutlineColumn`, `OutlineElement`. `RelationLayout.buildNestedTable()` → `NestedTable`, `NestedRow`, `NestedCell`, `ColumnHeader`. All JavaFX nodes.

**JS equivalent**: **This is the big rewrite.** Options:
- **React components**: `<Outline>`, `<NestedTable>`, `<OutlineElement>` → straightforward mapping. Use `react-window` or `@tanstack/virtual` for virtualization.
- **Vanilla DOM**: `document.createElement()` tree. Manual virtualization (harder).
- **Pluggable**: Core produces `LayoutDecisionNode` tree; renderer is a separate package.

**Portability**: **Full rewrite required** for this layer. But it's isolated — the core pipeline is renderer-agnostic. ~1500 LOC Java → ~800-1200 LOC TS per renderer.

**Risk**: VirtualFlow is complex (custom forked Flowless library, ~2000 LOC). Browser virtualization libraries handle this but with different APIs. **High complexity, medium risk** (well-solved problem in JS ecosystem).

### Layer 7: Interaction (query state + gestures)

**Java**: `LayoutQueryState`, `FieldState`, `InteractionHandler`, `LayoutInteraction` (12-variant sealed hierarchy), `InteractionMenuFactory`, `ColumnSortHandler`, `FieldSelectorPanel`, `FieldInspectorPanel`.

**JS equivalent**: State management is framework-agnostic. `LayoutQueryState` → Zustand/Jotai store or plain class. `LayoutInteraction` → TypeScript discriminated union. Context menus → framework-specific (React context menu lib, etc.).

**Portability**: Core state logic is **direct translation** (~400 LOC). UI panels (FieldSelectorPanel, etc.) are **renderer-specific** (~500 LOC per framework).

### Layer 8: Expression Language

**Java**: `ExpressionParser` → `Expr` AST → `ExpressionEvaluator`. Field references, arithmetic, comparisons, aggregates.

**JS equivalent**: Direct port. Parser combinators work the same way in JS/TS.

**Portability**: **Direct translation**. ~400 LOC.

### Layer 9: GraphQL Integration

**Java**: `GraphQlUtil`, `QueryExpander`, `TypeIntrospector`, `QueryRewriter`, `SchemaIntrospector`.

**JS equivalent**: `graphql` npm package provides parsing, AST manipulation, and `printAST` — same capabilities as graphql-java. The `QueryExpander` pattern (immutable AST transforms) maps directly.

**Portability**: **Direct translation** with simpler code (graphql-js has good AST visitor/transform utilities). ~500 LOC.

### Layer 10: CSS Styling

**Java**: JavaFX CSS with co-located `.css` files. Custom `-fx-*` properties. `Style` class applies stylesheets.

**JS equivalent**: Native browser CSS. Actually **simpler** — no JavaFX CSS translation needed. CSS-in-JS, CSS modules, or plain stylesheets all work.

**Portability**: **Simpler than Java**. CSS files need rewriting from JavaFX syntax to standard CSS, but the styling model is native to the browser.

---

## Size Estimate

| Package | Java LOC (approx) | TS LOC (estimated) | Effort | Notes |
|---------|-------------------|-------------------|--------|-------|
| `@kramer/core` (layers 1-5, 8) | ~3000 | ~2000 | Medium | Pure computation, direct port |
| `@kramer/graphql` (layer 9) | ~1200 | ~600 | Low | graphql-js simplifies AST work |
| `@kramer/react` (layer 6 renderer) | ~2500 | ~1200 | High | New code, virtualization integration |
| `@kramer/react-ui` (layer 7 panels) | ~1500 | ~800 | Medium | Framework-specific UI |
| `@kramer/dom` (vanilla renderer) | — | ~1000 | High | Alternative to React |
| **Total (React path)** | **~8200** | **~4600** | | |

Rough effort: **4-6 weeks** for a senior TS developer to produce a working React-based port with core + renderer + basic interaction.

---

## Risks & Gaps

| Risk | Severity | Mitigation |
|------|----------|------------|
| Text measurement accuracy varies across browsers/fonts | Medium | Use actual DOM measurement; provide calibration API; test cross-browser |
| VirtualFlow complexity (Flowless is 2000 LOC custom) | High | Use `@tanstack/virtual` — mature, well-tested, handles both directions |
| JavaFX CSS → browser CSS translation | Low | Browser CSS is more capable; rewrite styles rather than translate |
| Expression language parser | Low | Direct port; or use existing JS expression parser library |
| Server-side rendering (SSR) | Medium | Core can run in Node; measurement needs approximation without DOM |
| Bundle size for core | Low | Tree-shakeable TS; core is pure computation, small footprint |
| Maintaining parity with Java version | High | Shared test fixtures (JSON schema + data + expected decisions) as contract tests |

### Biggest Gap: Measurement

The measure phase requires text width computation, which needs a rendering context. In JavaFX, this is `Text.getLayoutBounds()`. In browsers, it's `canvas.measureText()` or DOM `offsetWidth`. These can give different results, meaning the same schema+data can produce different layout decisions on different platforms.

**Mitigation**: Accept platform-specific measurements. The layout algorithm adapts to whatever widths it receives — that's its core design. Cross-platform layout PARITY is not a goal; cross-platform layout QUALITY is.

---

## Proof of Concept Scope

Minimum slice to validate feasibility:

1. **Schema types** in TypeScript (Relation, Primitive, SchemaPath)
2. **Measure** a flat schema (3 primitives) using DOM text measurement
3. **Layout + justify + compress** pipeline producing a `LayoutDecisionNode`
4. **React renderer** that takes the decision node and renders a `<table>` for table mode
5. **Resize**: re-run pipeline on `window.resize`, verify table ↔ outline switching

This covers the critical path through all layers except GraphQL and interaction. Estimated effort: **3-5 days**.

---

## Package Structure

```
@kramer/core          — schema, measure protocol, layout pipeline, decision tree
@kramer/graphql       — GraphQL query → schema, QueryExpander, TypeIntrospector
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
| Virtualized lists | Buy | `@tanstack/virtual` — mature, framework-agnostic |
| GraphQL parsing | Buy | `graphql` (reference implementation) |
| Expression parsing | Build | Small, custom grammar; no library matches exactly |
| State management | Build | Simple class with change listeners; no external dependency needed |
| Text measurement | Build | Thin wrapper around `canvas.measureText()` or DOM measurement |
| Context menus | Buy (React) | `@radix-ui/react-context-menu` or similar |
| CSS | Build | Standard CSS modules or CSS-in-JS |

---

## Conclusion

The port is **feasible and well-bounded**. The Kramer architecture already separates computation (layers 1-5) from rendering (layer 6), with `LayoutDecisionNode` as the portable protocol between them. ~60% of the codebase is pure computation that translates directly to TypeScript. The rendering layer is a full rewrite but maps cleanly to React/DOM patterns.

**Recommendation**: Proceed with a proof of concept (3-5 days) to validate the measurement + layout + React rendering path before committing to the full port.

---

## Success Criteria

- [ ] Layer mapping covers all 10 architecture layers
- [ ] Size estimate with LOC and effort for each package
- [ ] Risk assessment with severity and mitigation
- [ ] Proof of concept scope defined
- [ ] Package structure and dependency assessment complete
