# RDR-015: Alternative Rendering Modes — Visualization Primitives & Crosstab

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P2
- **Created**: 2026-03-15
- **Related**: SIEUFERD (SIGMOD 2016 §3.3, Fig 5), RDR-009 (LayoutStylesheet), RDR-011 (layout protocol extraction), RDR-014 (hideIfEmpty/sort — soft dependency for Phase 3 crosstab pipeline ordering)

## Problem Statement

Kramer has exactly two rendering modes: table and outline. SIEUFERD (SIGMOD 2016) describes two additional per-node rendering options that Kramer lacks:

1. **Visualization Primitives**: Numeric Primitive fields can render as bar charts instead of text labels. SIEUFERD quote: "The field COURSES TAUGHT has a formatting option enabled on it to display numbers using a bar chart visualization." This is a per-Primitive FORMAT option, not a global mode.

2. **Crosstab/Pivot**: Applied per Relation node as a formatting option. SIEUFERD quote: "All the usual query interface actions remain available from the crosstab layout." Crosstab pivots a child field's values into column headers, creating a matrix display.

Both are orthogonal per-node options that coexist with the existing table/outline decision — they are not replacements but additional choices in the same adaptive decision framework.

---

## Feature 1: Visualization Primitives

### Current State

`Primitive` always renders via `PrimitiveTextStyle` which creates a `Label`-based cell. There is no mechanism to render a Primitive differently based on its data type or user preference.

### Proposed Design

Add a `renderMode` enum to control Primitive rendering:

```java
public enum PrimitiveRenderMode {
    TEXT,       // current behavior (Label)
    BAR,        // horizontal bar chart for numeric values
    BADGE       // colored badge for categorical values (enum-like)
}
```

> **Note**: SPARKLINE (mini line chart for numeric arrays) requires array-valued Primitives, which is a schema-layer extension touching `Primitive`, `measure()`, height computation, and cell factory. This is deferred to a separate future RDR.

Wire through `PrimitiveLayout` (per-instance), NOT `PrimitiveStyle` (per-class). This ensures the same Primitive schema node can render differently in different `AutoLayout` instances. A stylesheet-specified mode takes priority and skips auto-detection entirely.

```java
// On PrimitiveLayout (per-instance, set during measure):
private PrimitiveRenderMode renderMode = PrimitiveRenderMode.TEXT;
```

A `PrimitiveBarStyle` subclass (parallel to the existing `PrimitiveTextStyle`) is preferred over switch dispatch in `buildControl()`. This follows the existing style-class pattern and keeps rendering logic cohesive:

```java
// PrimitiveBarStyle extends PrimitiveStyle
// PrimitiveTextStyle extends PrimitiveStyle  (existing)
```

`PrimitiveLayout.buildControl()` delegates to the appropriate style subclass, which is determined at measure time based on stylesheet configuration or auto-detection.

### Bar Chart: Numeric Normalization

**Problem**: `MeasureResult.maxWidth` is the maximum rendered PIXEL width of text strings (computed by `style.width(row)` in `PrimitiveLayout.measure()`). It cannot be repurposed for numeric value normalization — bars would be scaled to text pixel widths, not data values.

**Solution**: Add a `NumericStats` sub-record to `MeasureResult`, following the nullable sub-record pattern established by RDR-013:

```java
record NumericStats(double numericMin, double numericMax) {}
```

Nullable on `MeasureResult` — only populated when `renderMode == BAR`. When null, BAR rendering is not active for this Primitive.

During `PrimitiveLayout.measure()`, when `renderMode == BAR`, parse numeric values and track the range:

```java
// In PrimitiveLayout.measure(), when renderMode == BAR:
double numericMin = Double.MAX_VALUE;
double numericMax = Double.MIN_VALUE;
for (JsonNode row : normalized) {
    double val = Double.parseDouble(SchemaNode.asText(row));
    numericMin = Math.min(numericMin, val);
    numericMax = Math.max(numericMax, val);
}
NumericStats numericStats = new NumericStats(numericMin, numericMax);
```

The bar width formula:
```
barWidth = (value - numericStats.numericMin()) / (numericStats.numericMax() - numericStats.numericMin()) * cellWidth
```

The sub-record is stored in `MeasureResult` and persists across resize cycles, consistent with the existing measure-once/layout-many architecture.

### Bar Chart: Height Specification

BAR mode height = one label line height (single-line, no wrapping). This is consistent with `PrimitiveTextStyle.getHeight(maxWidth, justified)` when `maxWidth <= justified` (no wrapping needed). `computeCellHeight()` dispatches on render mode:

