# RDR-013: Statistical Content-Width Measurement

## Metadata
- **Type**: Feature
- **Status**: closed
- **Priority**: P1
- **Created**: 2026-03-15
- **Accepted**: 2026-03-15
- **Closed**: 2026-03-16
- **Close-reason**: implemented
- **Reviewed-by**: self
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

## Alternatives Considered

### Alt A: Viewport-Biased Sampling via VirtualFlow LayoutObserver

Use the existing `Style.LayoutObserver.apply(cell, Primitive)` hook to collect width samples only from cells that VirtualFlow has materialized and rendered in the viewport.

**Pros:**
- Matches SIEUFERD's original approach — SIGMOD 2016 S3.4 describes sampling from rendered database rows as a natural byproduct of display
- No additional iteration over the full dataset; samples arrive "for free" as cells are created
- Could converge quickly for large datasets where only a small fraction is ever viewed

**Cons:**
- `Style.LayoutObserver.apply(cell, Primitive)` has zero production callers — all production call sites invoke the `apply(VirtualFlow, Relation)` overload. Wiring this up requires non-trivial plumbing
- Viewport-biased samples produce a biased distribution: if the user never scrolls, only the first N rows are sampled, systematically underrepresenting tail content (long values that appear late in sorted order)
- SIEUFERD's sampling rationale is to avoid full database scans; in Kramer, all data is already in-memory and `measure()` iterates the entire dataset on every call. The "free sampling" benefit does not transfer
- Non-deterministic: column width depends on scroll history, making layout tests fragile and reproducibility impossible

**Rejected because:** Full-dataset iteration is already performed; viewport sampling produces a strictly worse distribution at no savings. The LayoutObserver wiring cost is unnecessary overhead for an inferior result. (See RF-2.)

---

### Alt B: Fixed CSS-Based Width Hints per Field Type

Add CSS properties (e.g., `-kramer-content-type: integer | date | url | text`) to `PrimitiveStyle`. Layout reads these annotations and maps them to fixed width constants (e.g., `integer` → 40px, `date` → 80px, `url` → 200px).

**Pros:**
- Zero runtime cost: widths are resolved at stylesheet load time, no data iteration
- Fully deterministic and easy to test
- User has explicit control over column widths via CSS without touching Java code
- No convergence complexity; layout is stable from the first render

**Cons:**
- Requires users to annotate every field type in CSS — this is manual configuration, not automatic layout
- Width constants are application-specific and viewport-size-dependent; a 40px integer column is too narrow on HiDPI displays and too wide on compact views
- Does not adapt to actual data: a field declared as `integer` but containing 10-digit account numbers will be truncated
- Defeats the core Kramer premise: layout should be automatic and data-driven, not manually specified
- No mechanism for mixed-type fields (a "description" column that sometimes contains short IDs and sometimes free text)

**Rejected because:** Violates the fundamental requirement for adaptive, data-driven layout. This is the current state (CSS constants via `style.getMinValueWidth()`), which the RDR explicitly identifies as insufficient.

---

### Alt C: Streaming Quantile Algorithms (t-digest, P² Algorithm)

Replace the sorted-array percentile computation with a streaming algorithm (t-digest or the P² algorithm) that maintains approximate quantile estimates in O(1) space using only a single pass.

**Pros:**
- O(1) memory regardless of dataset size — no need to store all observed widths
- P² algorithm in particular is trivially implementable with no external dependency (5 marker variables)
- Well-suited for truly streaming data sources where the full dataset is never available at once
- t-digest provides better tail accuracy than P² for extreme quantiles (p99, p999)

**Cons:**
- Kramer's data is fully in-memory at measure time; the full dataset is always available. Streaming algorithms sacrifice accuracy for a memory constraint that does not exist here
- P² produces approximate quantiles with non-trivial error at small sample sizes (n < 100); for Kramer's typical dataset sizes this approximation error can exceed the convergence epsilon
- t-digest requires an external dependency (a ~20KB JAR); adding a library for a feature achievable with `Arrays.sort()` on an in-memory array violates Spartan design principles
- Streaming algorithms do not support re-computation on stylesheet change (the entire history of observations would need to be replayed) — cache invalidation becomes architecturally complex
- The convergence detection in Phase 2 compares successive p90Width values across `measure()` calls; streaming algorithms produce estimates that can oscillate between calls in ways that confound stability detection

**Rejected because:** The in-memory full-dataset assumption makes streaming quantiles a solution without a problem. A simple sorted sample on the data array already in memory produces exact quantiles at negligible cost. Streaming algorithms introduce approximation error and dependency weight for no practical benefit at Kramer's scale.

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

## Finalization Gate

**1. Has the problem been validated with real user scenarios?**

Partially. The problem is validated by code inspection: `PrimitiveLayout.measure()` demonstrably recomputes from CSS constants and simple average/max on every call with no content-awareness. The pathological cases (integer columns as wide as URL columns; average skewed by a single outlier) are reproducible from the existing test data. However, no end-user feedback has been collected on whether column width variance is perceived as a usability problem in practice. The explorer and toy-app applications exist and can be used to validate improvement against real GraphQL endpoints, but this testing has not been documented as a formal acceptance scenario. The risk is low — narrower integer columns and wider URL columns are strictly better — but formal user-facing validation against a representative GraphQL schema is recommended before Phase 2 (convergence/freezing) is marked complete.

**2. Is the solution the simplest that could work?**

