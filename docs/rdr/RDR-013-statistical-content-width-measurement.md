# RDR-013: Statistical Content-Width Measurement

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P1
- **Created**: 2026-03-15
- **Related**: RDR-009 (MeasureResult), RDR-014 (Conditional Display & Data Filtering), SIEUFERD (SIGMOD 2016 S3.4)

## Problem Statement

Kramer's `PrimitiveLayout.measure()` computes column widths using CSS-derived constants (`style.getMinValueWidth()`) and simple data statistics (average text width, max width, `isVariableLength` threshold). It has no awareness of actual data content distribution.

SIEUFERD (SIGMOD 2016) describes a superior approach: "column widths are based on average and confidence interval values that do not change once a target number of unique sample values have been collected from the database." Width is computed from sampled data values and frozen when the confidence interval stabilizes -- not from CSS constants.

The result: compact columns for integers, wider columns for URLs, without any user configuration. This is the single highest-impact SIEUFERD feature missing from Kramer.

---

## Current State

In `PrimitiveLayout.measure()`:
- `measure()` calls `clear()` then recomputes all statistics from scratch each invocation on the full dataset. There is no cross-call state accumulation -- every call iterates the entire data array.
- `averageWidth` = sum of rendered text widths / data size (recomputed from scratch each call)
- `maxWidth` = maximum observed text width (recomputed from scratch each call)
- `isVariableLength` = `maxWidth / averageWidth > variableLengthThreshold` (default 2.0)
- `effectiveWidth` = `isVariableLength ? averageWidth : maxWidth` (line 193)
- `dataWidth` = `Style.snap(Math.max(getNode().getDefaultWidth(), effectiveWidth))`
- `columnWidth` = `Math.max(labelWidth, dataWidth)`

Width measurement for individual values uses `style.width(row)` (delegates to `PrimitiveStyle.width(JsonNode)`), which computes text width plus horizontal inset. The floor width comes from `getNode().getDefaultWidth()` (a schema-node property).

Problems:
1. `style.getMinValueWidth()` is a CSS constant -- same for all fields regardless of content (used as compress floor, not in measure)
2. Average width is sensitive to outliers (one very long URL skews the average)
3. No convergence detection -- the width is recomputed from scratch on every `measure()` call
4. No distinction between content types (dates, numbers, IDs, free text)

---

## Proposed Design

### Statistical Width Model

Replace simple average/max with a statistical model per Primitive:

```java
record ContentWidthStats(
    double p50Width,          // median content width
    double p90Width,          // 90th percentile (captures "typical max" without outlier sensitivity)
    int sampleCount,          // number of unique values sampled
    boolean converged         // true when width estimate has stabilized
) {}
```

### Combined Width Formula

CSS measurement captures *chrome width* (insets, borders, padding). Statistical measurement captures *content width*. These are orthogonal:

```
effectiveWidth = isVariableLength ? p90Width : maxWidth
dataWidth = Style.snap(Math.max(getNode().getDefaultWidth(), effectiveWidth))
```

Where:
- `getNode().getDefaultWidth()` -- schema-node property, the absolute floor width
- `style.width(row)` -- renders text width per value via `PrimitiveStyle.width(JsonNode)` (text width + horizontal inset)
- `style.getMinValueWidth()` -- compress floor (applied in `computeCompress()`, not in `measure()`)

**Critical: `isVariableLength` interaction.** P90 replaces `averageWidth` ONLY in the `isVariableLength == true` branch. When `isVariableLength == false`, `maxWidth` is retained. This prevents truncation of fixed-length data (timestamps, UUIDs, 8-digit IDs) where the current code correctly uses `maxWidth` to ensure complete display. Any change to this classification affects sibling width allocation in `RelationLayout.justifyColumn()` (lines 624-663), which partitions children into fixed-length and variable-length groups for proportional distribution.

### Convergence and Freezing

Per SIEUFERD: once `converged == true`, the width is frozen. New data values do not trigger re-measurement. This provides layout stability -- adding more data rows doesn't cause column width oscillation.

Convergence criterion: direct stability check -- `|p90Width_new - p90Width_old| < epsilon` over the last k `measure()` invocations, AND `sampleCount >= minSamples`.

The convergence parameters are exposed as LayoutStylesheet properties, read per SchemaPath:

| Property Key | Type | Default | Description |
|-------------|------|---------|-------------|
| `stat-min-samples` | int | 30 | Minimum unique values before convergence is eligible |
| `stat-convergence-epsilon` | double | 1.0 | Maximum p90Width delta (pixels) to count as stable |
| `stat-convergence-k` | int | 3 | Consecutive stable `measure()` calls required to converge |

These are read via `LayoutStylesheet.getXxx(SchemaPath, key, default)` so that different fields can have different convergence thresholds (e.g., a free-text field may need a larger epsilon than a numeric ID column). These properties are registered in the RDR-ROADMAP.md LayoutStylesheet Property Registry.

