# RDR-011: Layout Protocol Extraction

## Metadata
- **Type**: Architecture
- **Status**: accepted
- **Priority**: P1
- **Created**: 2026-03-15
- **Accepted**: 2026-03-16
- **Reviewed-by**: self
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

The existing pipeline already produces layout decisions captured in four immutable result records. The `compute*()` methods already exist on `PrimitiveLayout` and `RelationLayout` (see `computeCompress()`, `computeCellHeight()`). Extract these into a unified decision tree by composing the existing records via `snapshotLayoutResult()`.

**Note on snapshot point**: `computeLayout()` re-runs `layout()` internally and must not be called as a snapshot point — it has side effects. `snapshotLayoutResult()` is the correct side-effect-free snapshot: it reads already-computed result records after the pipeline has converged without triggering re-computation.

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
    String fieldName,      // leaf field name from SchemaPath.leaf(); used for child data extraction
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

**Timing verification**: Verify that `snapshotLayoutResult()` at `AutoLayout.java:401` is invoked after `distributeExtraHeight()` in the `autoLayout()` call chain. If `snapshotLayoutResult()` is called before `distributeExtraHeight()`, `HeightResult` will reflect pre-distribution heights and the snapshot will be incorrect for Model A.

### Joint Design with RDR-015: Render Mode in LayoutResult

`LayoutResult.useTable` is a boolean — it cannot express CROSSTAB, BAR, BADGE, or SPARKLINE modes. Before Phase 2 (LayoutRenderer) can be implemented, `LayoutResult` must carry explicit render mode enums so that any renderer can dispatch on the full set of layout decisions without back-references to mutable layout objects.

**Canonical definition**: RDR-015 is authoritative for the `LayoutResult` extended signature, `RelationRenderMode`, and `PrimitiveRenderMode` enum definitions. Refer to RDR-015 for the record field list and backward-compatibility migration. This RDR depends on those types; it does not redefine them.

**Dependency**: Phase 2 (LayoutRenderer) is blocked until RDR-015 Phase 1 lands `RelationRenderMode` and `PrimitiveRenderMode` in `LayoutResult`.

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
        // Serialized (cross-process) scenario: use child.fieldName() for data extraction.
        // In-process scenario: MeasureResult.extractor() may be used instead (available
        // server-side where JavaFX measurement runs), but fieldName() is always present.
        JsonNode childData = data.get(child.fieldName());
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
- ~~RDR-010 C3 (LayoutStylesheet wiring for algorithm-tuning parameters)~~ — **implemented** (RDR-010 closed 2026-03-15). Phase 4 prerequisites from RDR-010 are satisfied.
- ~~RDR-010 S2 (protected measurement methods)~~ — **implemented** (RDR-010 closed 2026-03-15). Enables headless style implementations for Phase 4.
- RDR-015 (alternative rendering modes) — **prerequisite for Phase 2**. Defines `RelationRenderMode` and `PrimitiveRenderMode` enums that must be added to `LayoutResult` before `LayoutRenderer` can dispatch on the full set of rendering decisions

---

## Migration Strategy

1. **Phase 1**: Extract `LayoutDecisionNode` from existing pipeline output (LOW risk — read-only extraction from existing `compute*()` methods, existing pipeline unchanged)
2. **Phase 2**: Create `JavaFxLayoutRenderer` that consumes `LayoutDecisionNode` (MEDIUM risk — refactor `buildControl()` to use decisions)
3. **Phase 3**: Create `HtmlLayoutRenderer` as first non-JavaFX consumer (**MEDIUM** risk — new code, but must validate protocol completeness. Requires concrete acceptance test: render a reference schema with known data and compare HTML output pixel-for-pixel against JavaFX screenshot)
4. **Phase 4**: Factor measurement into pluggable strategy (**MEDIUM-HIGH** risk — touches Style class, must replicate 40+ inset properties and font metrics. Depends on RDR-010 C3)

