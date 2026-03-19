---
title: "Tufte Sparkline Rendering Mode"
id: RDR-019
type: Feature
status: closed
close_reason: "implemented (all 4 phases complete, zero deferred work)"
priority: P2
author: Hal Hildebrand
created: 2026-03-15
accepted_date: 2026-03-16
closed_date: 2026-03-16
reviewed-by: self
---

# RDR-019: Tufte Sparkline Rendering Mode

## Metadata
- **Type**: Feature
- **Status**: closed
- **Priority**: P2
- **Created**: 2026-03-15
- **Accepted**: 2026-03-16
- **Closed**: 2026-03-16
- **Close-reason**: implemented (all 4 phases complete, zero deferred work)
- **Reviewed-by**: self
- **Related**: RDR-015 (Alternative Rendering Modes — defers sparkline), RDR-009 (MeasureResult), RDR-011 (Layout Protocol)

## Background: Tufte Sparklines

Edward Tufte introduced sparklines in "Beautiful Evidence" (2006) as "intense, simple, word-sized graphics" — tiny inline charts that convey the shape of a data series without axes, labels, or chrome. Key design principles:

- **Word-sized**: Same height as surrounding text, flows inline
- **High data density**: Maximum data-ink ratio, zero chart-junk
- **Contextual**: The sparkline IS the value — not a separate visualization but an inline data representation
- **Band reference**: Optional gray band showing normal range (e.g., interquartile range)
- **End markers**: Dot on the last (current) value, optionally on min/max

Tufte's sparklines are the most information-dense visualization for time-series and sequential numeric data. They are ideal for Kramer's nested table cells where space is constrained.

---

## Problem Statement

Kramer's `Primitive` currently assumes scalar JSON values (`JsonNode` -> text label). For fields that are naturally sequences (time-series metrics, historical prices, score progressions), a text representation like `[1.2, 3.4, 2.1, 5.6, 4.3]` wastes space and is cognitively expensive to parse.

RDR-015 identified sparkline as a rendering mode but correctly deferred it because it requires **array-valued Primitives** — an extension to the current scalar-only model. This RDR addresses both the schema-layer extension and the rendering implementation.

---

## Schema-Layer Extension: Array-Valued Primitives

### Current State

`Primitive` extends `SchemaNode` and is `final`. It carries only a `defaultWidth` field and a `label` inherited from `SchemaNode`. The `measure()` pipeline in `PrimitiveLayout` treats each datum as a scalar: `style.width(row)` measures its text width. When `prim.isArray()` is true in `PrimitiveLayout.measure()`, it handles cardinality (multi-valued list display via `PrimitiveList`) but does NOT extract numeric series — it averages text widths across array elements and delegates to `PrimitiveList` for rendering.

### Proposed Extension

Array-vs-scalar detection is handled entirely in `PrimitiveLayout.measure()` renderMode logic, consistent with BAR/BADGE. No schema-layer change needed.

Detection: When a `JsonNode` datum is an `ArrayNode` AND `allElementsNumeric(datum)` is true (consistent with BAR detection), the layout qualifies for SPARKLINE rendering mode. This can be auto-detected during `PrimitiveLayout.measure()` or set explicitly via `LayoutStylesheet`.

---

## Sparkline Rendering Design

### MeasureResult Extension

Following the sub-record pattern established in `MeasureResult` (which already uses `List<MeasureResult> childResults` for composite state):

```java
record SparklineStats(
    double seriesMin,        // global minimum across all rows
    double seriesMax,        // global maximum across all rows
    int maxSeriesLength,     // longest series (determines data point count)
    double q1,               // 25th percentile (for Tufte band)
    double q3                // 75th percentile (for Tufte band)
    // lastValue is NOT here — it is per-row and computed per-cell in updateItem()
    // via values[values.length - 1]. SparklineStats holds only global aggregates.
) {}
```

