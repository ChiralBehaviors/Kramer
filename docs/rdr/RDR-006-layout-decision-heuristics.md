---
title: "Layout Decision Heuristic Improvements"
id: RDR-006
type: Enhancement
status: implemented
accepted_date: 2026-03-09
closed_date: 2026-03-09
close_reason: implemented
priority: P3
author: Hal Hildebrand
reviewed-by: self
created: 2026-03-08
related_issues:
  - "RDR-002 - Stylesheet Property Completion"
  - "RDR-004 - Outline Column Set Correctness"
---

# RDR-006: Layout Decision Heuristic Improvements

> Revise during planning; lock at implementation.
> If wrong, abandon code and iterate RDR.

## Problem Statement

Several layout decision heuristics in Kramer diverge from the paper, producing suboptimal layouts in edge cases:

1. **UseTable too conservative**: Kramer uses table only if narrower than outline column width. Paper uses table whenever it fits the available width from the parent. This misses valid table opportunities.

2. **Cardinality clamped [1,4]**: Hardcoded in `RelationLayout.measure()` lines 328-332. High-cardinality nested relations get insufficient row allocation, requiring unnecessary scrolling.

3. **"id" field silently excluded**: `GraphQlUtil.buildSchema()` drops fields named "id" with no configuration option. Users who want to display IDs cannot.

## Context

### UseTable Decision

Paper: "Always use a nested table layout if there is enough horizontal space available for it."

Kramer (`RelationLayout.layout()` line 292):
```java
if (tableWidth <= columnWidth()) {  // columnWidth ≈ max child outline width
    return nestTableColumn(...);
}
```

The comparison is `tableWidth <= columnWidth()` where `columnWidth()` returns the widest outline column. The paper's intent is `tableWidth <= availableWidth` from the parent. When a table is wider than the widest outline column but still fits the available space, Kramer incorrectly chooses outline.

### Cardinality Cap

```java
averageChildCardinality = Math.max(1, Math.min(4, ...));
```

For a nested relation averaging 10 items, Kramer allocates space for 4. The remaining 6 require scrolling within the VirtualFlow. The paper's approach would allocate proportionally to the actual average.

### "id" Field Exclusion

`GraphQlUtil.buildSchema(Field)` lines 89-91:
```java
if (!field.getName().equals("id")) {
    parent.addChild(new Primitive(field.getName()));
}
```

No configuration, no documentation. Users querying `{ users { id name email } }` will see name and email but never id.

## Research Findings

### Finding 1: UseTable Divergence Is Narrower Than Expected

**Source**: `RelationLayout.layout()` lines 281-296, `columnWidth()` line 194-196

The divergence between Kramer and the paper is confirmed but narrower than described in the Problem Statement. In `layout()`:

```java
columnWidth = children.stream()
                      .mapToDouble(c -> c.layout(available))
                      .max().orElse(0.0)
              + labelWidth;
double tableWidth = calculateTableColumnWidth();
if (tableWidth <= columnWidth()) {
    return nestTableColumn(Indent.TOP, new Insets(0));
}
return columnWidth();
```

`columnWidth()` returns `Style.snap(columnWidth + style.getOutlineCellHorizontalInset())`, where `columnWidth` is `labelWidth + max(child.layout(available))`. The `width` parameter passed from the parent is `(parentWidth - labelWidth) - style.getOutlineCellHorizontalInset()`.

