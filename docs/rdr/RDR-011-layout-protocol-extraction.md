# RDR-011: Layout Protocol Extraction

## Metadata
- **Type**: Architecture
- **Status**: proposed
- **Priority**: P1
- **Created**: 2026-03-15
- **Revised**: 2026-03-15 (gate findings addressed)
- **Related**: RDR-009 (MeasureResult immutability — implemented), RDR-008 (pipeline phases — implemented), RDR-010 (CSS integration remediation), RDR-015 (alternative rendering modes — Phase 2 dependency)

## Problem Statement

Kramer's adaptive hierarchical layout algorithm (Recursive Metamorphic Layout) is tightly coupled to JavaFX rendering. The three-phase pipeline (measure -> layout/compress -> build) produces optimal layouts for structured hierarchical data, but the decision algorithm and the rendering layer are interleaved in the same classes. This prevents using Kramer's layout intelligence for any target other than JavaFX — no web, PDF, terminal, or native mobile rendering.

No published system computes adaptive hierarchical layout as a protocol. Every layout system is locked to one rendering target. Kramer's four result records (`MeasureResult`, `LayoutResult`, `CompressResult`, `HeightResult`) are already pure immutable data (RDR-009 is implemented). Layout decisions (useTable at each Relation node) are also pure data. The extraction is surgical, not architectural.

---

## Core Idea

Define a serializable `LayoutDecisionNode` — a record tree **composing** the four existing result records at each schema node. This separates the WHAT (layout decisions) from the HOW (rendering). Kramer becomes a **library** that computes optimal hierarchical layouts consumable by any renderer.

---

## Computation vs Rendering Architecture

Two models are possible:

- **Model A (server computes geometry, ships with data)**: The server runs the full pipeline (measure -> layout -> compress -> height), serializes the `LayoutDecisionNode` tree as JSON, and ships it alongside the data. The client renders without layout computation. This is the **initial target** — it leverages the existing JavaFX measurement infrastructure and defers the headless measurement problem.

- **Model B (server ships schema, client measures)**: The server ships only the schema tree. The client provides its own `MeasurementStrategy` and runs the full pipeline locally. This requires Phase 4 (measurement abstraction) and is a future goal.

Model A is simpler and immediately achievable. Model B requires solving the measurement coupling problem (Phase 4).

---

## Target Renderers

| Target | Technology | Use Case |
|--------|-----------|----------|
| Web | HTML/CSS Grid with recursive `<table>`/`<details>` switching | Browser-based data exploration |
| PDF | OpenPDF / iText | Printed reports, catalogs |
| Terminal | TUI with box-drawing characters | CLI data tools |
| SwiftUI | iOS/macOS native | Mobile data browsers |
| JavaFX | Current Kramer (becomes one consumer) | Desktop apps |

---

## Architecture

### Phase 1: Extract LayoutDecisionNode

The existing pipeline already produces layout decisions captured in four immutable result records. The `compute*()` methods already exist on `PrimitiveLayout` and `RelationLayout` (see `computeLayout()`, `computeCompress()`, `computeCellHeight()`). Extract these into a unified decision tree by composing the existing records:

```java
/**
 * A single node in the layout decision tree. Composes the four
 * existing result records from each pipeline phase.
 *
 * @see MeasureResult  — data-dependent state (label/column/data widths, cardinality, extractor)
 * @see LayoutResult   — table-vs-outline decision, columnHeaderIndentation, constrainedColumnWidth
 * @see CompressResult — justified geometry, column set assignments, cellHeight (outline mode)
 * @see HeightResult   — final sizing, cellHeight (table mode), resolvedCardinality, columnHeaderHeight
 */
public record LayoutDecisionNode(
    SchemaPath path,
    MeasureResult measure,
    LayoutResult layout,
    CompressResult compress,
    HeightResult height,
    List<LayoutDecisionNode> children
) {
    public LayoutDecisionNode {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
```