```java
case BAR -> snap(style.getHeight(justified, justified));  // one line: Math.ceil(justified/justified) = 1
case TEXT -> snap(style.getHeight(maxWidth, justified));  // existing wrapping logic
```

### Auto-Detection

Auto-detection runs during `PrimitiveLayout.measure()`. The detected mode is stored on the `PrimitiveLayout` instance. A stylesheet-specified mode (`render-mode` property) takes priority and skips detection.

- All values parse as `Double` → set `renderMode = BAR`
- Low cardinality (< 10 distinct values) → set `renderMode = BADGE`
- Default: `TEXT`

**LayoutStylesheet integration**:
```java
String mode = stylesheet.getString(path, "render-mode", "text");
```

All property keys defined in this RDR are registered in the central LayoutStylesheet Property Registry (see `docs/rdr/RDR-ROADMAP.md`). Property naming follows kebab-case convention.

### Height/Width Implications

- BAR: height = one label line (no wrapping), width = full justified width
- BADGE: same as TEXT (Label with background color)
- No change to the measurement pipeline structure — bar mode consumes the same cell space but renders a Rectangle instead of text

---

## Feature 2: Crosstab as 3rd RelationLayout Mode

### Current State

`RelationLayout.layout(width)` makes a binary decision:
```java
if (tableWidth + style.getNestedHorizontalInset() <= width) {
    useTable = true;   // TABLE mode
} else {
    useTable = false;  // OUTLINE mode
}
```

There is no third option.

### Proposed Design

Add crosstab as a third mode. Crosstab requires:
- A **pivot field**: one child Primitive whose distinct values become column headers
- A **value field**: one child Primitive whose values populate the cells
- Remaining children: row identifiers

```java
public enum RelationRenderMode {
    AUTO,      // current adaptive behavior (table or outline)
    TABLE,     // force table
    OUTLINE,   // force outline
    CROSSTAB   // pivot by a designated field
}
```

**Pivot field ownership**: `pivotField` and `valueField` are configured on `LayoutStylesheet` (per `SchemaPath`), NOT on the `Relation` schema node. This allows the same schema to render differently in different `AutoLayout` instances — e.g., one view shows a relation as a table, another as a crosstab.

```java
// LayoutStylesheet configuration:
String pivot = stylesheet.getString(path, "pivot-field", "");
String value = stylesheet.getString(path, "value-field", "");
```

### Pivot Values: Collection During Measure

**Problem**: `RelationLayout.layout(double width)` takes ONLY a width parameter — no `JsonNode` data. Distinct pivot values are data-dependent and cannot be computed at layout time.

**Solution**: During `RelationLayout.measure()`, when `renderMode == CROSSTAB`, collect distinct pivot values from the data and store them in `MeasureResult`:

```java
// In RelationLayout.measure(), when renderMode == CROSSTAB:
Set<String> pivotValues = new LinkedHashSet<>();
for (JsonNode row : SchemaNode.asList(datum)) {
    JsonNode pivotNode = row.get(pivotField);
    if (pivotNode != null) {
        pivotValues.add(SchemaNode.asText(pivotNode));
    }
}
```

Store the pivot data in a `PivotStats` sub-record on `MeasureResult`, following the nullable sub-record pattern established by RDR-013:

```java
record PivotStats(List<String> pivotValues, int pivotCount) {}
```

Nullable on `MeasureResult` — only populated when `renderMode == CROSSTAB`. When null, CROSSTAB rendering is not active for this Relation. `pivotCount` is `pivotValues.size()`, cached for layout calculations.

`layoutCrosstab(double width)` reads from `measureResult.pivotStats()`.

**Pipeline ordering requirement**: Pivot value collection MUST run after RDR-014's filter step. The canonical pipeline order is: `sort -> filter (hideIfEmpty) -> collect pivot values -> measure children`. If pivots are collected before filtering, column headers may be generated for rows that hideIfEmpty would exclude — creating column headers with no corresponding data rows.

Updated `MeasureResult` (using sub-records, not inline fields):
```java
public record MeasureResult(
    double labelWidth,
    double columnWidth,
    double dataWidth,
    double maxWidth,
    int averageCardinality,
    boolean isVariableLength,
    int averageChildCardinality,
    int maxCardinality,
    Function<JsonNode, JsonNode> extractor,
    List<MeasureResult> childResults,
    // Mode-specific sub-records (nullable, per RDR-013 extension pattern):
    ContentWidthStats contentStats,  // RDR-013: statistical width data (Primitive)
    NumericStats numericStats,       // RDR-015: BAR mode range (Primitive)
    PivotStats pivotStats            // RDR-015: CROSSTAB pivot columns (Relation)
) { ... }
```