The zero-width case is handled explicitly: when `p50Width == 0` and `sampleCount >= minSamples`, converge immediately (all-empty data is stable by definition).

**Caching mechanism:** When converged, `PrimitiveLayout.measure()` checks for a cached `MeasureResult` and returns early without calling `clear()` or recomputing. Since `measure()` currently calls `clear()` and recomputes from scratch every invocation, the freezing mechanism must cache the frozen `MeasureResult` as a field and short-circuit at the top of `measure()`:

```java
if (frozenResult != null) {
    // Restore fields from cached result
    measureResult = frozenResult;
    return frozenResult.columnWidth();
}
clear();
// ... normal measure logic ...
```

The cached result is cleared only on stylesheet change (when `Style.setStyleSheets()` clears the layout cache), not on data change -- converged stats are data-stable by definition.

### Cache Invalidation Contract

The original caching description above is **incomplete**: `frozenResult` must be invalidated when ANY `LayoutStylesheet` property that affects the `measure()` input changes -- not just CSS stylesheet changes. CSS stylesheets affect chrome dimensions (insets, borders, padding), but `LayoutStylesheet` properties can alter the effective dataset itself (e.g., `hide-if-empty` filters rows, `sort-fields` reorders them) or change the rendering mode. A `frozenResult` computed against a pre-filter dataset is stale after the filter is toggled.

**Note:** SchemaPath is currently set during `measure()` (see RDR-ROADMAP.md). For Phase 2 convergence caching keyed by SchemaPath, SchemaPath must be available at construction time. This dependency is tracked as a standalone deliverable in the roadmap.

**Invalidation scope:** `frozenResult` is scoped to `(SchemaPath, stylesheetVersion)` where `stylesheetVersion` is a monotonically incrementing counter on `LayoutStylesheet`, bumped by every `setOverride()` call:

```java
// On LayoutStylesheet:
private long version = 0;

public long getVersion() { return version; }

public void setOverride(SchemaPath path, String property, Object value) {
    // ... existing override logic ...
    version++;
}
```

```java
// In PrimitiveLayout.measure():
if (frozenResult != null && frozenStylesheetVersion == stylesheet.getVersion()) {
    return frozenResult;  // cache hit
}
// ... compute fresh result, and on convergence:
frozenResult = measureResult;
frozenStylesheetVersion = stylesheet.getVersion();
```

**LayoutStylesheet properties that affect measure inputs:**

| Property | Effect on measure() | Defined in |
|----------|-------------------|------------|
| `hide-if-empty` | Filters rows from the input ArrayNode, changing cardinality and data distribution | RDR-014 |
| `sort-fields` | Reorders rows; does not change cardinality but affects which rows appear in viewport-sensitive contexts | RDR-014 |
| `render-mode` | Switches Primitive rendering (e.g., TEXT vs BAR), changing width semantics entirely | RDR-015 (future) |
| `filter` | General predicate filtering, analogous to `hide-if-empty` but user-defined | RDR-018 Phase 2 (future) |

Any new LayoutStylesheet property that changes the effective dataset or rendering semantics MUST be added to this table and MUST trigger `version++` via `setOverride()`.

**CSS stylesheet changes** continue to invalidate via the existing `Style.setStyleSheets()` path, which clears the entire layout cache (a broader invalidation). The `stylesheetVersion` mechanism is finer-grained: it invalidates only the frozen convergence cache, without forcing a full layout cache clear.

### Integration with MeasureResult

`ContentWidthStats` becomes a field on `MeasureResult` (RDR-009):

```java
record MeasureResult(
    // existing fields...
    double dataWidth,
    double maxWidth,
    boolean isVariableLength,
    // new:
    ContentWidthStats contentStats
) {}
```

### MeasureResult Extension Strategy

This RDR establishes the canonical pattern for extending `MeasureResult` with mode-specific state: **nullable sub-records**.

**Rule: All mode-specific state MUST be factored into nullable sub-records, not inlined as top-level fields.**

`ContentWidthStats` is the template. It is a self-contained record holding all state for statistical content-width measurement, attached to `MeasureResult` as a single nullable field. This pattern scales cleanly as new measurement modes are added — each mode owns its own sub-record, and `MeasureResult`'s constructor signature grows by one nullable parameter per mode rather than N fields per mode.

Coordinated adopters of this pattern:
- **RDR-013** (this RDR): `ContentWidthStats contentStats` — statistical content-width data for Primitives
- **RDR-015**: `NumericStats numericStats` — numeric range for BAR rendering mode (Primitive); `PivotStats pivotStats` — pivot column metadata for CROSSTAB rendering mode (Relation)
- **Future RDR-019**: `SparklineStats sparklineStats` — array-valued sparkline data (Primitive+SPARKLINE mode)

**Nullability contract**: `contentStats` is null when:
- `sampleCount == 0` (no data values observed), OR
- Statistical sizing is disabled (e.g., dataset below `minSamples` threshold, or feature toggled off via stylesheet)

