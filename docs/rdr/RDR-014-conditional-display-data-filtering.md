# RDR-014: Conditional Display & Data Filtering

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P1
- **Created**: 2026-03-15
- **Related**: SIEUFERD (Bakke et al., SIGMOD 2016), RDR-009 (LayoutStylesheet), RDR-013 (ContentWidthStats cache invalidation), Bakke InfoVis 2013 (core layout algorithm)

## Problem Statement

Kramer renders ALL data rows including those with zero children for optional relationships, and has no deterministic ordering guarantee for rows. Two features from SIEUFERD (Bakke et al., "Expressive Query Construction through Direct Manipulation of Nested Relational Results", SIGMOD 2016) address this:

1. **HIDE PARENT IF EMPTY**: GraphQL optional relationships return empty arrays. Kramer renders all JSON rows including those with zero children, creating visual noise. SIEUFERD provides per-Relation `hideParentIfEmpty` toggle controlling outer vs inner join display semantics.

2. **Tuple-Identifying Stable Sort**: SIEUFERD automatically sorts every relation on a tuple-identifying field subset so result ordering is stable as users hide/show fields. Without this, row ordering in Kramer depends on JSON array order, which may vary between queries.

---

## Feature 1: HIDE PARENT IF EMPTY

### Current State

`RelationLayout.measure()` receives `datum` as an `ArrayNode` (the already-extracted array of items for this relation). It iterates over the node's children, calling `fold()` and then `measure()` recursively. No filtering of items is applied. If a child Relation has zero items for a given parent row, the parent row still renders with an empty nested area.

The build phase uses a separate data path: `RelationLayout.extractFrom(datum)` applies the stored `extractor` function then calls `node.extractFrom(extracted)` (which does `extracted.get(field)`). Filtering must be applied consistently in both paths.

### Proposed Design

#### Property Placement

