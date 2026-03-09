---
title: "Stylesheet Property System Completion"
id: RDR-002
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
  - "RDR-001 - Java 25 Modernization (closed)"
---

# RDR-002: Stylesheet Property System Completion

> Revise during planning; lock at implementation.
> If wrong, abandon code and iterate RDR.

## Problem Statement

The Bakke 2013 paper defines 20 stylesheet properties (Table 1) that drive layout decisions. Kramer implements only 7 fully, 4 partially, and is missing 9 entirely. The most impactful missing property is `IsVariableLength`, which drives a chain of dependent behaviors: `ValueDefaultWidth` computation (average vs max width), `OutlineSnapValueWidth` rounding for fixed-width data, and `TableMaxPrimitiveWidth` capping.

Without these properties, all primitives are treated identically — fixed-width data (dates, IDs, enums) can't get clean uniform column widths, and variable-length data (descriptions, names) lacks proper width management.

## Context

### Gap Analysis Source

6-agent parallel deep analysis comparing Bakke 2013 paper against Kramer implementation (2026-03-08). Full findings in T3: `kramer-gap-analysis-stylesheet-properties`.

### Current State

| Property | Status | Location |
|----------|--------|----------|
| IsVariableLength | Missing | — |
| ValueDefaultWidth | Dead field | `Primitive.defaultWidth` (always 0) |
| OutlineSnapValueWidth | Missing | `Style.snap()` does ceil, not width-multiple rounding — deferred, see Phase 1 |
| OutlineMaxLabelWidth | Missing | `RelationLayout.labelWidth` unbounded |
| OutlineColumnMinWidth | Missing | No minimum enforced |
| OutlineMinValueWidth | Missing | No minimum enforced |
| TableMaxPrimitiveWidth | Missing | No cap before justification |
| OutlineBulletStyle | Missing | No bullets rendered |
| OutlineIndentWidth | Missing | No bullet indentation |

## Research Findings

### Finding 1: IsVariableLength Detection Is Data-Driven, Not Configurable

**Source**: `PrimitiveLayout.measure()` lines 152-190, `Primitive.defaultWidth` field

`PrimitiveLayout.measure()` already computes both `maxWidth` and `averageWidth` from the data:

```java
// Line 179-188
double averageWidth = 0;
averageCardinality = 1;
if (data.size() > 0) {
    averageCardinality = (int) Math.round((double) cardSum / data.size());
    averageWidth = summedDataWidth / data.size();
}
columnWidth = Math.max(labelWidth,
                       Style.snap(Math.max(getNode().getDefaultWidth(),
                                           averageWidth)));
```

Currently, `columnWidth` **always** uses `averageWidth`, which is the variable-length behavior. For fixed-length fields (dates, phone numbers, status codes), `maxWidth` would be more appropriate since all values have similar width and should get uniform columns.

`Primitive.defaultWidth` exists as a field (line 25) but is **never set** — always 0. It participates as a floor at line 187 but has no effect. This is the paper's `ValueDefaultWidth` property, currently dead code.

**Heuristic**: `isVariableLength = (averageWidth > 0 && maxWidth / averageWidth > THRESHOLD)` where threshold ≈ 2.0. When `averageWidth <= 0` (empty dataset), default to `isVariableLength = true` (safe fallback — preserves current behavior). When max/avg ratio is low (< 2.0), data is uniform-width → use `maxWidth` for the column. When high, data varies → use `averageWidth` (current behavior).

**Threshold derivation**: A date field "2026-01-01" has max/avg ≈ 1.01 (all values same length). A name field "Li" vs "Bartholomew Cunningham" has max/avg ≈ 7. Status codes ("OK", "ERROR", "PENDING") have max/avg ≈ 1.5. A threshold of 2.0 correctly classifies dates and status codes as fixed-length, and names and descriptions as variable-length. The threshold is not a stylesheet property — the paper treats IsVariableLength as a computed attribute.

**Note**: RDR-001 removed the never-assigned `PrimitiveLayout.variableLength` field as dead code. This `isVariableLength` is a replacement with actual computation and behavioral impact, not a regression.