When `contentStats` is null, `PrimitiveLayout` falls back to existing behavior (`averageWidth` for variable-length, `maxWidth` for fixed-length). Consumers MUST null-check before accessing sub-record fields. This same nullability contract applies to all mode-specific sub-records: null means "this mode is not active for this node."

### Why SIEUFERD Sampling Does Not Apply

SIEUFERD's sampling rationale is to avoid expensive full database scans. In Kramer, data is fully in-memory at measure time -- `PrimitiveLayout.measure()` already iterates the entire dataset on every call. Phase 1 computes percentiles from the full dataset, which is superior to viewport-biased sampling: it sees the true distribution, not just what happens to be visible. There is no performance reason to subsample in-memory arrays.

The `Style.LayoutObserver.apply(cell, Primitive)` hook exists in the code but has zero production callers -- all call sites (`NestedTable`, `Outline`, `NestedRow`) invoke the `apply(VirtualFlow, Relation)` overload instead. A VirtualFlow-based feedback loop would require new wiring and would only see viewport-biased samples, which is strictly worse than the full-dataset computation already performed in `measure()`.

---

## Implementation

### Phase 1: ContentWidthStats accumulation (LOW effort)
1. Add `ContentWidthStats` to `PrimitiveLayout`
2. In `measure()`, compute percentiles using a simple sorted sample reservoir (no need for streaming quantile algorithms at Kramer's data sizes -- the full dataset is already iterated)
3. Store in `MeasureResult`
4. Use `p90Width` as the `averageWidth` replacement in the `isVariableLength == true` branch ONLY. Retain `maxWidth` for the `isVariableLength == false` branch.
5. For datasets < minSamples (30), use existing behavior: `averageWidth` for variable-length, `maxWidth` for fixed-length

### Phase 2: Convergence and freezing (LOW effort)
1. Add convergence detection: track p90Width across successive `measure()` calls; converge when delta < epsilon for k consecutive calls. Read `stat-min-samples`, `stat-convergence-epsilon`, and `stat-convergence-k` from `LayoutStylesheet` per SchemaPath (see Convergence and Freezing section above)
2. When converged, cache the `MeasureResult` and short-circuit `measure()` to return early without `clear()` or recomputation
3. `AutoLayout.measure()` short-circuits when all Primitives converged

---

## Research Findings

### RF-1: CSS and Statistical Measurement Are Complementary (Confidence: HIGH)

CSS gives the container dimensions (insets, borders, padding). Data statistics give the content dimensions (typical character width of actual values). Neither alone produces adaptive layouts. Currently Kramer has CSS only; adding the statistical term is additive, not a replacement.

### RF-2: Full-Dataset Percentiles Are Superior to Viewport Sampling (Confidence: HIGH)

Kramer data is fully in-memory at measure time. `PrimitiveLayout.measure()` already iterates the entire dataset. Computing p90 from the full dataset is a trivial addition to the existing loop -- and produces the true distribution, unlike viewport-biased VirtualFlow sampling which only sees materialized cells. There is no "free sampling" benefit analogous to SIEUFERD's DB-query piggyback.

### RF-3: P90 vs Average for Variable-Length Only (Confidence: HIGH)

Average width is skewed by outliers (one long URL in 100 short IDs produces an artificially wide column). P90 captures "typical content width" while tolerating outliers. However, P90 must only replace `averageWidth` in the variable-length branch. For fixed-length columns (timestamps, UUIDs, formatted IDs), `maxWidth` is correct: these values have uniform width, and P90 would truncate 10% of values for no layout benefit.

---

## Risks and Considerations

| Risk | Severity | Mitigation |
|------|----------|------------|
| Initial sample too small for accurate stats | Low | For datasets < minSamples, use existing behavior (averageWidth for variable-length, maxWidth for fixed-length) |
| Convergence too slow for small datasets | Low | Small datasets are computed in full; convergence is only relevant when `measure()` is called repeatedly with growing data |
| Layout jitter if stats change between measure passes | Medium | Freeze on convergence; monotonic width (never shrink) |
| P90 on fixed-length columns causes truncation | High | P90 applied only in `isVariableLength == true` branch; fixed-length retains `maxWidth` |
| Division by zero when p50Width = 0 in convergence check | Low | When `p50Width == 0 && sampleCount >= minSamples`, converge immediately (all-empty data is stable) |

## Success Criteria

1. Column widths adapt to content: integer columns narrower than text columns without CSS tuning
2. Convergence achieved within first 30 unique values
3. No layout width oscillation after convergence
4. P90-based widths produce visually tighter layouts than current average-based widths on the test datasets
5. Zero performance regression on measure() for the existing test suite
6. All 142+ existing tests pass (statistical sizing is additive, not replacement)
7. Fixed-length columns (timestamps, UUIDs) display without truncation

## Recommendation

This is the single highest-impact, lowest-risk SIEUFERD feature. It builds directly on MeasureResult (RDR-009 Phase A) and requires no architectural changes. Phase 1 alone delivers measurable improvement. Recommend implementing immediately after RDR-009 Phase A completes.