Nullable on `MeasureResult` — only populated when `renderMode == SPARKLINE`. This follows the nullable sub-record pattern established by RDR-013 (`ContentWidthStats`) and adopted by RDR-015 (`NumericStats`, `PivotStats`).

`q1` and `q3` are computed across all series values flattened across all rows (not per-series medians). This is simpler and more Tufte-correct — the band shows where individual data points fall globally.

### Sparkline Cell Implementation

A `PrimitiveSparklineStyle` (following the `PrimitiveTextStyle`/`PrimitiveBarStyle` subclass pattern from RDR-015):

```java
public class PrimitiveSparklineStyle extends PrimitiveStyle {
    // Tufte sparkline parameters (all via LayoutStylesheet):
    // "sparkline-band-visible" — boolean, show IQR band (default: true)
    // "sparkline-end-marker" — boolean, show dot on last value (default: true)
    // "sparkline-min-max-markers" — boolean, show dots on min/max (default: false)
    // "sparkline-line-width" — double, stroke width in pixels (default: 1.0)
    // "sparkline-band-opacity" — double, IQR band fill opacity (default: 0.15)

    // NOTE: Real signature is build(FocusTraversal<?> pt, PrimitiveLayout p).
    // Data arrives later via updateItem(JsonNode). The cell must render the
    // sparkline inside updateItem(), not build(). Pseudocode below shows the
    // rendering logic that goes in the cell's updateItem() method.

    @Override
    public LayoutCell<?> build(FocusTraversal<?> pt, PrimitiveLayout layout) {
        return new SparklineCell(layout, pt);
    }

    // Inside SparklineCell.updateItem(JsonNode item):
    //   if (!item.isArray()) { fallbackToText(item); return; }
    //   double[] values = extractNumericSeries(item);
    //   SparklineStats stats = layout.getMeasureResult().sparklineStats();
    //
    //   Polyline line = new Polyline();
    //   // x: evenly spaced across justifiedWidth
    //   // y: scaled between seriesMin -> seriesMax within cellHeight
    //
    //   Rectangle band = new Rectangle(0, yQ3, width, yQ1 - yQ3);
    //   band.setOpacity(bandOpacity);
    //
    //   Circle endDot = new Circle(xLast, yLast, 1.5);
    //   getChildren().setAll(band, line, endDot);
}
```

### Height: Word-Sized

Per Tufte: sparkline height = one line of text height. This matches the existing `computeCellHeight()` for a single-line label — `PrimitiveLayout.computeCellHeight()` calls `snap(style.getHeight(mw, justified))` which returns one label line height when content fits. No height computation changes needed. The sparkline fits in the same vertical space as a text value.

### Width: Full Justified Width

`PrimitiveSparklineStyle.width()` returns `0.0` (following `PrimitiveBarStyle` precedent). The sparkline fills `justifiedWidth` from the compress/justify pipeline. A `sparkline-min-width` stylesheet property (default: `5 * cellHeight`) ensures readable aspect ratio — columns narrower than this minimum are padded to that width before sparkline rendering.

---

## Implementation Phases

### Phase 1: Schema Detection & MeasureResult (LOW-MEDIUM effort)

1. In `PrimitiveLayout.measure()`, detect NUMERIC_SERIES: `datum.isArray() && allElementsNumeric(datum)` (no schema-layer change; detection is in the layout layer consistent with BAR/BADGE)
2. Add `case SPARKLINE` to the `renderModeOverride` switch in `PrimitiveLayout.measure()` so explicit stylesheet opt-in is handled
3. When NUMERIC_SERIES detected, accumulate `SparklineStats` (min, max, quartiles, series length) across all rows in the measurement sample; percentiles computed across all values flattened (not per-series medians)
4. Add `SparklineStats` as nullable sub-record on `MeasureResult`
5. Add `"render-mode": "sparkline"` as LayoutStylesheet property (explicit opt-in or auto-detected)