**Phase 1 can begin immediately** — RDR-009 is already implemented and the four result records exist. No blockers.

**Sequencing with RDR-015**: Phase 1 captures `LayoutResult` as currently structured (boolean `useTable`). The `RelationRenderMode`/`PrimitiveRenderMode` migration from RDR-015 Phase 1 must complete before Phase 2, not before Phase 1. Phase 1 proceeds with the existing `useTable` field; Phase 2 is blocked until RDR-015 Phase 1 lands.

---

## Alternatives Considered

### Alt A: Serialize the Mutable SchemaNodeLayout Tree Directly (Jackson on Live Objects)

Capture layout state by serializing the existing `PrimitiveLayout`/`RelationLayout` object graph after the pipeline completes, using Jackson annotations on the live mutable objects.

**Pros:**
- No new record types; existing objects already hold all computed state.
- Phase 1 implementation would be a few Jackson annotations and a serialization call.
- No structural changes to the pipeline.

**Cons:**
- `SchemaNodeLayout` objects are mutable — serializing them mid-pipeline produces inconsistent snapshots. The `rootLevel` flag, `distributeExtraHeight` mutations, and ColumnSet slide-right balancing all modify state after intermediate pipeline phases. A snapshot taken before `distributeExtraHeight` runs will have wrong heights; a snapshot taken after will capture the transient `rootLevel == true` state.
- `MeasureResult.extractor` is a lambda closure embedded in the live objects — not serializable and cannot be excluded without a custom serializer for every containing type.
- `ColumnSet` is mutable with interleaved `List<Column>` internals modified during `compress()`. Jackson would serialize mid-mutation state unless the pipeline is redesigned to not mutate in-place.
- The serialized form becomes a dump of implementation internals, not a protocol. Any refactoring of field names or types is a breaking protocol change.
- Phase 4 (headless measurement) becomes impossible — the live object graph is inseparable from the JavaFX `Style` objects that computed it.

**Rejection reason:** Correctness and timing constraints make live-object serialization unreliable. The mutability of `ColumnSet`, the post-pipeline `distributeExtraHeight` adjustment, and the non-serializable lambda in `MeasureResult` create a minefield of partial-state bugs. More fundamentally, the goal is a stable cross-platform protocol, not an internal state dump. The `LayoutDecisionNode` approach (composing the four result records into an explicit snapshot) is unambiguously correct because `snapshotLayoutResult()` is called after the pipeline converges, not during it.

---

### Alt B: GraphQL-Based Protocol (Re-Query per Platform, Server Computes Layout)

Expose Kramer's layout computation as a GraphQL service. Clients query the layout endpoint with schema + data, receive layout decisions in the response.

**Pros:**
- Leverages existing `kramer-ql` module and GraphQL infrastructure already in the project.
- Protocol is self-documenting via GraphQL schema introspection.
- Stateless server — no client-side computation.
- Fits cleanly into existing `QueryRoot`/`GraphQlUtil` patterns.

**Cons:**
- Requires a running Kramer server for every layout computation, even for offline use cases (PDF generation, terminal tools, embedded apps).
- Round-trip latency is unacceptable for interactive resize events — the `AutoLayout` control fires layout on every width change.
- `isConverged()` stability gate requires iterative pipeline passes; a network round-trip per iteration would be prohibitively slow.
- Complicates the JavaFX renderer (currently a library with no server dependency) by introducing a mandatory network call.
- Does not solve the Phase 4 (headless measurement) problem — the server still requires JavaFX toolkit initialization to measure CSS insets.

**Rejection reason:** The primary use case is a local layout library consumed by JavaFX and future non-JavaFX renderers. Interposing a network boundary violates the performance contract for interactive layout (sub-millisecond resize response). The `LayoutDecisionNode` record tree achieves the same protocol goals without any network dependency — it is a value object that can be serialized to JSON for cross-process cases and passed in-memory for local cases.