**Impact on justification**: `PrimitiveLayout.compress()` (line 116-118) unconditionally sets `justifiedWidth = Style.snap(available)`, stretching all primitives to fill available space. For fixed-length fields, this wastes space. When `isVariableLength=false`, `compress()` should cap at `maxWidth` instead of stretching.

**Estimate**: ~20-25 LoC. Boolean field, zero-guard, heuristic in `measure()`, conditional `columnWidth`, `compress()` behavior change, threshold constant, `clear()` reset.

**Status**: Verified against code. Both `maxWidth` and `averageWidth` are already computed; the missing logic is the branching between them.

### Finding 2: Width Guards Are Independent, Straightforward Caps

**Source**: `RelationLayout.measure()` lines 335-338, `ColumnSet.compress()` lines 54-60, `PrimitiveLayout.compress()` line 117, `PrimitiveLayout.calculateTableColumnWidth()` lines 82-84

Four independent width guards, each a simple cap or floor at a well-defined code point:

1. **OutlineMaxLabelWidth**: `RelationLayout.measure()` computes `labelWidth` as `max(child.calculateLabelWidth())` at lines 335-338. Currently unbounded — a 200-character field name consumes the entire column. Fix: cap with `Math.min(labelWidth, style.getOutlineMaxLabelWidth())`. New `RelationStyle` property with default 200px (~14 characters at 14px base font — sufficient for typical field labels like "shipping_address" while preventing "user_profile_extended_metadata_description" from dominating). **~8 LoC.**

2. **OutlineColumnMinWidth**: `ColumnSet.compress()` computes column count at lines 54-60 as `floor(justified / maxColumnWidth)`. No minimum width guard — `fieldWidth` (line 64) can reach zero or negative when `labelWidth` exceeds `columnWidth`. Fix: add `style.getOutlineColumnMinWidth()` as a floor in the column count divisor. New `RelationStyle` property with default 60px (~4 characters at 14px — enough to display abbreviated values without being unreadable). **~8 LoC.**

3. **OutlineMinValueWidth**: `PrimitiveLayout.compress()` sets `justifiedWidth = Style.snap(available)` with no floor. If available space is very small, values become unreadable. Fix: `justifiedWidth = Style.snap(Math.max(available, style.getMinValueWidth()))`. New `PrimitiveStyle` property with default 30px (~2 characters at 14px — prevents zero-width rendering while allowing tight layouts). **~10 LoC.**

4. **TableMaxPrimitiveWidth**: `PrimitiveLayout.tableColumnWidth()` (line 224-226) and `calculateTableColumnWidth()` (line 82-84) both return `columnWidth()` with no cap. A single wide column can dominate the table. Fix: cap with `Math.min(columnWidth(), style.getMaxTablePrimitiveWidth())` applied in `tableColumnWidth()` and `calculateTableColumnWidth()` only — NOT in `columnWidth()`, which is also used by outline mode. New `PrimitiveStyle` property, default unbounded (Double.MAX_VALUE). **~10 LoC.**

All guards are independent — any can be implemented without the others. CSS configurability deferred to coordinate with RDR-006's approach (Java API only initially, CSS via RDR-002 Phase 2 when CssMetaData infrastructure is designed).

**Estimate**: ~36 LoC total production code. Each guard is 2-3 lines of logic + property declaration.

**Status**: Verified against code. All insertion points confirmed.

### Finding 3: Outline Bullets Require Rendering + Width Budget Changes

**Source**: `OutlineElement.java` line 76, `OutlineColumn.java`, `RelationLayout.measure()` line 335

The outline rendering hierarchy is: `Outline` → `OutlineCell` → `Span` → `OutlineColumn` → `OutlineElement` (label + content cell).

`OutlineElement` (line 76) builds its children as `getChildren().addAll(label, cell.getNode())`. Bullet insertion point: prepend a bullet `Label` before the existing label+cell pair. The bullet Label would use a new CSS class `.outline-bullet` for styling.

Indentation: `OutlineColumn` controls column layout. Adding left padding to the column applies indentation uniformly to all elements.

**Width budget impact**: `RelationLayout.measure()` computes `labelWidth` at lines 335-338 as the max of child label widths. Bullet width must be added to this budget: `labelWidth += style.getBulletWidth()`. Without this, bullets consume space from the value area, causing text truncation.

