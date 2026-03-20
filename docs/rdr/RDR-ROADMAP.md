# RDR Execution Roadmap

## Completed Waves (RDR-001 through RDR-028)

Waves 1-4 (RDR-011 through RDR-019) and the SIEUFERD layer integration (RDR-020/021/024/026/027/028) are **all complete**. 24 RDRs closed. See git history for details.

## Forward Roadmap: Bakke Gap Analysis (2026-03-19)

Gap analysis against all 4 Bakke papers identified remaining work. Organized by dependency wave.

### Wave 6: Interaction Surface (P0 — highest leverage)

The complete backend is built; this wave adds the user-facing controls.

| Step | Bead | RDR / Phase | Effort | Depends On |
|------|------|------------|--------|------------|
| 1 | Kramer-75nz | RDR-029 Phase 1: Column header sort/filter indicators | Medium | None (all backend done) |
| 2 | Kramer-75nz | RDR-029 Phase 2: Context menu system | Medium | Phase 1 |
| 3 | Kramer-75nz | RDR-029 Phase 3: Field selector panel | Medium | Phase 1 |

### Wave 7: Query Construction UI (P1)

| Step | Bead | RDR / Phase | Effort | Depends On |
|------|------|------------|--------|------------|
| 4 | Kramer-75nz | RDR-029 Phase 4: Formula/filter editor | High | Phase 2 |
| 5 | Kramer-dsxh | RDR-030 Phase 1: Schema tree panel | Medium | None (LayoutQueryState + InteractionHandler already exist) |
| 6 | Kramer-dsxh | RDR-030 Phase 2: Field properties inspector | Medium | Phase 5 |
| 7 | Kramer-xkqj | Undo/Redo for query operations | Medium | Requires new RDR (design excluded by RDR-027) |

### Wave 8: SIEUFERD Property Completion (P2-P3)

| Step | Bead | Description | Effort | Depends On |
|------|------|-------------|--------|------------|
| 8 | Kramer-8ic2 | CellFormat property | Medium | RDR-029 Phase 4 (editor) |
| 9 | Kramer-knh6 | AggregatePosition rendering | Medium | None (display concern, independent of context menus) |
| 10 | Kramer-iqkr | CollapseDuplicates property | Low-Med | None |
| 11 | Kramer-t36s | Frozen columns/rows | Medium | None |
| 12 | Kramer-i1kz | Drag-to-resize columns | Medium | None |

### Wave 9: Algorithm Refinement (P3)

| Step | Bead | Description | Effort | Depends On |
|------|------|-------------|--------|------------|
| 13 | Kramer-me5a | DP-optimal column partitioning (F2) | High | None |
| 14 | Kramer-er73 | DP-optimal height balancing (F3) | High | Step 13 |
| 15 | Kramer-10vi | Join manipulation UI | High | RDR-030 Phase 2 |

### Wave 10: Exploratory / Long-Term (P4)

| Step | Bead | Description | Effort | Depends On |
|------|------|-------------|--------|------------|
| 16 | Kramer-dsxh | RDR-030 Phases 3-4: Schema diagram + introspection | High | Phase 6 |
| 17 | Kramer-75nz | RDR-029 Phase 5: Keyboard shortcuts | Low | Phase 2 |
| 18 | — | RDR-025: Animated mode transitions | Medium | None |
| 19 | Kramer-20w4 | Multi-view linked worksheets (CHI 2011) | Very High | RDR-030 |
| 20 | Kramer-s9u4 | UNION / set difference query support | High | RDR-029 Phase 4 |

## Cross-RDR Design Decisions

### MeasureResult Extension Pattern (Owner: RDR-013)
All mode-specific state uses nullable sub-records: ContentWidthStats, NumericStats, PivotStats, SparklineStats.

### LayoutResult Render Mode (Owner: RDR-011 + RDR-015)
LayoutResult carries RelationRenderMode and PrimitiveRenderMode (replacing boolean useTable).

### Cache Invalidation Contract (Owner: RDR-013 + RDR-014)
frozenResult scoped to (SchemaPath, stylesheetVersion). LayoutStylesheet.setOverride() increments version.

### Canonical Measure Pipeline Ordering (Owner: RDR-014 + RDR-015)
The complete data pre-processing pipeline in `RelationLayout.measure()` across all RDRs:
```
extractFrom(datum)
  → sort (RDR-014: stable tuple-identifying sort)
  → filter (RDR-014: hideIfEmpty)
  → collect pivot values (RDR-015: crosstab pivot detection)
  → accumulate statistics (RDR-013: ContentWidthStats per Primitive)
  → measure children
```
This ordering is authoritative. Individual RDRs document their own steps but this is the unified sequence.

### LayoutStylesheet Property Registry (see below)

## LayoutStylesheet Property Registry

Central registry of all property keys across RDRs:

| Key | Type | Default | Owner RDR | Scope |
|-----|------|---------|-----------|-------|
| hide-if-empty | boolean | false | RDR-014 | Relation |
| sort-fields | string | "auto" | RDR-014 | Relation |
| render-mode | string | "auto" | RDR-015 | Both |
| pivot-field | string | "" | RDR-015 | Relation |
| value-field | string | "" | RDR-015 | Relation |
| visible | boolean | true | RDR-018 | Both |
| stat-min-samples | int | 30 | RDR-013 | Primitive |
| stat-convergence-epsilon | double | 1.0 | RDR-013 | Primitive |
| stat-convergence-k | int | 3 | RDR-013 | Primitive |
| sparkline-band-visible | boolean | true | RDR-019 | Primitive |
| sparkline-end-marker | boolean | true | RDR-019 | Primitive |
| sparkline-min-max-markers | boolean | false | RDR-019 | Primitive |
| sparkline-line-width | double | 1.0 | RDR-019 | Primitive |
| sparkline-band-opacity | double | 0.15 | RDR-019 | Primitive |

Convention: kebab-case, CSS-inspired names. All read via LayoutStylesheet.getXxx(SchemaPath, key, default).

## SchemaPath Lifecycle (Standalone Deliverable)
SchemaPath must be derived from SchemaNode tree topology at construction time, not as a measure() side-effect. Affects: RDR-013, 014, 015, 016, 017, 018, 019. Track as standalone bead.

## Integration Test Strategy
Create canonical reference JSON fixture exercising: empty relations, numeric fields, variable-length text, mixed types, deep nesting (3+ levels), array-valued primitives. Use as golden test across all RDR implementations.