Each sub-record is null when its mode is inactive. See RDR-013 "MeasureResult Extension Strategy" for the canonical nullability contract.

### Sparse Matrix Handling

Real data has variable-cardinality pivot fields: row A may have {Q1, Q2, Q3}, row B may have {Q2, Q4}. This creates a sparse matrix.

Rules:
1. **Column set = union** of all pivot values across ALL rows, computed during `measure()`. The `pivotStats.pivotValues()` list contains this union, in insertion order.
2. **Missing intersections** render as empty cells. The `CrosstabCell` at a (row, pivotValue) intersection where no data exists renders as a blank region.
3. **Data changes trigger full remeasure**. If new data introduces previously-unseen pivot values, the pivot column set is stale. Any data update (`AutoLayout.setData()`) must re-run `measure()`, which recomputes the union. This is consistent with the existing measure-on-data-change contract.

### Layout Decision

```java
if (renderMode == CROSSTAB && measureResult.pivotStats() != null
        && !measureResult.pivotStats().pivotValues().isEmpty()) {
    return layoutCrosstab(width);
} else if (renderMode == TABLE || (renderMode == AUTO && tableWidth + insets <= width)) {
    return nestTableColumn(indent, insets);
} else {
    return columnWidth();
}
```

**Forced TABLE/OUTLINE guard**: When `renderMode == TABLE` forces `useTable = true` but `tableWidth + insets > width`, `justifyTable()` can produce invalid (negative) widths. Guard: forced TABLE falls back to OUTLINE when width is genuinely insufficient (`justifiedWidth` would be less than `tableColumnWidth`). Document this as intentional degradation:

```java
if (renderMode == TABLE) {
    double minTableWidth = calculateTableColumnWidth() + style.getNestedHorizontalInset();
    if (minTableWidth <= width) {
        return nestTableColumn(indent, insets);
    }
    // Insufficient width — degrade to outline
}
```

### Crosstab Layout

```java
private double layoutCrosstab(double width) {
    List<String> pivotValues = measureResult.pivotStats().pivotValues();
    // 1. Row header width = sum of non-pivot, non-value child column widths
    // 2. Each pivot value column gets equal share of (width - rowHeaderWidth)
    // 3. Compute CrosstabHeader from pivotValues
    // 4. Compute per-row cell structure
    return width;
}
```

### CrosstabHeader Placement: Outside VirtualFlow

**Problem**: `SizeTracker` (line 55–74 of `SizeTracker.java`) uses a single `cellLengthVar` for all cells. Mixing a CrosstabHeader with data rows inside the VirtualFlow would break scroll estimation because the header has a different height than data rows.

**Solution**: CrosstabHeader is placed OUTSIDE the VirtualFlow, exactly as `NestedTable` places its `TableHeader` outside. Reference the `NestedTable` constructor pattern (lines 61–70):

```java
// NestedTable pattern — header outside VirtualFlow:
Region header = layout.buildColumnHeader();           // header region
rows = new NestedRow(snap(height - headerHeight), ...); // VirtualFlow for data rows
getChildren().addAll(header, rows);                   // header + rows as siblings
```

CrosstabTable follows the same structure:
```java
Region crossTabHeader = layout.buildCrossTabHeader();  // pivot column labels
double dataHeight = height - crossTabHeader.prefHeight(-1);
rows = new CrosstabRow(snap(dataHeight), ...);         // VirtualFlow for data only
getChildren().addAll(crossTabHeader, rows);
```

Height available for data rows = total height - `crossTabHeader.getHeight()`.

### New Rendering Components

- `CrosstabHeader` — extends existing `ColumnHeader` with dynamic columns from `measureResult.pivotStats().pivotValues()`
- `CrosstabCell` — renders the value at intersection of row identifier × pivot value
- `CrosstabRow` — a row in the crosstab VirtualFlow (structurally parallel to `NestedRow`)

---

## MeasureResult Extension Pattern

This RDR follows the nullable sub-record pattern established by RDR-013 ("MeasureResult Extension Strategy") for all mode-specific additions to `MeasureResult`.

**Pattern**: Each rendering mode that requires data-dependent state during measure contributes a self-contained sub-record, attached to `MeasureResult` as a single nullable field. The sub-record is null when its mode is inactive.

