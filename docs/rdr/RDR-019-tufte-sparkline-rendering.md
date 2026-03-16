# RDR-019: Tufte Sparkline Rendering Mode

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P2
- **Created**: 2026-03-15
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

Add a `valueType` classification to `Primitive`:

```java
public enum PrimitiveValueType {
    SCALAR,         // current behavior — single JSON value
    NUMERIC_SERIES  // ordered sequence of numbers (sparkline-compatible)
}
```

On `Primitive`:
```java
private PrimitiveValueType valueType = PrimitiveValueType.SCALAR;
```

Detection: When a `JsonNode` datum is an `ArrayNode` AND all elements are numeric (`isNumber()`), the Primitive qualifies as `NUMERIC_SERIES`. This can be auto-detected during `PrimitiveLayout.measure()` or set explicitly via `LayoutStylesheet`.

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
    double q3,               // 75th percentile (for Tufte band)
    double lastValue         // most recent value (for end marker)
) {}
```

Nullable on `MeasureResult` — only populated when `renderMode == SPARKLINE`. This follows the nullable sub-record pattern established by RDR-013 (`ContentWidthStats`) and adopted by RDR-015 (`NumericStats`, `PivotStats`).

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

    @Override
    public Node build(JsonNode data, PrimitiveLayout layout) {
        if (!data.isArray()) return fallbackToText(data, layout);

        double[] values = extractNumericSeries(data);
        SparklineStats stats = layout.getMeasureResult().sparklineStats();

        // Create Polyline scaled to cell dimensions:
        // x: evenly spaced across justifiedWidth
        // y: scaled between seriesMin -> seriesMax within cellHeight
        Polyline line = new Polyline();
        // ... scale and populate points

        // Tufte band (IQR reference):
        Rectangle band = new Rectangle(0, yQ3, width, yQ1 - yQ3);
        band.setOpacity(bandOpacity);

        // End marker:
        Circle endDot = new Circle(xLast, yLast, 1.5);

        return new StackPane(band, line, endDot);
    }
}
```

### Height: Word-Sized

Per Tufte: sparkline height = one line of text height. This matches the existing `computeCellHeight()` for a single-line label — `PrimitiveLayout.computeCellHeight()` calls `snap(style.getHeight(mw, justified))` which returns one label line height when content fits. No height computation changes needed. The sparkline fits in the same vertical space as a text value.

### Width: Full Justified Width

Sparklines use the full `justifiedWidth` allocated to the column. More width = more horizontal resolution = better shape representation. No special width computation needed — the existing `compress()` and `justify()` pipeline provides the width.

---

## Implementation Phases

### Phase 1: Schema Detection & MeasureResult (LOW-MEDIUM effort)

1. Add `PrimitiveValueType` enum and field to `Primitive`
2. In `PrimitiveLayout.measure()`, detect NUMERIC_SERIES: `datum.isArray() && allElementsNumeric(datum)`
3. When NUMERIC_SERIES detected, accumulate `SparklineStats` (min, max, quartiles, series length) across all rows in the measurement sample
4. Add `SparklineStats` as nullable sub-record on `MeasureResult`
5. Add `"render-mode": "sparkline"` as LayoutStylesheet property (explicit opt-in or auto-detected)

### Phase 2: Sparkline Cell Rendering (MEDIUM effort)

1. Create `PrimitiveSparklineStyle` extending `PrimitiveStyle`
2. Implement `build()` with `Polyline` + optional `Rectangle` band + optional `Circle` markers
3. Wire through `Style` factory: when `renderMode == SPARKLINE`, create `PrimitiveSparklineStyle`
4. CSS: `sparkline.css` co-located stylesheet for stroke color, band color, marker color
5. Downsampling: when series length exceeds horizontal pixel count, reduce to one point per pixel

### Phase 3: LayoutStylesheet Properties (LOW effort, after RDR-018 Phase 1)

1. Register sparkline-specific properties in the LayoutStylesheet property registry
2. Wire `sparkline-band-visible`, `sparkline-end-marker`, `sparkline-min-max-markers`, `sparkline-line-width`, `sparkline-band-opacity`

### Phase 4: LayoutDecisionNode Integration (LOW effort, after RDR-011)

1. `PrimitiveRenderMode.SPARKLINE` in `LayoutResult` (per RDR-015's enum)
2. `SparklineStats` serialized in `MeasureResult` for cross-process rendering
3. HTML sparkline renderer: SVG `<polyline>` + `<rect>` for band + `<circle>` for markers

---

## Interaction with Existing Architecture

**Sealed hierarchy**: No change needed. `Primitive` is `final`, not sealed — adding `PrimitiveValueType` is a field addition. `PrimitiveLayout` is one of two permitted `SchemaNodeLayout` subtypes — sparkline rendering is a dispatch within `PrimitiveLayout.buildControl()`, not a new layout type.

**VirtualFlow**: Sparkline cells are the same height as text cells (word-sized). No impact on `SizeTracker` uniform-length assumption.

**autoFold**: No interaction — sparkline operates on leaf Primitives, autoFold operates on Relations.

**Existing measure pipeline**: `PrimitiveLayout.measure()` already branches on `prim.isArray()` (lines 162-177). The NUMERIC_SERIES detection slots into this existing branch — instead of averaging text widths for array elements, it collects numeric statistics. The branching point already exists.

**RDR-015 coordination**: RDR-015 defers sparkline to this RDR. RDR-015's `PrimitiveRenderMode` enum should include `SPARKLINE` once this RDR is accepted. The `PrimitiveSparklineStyle` subclass follows the same pattern as `PrimitiveBarStyle`.

**RDR-009 coordination**: `MeasureResult` gains a nullable `SparklineStats` sub-record. This follows the nullable sub-record pattern established by RDR-013 (`ContentWidthStats`) and adopted by RDR-015 (`NumericStats`, `PivotStats`).

**RDR-013 coordination**: Statistical content width measurement can inform sparkline column width allocation — wider columns give sparklines more horizontal resolution. The statistical width engine from RDR-013 naturally benefits sparkline readability.

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

Phase 1 (schema detection + MeasureResult) is the prerequisite. Phase 2 (rendering) delivers the visual feature. Phases 3-4 are integration with the broader roadmap. Total effort: MEDIUM across all phases. The schema-layer extension (`PrimitiveValueType`) is the key design decision — once that exists, the rendering is straightforward JavaFX shape work.

This is the highest data-density visualization mode Kramer can offer. Tufte sparklines in nested table cells, with adaptive layout deciding column widths from statistical content measurement (RDR-013), is a combination no other layout system provides.
