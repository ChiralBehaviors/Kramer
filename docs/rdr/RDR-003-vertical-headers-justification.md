---
title: "Vertical Table Headers and Two-Phase Justification"
id: RDR-003
type: Feature
status: closed
accepted_date: 2026-03-09
closed_date: 2026-03-09
close_reason: implemented
priority: P2
author: Hal Hildebrand
reviewed-by: self
created: 2026-03-08
related_issues:
  - "RDR-002 - Stylesheet Property Completion"
---

# RDR-003: Vertical Table Headers and Two-Phase Justification

> Revise during planning; lock at implementation.
> If wrong, abandon code and iterate RDR.

## Problem Statement

The paper specifies `UseVerticalTableHeader` — when a table column is narrow relative to its label text, the label should be rotated 90 degrees. This is missing entirely from Kramer. `ColumnHeader` always renders labels horizontally, causing truncation or overflow on narrow columns with long labels.

Additionally, the paper describes a two-phase table justification: (1) use extra space to rotate vertical headers back to horizontal, then (2) distribute remaining space proportionally among variable-length columns only. Kramer skips phase 1 and distributes to ALL columns in phase 2, wasting space on fixed-width columns.

## Context

### Current Behavior

`ColumnHeader.java` lines 40-61: Always calls `layout.label(width, height)` horizontally. No rotation logic exists.

`RelationLayout.justifyColumn()` lines 483-500: Distributes extra space to all children proportionally by `tableColumnWidth` ratio. No variable-length filtering.

### Paper's Two-Phase Justification

1. **Phase 1**: For each vertical header, check if there's enough justified width to display it horizontally. If so, set `UseVerticalTableHeader = false` and consume that extra width.
2. **Phase 2**: Distribute remaining extra width among variable-length primitive columns proportionally to their `TableColumnWidth`.

## Proposed Solution

### Vertical Header Rotation

Add rotation support to `ColumnHeader`:
- Compute `UseVerticalTableHeader` in `PrimitiveLayout.nestTableColumn()`: vertical if `labelWidth > tableColumnWidth() * VERTICAL_THRESHOLD`
- **Rendering approach**: `Label.setRotate(-90)` does NOT change JavaFX layout bounds — the parent `VBox` still allocates pre-rotation dimensions. Therefore, wrap the rotated `Label` in a fixed-size `Pane` whose min/pref/max are set to the post-rotation visual dimensions (width = `labelStyle.getHeight()`, height = `labelWidth`). Position the `Label` inside via `setTranslateX`/`setTranslateY` to center within the wrapper.
- When vertical, the header consumes `labelStyle.getHeight()` as width (font height) but `labelWidth` as height

### Two-Phase Justification

Update `RelationLayout.justifyColumn()`. **Critical**: ALL children must receive a `justify()` call — fixed-length children at their natural width, variable-length children with proportional extra space. Otherwise `justifiedWidth` stays at `-1.0` from `clear()`.

```java
// Phase 1: un-rotate headers where space allows
double remaining = available - tableColumnWidth; // extra space
for (child : children) {
    if (child instanceof PrimitiveLayout pl && pl.isUseVerticalHeader()) {
        double needed = pl.getLabelWidth() - pl.tableColumnWidth();
        if (remaining >= needed) {
            remaining -= needed;
            pl.setUseVerticalHeader(false);
        }
    }
}
// Phase 2a: justify fixed-length children at natural width
for (child : children) {
    if (child instanceof PrimitiveLayout pl && !pl.isVariableLength()) {
        child.justify(child.tableColumnWidth());
        remaining unchanged — fixed children get no extra
    }
}
// Phase 2b: distribute remaining to variable-length children
double varTotal = variableChildren.mapToDouble(tableColumnWidth).sum();
// last variable child gets exact remainder (anti-drift)
for (variable child) {
    child.justify(child.tableColumnWidth() + remaining * (child.tableColumnWidth() / varTotal));
}
// Edge case: if no variable-length children, fall back to proportional for all
```