Three new `RelationStyle` properties needed:
- `bulletText` (String, default `""` — no bullet) — glyph to render (e.g., `"•"`, `"◦"`, `"▪"`)
- `bulletWidth` (double, default 0) — width reserved for bullet in layout budget
- `indentWidth` (double, default 0) — left padding on `OutlineColumn`

No new classes required. CSS: add `.outline-bullet` rule in `default.css`. The bullet `Label` is created only when `bulletText` is non-empty.

**Estimate**: ~25 LoC production code. Bullet rendering in `OutlineElement`, width budget in `RelationLayout.measure()`, padding in `OutlineColumn`, 3 properties in `RelationStyle`.

**Status**: Verified against code. Rendering hierarchy confirmed; insertion points identified.

## Proposed Solution

### Constructor Strategy

RelationStyle currently has a 10-param constructor (9 UI params + `maxAverageCardinality` from RDR-006). Adding 5 more params (Phases 2+3) would create a 15-param constructor — fragile and error-prone for same-type double args. To manage this:

- **Phase 1**: No style changes needed (IsVariableLength is data-driven, computed in `PrimitiveLayout.measure()`).
- **Phase 2**: Add new properties as non-final fields with hardcoded defaults and setters on `RelationStyle` and `PrimitiveStyle`. This avoids constructor churn while maintaining backward compatibility. `PrimitiveStyle` properties (`minValueWidth`, `maxTablePrimitiveWidth`) are declared on the abstract class with defaults; `PrimitiveTextStyle`'s constructor is unchanged.
- **Phase 3**: Same pattern — non-final fields with defaults on `RelationStyle`.

This is a deliberate departure from RelationStyle's current immutable-via-constructor pattern. The alternative (builder or config record) would be cleaner but is premature for 5 properties that will likely move to CSS once `CssMetaData` infrastructure is designed.

### Phase 1: IsVariableLength + ValueDefaultWidth (Core)

Add `IsVariableLength` detection to `PrimitiveLayout.measure()`:

```java
// Guard empty data: default to variable-length (safe fallback)
if (averageWidth > 0) {
    isVariableLength = (maxWidth / averageWidth > VARIABLE_LENGTH_THRESHOLD);
} else {
    isVariableLength = true;
}
```

When `IsVariableLength`:
- `ValueDefaultWidth` = `averageWidth` (current behavior, but now explicit)
- Column grows to fill available space during justification

When NOT `IsVariableLength`:
- `ValueDefaultWidth` = `maxWidth` (ensures all values fit without wrapping)

**OutlineSnapValueWidth**: Deferred — blocked pending IsVariableLength validation. Snapping fixed-width columns to value-width multiples (e.g., 78px date field → 78, 156, 234px columns) is a natural companion to IsVariableLength but adds complexity to `Style.snap()` which currently has no per-field context. Will be addressed in a follow-up RDR after Phase 1 validates the IsVariableLength heuristic.

### Phase 2: Width Guards

- `OutlineMaxLabelWidth`: Cap `RelationLayout.labelWidth` at configurable max (default 200px)
- `OutlineColumnMinWidth`: Enforce minimum in `ColumnSet.compress()` column count calculation (default 60px)
- `OutlineMinValueWidth`: Enforce minimum in `PrimitiveLayout.compress()` (default 30px)
- `TableMaxPrimitiveWidth`: Cap `PrimitiveLayout.tableColumnWidth()` and `calculateTableColumnWidth()` for table mode only — `columnWidth()` is NOT modified (default MAX_VALUE)

CSS configurability deferred — Java API only initially (coordinate with RDR-006 precedent).

### Phase 3: Outline Bullets

- Add bullet glyph rendering to `OutlineElement` (configurable: disc, circle, square, none)
- Add indentation width property to `RelationStyle`, consumed by `OutlineElement`/`OutlineColumn`

## Implementation Plan