### Phase 2: Sparkline Cell Rendering (MEDIUM effort)

1. Create `PrimitiveSparklineStyle` extending `PrimitiveStyle`; `width()` returns `0.0` (matches `PrimitiveBarStyle`)
2. Implement `build()` with `Polyline` + optional `Rectangle` band + optional `Circle` markers; `lastValue` read from `values[values.length - 1]` in `updateItem()`, not from `SparklineStats`
3. Wire through `Style` factory: when `renderMode == SPARKLINE`, create `PrimitiveSparklineStyle`. **SPARKLINE must preempt the `averageCardinality > 1` guard in `buildControl()`. Check `renderMode == SPARKLINE` BEFORE the cardinality check. Alternatively, set `averageCardinality = 1` when NUMERIC_SERIES is detected during `measure()` (the array IS the value, not multiple values).**
4. Add `cachedSparklineStyle = null` to `PrimitiveLayout.clear()` alongside `cachedBarStyle` and `cachedBadgeStyle`
5. CSS: `sparkline.css` co-located stylesheet for stroke color, band color, marker color
6. Downsampling: when series length exceeds horizontal pixel count, reduce to one point per pixel

### Phase 3: LayoutStylesheet Properties (LOW effort, after RDR-018 Phase 1)

1. Register sparkline-specific properties in the LayoutStylesheet property registry
2. Wire `sparkline-band-visible`, `sparkline-end-marker`, `sparkline-min-max-markers`, `sparkline-line-width`, `sparkline-band-opacity`

### Phase 4: LayoutDecisionNode Integration (LOW effort, after RDR-011)

