# RDR-014: Conditional Display & Data Filtering

## Metadata
- **Type**: Feature
- **Status**: closed
- **Priority**: P1
- **Created**: 2026-03-15
- **Accepted**: 2026-03-15
- **Closed**: 2026-03-16
- **Close-reason**: implemented
- **Reviewed-by**: self
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
- Add `boolean getBoolean(SchemaPath, String, boolean)` to `LayoutStylesheet` interface and `DefaultLayoutStylesheet` (already implemented — Kramer-4sg)

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

**Auto-detection**: When `sortFields` is empty and `autoSort` is true, identify tuple-identifying fields heuristically:
1. Fields named "id", "key", "name" (common primary identifiers)
2. First Primitive child (if no heuristic match)
3. Sort ascending by the identified field(s)

`autoSort` defaults to **false** (opt-in). Opt-in default preserves backward compatibility (SC-6). Users enable via LayoutStylesheet or `Relation.setSortFields()`.

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

Sort operates on `datum` before extractor application and is therefore compatible with autoFold — no guard needed (unlike Feature 1's filter which requires intact parent-child structure). autoFold flattens the hierarchy after sort has already produced a stable ordering.

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
- Sort predicate stored on `RelationLayout` and applied in build-phase `extractFrom()` path, parallel to Feature 1's filter composition

The sort composes with the extractor in `extractFrom()` analogously to Feature 1's filter:

```java
// In RelationLayout.extractFrom(datum):
ArrayNode extracted = (ArrayNode) extractor.apply(datum);
if (!sortFields.isEmpty() || autoSort) {
    List<JsonNode> sorted = StreamSupport
        .stream(extracted.spliterator(), false)
        .sorted(buildComparator(resolvedSortFields))
        .toList();
    extracted = JsonNodeFactory.instance.arrayNode().addAll(sorted);
}
return extracted;
```

This ensures `extractFrom()` returns the same item order that `measure()` computed dimensions for.

---

### Integration Test Fixture

A canonical integration test fixture for the combined pipeline is specified in RDR-ROADMAP.md.

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

## Alternatives Considered

### Alt A: Filter at the GraphQL Query Level (Server-Side)

Add `@skip` or conditional arguments to the GraphQL query so the server never returns empty-child parents in the first place.

**Pros:**
- Zero layout engine complexity — filtering happens before data reaches Kramer
- Reduces payload size over the wire
- Standard GraphQL mechanism (`@skip(if: $condition)`, custom directives, or connection `filter:` arguments)

**Cons:**
- Requires schema cooperation: not all GraphQL APIs expose filtering arguments on every relationship
- Forces the caller to know and express join semantics in the query, coupling UI intent to transport concerns
- Not usable for non-GraphQL data sources (direct `JsonNode` construction, fixture data, offline scenarios)
- The `hideParentIfEmpty` toggle is a display-time decision; baking it into the query prevents toggling without re-fetching

**Rejected because:** Kramer is a layout engine, not a query builder. Display configuration (inner vs outer join rendering) must be expressible without re-fetching data. The `LayoutStylesheet` / `SchemaPath` model (RDR-009) is exactly the right layer.

---

### Alt B: CSS `display:none` Equivalent — Hide Visually but Still Measure

Render empty-child rows into the cell pipeline but mark them with a CSS class that sets `visibility: hidden` or `managed: false` in JavaFX, keeping them in the flow for measurement but invisible to users.

**Pros:**
- No changes to data paths (`measure()` and `extractFrom()` are untouched)
- Layout geometry is preserved — hiding/showing rows does not trigger re-measurement
- Simple to toggle: change a CSS property rather than re-running `measure()`

**Cons:**
- `managed: false` in JavaFX removes the node from layout flow — effectively the same as removal, invalidating the "no re-measurement" argument
- `visibility: hidden` keeps space allocated, wasting screen real estate for empty rows — the opposite of the desired density improvement
- `maxCardinality` and `averageCardinality` would be computed against the full (unfiltered) row set, inflating layout metrics and producing oversized columns
- Violates the SIEUFERD design intent: the goal is density-preserving exclusion, not concealment
- Filtered rows still consume `VirtualFlow` cell slots, degrading scrolling performance for large datasets

**Rejected because:** The core requirement is that empty rows do not occupy layout space and do not inflate cardinality metrics. Visual hiding without structural removal fails both goals. The build-phase data path would diverge from the measure-phase view of the dataset, which is an explicit non-goal (see Design Requirement in Feature 1).

---

### Alt C: Schema-Level Annotation Instead of LayoutStylesheet Property

Place `hideIfEmpty` and `sortFields` directly on the `Relation` schema node as first-class fields (no nullable override, no delegation to stylesheet).

**Pros:**
- Simpler resolution logic — no two-tier lookup (stylesheet first, Relation override second)
- Schema captures the full display intent in one place
- Consistent with how `autoFoldable` is currently modeled on `Relation`

**Cons:**
- Violates the RDR-009 separation of concerns: `Relation` is structural (what fields exist, how they compose), while `LayoutStylesheet` is presentational (how to display them)
- A single `Relation` instance is shared across all usages of that schema path. LayoutStylesheet allows per-context overrides — the same `Relation` can render `hideIfEmpty=true` in one context and `false` in another (e.g., a dashboard view vs. a detail view)
- Mutating `Relation` fields at runtime for display toggling is unsafe if the schema is shared across multiple `AutoLayout` instances

**Rejected because:** RDR-009 established the stylesheet / schema separation precisely for this class of display-configuration property. Adding to `Relation` directly would erode that boundary. The nullable `Relation.hideIfEmpty` override (delegating to stylesheet when null) is retained for programmatic convenience in single-instance usage while keeping the stylesheet as the authoritative source.

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
| resolveCardinality phantom row when all items filtered | Medium | When `hideIfEmpty` filters ALL items, `maxCardinality=0`; `resolveCardinality` clamps to 1 producing one phantom row. Mitigation: suppress Relation rendering when `maxCardinality==0` after filtering with `shouldFilter==true`. Deferred to Phase 2. |

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

## Finalization Gate

### 1. Has the problem been validated with real user scenarios?

Partially. The problem is sourced from a published academic system (SIEUFERD, Bakke et al., SIGMOD 2016) that was validated against real relational reporting tasks. The `hideParentIfEmpty` behavior directly maps to a concrete user complaint in Kramer: optional GraphQL relationships (e.g., a user with no associated orders) render as a visible row with an empty nested table, creating visual noise that obscures meaningful rows. This is reproducible with any GraphQL endpoint that uses optional 1-to-many relationships — a near-universal pattern.

The stable sort problem is similarly concrete: GraphQL resolvers frequently return rows in non-deterministic order (dependent on backend query planner), causing visible row reordering between identical queries with no data change. This is directly observable in the `explorer` app.

Assumption acknowledged: no formal user study has been conducted for the Kramer-specific implementation. Confidence is HIGH that the problem class is real; confidence is MEDIUM that the proposed defaults (`hideIfEmpty=false`, `autoSort=false`) are the right out-of-box behavior. Validation will be possible once the `explorer` app can exercise the feature interactively.

### 2. Is the solution the simplest that could work?

Yes, for Feature 2 (stable sort). Adding a comparator step in `RelationLayout.measure()` with a conservative heuristic is minimal — no new data structures, no schema changes, one new `Comparator` implementation.

For Feature 1 (HIDE IF EMPTY), the solution is as simple as the problem allows. The dual data-path requirement (consistent filtering in both `measure()` and `extractFrom()`) adds necessary complexity that cannot be avoided without accepting a correctness bug (measure and build phases see different data). The stylesheet/override split adds one method call for property resolution but is required by the RDR-009 architecture. The autoFold guard is a single boolean check. No additional complexity is introduced beyond what the constraints demand.

### 3. Are all assumptions verified or explicitly acknowledged?

Verified:
- `datum` in `RelationLayout.measure()` is already the extracted `ArrayNode` (confirmed by reading current `RelationLayout` implementation)
- autoFold destroys parent context — structural constraint confirmed (RF-3)
- Schema tree has no cycles — confirmed by `Relation` children being a `List<SchemaNode>` with no parent back-pointer (RF-4)

Explicitly acknowledged assumptions:
- Shallow empty-check is sufficient for Phase 1 (deep recursive check deferred — may be needed if users have 3+ level optional nesting)
- Auto-sort heuristic (`id` → `key` → `name` → first Primitive) is sufficient for GraphQL schemas; edge cases handled by manual `sortFields` override
- `JsonNode` arrays in Kramer are < 1000 items; performance of in-memory sort/filter is sub-millisecond at this scale
- RDR-013's cache invalidation contract (stylesheet version counter) is in place or will be before these features are active

### 4. What is the rollback strategy if this fails?

Feature flags via `LayoutStylesheet` defaults provide natural rollback:

- **Sort rollback**: Default is `autoSort=false` (opt-in). If auto-sort produces incorrect ordering for a specific schema, the caller sets `sortFields = List.of()` and `autoSort = false` on the `Relation` or via stylesheet override. No data migration required.
- **Filter rollback**: Default is `hideIfEmpty=false` (current behavior preserved). If the filter introduces a layout regression (e.g., cardinality undercount in edge cases), reverting the stylesheet property restores previous rendering instantly without code change.
- **Code rollback**: The two-PR delivery (Sort PR first, HIDE IF EMPTY second) provides independent rollback points. HIDE IF EMPTY can be reverted without affecting the sort implementation.
- **Worst case**: Both features are confined to `RelationLayout.measure()` and `RelationLayout.extractFrom()`. A revert of those two methods restores exact prior behavior. No schema migration, no persistent state, no external service changes.

### 5. Are there cross-cutting concerns with other RDRs?

Yes — several active interactions:

- **RDR-013 (ContentWidthStats cache)**: Filtering changes the effective dataset that `measure()` operates on. Any cached `MeasureResult` or `frozenResult` computed against the unfiltered dataset is stale when `hideIfEmpty` is toggled. RDR-013's cache invalidation contract (stylesheet version counter increment on `setOverride()`) must be in place and must cover this case. This is called out explicitly in the Combined Pipeline section of this RDR.

- **RDR-009 (LayoutStylesheet)**: Both features add properties to the stylesheet registry (`hide-if-empty`, `sort-fields`). RDR-009 Phase C (SchemaPath resolution) must be complete or this RDR's stylesheet integration must be wired in the same phase.

- **RDR-015 (Alternative Rendering Modes)**: If a rendering mode switch (outline ↔ nested table) is triggered mid-session, the filtered/sorted data view must be consistent across both rendering paths. The Combined Pipeline section's requirement that sort and filter apply in both `measure()` and `extractFrom()` covers this — the cell-building path (which feeds both renderers) sees the same pre-processed data.

- **RDR-016 (Layout Stability / Incremental Update)**: If incremental update recomputes only a subtree (not the full layout), and that subtree's cardinality changed due to filtering, the parent layout metrics must be re-evaluated. RDR-016's incremental strategy must account for cardinality changes propagating upward.

- **Integration test fixture (this RDR)**: The canonical JSON fixture introduced in this RDR (Section: Integration Test Fixture) is designed to exercise the composed pipeline from RDR-013, 014, 015, and 016. Coordination needed to ensure later RDRs extend rather than replace this fixture.

---

## Recommendation

Implement as **two sequential PRs**:

1. **PR 1: Stable Sort** — simpler, no regression risk (additive behavior only when sortFields configured or autoSort enabled). Clear test surface: verify ordering of items with numeric IDs, mixed types, null fields.

2. **PR 2: HIDE IF EMPTY** — depends on sort being stable (filter after sort). Has the autoFold guard constraint, dual data-path requirement, and stylesheet integration. Independent rollback if issues arise.

Both features modify the `RelationLayout.measure()` data extraction path, but sequential delivery provides a cleaner test surface and independent rollback for each feature. Can proceed independently of RDR-009 Phase C — the stylesheet integration can be wired later.