**Key insight**: When any child is a `PrimitiveLayout`, `child.layout(available)` returns the primitive's measured column width — which directly contributes to `columnWidth`. In typical schemas with mixed primitive and relation children, `columnWidth()` and `width` are close. The divergence manifests primarily when:
- ALL children are table-mode Relations (each child's `layout()` returns its own outline column width, which may be much narrower than `width`)
- A relation's table width exceeds its outline column width but fits within the parent's available width

The fix (`tableWidth <= width` instead of `tableWidth <= columnWidth()`) is additive in the substantive case where all children are Relations in table mode (where `columnWidth()` may be significantly smaller than `width`). When primitives are present, `columnWidth()` may exceed `width` by up to 1px due to `Style.snap()` using `Math.ceil`, making the fix marginally stricter in that sub-pixel range. Practical impact is zero — the fix is safe and regression-free.

**Status**: Verified against code. Direction is correct; sub-pixel rounding edge case is negligible.

### Finding 2: Two Distinct Cardinality Caps

**Source**: `RelationLayout.measure()` lines 328-332, `resolveCardinality()` line 508-510

Two separate cardinality caps exist, often conflated:

1. **Cap 1 (Artificial)**: `Math.min(4, ...)` in `measure()` — clamps `averageChildCardinality` to [1,4]. This controls how many rows of space are allocated per cell during outline height calculation. Hardcoded, no configuration.

2. **Cap 2 (Natural)**: `resolveCardinality()` returns `Math.max(1, Math.min(cardinality, maxCardinality))` — clamps to the actual data size. This is data-bounded and correct.

Cap 1 is the problematic one. For a relation averaging 10 items, only 4 rows of space are allocated. The remaining items require scrolling in VirtualFlow.

**Risk of removing Cap 1 entirely**: An uncapped cardinality causes layout explosion — a relation averaging 50 items would allocate 50 rows per cell, consuming the entire viewport for a single field.

**Recommendation**: Parameterize Cap 1 with a **default of 10**. A cap of 4 causes unnecessary scrolling for typical real-world data (5-15 items per relation). A cap of 10 represents a practical upper bound for visible-without-scroll display on typical screen heights (~600px with ~20px cell height = 30 visible rows, meaning 10 is conservative but avoids extreme single-relation heights). Per-node CSS tuning handles outliers. The paper does not specify a cap, but the paper's examples show modest cardinalities (3-5). Note: the cap applies independently at each nesting level; multi-level compounding is inherent to nested layouts and not addressable by a per-level cap alone.

`RelationStyle` currently has no cardinality-related properties. A new `maxAverageCardinality` property is needed. CSS support via `-kramer-max-average-cardinality` enables per-relation tuning.

**Status**: Verified. Default of 10 balances paper conformance with practical usability.

### Finding 3: "id" Exclusion Is Original, Undocumented, Unprincipled

**Source**: `GraphQlUtil.java` lines 89-91 (in `buildSchema(Field)`) and lines 110-112 (in `buildSchema(Relation, InlineFragment)`)

The exclusion exists in two methods, both with identical logic:
```java
if (!field.getName().equals("id")) {
    parent.addChild(new Primitive(field.getName()));
}
```

Present since the initial import (2016). No comment, no paper basis, no configuration. The paper makes no mention of excluding fields by name.

**Callers identified**:
- `buildSchema(Field)` — called from `buildSchema(String)` line 141 and `buildSchema(String, String)` line 173
- `buildSchema(Relation, InlineFragment)` — called recursively for inline fragments
- External callers: `AutoLayoutExplorer`, `SinglePageApp`, and any downstream user of the API

**Fix approach**: Add `Set<String> excludedFields` parameter to both methods that filter primitives. Provide overloads that default to `Set.of("id")` for backward compatibility. The `buildSchema(String)` and `buildSchema(String, String)` entry points delegate to `buildSchema(Field)`, so the parameter threads through naturally.

**Status**: Verified. Straightforward API addition with full backward compatibility.

## Proposed Solution

### Fix 1: UseTable Decision

Change the comparison to use the available width passed from the parent:

```java
public double layout(double width) {
    // ...
    double tableWidth = calculateTableColumnWidth();
    if (tableWidth <= width) {  // Compare against available width, not outline column width
        return nestTableColumn(Indent.TOP, new Insets(0));
    }
    return columnWidth();
}
```

This matches the paper exactly and will produce more tables in intermediate-width scenarios.

### Fix 2: Parameterize Cardinality Cap

Replace the hardcoded cap with a configurable parameter:

```java
// Default: 10 (balances paper conformance with practical limits).
// Can be set via RelationStyle or CSS (-kramer-max-average-cardinality).
int maxCard = style.getMaxAverageCardinality(); // default: 10
averageChildCardinality = Math.max(1, Math.min(maxCard, ...));
```

Default of 10 prevents layout explosion while accommodating typical data (see Finding 2). The previous cap of 4 can be restored via the Java API for backward compatibility. CSS configurability is deferred to coordinate with RDR-002 Phase 2, which also adds style properties and requires designing the CSS mechanism (no `CssMetaData`/`StyleableProperty` infrastructure exists in Kramer's current style system).

### Fix 3: Configurable "id" Exclusion

Add a configurable set of excluded field names:

```java
// In GraphQlUtil or a configuration object
private static final Set<String> DEFAULT_EXCLUDED = Set.of("id");

public static Relation buildSchema(Field parentField, Set<String> excludedFields) {
    // ...
    if (!excludedFields.contains(field.getName())) {
        parent.addChild(new Primitive(field.getName()));
    }
}
```

Keep backward compatibility with a default exclusion set, but allow override.

## Implementation Plan

### Fix 1: UseTable Decision (~1 line)
1. Change `RelationLayout.layout()` line 292 from `tableWidth <= columnWidth()` to `tableWidth <= width`
2. Verify with existing smoke tests that no regressions occur (fix is strictly additive)
3. Add unit test for table chosen when `tableWidth > columnWidth()` but `tableWidth <= width`

### Fix 2: Parameterize Cardinality Cap (~5 lines code + tests)
4. Add `maxAverageCardinality` field and getter to `RelationStyle` with default 10 (Java API only — CSS configurability deferred to coordinate with RDR-002 Phase 2 style mechanism design)
5. Update `RelationLayout.measure()` lines 328-332 to use `style.getMaxAverageCardinality()` instead of hardcoded 4
6. Add unit tests for cardinality capping at configurable values, including 2-level nesting scenario

### Fix 3: Configurable "id" Exclusion (~20 lines)
8. Add `Set<String> excludedFields` parameter to `GraphQlUtil.buildSchema(Field, Set<String>)`
9. Add `Set<String> excludedFields` parameter to `GraphQlUtil.buildSchema(Relation, InlineFragment, Set<String>)`
10. Add backward-compatible overloads defaulting to `Set.of("id")`
11. Add new entry points: `buildSchema(String query, Set<String> excludedFields)` and `buildSchema(String query, String source, Set<String> excludedFields)` — not just Field-level overloads
12. Add unit tests for inclusion/exclusion of "id" fields

## Test Plan

### Fix 1: UseTable Decision
- **Scenario**: Table fits available width but wider than outline column → **Verify**: table mode chosen (currently outline)
- **Scenario**: Table wider than available width → **Verify**: outline mode chosen (unchanged)
- **Scenario**: Table narrower than outline column → **Verify**: table mode chosen (unchanged)

### Fix 2: Cardinality Cap
- **Scenario**: Nested relation averaging 8 items with default cap (10) → **Verify**: 8 rows allocated
- **Scenario**: Nested relation averaging 15 items with default cap (10) → **Verify**: 10 rows allocated (capped)
- **Scenario**: Custom cap set to 4 via style → **Verify**: legacy behavior preserved

### Fix 3: "id" Exclusion
- **Scenario**: Query with "id" field, default exclusion → **Verify**: id not in schema (backward compatible)
- **Scenario**: Query with "id" field, empty exclusion set → **Verify**: id appears in schema
- **Scenario**: Custom exclusion set `{"id", "version"}` → **Verify**: both excluded
- **Scenario**: Inline fragment with "id" field → **Verify**: exclusion applies consistently

### Multi-level Nesting (Fix 2 risk validation)
- **Scenario**: 2-level nested relations (parent avg 10, child avg 8) with default cap → **Verify**: height stays within viewport bounds

### Regression
- **Scenario**: Existing smoke tests → **Verify**: no regressions with all three fixes

## References

- Bakke 2013, §3.3 (hybrid layout heuristic)
- T3: `kramer-gap-analysis-layout-algorithm`
- T3: `kramer-gap-analysis-data-model`