The primary home for `hideIfEmpty` is `LayoutStylesheet`, addressed by `SchemaPath` (per RDR-009's separation of schema structure from display configuration). `Relation` carries an optional override for programmatic use; when unset, the value delegates to the stylesheet.

```java
// On LayoutStylesheet (primary):
boolean hideIfEmpty = stylesheet.getBoolean(path, "hide-if-empty", false);

// On Relation (optional programmatic override):
private Boolean hideIfEmpty = null; // null = delegate to stylesheet

public Boolean getHideIfEmpty() { return hideIfEmpty; }
public void setHideIfEmpty(Boolean hide) { this.hideIfEmpty = hide; }
```

Default is `false` — current behavior is preserved (outer join display: show all parents). Users opt in explicitly. SIEUFERD's "hide by default" semantics can be applied globally via a LayoutStylesheet rule.

#### autoFold Interaction

`hideIfEmpty` applies ONLY when `getNode().getAutoFoldable() == null`. When `Relation.getAutoFoldable()` returns non-null, the `fold()` method calls `flatten()`, which collapses parent-child into flat grandchild items — destroying the parent row context that `hasNonEmptyChildren` needs to inspect. The filter predicate requires intact parent-child structure to determine which parents have empty children.

Implementation must include an explicit guard:

```java
// Guard: filtering is incompatible with autoFold
boolean shouldFilter = resolvedHideIfEmpty
    && getNode().getAutoFoldable() == null;
```

This is a structural constraint, not a limitation to be lifted later. autoFold flattens the hierarchy; there are no "parents with empty children" to filter because the parent level no longer exists.

#### Data Path: measure()

The `datum` parameter to `RelationLayout.measure()` is already the extracted array of items. It is NOT a parent node from which items need extraction. The pseudocode operates on `datum` directly:

```java
// In RelationLayout.measure(), before existing child iteration:
ArrayNode items = (ArrayNode) datum;
if (shouldFilter) {
    List<JsonNode> filtered = StreamSupport
        .stream(items.spliterator(), false)
        .filter(item -> hasNonEmptyChildren(item, getNode()))
        .toList();
    items = JsonNodeFactory.instance.arrayNode().addAll(filtered);
}
maxCardinality = items.size();
// ... proceed with child iteration using filtered items
```

#### Data Path: build phase

**Design requirement**: The filter predicate must be stored on `RelationLayout` alongside the `extractor` lambda and applied consistently in BOTH `measure()` and the build-phase data extraction (`extractFrom()`). The build phase currently uses `RelationLayout.extractFrom(datum)` which calls `extractor.apply(datum)` then `node.extractFrom(extracted)` — a separate code path that would bypass any filtering done only in `measure()`.

The stored filter must wrap or compose with the existing extractor so that `extractFrom()` returns already-filtered data matching what `measure()` computed dimensions for.

#### hasNonEmptyChildren semantics

The empty check is **shallow** (Phase 1): a parent is considered "empty" if all its immediate child Relations produce zero items. Deep/recursive emptiness checking is deferred — it adds complexity and the shallow check covers the primary use case (optional GraphQL relationships returning `[]`).

```java
private boolean hasNonEmptyChildren(JsonNode item, Relation relation) {
    for (SchemaNode child : relation.getChildren()) {
        if (child.isRelation()) {
            JsonNode childData = item.get(child.getField());
            if (childData != null && childData.isArray() && childData.size() > 0) {
                return true;
            }
        }
    }
    // No child Relations, or all child Relations empty
    return !relation.getChildren().stream().anyMatch(SchemaNode::isRelation);
}
```

### Effort: MEDIUM
- Stylesheet property with SchemaPath resolution
- Optional override on `Relation`
- autoFold guard
- Filter applied in both measure() and build data paths
- Shallow empty check

---

## Feature 2: Stable Tuple-Identifying Sort

### Current State

JSON array items are rendered in the order they appear in the `JsonNode` array. GraphQL responses may not have deterministic ordering (depends on resolver implementation). Users see rows jump around between queries.

### Proposed Design

Add a `sortFields` property to `Relation`:

```java
// On Relation class:
private List<String> sortFields = List.of(); // empty = no sort

public List<String> getSortFields() { return sortFields; }
public void setSortFields(List<String> fields) { this.sortFields = fields; }
```

**Auto-detection**: When `sortFields` is empty and `autoSort` is true (default), identify tuple-identifying fields heuristically:
1. Fields named "id", "key", "name" (common primary identifiers)
2. First Primitive child (if no heuristic match)
3. Sort ascending by the identified field(s)

In `RelationLayout.measure()`, sort items before measurement. The `datum` parameter is already the extracted `ArrayNode`:

```java
ArrayNode items = (ArrayNode) datum;
List<JsonNode> sorted = StreamSupport
    .stream(items.spliterator(), false)
    .sorted(buildComparator(relation.getSortFields()))
    .toList();
items = JsonNodeFactory.instance.arrayNode().addAll(sorted);
```

#### Type-aware comparison

The comparator must dispatch on `JsonNode` type to avoid incorrect lexicographic ordering of numbers:

```java
private Comparator<JsonNode> buildComparator(List<String> fields) {
    return (a, b) -> {
        for (String field : fields) {
            JsonNode av = a.get(field);
            JsonNode bv = b.get(field);
            // Null handling: missing/null fields sort last
            if (av == null || av.isNull()) return (bv == null || bv.isNull()) ? 0 : 1;
            if (bv == null || bv.isNull()) return -1;
            int cmp;
            if (av.isNumber() && bv.isNumber()) {
                cmp = Double.compare(av.asDouble(), bv.asDouble());
            } else {
                cmp = av.asText().compareTo(bv.asText());
            }
            if (cmp != 0) return cmp;
        }
        return 0;
    };
}
```

**LayoutStylesheet integration**:
```java
String sortSpec = stylesheet.getString(path, "sort-fields", "auto");
```

**Cache invalidation (cross-reference RDR-013):** Changing `hide-if-empty` or `sort-fields` via `LayoutStylesheet.setOverride()` alters the effective dataset that `measure()` operates on. Any cached `MeasureResult` or `frozenResult` (RDR-013 Phase 2 convergence cache) computed against the prior dataset is stale and must be invalidated. Per RDR-013's Cache Invalidation Contract, `LayoutStylesheet.setOverride()` must increment a `version` counter. `PrimitiveLayout.measure()` checks `stylesheet.getVersion()` against the version stored at freeze time; a mismatch forces recomputation. This ensures that toggling `hide-if-empty` from `false` to `true` (which removes rows, changing cardinality and width distribution) correctly triggers re-measurement across the entire layout pipeline, not just for the Relation where filtering is applied but also for its child Primitives whose `ContentWidthStats` were computed against the unfiltered dataset.

All property keys defined in this RDR are registered in the central LayoutStylesheet Property Registry (see `docs/rdr/RDR-ROADMAP.md`). Property naming follows kebab-case convention.

### Effort: LOW
- Sort fields property on `Relation`
- Sort step in `RelationLayout.measure()` operating on `datum` ArrayNode directly
- Type-aware comparator with null-last semantics

---

### Integration Test Fixture

This RDR introduces the first data pre-processing step in `measure()`. As subsequent RDRs (013, 015, 016, 019) add statistical accumulation, pivot collection, and caching to the same pipeline, a canonical reference JSON fixture should be created exercising: empty relations, numeric fields, variable-length text, mixed types, deep nesting (3+ levels), and array-valued primitives. This fixture serves as a golden test for verifying the composed pipeline across all RDR implementations. See RDR-ROADMAP.md for the full integration test strategy.

---

## Combined Pipeline

The data pre-processing pipeline in `RelationLayout.measure()` becomes:

```
datum (already an ArrayNode of items)
  → sort (stable, by tuple-identifying fields; type-aware comparison)
  → filter (hideIfEmpty, only when autoFoldable == null)
  → measure children
```

This ordering matters: sort first ensures stable ordering even after filtering removes rows.

As with filtering, the sort must also be stored and applied consistently in the build-phase data path (`extractFrom()`).

---

## Research Findings

### RF-1: HIDE IF EMPTY Maps to JOIN Semantics (Confidence: HIGH)
`hideIfEmpty=true` is equivalent to INNER JOIN display (only show parents with matching children). `hideIfEmpty=false` is LEFT OUTER JOIN display (show all parents). This is a well-understood relational algebra concept, not a UI heuristic.

### RF-2: Auto-Sort Heuristic is Sufficient (Confidence: MEDIUM)
For GraphQL schemas, the `id` field convention is near-universal (GraphQL spec recommends it). For non-GraphQL schemas, first-Primitive fallback provides reasonable ordering. Manual override via `sortFields` covers edge cases.

### RF-3: autoFold Destroys Parent Context (Confidence: HIGH)
`Relation.getAutoFoldable()` triggers `flatten()` which collapses parent-child items into a flat grandchild array. The parent row structure required for `hasNonEmptyChildren` no longer exists after folding. This is a structural incompatibility, not a bug to fix — the guard (`autoFoldable == null`) is the correct design.

### RF-4: Schema Tree Structure (Confidence: HIGH)
The `Relation` schema is a tree (children are `SchemaNode` instances in a `List`; no parent pointers or back-edges). Recursive empty checks do not risk cycles. However, deep recursion is deferred to Phase 2 — shallow checking is sufficient for the primary use case.

---

## Risks and Considerations

| Risk | Severity | Mitigation |
|------|----------|------------|
| Filtering changes cardinality, affecting layout metrics | Low | Filtered cardinality is the correct input to layout — current behavior (counting empty children) inflates averageCardinality |
| Auto-sort heuristic picks wrong field | Low | Manual override via sortFields; heuristic is conservative (prefers "id") |
| Performance of filter + sort on large arrays | Very Low | JSON arrays in Kramer are typically < 1000 items; sort + filter is sub-millisecond |
| Build path bypasses filtering | Medium | Design requirement: filter predicate stored on `RelationLayout` alongside `extractor`, applied in both `measure()` and `extractFrom()` |
| hideIfEmpty with autoFold | Medium | Explicit guard prevents application when autoFoldable != null; documented as structural constraint |

## Success Criteria

1. Parents with empty children are shown by default (preserving current outer-join behavior); `hideIfEmpty=true` hides them
2. Row ordering is deterministic and stable across queries for the same data
3. Sort and filter are composable (both active simultaneously)
4. LayoutStylesheet can control both properties per SchemaPath (primary), Relation carries optional override (secondary)
5. Filtered cardinality correctly feeds into averageCardinality measurement
6. All existing tests pass — default `hideIfEmpty=false` preserves current behavior (SC-6 compliance)
7. Numeric sort fields sort numerically, not lexicographically
8. Missing/null sort fields sort last without NPE
9. Filter and sort are applied in both measure() and build-phase data paths

## Recommendation

Implement as **two sequential PRs**:

1. **PR 1: Stable Sort** — simpler, no regression risk (additive behavior only when sortFields configured or autoSort enabled). Clear test surface: verify ordering of items with numeric IDs, mixed types, null fields.

2. **PR 2: HIDE IF EMPTY** — depends on sort being stable (filter after sort). Has the autoFold guard constraint, dual data-path requirement, and stylesheet integration. Independent rollback if issues arise.

Both features modify the `RelationLayout.measure()` data extraction path, but sequential delivery provides a cleaner test surface and independent rollback for each feature. Can proceed independently of RDR-009 Phase C — the stylesheet integration can be wired later.