**Dependency**: RDR-002 (IsVariableLength) — now closed/implemented. `PrimitiveLayout.isVariableLength()` accessor exists. `RelationLayout` children are treated as variable-length by default (accessed via `instanceof PrimitiveLayout pl ? !pl.isVariableLength() : false` pattern).

## Research Findings

### Finding 1: ColumnHeader Rendering — Always Horizontal, No Rotation

**Source**: `ColumnHeader.java` (62 lines), `LabelStyle.java`, `SchemaNodeLayout.label()`

`ColumnHeader` extends `VBox` with 3 constructors:
- No-arg (base initialization)
- Primitive: `ColumnHeader(double width, double height, PrimitiveLayout layout)` — adds `layout.label(width, height)`
- Relation: `ColumnHeader(double width, double height, RelationLayout layout, List<Function<Double, ColumnHeader>> nestedHeaders)` — parent label on top, `HBox` of nested headers below with height split in half

Labels are always created via `LabelStyle.label()` → `LayoutLabel` with fixed min/pref/max sizes. **No rotation logic exists anywhere in the codebase.** JavaFX `Label.setRotate(-90)` is available but unused.

Creation chain: `NestedTable` → `layout.buildColumnHeader()` → `TableHeader(HBox)` → foreach child, `c.columnHeader().apply(height)` → `ColumnHeader` instance.

`columnHeaderHeight` is computed recursively: `labelStyle.getHeight() + max(children.columnHeaderHeight())`. Currently assumes horizontal labels — does not account for rotated label width.

**Impact for RDR-003**: ColumnHeader needs a vertical rendering mode where:
- `Label.setRotate(-90)` is applied
- Width/height constraints are swapped (visual width = original height, visual height = original width)
- `columnHeaderHeight` computation must change: vertical headers are taller (labelWidth becomes height)

**Status**: Verified via code read. No test coverage for ColumnHeader.

### Finding 2: justifyColumn() — Proportional to ALL Children, No Fixed/Variable Distinction

**Source**: `RelationLayout.justifyColumn()` (lines 494-511)

```java
protected void justifyColumn(double available) {
    if (children.isEmpty()) return;
    double[] remaining = new double[] { available };
    SchemaNodeLayout last = children.get(children.size() - 1);
    justifiedWidth = Style.snap(available);
    children.forEach(child -> {
        double childJustified = Style.relax(available
                                            * (child.tableColumnWidth()
                                               / tableColumnWidth));
        if (child.equals(last)) {
            childJustified = remaining[0];
        } else {
            remaining[0] -= childJustified;
        }
        child.justify(childJustified);
    });
}
```

Space distribution is purely proportional: `available × (child.tableColumnWidth / total)`. Every child — fixed-width or variable-length — gets proportional expansion. A date column (70px) in a 200px table justified to 300px would stretch to 105px, wasting space on fixed-width data.

`justifyTable()` just calls `justifyColumn(available - columnHeaderIndentation)`.

**Paper requires two-phase approach**:
- Phase 1: Use extra space to un-rotate vertical headers back to horizontal
- Phase 2: Distribute remaining extra width only to variable-length columns

**Impact for RDR-003**: `justifyColumn()` must be rewritten with both phases. RDR-002's `isVariableLength()` on `PrimitiveLayout` is now available for Phase 2 filtering. For `RelationLayout` children (nested tables), they should be treated as variable-length by default.

**Status**: Verified. No test coverage for justifyColumn().

### Finding 3: Table Layout Pipeline — Full Execution Order

**Source**: `SchemaNodeLayout.autoLayout()`, `RelationLayout.layout()`, `compress()`, `nestTableColumn()`

The production pipeline is:
1. **measure(data)** → Traverses data, computes initial widths (columnWidth, labelWidth, averageCardinality)
2. **layout(width)** → Decides table vs outline: `calculateTableColumnWidth() <= width`
   - If table: calls `nestTableColumn(TOP, Insets(0))` which recursively sets `useTable=true` and accumulates `tableColumnWidth`
   - If outline: returns `columnWidth()`