**Note on existing records**:
- `MeasureResult` — already captured by `PrimitiveLayout.getMeasureResult()` and `RelationLayout.getMeasureResult()`
- `LayoutResult` — already produced by `PrimitiveLayout.computeLayout()` and `RelationLayout.computeLayout()` (includes `columnHeaderIndentation` and `useVerticalHeader`)
- `CompressResult` — already produced by `PrimitiveLayout.computeCompress()` and `RelationLayout.computeCompress()` (includes `columnSets` and `justifiedWidth`)
- `HeightResult` — already produced by `PrimitiveLayout.computeCellHeight()` and `RelationLayout.computeCellHeight()` (includes `resolvedCardinality` and `columnHeaderHeight`)

No separate `PrimitiveDecision`/`RelationDecision` hierarchy is needed — the four result records already carry all geometry.

**ColumnSet state capture**: `CompressResult.columnSets()` holds `List<ColumnSet>`, but `ColumnSet` is currently a mutable class with `List<Column>` internals that are mutated during `compress()` (slide-right balancing). For serialization, define an explicit snapshot:

```java
record ColumnSetSnapshot(
    List<ColumnSnapshot> columns
) {}

record ColumnSnapshot(
    double width,
    double cellHeight,
    List<SchemaPath> fields  // ordered field references
) {}
```

Alternatively, capture post-`compress()` `ColumnSet` state by reading the finalized column widths, heights, and field assignments.

**Serialization caveat**: `MeasureResult.extractor` is a `Function<JsonNode, JsonNode>` lambda — not Jackson-serializable. For Model A (server-side computation), the extractor is not needed in the serialized tree since the server has already applied it. For cross-process serialization, `extractor` must be excluded or replaced with a serializable path expression (e.g., the `SchemaPath`).

**`rootLevel` flag**: The current codebase uses a transient `rootLevel` boolean on `SchemaNodeLayout` (set to `true` during `buildControl()`, then immediately reset to `false`). This flag controls whether `NestedTable`/`NestedRow` apply root-level styling. The decision tree should either capture this as a field on the root `LayoutDecisionNode`, or the renderer should infer it positionally (the top-level node is always root). Prefer the positional approach to avoid leaking rendering concerns into the decision tree.

**`distributeExtraHeight`**: This is a post-decision adjustment that redistributes surplus viewport height among table rows (`RelationLayout.distributeExtraHeight()`). It modifies `cellHeight` and `height` via `adjustHeight()` after the main pipeline completes. Two options:
1. Run `distributeExtraHeight` before capturing `HeightResult`, so the decision tree reflects final heights.
2. Capture the unadjusted heights and include `availableHeight` as a field on the root node, letting the renderer apply distribution. Option 1 is simpler for Model A.

### Joint Design with RDR-015: Render Mode in LayoutResult

`LayoutResult.useTable` is a boolean — it cannot express CROSSTAB, BAR, BADGE, or SPARKLINE modes. Before Phase 2 (LayoutRenderer) can be implemented, `LayoutResult` must carry explicit render mode enums so that any renderer can dispatch on the full set of layout decisions without back-references to mutable layout objects.

`LayoutResult` must replace `boolean useTable` with `RelationRenderMode relationMode` (TABLE, OUTLINE, CROSSTAB) and add `PrimitiveRenderMode primitiveMode` (TEXT, BAR, BADGE, SPARKLINE). The extended signature:

```java
record LayoutResult(
    RelationRenderMode relationMode,  // TABLE, OUTLINE, CROSSTAB (replaces useTable)
    PrimitiveRenderMode primitiveMode, // TEXT, BAR, BADGE, SPARKLINE
    boolean useVerticalHeader,
    double tableColumnWidth,
    double columnHeaderIndentation,
    double constrainedColumnWidth,
    List<LayoutResult> childResults
) {}
```

**Backward compatibility**: `relationMode == TABLE` is equivalent to the old `useTable == true`; `relationMode == OUTLINE` is equivalent to `useTable == false`. Existing callers that checked `useTable` migrate to `relationMode == TABLE`. `primitiveMode` defaults to `TEXT` for all existing Primitive nodes, preserving current behavior.