### Phase 1: IsVariableLength + ValueDefaultWidth (~20-25 LoC + tests)
1. Add `isVariableLength` boolean field and `VARIABLE_LENGTH_THRESHOLD = 2.0` constant to `PrimitiveLayout`
2. Reset `isVariableLength` in `clear()` (default: `true` for safe fallback)
3. Compute in `PrimitiveLayout.measure()` lines 179-188: guard `averageWidth > 0`, then `isVariableLength = (maxWidth / averageWidth > VARIABLE_LENGTH_THRESHOLD)` after existing max/avg computation
4. Branch `columnWidth` assignment: if variable-length, use `averageWidth` (current); if fixed-length, use `maxWidth`
5. Update `PrimitiveLayout.compress()` line 117: when `!isVariableLength`, cap `justifiedWidth` at `Math.min(available, maxWidth)` instead of stretching to `available`
6. Add unit tests for both branches with mock data, including empty dataset edge case

### Phase 2: Width Guards (~36 LoC + tests)
7. Add non-final fields with setters: `outlineMaxLabelWidth` (default 200) and `outlineColumnMinWidth` (default 60) to `RelationStyle`
8. Add non-final fields with setters: `minValueWidth` (default 30) and `maxTablePrimitiveWidth` (default MAX_VALUE) to `PrimitiveStyle` (abstract class — no constructor changes needed for `PrimitiveTextStyle`)
9. Apply `outlineMaxLabelWidth` cap in `RelationLayout.measure()` after line 338: `labelWidth = Math.min(labelWidth, style.getOutlineMaxLabelWidth())`
10. Apply `outlineColumnMinWidth` floor in `ColumnSet.compress()` line 56-60: add to column count divisor
11. Apply `minValueWidth` floor in `PrimitiveLayout.compress()` line 117
12. Apply `maxTablePrimitiveWidth` cap in `PrimitiveLayout.tableColumnWidth()` and `calculateTableColumnWidth()` only — NOT in `columnWidth()` which affects outline mode
13. CSS configurability deferred — Java API only initially (coordinate with RDR-006 precedent)

### Phase 3: Outline Bullets (~25 LoC + tests)
14. Add non-final fields with setters: `bulletText` (String, default ""), `bulletWidth` (double, default 0), `indentWidth` (double, default 0) to `RelationStyle`
15. In `OutlineElement` constructor (line 76): prepend bullet `Label` when `bulletText` is non-empty
16. In `OutlineColumn`: apply `indentWidth` as left padding
17. In `RelationLayout.measure()` after line 338: add `style.getBulletWidth()` to `labelWidth` budget
18. Add `.outline-bullet` CSS class in `default.css`
19. Add unit tests for bullet rendering and width budget impact

## Test Plan

### Phase 1: IsVariableLength + ValueDefaultWidth
- **Scenario**: Uniform-width data (dates: "2026-01-01" to "2026-12-31") → **Verify**: `isVariableLength=false`, `columnWidth=maxWidth`
- **Scenario**: Variable-width data (names: "Al" to "Alexander Hamilton") → **Verify**: `isVariableLength=true`, `columnWidth=averageWidth`
- **Scenario**: Fixed-length field during compress → **Verify**: `justifiedWidth` capped at `maxWidth`, not stretched
- **Scenario**: Variable-length field during compress → **Verify**: `justifiedWidth` stretches to available (current behavior)
- **Scenario**: Empty dataset (data.size()==0) → **Verify**: `isVariableLength=true` (safe fallback, no division by zero)
- **Scenario**: Single-row dataset → **Verify**: max==avg, ratio ≈ 1.0, classified as fixed-length

### Phase 2: Width Guards
- **Scenario**: 200-character field label → **Verify**: capped at `outlineMaxLabelWidth`
- **Scenario**: Very narrow available width → **Verify**: column count limited by `outlineColumnMinWidth`
- **Scenario**: Tiny compress available → **Verify**: `minValueWidth` floor prevents zero-width values
- **Scenario**: Wide table column → **Verify**: `maxTablePrimitiveWidth` cap applied

### Phase 3: Outline Bullets
- **Scenario**: `bulletText="•"` → **Verify**: bullet Label prepended in OutlineElement
- **Scenario**: `bulletText=""` (default) → **Verify**: no bullet rendered
- **Scenario**: Bullet + indentation → **Verify**: `labelWidth` budget includes `bulletWidth`

## References

- Bakke 2013, Table 1 (stylesheet properties)
- T3: `kramer-gap-analysis-stylesheet-properties`