3. **compress(width)** → For table mode, dispatches to `justifyTable(width)` → `justifyColumn()`
4. **calculateRootHeight()** → For table mode, computes `cellHeight` and total `height` including `columnHeaderHeight`
5. **buildControl()** → Returns `NestedTable` or `Outline`

Key width fields:
- `columnWidth` — outline mode width (max child + label)
- `tableColumnWidth` — table mode width (sum of children's table widths)
- `justifiedWidth` — final width after justify/compress

**Where useVerticalHeader fits**: Should be computed in `nestTableColumn()` after `tableColumnWidth` is known, by comparing `labelWidth > tableColumnWidth * threshold`. This is the point where we know whether a column is narrow relative to its label.

**Status**: Verified via code read.

### Finding 4: columnHeader() Lambda Pattern

**Source**: `PrimitiveLayout.columnHeader()` (lines 107-111), `RelationLayout.columnHeader()` (lines 171-178)

Column headers are created via deferred lambdas: `Function<Double, ColumnHeader>`. The `Double` parameter is the rendered height, applied when `TableHeader` iterates children:

```java
// PrimitiveLayout
return rendered -> new ColumnHeader(Style.snap(justifiedWidth + columnHeaderIndentation),
                                    rendered, this);
// RelationLayout
return rendered -> new ColumnHeader(width, rendered, this, nestedHeaders);
```

**Impact for RDR-003**: The `columnHeader()` lambda is the natural place to inject vertical rotation logic. At lambda execution time, `justifiedWidth` and `useVerticalHeader` are already computed. The lambda can check `useVerticalHeader` and either:
- Create a normal horizontal ColumnHeader, or
- Create a rotated ColumnHeader with swapped dimensions

**Status**: Verified.

### Finding 5: Height Implications of Vertical Headers

**Source**: `RelationLayout.calculateTableHeight()` (lines 430-439), `columnHeaderHeight()` (lines 182-191)

Table height is: `(resolvedCardinality × cellHeight) + columnHeaderHeight + insets`.

`columnHeaderHeight` currently assumes horizontal labels: `snap(labelStyle.getHeight())` for primitives. With vertical headers, the header height would be `labelWidth` (the text width becomes the vertical extent after rotation). This is potentially much taller.

For the two-phase justification, Phase 1 "un-rotating" headers would reduce `columnHeaderHeight`, gaining vertical space. This interacts with the overall table height calculation.

**Complication**: `columnHeaderHeight` is computed lazily and cached. Vertical header decisions must be made before height computation. The current pipeline order (`nestTableColumn` before `calculateTableHeight`) supports this — we can set `useVerticalHeader` during `nestTableColumn()` and read it during `columnHeaderHeight()`.

**Status**: Verified.

## Implementation Plan

### Constructor Strategy

Consistent with RDR-002: non-final fields with setter/getter pairs. No constructor parameter proliferation.

### Phase 1: Vertical Header Detection (~15 LoC)
1. Add `private boolean useVerticalHeader` field to **`PrimitiveLayout`** only (not `SchemaNodeLayout` — vertical rotation is semantically valid only for leaf primitives, per test plan scenario 7). Default `false`.
2. Add `double verticalHeaderThreshold` property to `PrimitiveStyle` (default `1.5`). Placed on `PrimitiveStyle` because it governs primitive-table-display behavior, consistent with `minValueWidth` and `maxTablePrimitiveWidth`.
3. In `PrimitiveLayout.nestTableColumn()`: after `columnHeaderIndentation` is set, compute `useVerticalHeader = labelWidth > tableColumnWidth() * style.getVerticalHeaderThreshold()`
4. Add `public boolean isUseVerticalHeader()` and `public void setUseVerticalHeader(boolean)` accessors on `PrimitiveLayout`
5. Add `useVerticalHeader = false` to `PrimitiveLayout.clear()`. This is correct because `useVerticalHeader` is set in `nestTableColumn()` which runs AFTER `clear()` in the `layout()` pipeline. (Contrast with `isVariableLength` which must NOT reset because it's set in `measure()` which precedes `clear()`.)

### Phase 2: ColumnHeader Vertical Rendering (~30 LoC)
6. Update `ColumnHeader` primitive constructor: if `layout.isUseVerticalHeader()`, wrap rotated `Label` in a fixed-size `Pane` with post-rotation dimensions (width = `labelStyle.getHeight()`, height = `labelWidth`). Apply `Label.setRotate(-90)` on the label inside the wrapper. Position via `setTranslateX`/`setTranslateY`. **Do NOT rely on bare `setRotate()` alone** — JavaFX layout bounds are pre-rotation.
7. Override `PrimitiveLayout.columnHeaderHeight()`: if `useVerticalHeader`, return `Style.snap(labelWidth)` instead of `labelStyle.getHeight()`. (`SchemaNodeLayout.columnHeaderHeight()` stays unchanged — `RelationLayout` always delegates to children.)
8. Update `PrimitiveLayout.columnHeader()` lambda: check `useVerticalHeader` flag, pass adjusted dimensions to ColumnHeader

### Phase 3: Two-Phase Justification (~55 LoC)
9. Rewrite `RelationLayout.justifyColumn()`:
   - **Phase 1 — Un-rotate**: Iterate children. For each `instanceof PrimitiveLayout pl && pl.isUseVerticalHeader()`, if `remaining >= pl.getLabelWidth() - pl.tableColumnWidth()`, un-rotate (set `useVerticalHeader = false`), consume the width difference.
   - **Phase 2a — Justify fixed-length**: For each child that is `instanceof PrimitiveLayout pl && !pl.isVariableLength()`, call `child.justify(child.tableColumnWidth())`. These get their natural width, no extra.
   - **Phase 2b — Distribute to variable-length**: Compute `varTotal` from variable-length children (includes `RelationLayout` children, which are variable by default). Distribute `remaining` proportionally. Last variable child gets exact remainder (anti-drift pattern).
   - **Edge case**: If no variable-length children exist, fall back to current proportional distribution for all children.
   - Access pattern: `instanceof PrimitiveLayout pl ? !pl.isVariableLength() : false` — `RelationLayout` children are always treated as variable-length.

### Phase 4: CSS Integration — DEFERRED
Deferred per RDR-002 precedent: "CSS configurability deferred — Java API only initially." The `verticalHeaderThreshold` property is exposed via Java getter/setter on `PrimitiveStyle` only. CSS `CssMetaData` integration will be coordinated across all style properties in a future RDR.

## Test Plan

- **Scenario**: Narrow column + long label → **Verify**: `useVerticalHeader` set to true, `columnHeaderHeight` returns `labelWidth`
- **Scenario**: Wide column + short label → **Verify**: `useVerticalHeader` stays false, `columnHeaderHeight` returns `labelStyle.getHeight()`
- **Scenario**: Table justified wider → **Verify**: Phase 1 un-rotates headers that now fit horizontally, `useVerticalHeader` set back to false
- **Scenario**: Phase 1 un-rotation reduces height → **Verify**: `columnHeaderHeight` decreases after un-rotation, total table height decreases accordingly
- **Scenario**: Mixed fixed/variable columns → **Verify**: Phase 2 gives fixed-length their natural `tableColumnWidth()`, extra space goes only to variable-length
- **Scenario**: All fixed-width columns → **Verify**: Falls back to proportional distribution (no variable-length children)
- **Scenario**: No vertical headers → **Verify**: Phase 1 is no-op, Phase 2 still filters variable-length only
- **Scenario**: Nested relation table headers → **Verify**: Vertical rotation applies at leaf (primitive) level only; `RelationLayout.isUseVerticalHeader()` is not defined
- **Scenario**: All children receive justify() → **Verify**: After justifyColumn(), no child has `justifiedWidth == -1`
- **Scenario**: Resize triggers re-layout → **Verify**: `useVerticalHeader` resets via `clear()` and is recomputed correctly

## References

- Bakke 2013, §3.3 (table justification)
- Bakke 2013, Table 1 (`UseVerticalTableHeader`)
- T3: `kramer-gap-analysis-layout-algorithm`