**Why this belongs in LayoutResult, not on layout objects**: `LayoutDecisionNode` composes the four result records (`MeasureResult`, `LayoutResult`, `CompressResult`, `HeightResult`). If render mode lives only as an instance field on `PrimitiveLayout`/`RelationLayout`, the decision tree cannot carry it — renderers would need access to the mutable layout objects, defeating the purpose of the protocol extraction. The enums must be in `LayoutResult` so that `LayoutDecisionNode` captures the complete rendering decision.

**Dependency**: RDR-015 defines the `PrimitiveRenderMode` and `RelationRenderMode` enums and their semantics. This RDR consumes them as fields in `LayoutResult`. Phase 2 (LayoutRenderer) is blocked until these enums exist in `LayoutResult`.

### Phase 2: Define Renderer Interface

```java
/**
 * Visitor/fold over the LayoutDecisionNode tree. The renderer
 * receives each node paired with its pre-rendered children,
 * preserving the correlation between child decisions and child
 * render results.
 *
 * @param <T> the render output type (e.g., javafx.scene.Node, org.w3c.dom.Element)
 */
public interface LayoutRenderer<T> {
    T renderPrimitive(LayoutDecisionNode node, JsonNode data);
    T renderRelation(LayoutDecisionNode node, JsonNode data,
                     List<Map.Entry<LayoutDecisionNode, T>> children);
}
```

No separate `renderRoot` — the top-level call is just `renderRelation` on the root node. The caller knows positionally which node is root.

Children are passed as `Map.Entry<LayoutDecisionNode, T>` pairs to preserve the correlation between each child's decision and its rendered result. This avoids the structural inversion where a flat `List<T> children` loses which child produced which result.

Usage pattern (tree walk):
```java
public <T> T render(LayoutDecisionNode node, JsonNode data,
                    LayoutRenderer<T> renderer) {
    if (node.children().isEmpty()) {
        return renderer.renderPrimitive(node, data);
    }
    List<Map.Entry<LayoutDecisionNode, T>> childResults = new ArrayList<>();
    for (LayoutDecisionNode child : node.children()) {
        JsonNode childData = extractChildData(child, data);
        T childResult = render(child, childData, renderer);
        childResults.add(Map.entry(child, childResult));
    }
    return renderer.renderRelation(node, data, childResults);
}
```

JavaFX rendering becomes `JavaFxLayoutRenderer implements LayoutRenderer<Node>`.

### Phase 3: HTML Renderer (First Non-JavaFX Consumer)

Create `HtmlLayoutRenderer implements LayoutRenderer<Element>` as the first validation that the protocol generalizes. Uses HTML/CSS Grid for table mode and `<details>`/`<summary>` for outline mode.

### Phase 4: Factor Measurement Out of JavaFX

Currently `Style.computeRelationStyle()` constructs **9 JavaFX component instances** (`NestedTable`, `NestedRow`, `NestedCell`, `Outline`, `OutlineCell`, `OutlineColumn`, `OutlineElement`, `Span`, `LayoutLabel`) plus a `Scene`, calls `applyCss()`/`layout()` on each, and reads computed insets. `Style.computePrimitiveStyle()` constructs 3 instances (`PrimitiveList`, `LayoutLabel`, `Label`).

A `MeasurementStrategy` interface must provide the following inset/font properties:

**RelationStyle** (8 `Insets` objects = 32 individual values from `getLeft()`/`getRight()`/`getTop()`/`getBottom()`):
1. `table` insets (4 values)
2. `row` insets (4 values)
3. `rowCell` insets (4 values)
4. `outline` insets (4 values)
5. `outlineCell` insets (4 values)
6. `column` insets (4 values)
7. `span` insets (4 values)
8. `element` insets (4 values)

**PrimitiveStyle**:
9. `listInsets` (4 values)
10. `primitiveStyle` label insets + font metrics (4 inset values + font + lineHeight)

**LabelStyle** (per-node):
11. Label insets (4 values)
12. Label font + lineHeight (for text width computation)