---

### Alt C: CSS-Only Protocol (Send CSS + Data, Let Each Platform Do Its Own Layout)

Ship a CSS stylesheet alongside the JSON data. Each rendering platform applies its own layout engine using the CSS as the specification. Kramer's role is reduced to generating a CSS ruleset.

**Pros:**
- Web renderers already implement CSS — no custom layout engine needed in the browser.
- Stylesheet is small and human-readable.
- Aligns with existing CSS-driven styling in Kramer (the `LayoutStylesheet` approach in RDR-010).

**Cons:**
- CSS cannot express Kramer's hierarchical adaptive layout decisions. The `useTable`/`useOutline` switch at each `Relation` node, column-set assignments, and `distributeExtraHeight` geometry are algorithmic decisions that CSS media queries and flex/grid layout cannot replicate from a stylesheet alone.
- Each target platform would need to re-implement the RML algorithm independently, defeating the purpose of centralizing layout intelligence in Kramer.
- Non-web targets (PDF, terminal, SwiftUI) do not implement CSS — they would need a CSS-to-layout interpreter in addition to their own renderer.
- `ColumnSet` geometry (which fields go in which column, justified widths, cell heights) is output of the compress phase — it cannot be encoded as CSS without essentially serializing `CompressResult` anyway.

**Rejection reason:** CSS is a styling language, not a layout decision protocol. The RML algorithm produces structural decisions (column assignments, render mode per node, justified geometry) that are not expressible as CSS rules. A CSS-only protocol would require each platform to re-implement the algorithm independently. The `LayoutDecisionNode` record tree is the correct granularity — it encodes what was decided and how space was divided, leaving only rendering mechanics to each platform.

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

## Finalization Gate

### 1. Has the problem been validated with real user scenarios?

Partially. The JavaFX pipeline is exercised by 142+ existing tests that cover measure, layout, compress, and height phases across primitive and relation nodes. These tests validate that the pipeline produces correct geometry. However, no test yet validates the cross-platform serialization scenario — shipping a `LayoutDecisionNode` tree to a non-JavaFX renderer and confirming that the rendered output matches the JavaFX reference. The HTML renderer (Phase 3) must provide this validation before the protocol can be considered proven. The problem (layout intelligence locked to JavaFX) is real and well-evidenced; the solution's generality is an assumption until Phase 3 produces a passing acceptance test.

### 2. Is the solution the simplest that could work?

Yes, given the constraints. The core mechanism — composing the four existing result records (`MeasureResult`, `LayoutResult`, `CompressResult`, `HeightResult`) into a `LayoutDecisionNode` tree via `snapshotLayoutResult()` — adds no new algorithmic complexity. The `LayoutDecisionKey` cache (the embryonic protocol already present in the codebase) demonstrates that the pipeline already produces stable, reusable decision trees. The `isConverged()` stability gate ensures snapshot capture happens after the pipeline has reached a fixed point, eliminating partial-state bugs. The `MeasureResult.extractor` exclusion (a single `@JsonIgnore` or DTO projection) handles the one non-serializable field. Simpler alternatives (Alt A: serialize live objects, Alt C: CSS-only) were rejected because they cannot correctly capture pipeline state or cannot express the full decision space.

### 3. Are all assumptions verified or explicitly acknowledged?

The following assumptions are **verified** (by existing implementation and tests):
- Four result records are immutable and already produced by `compute*()` methods on `PrimitiveLayout` and `RelationLayout` (RDR-009 implemented).
- `MeasureResult.extractor` is a lambda and is not Jackson-serializable; Model A excludes it correctly because data extraction is server-side.
- `ColumnSet` is mutable during `compress()`; `ColumnSetSnapshot`/`ColumnSnapshot` records provide the correct post-compress capture.
- `distributeExtraHeight` runs after the main pipeline; for Model A, it should complete before `snapshotLayoutResult()` is called so that `HeightResult` reflects final geometry.