This RDR contributes two sub-records:
- `NumericStats numericStats` — populated only when `renderMode == BAR` (Primitive). Contains numeric range for bar normalization.
- `PivotStats pivotStats` — populated only when `renderMode == CROSSTAB` (Relation). Contains distinct pivot column values and count.

Future RDR-019 (SPARKLINE) will contribute `SparklineStats sparklineStats` following the same pattern.

**All mode-specific MeasureResult additions MUST follow this nullable sub-record pattern.** Inline fields (e.g., `double numericMin, double numericMax` as top-level `MeasureResult` parameters) are prohibited — they pollute the record signature, create ambiguous nullability, and make the record constructor increasingly unwieldy as modes are added.

---

## Joint Design with RDR-011: Render Mode Protocol

The `PrimitiveRenderMode` and `RelationRenderMode` enums defined in this RDR must be added as fields on `LayoutResult` (defined in RDR-011's `LayoutDecisionNode` composition), not just as instance fields on `PrimitiveLayout`/`RelationLayout`.

**Why**: RDR-011 extracts a `LayoutDecisionNode` tree that composes four immutable result records (`MeasureResult`, `LayoutResult`, `CompressResult`, `HeightResult`). This decision tree is the sole input to any `LayoutRenderer<T>` implementation. If render mode is stored only on the mutable layout objects, the decision tree is incomplete — renderers would need back-references to `SchemaNodeLayout`, which defeats protocol extraction.

**Extended `LayoutResult` signature** (replaces `boolean useTable`):

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

**Consumer**: RDR-011's `LayoutRenderer<T>` interface dispatches on these enums. For example, an HTML renderer would emit `<table>` for TABLE, `<details>` for OUTLINE, and a pivot grid for CROSSTAB. A PDF renderer would dispatch on the same enums to produce the corresponding PDF table structures. Any new renderer targets these enums without knowing the layout algorithm internals.

**Backward compatibility**: `relationMode == TABLE` is equivalent to the old `useTable == true`. `primitiveMode` defaults to `TEXT` for all existing Primitive nodes. No behavioral change for code that does not opt into alternative modes.

**Current state of `LayoutResult.java`**: The record currently has `boolean useTable` as its first field. The migration replaces this single boolean with `RelationRenderMode relationMode` and adds `PrimitiveRenderMode primitiveMode`. All callers that check `layout.useTable()` migrate to `layout.relationMode() == TABLE`.

---

## Architectural Considerations

**Sealed hierarchy impact**: `SchemaNodeLayout` is sealed, permitting only `PrimitiveLayout` and `RelationLayout`. Crosstab does NOT require a new layout type — it's a rendering variation within `RelationLayout`, like table vs outline. The layout decision and compress phases work the same way; only `buildControl()` differs.

**VirtualFlow compatibility**: Crosstab rows are structurally similar to `NestedRow` — each row has a fixed set of cells. `VirtualFlow<CrosstabRow>` follows the same pattern as `VirtualFlow<NestedRow>`. The CrosstabHeader is placed outside the VirtualFlow (see above), so `SizeTracker`'s uniform-cell-length assumption holds for the data rows.

**`adjustHeight()` and `distributeExtraHeight()`**: Both methods currently branch on `useTable` vs outline. Crosstab mode needs its own branches. `adjustHeight()` must distribute delta across crosstab data rows (excluding the fixed-height header). `distributeExtraHeight()` applies the same soft-cap logic as table mode, scoped to data rows only.

**Per the papers**: Crosstab is a per-Relation formatting option that coexists with all other operations (filter, sort, hide). "All the usual query interface actions remain available from the crosstab layout." This is architecturally correct — crosstab changes rendering, not the data pipeline.

---

## Implementation Phases

### Phase 1: PrimitiveRenderMode.BAR (LOW effort)
- Add `PrimitiveRenderMode` enum (TEXT, BAR, BADGE)
- Add `NumericStats` sub-record and nullable `numericStats` field to `MeasureResult` (per RDR-013 extension pattern)
- Implement `PrimitiveBarStyle` subclass (parallel to `PrimitiveTextStyle`)
- Bar cell: `StackPane` with colored `Rectangle`, width = `(value - numericStats.numericMin()) / (numericStats.numericMax() - numericStats.numericMin()) * cellWidth`
- Bar height = one label line height (single-line, no wrapping)
- Auto-detect numeric fields during `measure()`; store detected mode on `PrimitiveLayout` instance

### Phase 2: PrimitiveRenderMode.BADGE (LOW effort)
- Colored Label with category-based CSS class
- Auto-detect low-cardinality fields during `measure()`

### Phase 3: Crosstab (MEDIUM effort)
- Add `RelationRenderMode` enum
- Configure `pivotField`/`valueField` via `LayoutStylesheet` (per SchemaPath)
- Collect distinct pivot values during `measure()`, store in `MeasureResult.pivotStats()` (nullable `PivotStats` sub-record per RDR-013 extension pattern)
- Handle sparse matrices: column set = union, missing intersections = empty cells
- Place CrosstabHeader OUTSIDE VirtualFlow (following `NestedTable` pattern)
- Add crosstab branches to `adjustHeight()` and `distributeExtraHeight()`
- Implement CrosstabHeader, CrosstabCell, CrosstabRow
- Guard: forced TABLE/OUTLINE falls back when width is insufficient

### Phase 4 (deferred): SPARKLINE
- Requires array-valued Primitives — a schema-layer extension touching `Primitive`, `measure()`, height computation, and cell factory
- **Deferred to a separate RDR** due to schema-layer scope

---

## Research Findings

### RF-1: Crosstab Scope Is Bounded (Confidence: HIGH)

Crosstab is NOT a new layout algorithm — it's a different data arrangement within the same width-constrained rendering. The layout decision logic (does this fit in the available width?) applies identically. This is the same architectural pattern as table vs outline: same decision framework, different rendering.

### RF-2: VirtualFlow Supports Homogeneous Data Rows (Confidence: HIGH)

The existing Flowless VirtualFlow uses `SizeTracker` with a single `cellLengthVar` (uniform cell height). CrosstabHeader is placed outside the VirtualFlow — exactly as `NestedTable` places `TableHeader` — so data rows remain uniform and scroll estimation is correct.

### RF-3: Pivot Data Is Available Only During Measure (Confidence: HIGH)

`RelationLayout.layout(double width)` has no access to `JsonNode` data. All data-dependent state (including pivot values) must be collected during `measure()` and stored in `MeasureResult`. This is consistent with the existing measure-once/layout-many architecture.

### RF-4: Pivot Collection Must Be Post-Filter to Avoid Phantom Column Headers (Confidence: HIGH)

If pivot value collection runs before RDR-014's hideIfEmpty filter step, the pivot column set may include values from rows that will subsequently be filtered out. This produces column headers with no corresponding data rows — "phantom columns" that waste horizontal space and confuse users. The fix is a strict pipeline ordering constraint: `sort -> filter (hideIfEmpty) -> collect pivot values -> measure children`. This ordering ensures the pivot column set reflects only the rows that survive filtering.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Crosstab with many pivot values creates very wide tables | Medium | Cap pivot columns; overflow scrolls horizontally |
| Sparse pivot data creates mostly-empty matrices | Medium | Empty cells render as blank; consider collapsing sparse columns in a future iteration |
| Bar chart with non-numeric data | Low | Auto-detection validates parseability; non-numeric values fall back to TEXT |
| Auto-detection heuristic picks wrong mode | Low | Manual override via stylesheet; auto is a suggestion, stylesheet takes priority |
| Forced TABLE mode at insufficient width | Low | Guard falls back to OUTLINE; documented as intentional degradation |
| SPARKLINE touches schema layer | N/A | Deferred to separate RDR |

## Success Criteria

1. Numeric fields render as proportional bar charts (normalized to data range, not text pixel width) when renderMode=BAR
2. Low-cardinality fields render as colored badges when renderMode=BADGE
3. Crosstab correctly pivots data: distinct values of pivot field (collected during measure) become column headers
4. Crosstab handles sparse matrices: missing intersections render as empty cells
5. CrosstabHeader is placed outside VirtualFlow; SizeTracker uniform-cell assumption holds
6. Crosstab layout respects width constraints (degrades to outline if too many pivot values)
7. Pivot field/value field configured via LayoutStylesheet per SchemaPath, not on Relation schema node
8. All modes configurable via LayoutStylesheet per SchemaPath
9. All existing tests pass (new modes are opt-in)

## Recommendation

Phase 1 (bar chart) is immediately valuable and LOW effort — implement first. Phase 3 (crosstab) is the most architecturally significant addition and should follow Phase 1. Both can proceed independently of RDR-009, though LayoutStylesheet integration (Phase C of RDR-009) provides the natural per-path configuration mechanism. SPARKLINE is deferred to a separate RDR due to schema-layer implications.