Total: **40+ individual inset values** plus font metrics per schema node. This is substantially more than "~50 lines" of coupling — the measurement surface area is wide.

---

## Dependencies

- ~~RDR-009 Phase A (MeasureResult extraction)~~ — **implemented** (closed, bead Kramer-53g)
- ~~RDR-009 Phase B (LayoutResult extraction)~~ — **implemented** (closed, bead Kramer-53g)
- RDR-010 C3 (LayoutStylesheet wiring for algorithm-tuning parameters) — **prerequisite for Phase 4**. Phase 4 requires algorithm-tuning parameters to be decoupled from cached style objects. RDR-010 C3 moves them to `LayoutStylesheet`; without this, a `MeasurementStrategy` would need to replicate the mutable-setter pattern.
- RDR-010 S2 (protected measurement methods) — enables headless style implementations for Phase 4
- RDR-015 (alternative rendering modes) — **prerequisite for Phase 2**. Defines `RelationRenderMode` and `PrimitiveRenderMode` enums that must be added to `LayoutResult` before `LayoutRenderer` can dispatch on the full set of rendering decisions

---

## Migration Strategy

1. **Phase 1**: Extract `LayoutDecisionNode` from existing pipeline output (LOW risk — read-only extraction from existing `compute*()` methods, existing pipeline unchanged)
2. **Phase 2**: Create `JavaFxLayoutRenderer` that consumes `LayoutDecisionNode` (MEDIUM risk — refactor `buildControl()` to use decisions)
3. **Phase 3**: Create `HtmlLayoutRenderer` as first non-JavaFX consumer (**MEDIUM** risk — new code, but must validate protocol completeness. Requires concrete acceptance test: render a reference schema with known data and compare HTML output pixel-for-pixel against JavaFX screenshot)
4. **Phase 4**: Factor measurement into pluggable strategy (**MEDIUM-HIGH** risk — touches Style class, must replicate 40+ inset properties and font metrics. Depends on RDR-010 C3)

**Phase 1 can begin immediately** — RDR-009 is already implemented and the four result records exist. No blockers.

---

## Research Findings

### RF-1: Pipeline Phase Independence (Confidence: HIGH)

The existing pipeline phases (measure, layout, compress, height, build) are already functionally independent — each reads from the previous phase's output. The coupling is through shared mutable state on SchemaNodeLayout objects, not through algorithmic interdependence. The `compute*()` methods on `PrimitiveLayout` and `RelationLayout` already produce the immutable result records. Extraction to a `LayoutDecisionNode` tree composes these records without algorithmic change.

### RF-2: Measurement Coupling Is Deeper Than Originally Assessed (Confidence: HIGH)

`Style.computeRelationStyle()` creates 9 JavaFX components and reads 8 `Insets` objects (32 individual values). `computePrimitiveStyle()` creates 3 components and reads list insets plus `LabelStyle` font metrics. `LabelStyle` itself uses `Text` for line-height measurement and `Font` for text-width computation. Total measurement surface: 40+ inset values plus font metrics per schema node.

A `MeasurementStrategy` must provide:
- 8 `Insets` for relation components (table, row, rowCell, outline, outlineCell, column, span, element)
- 1 `Insets` for primitive list
- `LabelStyle` equivalent (insets + font + lineHeight)
- `PrimitiveTextStyle` equivalent (primitiveStyle insets + font for text width)
- Text measurement: `width(String)` and `getHeight(double rows)` per font

This is not a thin abstraction layer. A configuration-based `MeasurementStrategy` (explicit JSON inset values) is the practical path for non-JavaFX targets.

### RF-3: Serialization Format (Confidence: MEDIUM)

`LayoutDecisionNode` as Java records is naturally JSON-serializable via Jackson (already a project dependency) — **with one exception**: `MeasureResult.extractor` is a `Function<JsonNode, JsonNode>` lambda, which is not serializable. For Model A (server computes, client renders), the extractor is consumed server-side and should be excluded from serialization via `@JsonIgnore` or a DTO projection. A web renderer receives the decision tree as JSON and renders it client-side with pre-extracted data.

---

