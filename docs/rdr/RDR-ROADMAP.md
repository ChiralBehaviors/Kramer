# RDR Execution Roadmap (011-019)

## Optimal Execution Sequence

Phase priorities (P1/P2) reflect strategic importance, not execution order.
Individual phases within RDRs should be executed based on dependencies and effort.

### Wave 1: Zero-Dependency Quick Wins
| Step | RDR / Phase | Effort | Description |
|------|------------|--------|-------------|
| 1 | RDR-016 Phase 1 | Low | Outline scroll preservation fix (OptionalInt) |
| 2 | RDR-013 Phase 1 | Low | P90 statistical width for variable-length columns |
| 3 | RDR-014 PR1 | Low | Stable tuple-identifying sort |
| 4 | RDR-017 Phases 1+3 | Low | Data-level search + search bar UI |

### Wave 2: Low-Dependency Features
| Step | RDR / Phase | Effort | Depends On |
|------|------------|--------|------------|
| 5 | RDR-014 PR2 | Medium | RDR-014 PR1 |
| 6 | RDR-015 Phase 1 (bar) | Low | None |
| 6b | RDR-015 Phase 2 (badge) | Low | None |
| 7 | RDR-011 Phase 1 | Low | None (RDR-009 already done) |
| 8 | RDR-016 Phase 2 | Low-Med | None |

### Wave 3: Architectural Integration
| Step | RDR / Phase | Effort | Depends On |
|------|------------|--------|------------|
| 9 | SchemaPath lifecycle fix | Low | Standalone deliverable |
| 10 | RDR-011 Phase 2 | Medium | Phase 1 + RDR-015 renderMode in LayoutResult |
| 11 | RDR-018 Phase 1 | Low-Med | RDR-014 + RDR-015 done |
| 12 | RDR-013 Phase 2 | Low | Phase 1 + cache invalidation contract |
| 13 | RDR-015 Phase 3 (crosstab) | Medium | Phase 1 + pipeline ordering |
| 14 | RDR-019 Phase 1-2 | Medium | MeasureResult sub-record pattern + PrimitiveRenderMode.SPARKLINE enum value (from RDR-015 enum, added when RDR-019 accepted) |

### Wave 4: Protocol & Rendering
| Step | RDR / Phase | Effort | Depends On |
|------|------------|--------|------------|
| 15 | RDR-011 Phase 3 (HTML) | Medium | Phase 2 |
| 16 | RDR-016 Phase 4-5 | Medium | RDR-011 |
| 17 | RDR-017 Phase 2b (nested) | Med-High | FocusController API + async coordination |

### Wave 5: Research / Long-Term
| Step | RDR / Phase | Effort | Depends On |
|------|------------|--------|------------|
| 18+ | RDR-012 Phase 1 | Multi-sprint | RDR-011 Phase 1 |
| 19+ | RDR-018 Phase 2 | High | Expression language spec |
| 20+ | RDR-012 Phases 2-4 | Multi-quarter | Phase 1 |
| 21+ | RDR-018 Phase 3 | High | GraphQlUtil redesign (separate RDR) |

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