The following assumptions are **explicitly acknowledged but unverified**:
- `LayoutDecisionNode` contains sufficient information for a non-JavaFX renderer to produce visually equivalent output without back-references to `SchemaNodeLayout`. This is the central protocol completeness assumption and is not proven until Phase 3.
- A configuration-based `MeasurementStrategy` (40+ explicit inset values, no JavaFX toolkit) will produce layouts close enough to the JavaFX-measured baseline to be acceptable for non-JavaFX targets. The acceptable tolerance has not been defined.
- `RelationRenderMode`/`PrimitiveRenderMode` enums (RDR-015) will cover the full set of render modes needed by Phase 2 without requiring further `LayoutResult` changes.

### 4. What is the rollback strategy if this fails?

Phase 1 (extracting `LayoutDecisionNode`) is read-only relative to the existing pipeline — it adds a snapshot method without modifying any `compute*()` method or altering pipeline execution order. If Phase 1 produces incorrect geometry in the snapshot tree, the existing pipeline and JavaFX renderer are unaffected. Rollback is removing the snapshot method and the `LayoutDecisionNode` record.

Phase 2 (refactoring `buildControl()` to consume `LayoutDecisionNode`) carries the first non-trivial rollback cost — if `JavaFxLayoutRenderer` cannot faithfully reproduce current rendering, the `buildControl()` path must be restored. The mitigation is maintaining the existing `buildControl()` implementation in parallel until the `JavaFxLayoutRenderer` passes all 142+ tests. Only then is the old path removed.

Phase 3 (HTML renderer) is entirely new code with no rollback concern for existing functionality. If the HTML renderer cannot match JavaFX output, the acceptance test fails and Phase 3 is incomplete, but JavaFX rendering is unaffected.

Phase 4 (headless measurement) is the highest-risk phase and should be treated as independently reversible. It is blocked on RDR-010 C3/S2 and should not begin until Phases 1–3 are stable. If Phase 4 proves impractical (e.g., the 40+ inset measurement surface requires too many platform-specific calibration values), the project can stop at Phase 3 — a cross-platform JSON protocol with server-side JavaFX measurement is still a meaningful outcome.

### 5. Are there cross-cutting concerns with other RDRs?

**RDR-009** (MeasureResult immutability): Implemented and closed. The four result records this RDR depends on are in place. No conflict.

**RDR-008** (pipeline phases): Implemented and closed. The three-phase pipeline structure this RDR assumes is established. No conflict.

**RDR-010** (CSS integration remediation): Active dependency. Phase 4 is blocked on RDR-010 C3 (LayoutStylesheet wiring for algorithm-tuning parameters) and RDR-010 S2 (protected measurement methods). If RDR-010 is descoped or delayed, Phase 4 is correspondingly delayed. Phases 1–3 have no dependency on RDR-010.

**RDR-015** (alternative rendering modes): Phase 2 is blocked on RDR-015 defining `RelationRenderMode` and `PrimitiveRenderMode` enums. If RDR-015 is revised to use a different representation (e.g., a string tag rather than enums), `LayoutResult` must be updated accordingly. The `LayoutDecisionNode` serialized form is affected. This cross-RDR interface should be locked before Phase 2 begins.

**RDR-012** (reactive semantic constraint layout), **RDR-016** (layout stability / incremental update), **RDR-018** (query-semantic layout stylesheet): These RDRs extend or constrain the layout pipeline. Any pipeline change they introduce must preserve the post-convergence snapshot contract.

**RDR-016 convergence invariant**: `snapshotLayoutResult()` is only callable when `isConverged() == true` for all nodes in the subtree. Partial pipeline re-runs (triggered by incremental update) must suppress snapshot capture until convergence is restored across the full subtree. A `LayoutDecisionNode` produced during a partial re-run is undefined and must not be consumed by renderers or cached.

---

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