## Risks and Considerations

| Risk | Severity | Mitigation |
|------|----------|------------|
| API surface too large for initial extraction | Medium | Start with read-only LayoutDecisionNode; defer Renderer interface to Phase 2 |
| CSS measurement coupling deeper than expected | **Medium-High** | 40+ inset properties required. Profile actual JavaFX dependencies in Style; RDR-010 C3 and S2 are prerequisites for Phase 4 |
| `MeasureResult.extractor` not serializable | Medium | Exclude from serialized form; document that Model A provides pre-extracted data |
| `ColumnSet` is mutable; post-compress state hard to snapshot | Medium | Define `ColumnSetSnapshot`/`ColumnSnapshot` records or capture finalized state |
| `distributeExtraHeight` runs after main pipeline | Low | Run before capturing HeightResult (Model A) or document as renderer-side adjustment |
| HTML renderer may not fully replicate JavaFX layout fidelity | **Medium** | Require concrete acceptance test comparing HTML and JavaFX output for reference schema |
| Performance of serialization for interactive use | Low | Records are small (5-20 nodes); Jackson serialization is sub-millisecond |
| Renderer interface may not generalize across all targets | Medium | Validate with 2 targets (JavaFX + HTML) before committing the interface |

## Success Criteria

1. `LayoutDecisionNode` composes the four existing result records (`MeasureResult`, `LayoutResult`, `CompressResult`, `HeightResult`) at each tree node — no separate decision hierarchy
2. `LayoutDecisionNode` captures all information needed to render a complete layout (no back-references to `SchemaNodeLayout` needed)
3. Existing JavaFX rendering works identically when driven from `LayoutDecisionNode`
4. At least one non-JavaFX renderer (HTML) produces visually equivalent output, verified by acceptance test
5. Layout computation (measure -> decide) runs without JavaFX toolkit initialization when using headless measurement (Phase 4)
6. Decision tree is JSON-serializable for cross-process consumption (excluding `MeasureResult.extractor`)
7. All existing 142+ tests pass unchanged

## Recommendation

Phase 1 can begin now — RDR-009 is implemented and the four result records (`MeasureResult`, `LayoutResult`, `CompressResult`, `HeightResult`) exist in the codebase. The `compute*()` methods on `PrimitiveLayout` and `RelationLayout` already produce these records. Phase 1 composes them into `LayoutDecisionNode` — a straightforward extraction.

Phase 4 (headless measurement) is blocked on RDR-010 C3 (LayoutStylesheet wiring) and carries MEDIUM-HIGH risk due to the 40+ inset measurement surface.

**Source file references**:
- `kramer/src/main/java/com/chiralbehaviors/layout/MeasureResult.java`
- `kramer/src/main/java/com/chiralbehaviors/layout/LayoutResult.java`
- `kramer/src/main/java/com/chiralbehaviors/layout/CompressResult.java`
- `kramer/src/main/java/com/chiralbehaviors/layout/HeightResult.java`
- `kramer/src/main/java/com/chiralbehaviors/layout/PrimitiveLayout.java` (compute methods: lines 267-311)
- `kramer/src/main/java/com/chiralbehaviors/layout/RelationLayout.java` (compute methods: lines 308-351; distributeExtraHeight: lines 108-132; nestTableColumn with Indent geometry: lines 458-476)
- `kramer/src/main/java/com/chiralbehaviors/layout/ColumnSet.java` (mutable compress/slide-right: lines 51-97)
- `kramer/src/main/java/com/chiralbehaviors/layout/style/Style.java` (computeRelationStyle: lines 226-282; computePrimitiveStyle: lines 183-219)
- `kramer/src/main/java/com/chiralbehaviors/layout/style/RelationStyle.java` (8 Insets fields: lines 40-54)
- `kramer/src/main/java/com/chiralbehaviors/layout/style/PrimitiveStyle.java` (listInsets + font metrics)
- `kramer/src/main/java/com/chiralbehaviors/layout/SchemaNodeLayout.java` (rootLevel flag: line 142; distributeExtraHeight: line 168)