Yes, for Phase 1. Computing percentiles via `Arrays.sort()` on the data already iterated in `measure()` is the minimal change: no new dependencies, no architectural changes, no new call sites. The `ContentWidthStats` sub-record is added to `MeasureResult` as a nullable field following the established RDR-009 pattern. Phase 2 (convergence caching) adds the `frozenResult` field and a version-keyed short-circuit, which is also minimal. The only non-trivial complexity is the `stylesheetVersion` invalidation mechanism, which is required for correctness given the LayoutStylesheet properties catalogued in the Cache Invalidation Contract section. Simpler invalidation strategies (e.g., always recompute) were considered and rejected because they defeat the layout-stability goal. Alt C (streaming quantiles) and Alt A (viewport sampling) were both simpler in some dimensions but inferior in correctness; this design is the simplest correct solution.

**3. Are all assumptions verified or explicitly acknowledged?**

The following assumptions are explicitly acknowledged:

- *Data is fully in-memory at measure time* — verified by inspection of `PrimitiveLayout.measure()` and the Jackson `ArrayNode` data model. If a future data source streams lazily, Phase 1 percentile computation would need revision.
- *`isVariableLength` classification is reliable* — the threshold (`maxWidth / averageWidth > 2.0`) is an existing heuristic. RF-3 documents the risk: P90 is only safe in the variable-length branch. If the heuristic misclassifies a fixed-length column as variable-length, P90 may truncate values. This is inherited behavior, not introduced by this RDR.
- *SchemaPath is available at `measure()` time for Phase 2* — the RDR explicitly flags this as unverified: "SchemaPath is currently set during `measure()` (see RDR-ROADMAP.md). For Phase 2 convergence caching keyed by SchemaPath, SchemaPath must be available at construction time." This is tracked as a standalone roadmap deliverable.
- *`minSamples = 30` is sufficient for stable p90 estimation* — a standard statistical rule of thumb (central limit theorem); no domain-specific validation has been performed against Kramer's actual field distributions. Exposed as a configurable LayoutStylesheet property to allow tuning per field.
- *Width oscillation can be prevented by monotonic-width + convergence* — assumed but not yet empirically tested. Success Criterion 3 (no oscillation after convergence) validates this assumption.

**4. What is the rollback strategy if this fails?**

Phase 1 is purely additive: `ContentWidthStats` is a new nullable field on `MeasureResult`, and `p90Width` is used only in the `isVariableLength == true` branch. Rollback is a one-line revert: replace `p90Width` with `averageWidth` and remove the `ContentWidthStats` computation from `measure()`. The `ContentWidthStats` field on `MeasureResult` can be left as null-returning dead code until the next cleanup cycle.

Phase 2 (convergence caching) introduces the `frozenResult` short-circuit. If this produces layout regressions (e.g., stale widths after stylesheet changes), the entire Phase 2 block can be disabled by removing the early-return guard. The `stylesheetVersion` mechanism on `LayoutStylesheet` is independently useful (it is referenced in future RDRs) and does not need to be reverted even if the freeze logic is disabled.

Feature flag via LayoutStylesheet: if a `stat-enabled` property is added (defaulting to `true`), the statistical path can be toggled off per schema path without a code change. This is not specified in the current design but would be a low-cost addition if production rollback must be non-deployment.

**5. Are there cross-cutting concerns with other RDRs?**

Yes, several:

- **RDR-009 (MeasureResult)**: `ContentWidthStats` extends `MeasureResult` as a nullable sub-record. This RDR establishes the canonical nullable sub-record extension pattern, which RDR-015 and a future RDR-019 are required to follow. Any change to `MeasureResult`'s record definition requires coordinating all callers; the null-check contract must be enforced across all consumers.
- **RDR-014 (Conditional Display & Data Filtering)**: The `hide-if-empty` and `sort-fields` properties defined in RDR-014 affect the effective dataset seen by `measure()`, requiring `frozenResult` invalidation. This cross-cutting dependency is explicitly documented in the Cache Invalidation Contract table. RDR-014 implementation must ensure these properties trigger `LayoutStylesheet.version++` via `setOverride()`.
- **RDR-015 (Alternative Rendering Modes)**: The `render-mode` property switches Primitive rendering semantics (TEXT vs BAR), which changes what "width" means. A BAR rendering mode would use `NumericStats.max` for scaling, not `p90Width`. The nullable sub-record pattern established here (`contentStats`) is the template for `numericStats` in RDR-015. The Cache Invalidation Contract table must be updated when RDR-015 is implemented to include `render-mode`.
- **RDR-016 (Layout Stability & Incremental Update)**: RDR-016 addresses layout stability under incremental data updates. The convergence/freezing mechanism in Phase 2 of this RDR is complementary but independent: Phase 2 freezes on statistical convergence; RDR-016 addresses stability under structural data changes. If both are active, their invalidation paths must not conflict.
- **RDR-018 (Query Semantic Layout Stylesheet)**: The `stat-min-samples`, `stat-convergence-epsilon`, and `stat-convergence-k` properties introduced here are registered in the LayoutStylesheet Property Registry managed by the roadmap. RDR-018's stylesheet query integration must handle these numeric properties consistently with other LayoutStylesheet properties. The `filter` property listed in the Cache Invalidation Contract table is a future RDR-018 Phase 2 item.

---

## Recommendation

This is the single highest-impact, lowest-risk SIEUFERD feature. It builds directly on MeasureResult (RDR-009 Phase A) and requires no architectural changes. Phase 1 alone delivers measurable improvement. Recommend implementing immediately after RDR-009 Phase A completes.