1. `PrimitiveRenderMode.SPARKLINE` in `LayoutResult` (per RDR-015's enum)
2. `SparklineStats` serialized in `MeasureResult` for cross-process rendering
3. HTML sparkline renderer: SVG `<polyline>` + `<rect>` for band + `<circle>` for markers

---

## Interaction with Existing Architecture

**Sealed hierarchy**: No change needed. `Primitive` is `final`, not sealed — no schema-layer field added. `PrimitiveLayout` is one of two permitted `SchemaNodeLayout` subtypes — sparkline rendering is a dispatch within `PrimitiveLayout.buildControl()`, not a new layout type.

**VirtualFlow**: Sparkline cells are the same height as text cells (word-sized). No impact on `SizeTracker` uniform-length assumption.

**autoFold**: No interaction — sparkline operates on leaf Primitives, autoFold operates on Relations.

**Existing measure pipeline**: `PrimitiveLayout.measure()` already branches on `prim.isArray()` (lines 162-177). The NUMERIC_SERIES detection slots into this existing branch — instead of averaging text widths for array elements, it collects numeric statistics. The branching point already exists.

**RDR-015 coordination**: RDR-015 defers sparkline to this RDR. RDR-015's `PrimitiveRenderMode` enum should include `SPARKLINE` once this RDR is accepted. The `PrimitiveSparklineStyle` subclass follows the same pattern as `PrimitiveBarStyle`.

**RDR-009 coordination**: `MeasureResult` gains a nullable `SparklineStats` sub-record. This follows the nullable sub-record pattern established by RDR-013 (`ContentWidthStats`) and adopted by RDR-015 (`NumericStats`, `PivotStats`).

**RDR-013 coordination**: Statistical content width measurement can inform sparkline column width allocation — wider columns give sparklines more horizontal resolution. The statistical width engine from RDR-013 naturally benefits sparkline readability.

---

## Alternatives Considered

### Alt A: Embed Sparklines as Inline SVG in a WebView

Each sparkline cell hosts a `javafx.scene.web.WebView` rendering an SVG `<polyline>` element inline.

**Pros:**
- SVG sparklines are the web standard; the rendering model is well-understood
- All visual parameters (stroke, fill, opacity) are trivially controlled via inline CSS
- No JavaFX shape coordinate mapping required — SVG viewBox handles scaling

**Cons:**
- `WebView` carries a full Chromium/WebKit embedded browser; memory cost per cell is prohibitive in a virtualized list with hundreds of cells
- `WebView` initialization is asynchronous; VirtualFlow recycle-and-update cycles will produce visible blank frames
- No integration with JavaFX CSS theming or `LayoutStylesheet` property resolution
- Eliminates the RDR-011 layout protocol advantage: JavaFX and HTML renderers would share no code path, requiring two independent sparkline implementations

**Rejection reason:** Memory and initialization overhead in a virtualized cell context is unacceptable. The VirtualFlow recycles cells continuously; a WebView per cell destroys performance.

---

### Alt B: Third-Party Charting Library (JFreeChart, TilesFX, or similar)

Use an existing charting library to produce sparkline charts embedded as `ImageView` snapshots or as native JavaFX `Node` subtrees.

**Pros:**
- Mature rendering with well-tested edge cases (nulls, ragged series, one-point degenerate series)
- Library may already handle downsampling and IQR computation
- Faster initial implementation if the library provides a `SparklineChart` control

**Cons:**
- JFreeChart is a Swing/AWT library; embedding in JavaFX requires `SwingNode`, introducing thread-boundary issues and blurry HiDPI rendering
- TilesFX and other JavaFX chart libraries are full-chart oriented, not word-sized sparkline oriented — minimum height exceeds one text line
- Any third-party dependency for a leaf cell rendering task violates the spartan dependency principle; the actual JavaFX primitive required (`Polyline`, `Rectangle`, `Circle`) is three classes from the JDK
- External libraries cannot participate in Kramer's `LayoutStylesheet` property system without adapter wrappers that cost more than writing it directly

**Rejection reason:** No available library provides word-sized, text-height sparklines suitable for VirtualFlow cells. The implementation using raw JavaFX shapes is simpler and smaller than any adapter layer would be.

---

### Alt C: Server-Side Pre-Rasterized Images (PNG in ImageView)

Render sparklines server-side as PNG images, embed via `ImageView` in the cell. The server computes sparkline pixels from the same JSON data and includes the PNG URL or base64 in the payload.

**Pros:**
- Completely decouples rendering from client-side JavaFX version or platform
- Reuses existing image loading infrastructure (`ImageView` is already a first-class JavaFX node)
- Renders identically across all client platforms (desktop, HTML via `<img>`)

**Cons:**
- Requires round-trips to a rendering service; incompatible with offline operation and local file data sources that Kramer must support
- Blurs the boundary between data and presentation: PNG pixels in JSON payloads are not semantically typed; the schema layer cannot inspect or adapt them
- Resolution mismatch: rasterized at server's assumed DPI, not the client's actual DPI — sparklines will be blurry on HiDPI displays unless the server knows the client density
- Eliminates `SparklineStats` from `MeasureResult`; the layout engine loses the ability to normalize min/max across rows, breaking the column-global normalization that makes sparklines meaningful for comparison

**Rejection reason:** Incompatible with Kramer's local-data model and column-global normalization requirement. Column normalization (same y-scale across all rows) is impossible when each row's sparkline is pre-rendered independently server-side.

---

## Finalization Gate

### 1. Has the problem been validated with real user scenarios?

Partially. The core scenario — numeric time-series fields displayed as text arrays like `[1.2, 3.4, 2.1]` in nested table cells — appears in real GraphQL APIs (metric histories, price series, score progressions) that the explorer app targets. RDR-015 identified sparkline as a user-facing need independently of this RDR, providing corroboration from a separate design review. The RDR has not been validated against a live user study; the evidence is architecture-driven inference. This is explicitly acknowledged as an assumption.

### 2. Is the solution the simplest that could work?

Yes. The implementation uses three JavaFX shape primitives (`Polyline`, `Rectangle`, `Circle`) that are already in the JDK. No new dependencies. No schema-layer change is needed — array-vs-scalar detection is handled entirely in `PrimitiveLayout.measure()` with the predicate `datum.isArray() && allElementsNumeric(datum)`, consistent with BAR/BADGE. The critical research finding (019-research-3, RF-2) confirms the branching point already exists in `PrimitiveLayout.measure()` at the `isArray()` branch — this is an extension of an existing dispatch, not a new one. `SparklineStats` is a 4th nullable sub-record on `MeasureResult`, following the identical pattern of `ContentWidthStats` (RDR-013).

### 3. Are all assumptions verified or explicitly acknowledged?

Verified via post-Wave2 research (019-research-1 through 019-research-7):

- **`PrimitiveRenderMode.SPARKLINE` is a non-breaking enum extension** — confirmed via research. Adding to a `switch` without a sparkline arm leaves existing behavior unchanged.
- **`PrimitiveSparklineStyle` follows `PrimitiveBarStyle` inner class pattern** — confirmed. The subclass pattern is established and the extension point is `buildControl()` + cached style, not `computePrimitiveStyle()`.
- **Numeric detection must bifurcate**: `datum.isArray() && allElementsNumeric(datum)` → SPARKLINE; scalar → BAR. This bifurcation is critical and confirmed as a correctness requirement in research. `allElementsNumeric` is consistent with BAR detection semantics.
- **`SparklineStats` is the 4th nullable sub-record** (after `ContentWidthStats`, `NumericStats`, `PivotStats`) — position confirmed.
- **`build()` signature is `(FocusTraversal<?>, PrimitiveLayout)`; data arrives via `updateItem()`** — confirmed. Sparkline rendering logic must live in `updateItem()`, not `build()`.
- **`cachedSparklineStyle` must be null-reset in `clear()`** — confirmed as required to prevent stale cached style on cell recycle. Added to Phase 2 implementation steps.
- **`SPARKLINE` must preempt `averageCardinality > 1` guard in `buildControl()`** — the array IS the value, not multiple values. Check `renderMode == SPARKLINE` before the cardinality guard. Added to Phase 2 implementation steps.

Unverified assumption: that `Polyline` performance is adequate for series >500 points within VirtualFlow recycle timing. The downsampling mitigation (one point per horizontal pixel) is documented in the Risks table and is standard practice, but has not been benchmarked in this codebase.

### 4. What is the rollback strategy if this fails?

The feature is strictly opt-in. `PrimitiveRenderMode.SPARKLINE` is only activated when either (a) the `LayoutStylesheet` explicitly sets `render-mode: sparkline` for a path, or (b) auto-detection is enabled and a field qualifies. No existing code path is altered; the `isArray()` branch gains a new sub-branch but the existing `PrimitiveList` path remains reachable. Rollback is: remove the `SPARKLINE` arm from the render mode switch, delete `PrimitiveSparklineStyle` and `SparklineStats`, drop the nullable field from `MeasureResult`. Because `SparklineStats` is nullable and `PrimitiveRenderMode.SPARKLINE` is an additive enum value, removing them requires only targeted deletions with no ripple through existing callers. The feature is isolatable. No `PrimitiveValueType` schema-layer enum was introduced, so there is no schema migration to roll back.

### 5. Are there cross-cutting concerns with other RDRs?

Yes, three active dependencies:

- **RDR-015 (Alternative Rendering Modes)**: `PrimitiveRenderMode.SPARKLINE` must be added to RDR-015's enum once this RDR is accepted. RDR-015 explicitly defers sparkline here. The `PrimitiveSparklineStyle` subclass pattern is defined in RDR-015 — both RDRs must be accepted together or sequentially with RDR-015 first.
- **RDR-009 (MeasureResult / Stylesheet Data Structure)**: `SparklineStats` is a new nullable sub-record on `MeasureResult`. This is a data structure change gated on RDR-009's `MeasureResult` definition being stable. If RDR-009 revises the sub-record pattern, `SparklineStats` placement may shift.
- **RDR-018 (Query Semantic Layout Stylesheet)**: Phase 3 of this RDR (LayoutStylesheet properties for sparkline parameters) is gated on RDR-018 Phase 1 establishing the property registry. Sparkline visual parameters (`sparkline-band-visible`, `sparkline-line-width`, etc.) cannot be wired until the property registration mechanism exists.
- **RDR-013 (Statistical Content Width)**: No blocking dependency, but the statistical width engine from RDR-013 directly benefits sparkline column widths — wider columns improve horizontal resolution. Coordination is additive, not required for Phase 1-2.

---

## Research Findings

### RF-1: Tufte Sparklines Are Word-Sized by Design (Confidence: HIGH)

Tufte's original specification: "Sparklines are small enough to be embedded in a line of text, or in a cell of a table or spreadsheet." This maps to Kramer's `PrimitiveLayout.computeCellHeight()` which returns `snap(style.getHeight(mw, justified))` — one label line height. No height computation changes needed.

### RF-2: Array-Valued Primitives Are Already Partially Handled (Confidence: HIGH)

`PrimitiveLayout.measure()` already has an `isArray()` branch (lines 162-177) for cardinality-based multi-value display via `PrimitiveList`. The extension is adding a NUMERIC_SERIES classification that triggers sparkline rendering instead of list rendering. The branching point already exists in the measure pipeline.

### RF-3: JavaFX Polyline Is Sufficient (Confidence: HIGH)

`javafx.scene.shape.Polyline` takes a flat double array of x,y pairs, renders with anti-aliasing, and supports stroke width/color via CSS. No custom canvas rendering needed. For typical series lengths (10-200 points), Polyline is performant within a VirtualFlow cell.

### RF-4: SVG Sparklines Are the Web Standard (Confidence: HIGH)

For RDR-011's HTML renderer, sparklines map to SVG `<polyline>` (line), `<rect>` (band), `<circle>` (markers). The same `SparklineStats` from `MeasureResult` drives both JavaFX and SVG rendering — the layout protocol works cleanly for this mode.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Non-numeric array elements mixed into series | Low | Skip non-numeric elements; if >50% non-numeric, fall back to text |
| Very long series (>1000 points) degrade Polyline performance | Low | Downsample to viewport-pixel-count points (one point per horizontal pixel) |
| Null values in series create gaps | Medium | Use NaN convention: gap in Polyline, or interpolate linearly |
| Series length varies across rows (ragged data) | Low | `SparklineStats.maxSeriesLength` normalizes x-axis scaling across rows |
| Quartile computation on small series (<4 values) | Low | Suppress band when series.length < 4; show line only |

---

## Success Criteria

1. Array-valued numeric fields render as word-sized inline sparklines
2. Sparkline height matches surrounding text height (Tufte word-sized principle)
3. IQR band visible by default, showing typical value range
4. End marker (dot on last value) visible by default
5. Min/max values globally normalized across all rows in the column
6. LayoutStylesheet controls all visual parameters per SchemaPath
7. Non-numeric arrays fall back gracefully to text/list rendering
8. HTML renderer produces equivalent SVG sparklines from same MeasureResult
9. All existing tests pass (sparkline is opt-in)

---

## Recommendation

Phase 1 (detection in `PrimitiveLayout.measure()` + MeasureResult) is the prerequisite. Phase 2 (rendering) delivers the visual feature. Phases 3-4 are integration with the broader roadmap. Total effort: MEDIUM across all phases. The layout-layer detection (no schema change) is simpler than the previously considered `PrimitiveValueType` enum approach — once NUMERIC_SERIES detection is wired in `measure()`, the rendering is straightforward JavaFX shape work.

This is the highest data-density visualization mode Kramer can offer. Tufte sparklines in nested table cells, with adaptive layout deciding column widths from statistical content measurement (RDR-013), is a combination no other layout system provides.
