---
title: "Core Algorithm Reevaluation & Bakke 2013 Alignment"
id: RDR-008
type: Architecture
status: accepted
accepted_date: 2026-03-14
priority: P1
author: Hal Hildebrand
reviewed-by: self
created: 2026-03-14
related_issues:
  - "RDR-001 through RDR-007 (all closed)"
  - "kramer-gap-analysis-consolidated-summary (T3)"
  - "kramer-gap-analysis-layout-algorithm (T3)"
  - "kramer-gap-analysis-three-phase-pipeline (T3)"
---

# RDR-008: Core Algorithm Reevaluation & Bakke 2013 Alignment

## Relationship to RDR-007

RDR-007 ("Comprehensive Layout Engine Bug Remediation", closed 2026-03-10) stated: *"While the core algorithm faithfully implements the Bakke 2013 paper, implementation-level issues cause [bugs]."* **That characterization was incorrect.** RDR-007's scope was limited to implementation-level bugs (memory leaks, inset stacking, hit testing, measurement inflation). It did not perform a section-by-section comparison of the algorithm against the paper.

This RDR corrects the algorithmic characterization. A full re-read of the Bakke 2013 paper alongside the source code reveals significant algorithmic divergences — not just implementation bugs. The bugs RDR-007 addresses remain valid and independent of the algorithmic divergences documented here.

**Interaction with RDR-007 Phase 3**: RDR-007 Phase 3 corrects the measurement foundation (`LabelStyle.getLineHeight()` inflation, double-counted padding). These measurement corrections change all pixel widths in the system, which affects the calibration of any new defaults introduced here (particularly F1). See Phase 1 ordering notes below.

**Interaction with RDR-006**: The table/outline decision logic (RF-9, classified as POSITIVE) reflects the post-RDR-006-Fix-1 state. RDR-006 corrected the comparison from `tableWidth <= columnWidth()` to `tableWidth + nestedHorizontalInset <= width`. RF-9's positive classification is a recently-fixed finding, not a historically-positive one.

## Context

A thorough re-read of the full Bakke 2013 paper ("Automatic Layout of Structured Hierarchical Reports", InfoVis 2013) alongside a complete analysis of all core module source code reveals several significant divergences between Kramer's implementation and the paper's specified algorithm. Prior RDRs (001-007) addressed specific bugs and feature gaps but did not question the fundamental algorithm pipeline. This RDR addresses the structural and algorithmic issues at the foundation.

## Problem Statement

The Kramer autolayout engine diverges from Bakke 2013 in ways that affect layout quality, performance, and maintainability:

1. **The most impactful behavioral bug**: `maxTablePrimitiveWidth` defaults to `Double.MAX_VALUE`, meaning variable-length primitive columns have no width cap. The paper specifies ~50 characters. This causes wide description fields to inflate table width, preventing table mode from triggering and forcing unnecessarily verbose outline layouts.

2. **Mutable state machine architecture**: The paper uses a clean stylesheet data structure (schema path → property values) with immutable phase boundaries. Kramer stores all three phases' state (measurement results, layout decisions, render parameters) as mutable fields on the same `SchemaNodeLayout` objects. This requires fragile `clear()` calls and per-field reasoning about which values survive across phases.

3. **Expensive uncached style measurement**: The paper assumes pre-configured stylesheet constants. Kramer creates temporary off-screen `Scene` objects per schema node per `measure()` call to read CSS-computed insets. No caching — same insets are re-measured on every data change.

4. **Algorithm deviations in column partitioning and column set formation** that produce suboptimal layouts in certain configurations.

## Analysis

### Pipeline Architecture (Paper §3.5)

**Paper**: Measure → Auto-Style → Layout, with a separate stylesheet data structure.

- **Measure**: First pass over input data. Computes average rendered width of each primitive and average cardinality of each relation.
- **Auto-Style**: Pass over schema only. Sets remaining stylesheet properties (table/outline decisions, column widths, vertical headers, column partitioning).
- **Layout**: Second pass over input data. Constructs output using the stylesheet.

**Kramer**: `measure()` → `layout()` + `compress()` → `calculateRootHeight()` → `distributeExtraHeight()` → `buildControl()`

The phase mapping is conceptually correct:
- `measure()` → Paper's Measure
- `layout()` + `compress()` → Paper's Auto-Style
- `buildControl()` → Paper's Layout

The critical divergence is that Kramer conflates all state into mutable `SchemaNodeLayout` objects instead of a separate stylesheet. This manifests as:
- Comments like `"isVariableLength must NOT be reset here"` (PrimitiveLayout.java:287)
- The need for `clear()` at the start of each phase to reset previous-phase state
- Fields that serve double duty across phases (e.g., `columnWidth` computed in measure, used in layout)

### Table Column Width Cap (Paper §3.3 — CRITICAL)

Paper: *"For variable-width primitive fields, we experimented with various heuristics, and found the average width of values in the field to be a sensible minimum, limited upwards to a constant value (on the order of 50 characters, a standard book column size)."*

Kramer: `PrimitiveStyle.maxTablePrimitiveWidth = Double.MAX_VALUE`

**Impact**: A description field averaging 400px wide has no cap, so `calculateTableColumnWidth()` returns 400px. The parent's table width exceeds available space, forcing outline mode. With the paper's cap (~300px for 50 chars at typical font), the table would fit and produce a much more compact layout.

**Note**: Both `PrimitiveLayout.calculateTableColumnWidth()` (line 88) and `PrimitiveLayout.tableColumnWidth()` (line 248) return `Math.min(dataWidth, style.getMaxTablePrimitiveWidth())`. This duplication means both methods will see the changed default, but the duplication itself should be eliminated during implementation.

### Column Set Formation (Paper §3.4 — MEDIUM)

Paper: Only **outline-mode relations** are excluded from column sets. The paper says: *"any relation field in an outline is excluded from participating in a set of multiple columns if that would cause it to be rendered as an outline."*

Kramer adds: `childWidth > halfWidth` — wide primitives also break column sets. This is a heuristic extension not in the paper. It prevents very unbalanced columns but may produce suboptimal layouts by creating unnecessary single-field column sets.

**Note**: The `halfWidth` guard serves as a conservative proxy that prevents the worst column partitioning failures in the absence of the paper's DP algorithm. Removing it without implementing DP would likely degrade layouts (see F2/F3 coupling below).

### Column Partitioning Algorithm (Paper §3.4 — MEDIUM)

Paper: *"A better approach is to split the columns in such a way as to minimize the total vertical space consumed; this can be done easily with a dynamic programming routine."*

Kramer: Uses a greedy slide-right heuristic in `ColumnSet.compress()`. While effective for most cases, it doesn't guarantee optimal partitions for all configurations. The paper specifically recommends DP.

### Missing OutlineSnapValueWidth (Paper Table 1 — LOW)

Paper: *"Multiple to round up to when setting the width of a non-variable primitive value in an outline sublayout."*

Kramer: Not implemented. Non-variable-length field widths are used directly without grid snapping, reducing visual consistency across fields.

### Style Measurement Performance (PERFORMANCE)

`Style.style(Primitive)` and `Style.style(Relation)` each create temporary `Scene` objects with dummy nodes, apply CSS, and read computed insets. This happens for every schema node during `measure()` via `model.layout(node)` → `new PrimitiveLayout(p, style(p))`. For a schema with N nodes, N scenes are created. No caching between `measure()` calls.

### Hardcoded VARIABLE_LENGTH_THRESHOLD (LOW)

`PrimitiveLayout.VARIABLE_LENGTH_THRESHOLD = 2.0` is hardcoded. The paper treats all properties as configurable via the stylesheet.

## Proposed Changes

### Phase 1: Critical Behavioral Fix

**Prerequisite**: F1 should be implemented **after** RDR-007 Phase 3 (measurement foundation correction) is complete. The appropriate default for `maxTablePrimitiveWidth` depends on correct font metric values. Calibrate the default against the paper's course catalog example only after the measurement baseline is corrected.

**F1. Set sensible default for `maxTablePrimitiveWidth`**
- Change default from `Double.MAX_VALUE` to a value approximating 50 characters at the current font (~300-350px, calibrated after RDR-007 Phase 3 measurement correction)
- Derive from font metrics where possible (e.g., `50 * averageCharWidth`)
- **Breaking change**: This changes layout behavior for every existing user. Schemas with wide text fields will switch from outline mode to table mode. The existing setter `PrimitiveStyle.setMaxTablePrimitiveWidth(Double.MAX_VALUE)` provides a migration path for users who need the prior behavior. This change warrants a major or minor version bump with release notes.
- **Note on CSS configurability**: Kramer does not currently have `CssMetaData`/`StyleableProperty` infrastructure (per RDR-006 Fix 2 which deferred this). Making the default CSS-configurable requires new infrastructure and is out of scope for F1. The programmatic setter is sufficient.
- Expected impact: table mode will trigger much more often for schemas with description/text fields, producing dramatically more compact layouts

### Phase 2: Algorithm Corrections

**F2. Remove `halfWidth` exclusion from column sets (COUPLED with F3)**
- Only exclude outline-mode relations, per the paper
- **Must be implemented together with F3.** The `halfWidth` guard currently compensates for the greedy partitioner's inability to handle mixed-width fields well. Removing it without DP would create column sets where a 400px field and three 30px fields share a column set, producing suboptimal partitions. Leave the `halfWidth` guard in place until DP partitioning is implemented.

**F3. Implement DP column partitioning (COUPLED with F2)**
- Replace greedy slide-right with DP minimizing total vertical space
- Use schema-only layouts for the optimization (per paper)
- **Evaluation criterion**: F3 will be implemented if visual regression tests or user reports demonstrate layouts where greedy partitioning produces suboptimal column splits. If the greedy approach produces acceptable results for all tested schemas, F3 may be deferred — but F2 must also remain deferred.

**F4. Implement `OutlineSnapValueWidth`**
- Add configurable snap grid for non-variable-length field widths
- Improves visual consistency in outline layouts

**F5. Make `VARIABLE_LENGTH_THRESHOLD` configurable**
- Move to `PrimitiveStyle` alongside other configurable parameters

### Phase 3: Architectural Improvement

**F6. Introduce stylesheet data structure**

This is a significant architectural refactor — effectively a module redesign rather than a simple refactoring. **F6 is gated on a separate design document** that must address:

1. **Schema path abstraction**: No path abstraction currently exists in Kramer. Define how schema nodes are addressed in the stylesheet (field name path? tree position? identity?).
2. **`LayoutStylesheet` interface**: What maps to what — schema path → property value? How are per-phase properties distinguished?
3. **Call site inventory**: All methods on `SchemaNodeLayout`, `PrimitiveLayout`, and `RelationLayout` that read mutable phase state must be identified and migrated. This includes ~50 mutable fields across the two concrete classes.
4. **Interactive resize path**: `AutoLayout.resize()` → `layout.autoLayout()` assumes mutable layout objects. The immutable phase result model must accommodate re-layout at different widths without re-measuring.
5. **Migration strategy**: Can Phases 1 and 2 algorithmic changes be done in the current mutable architecture? (Yes — F1-F5 are independent of F6.) F6 should follow, not precede, algorithmic corrections.
6. **Incremental vs big-bang**: Can the refactor be done incrementally (extract one phase at a time) or must it be a single large change?

Phases 1 and 2 should proceed in the current architecture. F6 follows after the design document is reviewed and accepted as a separate RDR.

**F7. Cache style measurements**
- Cache CSS-computed insets keyed by (styleClass, stylesheets) rather than re-measuring per schema node
- Alternatively: measure once per unique style configuration, not per schema node
- **Cache invalidation**: The cache must be cleared when `Style.setStyleSheets()` is called. The existing `layout = null` path in `AutoLayout` (line 83) triggers remeasurement on stylesheet change, but a standalone style cache in `Style` must also be invalidated on that path.
- **Benchmark**: Establish a baseline measurement of `measure()` time for a schema of N nodes before and after F7 to verify the improvement.

### Phase 4: Performance

**F8. Lazy/cached layout object creation**
- Don't recreate `SchemaNodeLayout` objects on every `measure()` call when the schema hasn't changed
- Separate data-dependent measurements from schema-dependent structure

## Risks and Considerations

- **F1** is medium risk — low implementation complexity but a **behavioral breaking change** for existing users. The existing `setMaxTablePrimitiveWidth(Double.MAX_VALUE)` setter provides a migration path. Must be calibrated after RDR-007 Phase 3 measurement corrections.
- **F2-F3** are coupled and medium risk — removing the `halfWidth` guard without DP degrades layouts. Implement together or defer both.
- **F4-F5** are low risk — additive features with no breaking changes
- **F6** is high risk — near-module-rewrite touching every class in the layout pipeline. Gated on separate design document. Should follow F1-F5.
- **F7** is medium risk — requires correct cache invalidation on stylesheet change
- **F8** is medium risk — requires careful invalidation when schema changes
- All changes should be validated against a committed test fixture representing the paper's course catalog data
- Visual regression testing is essential since layout changes affect rendered output

## Success Criteria

1. Table mode triggers for schemas with description/text fields (currently forced to outline by uncapped table width)
2. A committed JSON test fixture representing the paper's course catalog schema produces verifiable assertions: table mode chosen for flat relations, correct number of column sets, correct column partitioning. This fixture and its assertions constitute the visual regression baseline.
3. No performance regression for F1-F5; F7 reduces `measure()` time (measured by benchmark test comparing before/after for a schema of N nodes)
4. Phase boundaries are clean — no fields that "must survive" across phases (F6 criterion, deferred to design document)
5. All existing tests pass; new tests cover paper-specified behavior
6. Existing users can restore prior behavior via `setMaxTablePrimitiveWidth(Double.MAX_VALUE)` — documented in release notes

## Research Findings

### RF-1: maxTablePrimitiveWidth Default Prevents Table Mode (CRITICAL)
- **Status**: Verified
- **Source**: Code analysis (PrimitiveStyle.java:185) + Paper §3.3
- **Finding**: `PrimitiveStyle.maxTablePrimitiveWidth` defaults to `Double.MAX_VALUE`. Paper §3.3 states: *"the average width of values in the field [is] a sensible minimum, limited upwards to a constant value (on the order of 50 characters, a standard book column size)."* With no cap, `calculateTableColumnWidth()` returns the full average data width for variable-length fields (e.g., 400px for description text). The parent's `layout()` compares `tableWidth + nestedHorizontalInset <= width` — uncapped child widths push tableWidth beyond available width, forcing outline mode. This is the single most impactful behavioral divergence from the paper.
- **Evidence**: `PrimitiveLayout.calculateTableColumnWidth()` at line 88: `Math.min(dataWidth, style.getMaxTablePrimitiveWidth())` — with MAX_VALUE, the min is always dataWidth. Note: `PrimitiveLayout.tableColumnWidth()` at line 248 is an identical duplicate.
- **Impact**: Schemas with description/text fields almost never trigger table mode.

### RF-2: Pipeline Architecture — Mutable State Machine (HIGH)
- **Status**: Verified
- **Source**: Code analysis (SchemaNodeLayout.java, RelationLayout.java, PrimitiveLayout.java) + Paper §3.5
- **Finding**: Paper uses a separate stylesheet data structure mapping schema paths to property values. Each phase reads/writes immutable results. Kramer stores measurement results, layout decisions, and render parameters as mutable fields on the same `SchemaNodeLayout` objects. Evidence of fragility: `PrimitiveLayout.clear()` (line 284-291) contains comment: *"isVariableLength must NOT be reset here — it is set in measure() and must survive through layout() into compress()"*. Fields serving double duty: `columnWidth` computed in `measure()`, overwritten in `layout()`. `useTable` set `false` in `clear()`, potentially overridden by parent's `nestTableColumn()`.
- **Evidence**: `SchemaNodeLayout` has 8 mutable fields shared across 3 phases. `clear()` must be called at phase boundaries but cannot reset everything.

### RF-3: Style Measurement Creates N Scene Objects (PERFORMANCE)
- **Status**: Verified
- **Source**: Code analysis (Style.java:158-194, Style.java:196-252)
- **Finding**: `Style.style(Primitive)` creates a temporary `Scene(root, 800, 600)`, adds stylesheets, calls `applyCss()` and `layout()` to read computed insets. Called via `model.layout(p)` → `new PrimitiveLayout(p, style(p))` for every schema node during `measure()`. `Style.style(Relation)` is even heavier — creates 9 temporary nodes (NestedTable, NestedRow, NestedCell, Outline, OutlineCell, OutlineColumn, OutlineElement, Span, LayoutLabel). No caching between calls. For a schema with N nodes, N scenes created per `measure()` invocation.
- **Evidence**: `style(Primitive)` at line 158, `style(Relation)` at line 196. Both instantiate `new Scene(root, 800, 600)`.

### RF-4: Column Set Formation Diverges from Paper (MEDIUM)
- **Status**: Verified
- **Source**: Code analysis (RelationLayout.java:240-254) + Paper §3.4
- **Finding**: Paper §3.4 says only outline-mode relations are excluded from column sets: *"any relation field in an outline is excluded from participating in a set of multiple columns if that would cause it to be rendered as an outline."* Kramer adds `childWidth > halfWidth` at line 244: wide primitives also break column sets. This is a heuristic extension not specified in the paper. It may produce suboptimal layouts by creating unnecessary single-field column sets for wide-but-not-relation fields. However, this guard compensates for the absence of DP column partitioning (RF-5) — removing it without DP would degrade layouts.
- **Evidence**: `boolean excluded = (child instanceof RelationLayout rl) && !rl.isUseTable(); if (excluded || childWidth > halfWidth || current == null)` — the `childWidth > halfWidth` condition is not in the paper.

### RF-5: Greedy Column Partitioning vs Paper's DP (MEDIUM)
- **Status**: Verified
- **Source**: Code analysis (ColumnSet.java:51-97, Column.java:76-92) + Paper §3.4
- **Finding**: Paper §3.4: *"A better approach is to split the columns in such a way as to minimize the total vertical space consumed; this can be done easily with a dynamic programming routine."* Kramer uses a greedy slide-right algorithm: iteratively moves the last field from left column to right column while it reduces max height. Converges but doesn't guarantee optimal partition. Paper also specifies using schema-only layouts for the optimization. Kramer uses measured data (average cardinality) which is functionally similar but not identical.
- **Evidence**: `ColumnSet.compress()` lines 78-93: `while (columns.get(i).slideRight(cardinality, columns.get(i + 1), style, fieldWidth)) {}`

### RF-6: Missing OutlineSnapValueWidth (LOW)
- **Status**: Verified
- **Source**: Paper Table 1
- **Finding**: Paper Table 1 defines `OutlineSnapValueWidth`: *"Multiple to round up to when setting the width of a non-variable primitive value in an outline sublayout."* This snaps non-variable-length field widths to a grid for visual consistency (e.g., round up to nearest 10px). Not implemented anywhere in Kramer. Non-variable fields use exact measured widths.
- **Evidence**: No snap logic in `PrimitiveLayout.compress()` (line 121-129) or `PrimitiveLayout.measure()`.

### RF-7: VARIABLE_LENGTH_THRESHOLD Hardcoded (LOW)
- **Status**: Verified
- **Source**: Code analysis (PrimitiveLayout.java:45)
- **Finding**: `VARIABLE_LENGTH_THRESHOLD = 2.0` is a `static final` constant. Paper treats all properties as configurable via the stylesheet. The threshold determines whether a field is treated as variable-length (ratio of max/average width). Should be configurable per `PrimitiveStyle` like other parameters.
- **Evidence**: Line 45: `static final double VARIABLE_LENGTH_THRESHOLD = 2.0;`

### RF-8: Paper's Three-Phase Execution Correctly Mapped (POSITIVE)
- **Status**: Verified
- **Source**: Code analysis + Paper §3.5
- **Finding**: Despite the mutable state issue, the functional mapping of paper phases to code is correct: `measure()` computes data statistics (Paper's Measure), `layout()`+`compress()` make width decisions without re-reading data (Paper's Auto-Style), `buildControl()` constructs UI (Paper's Layout). The `measure()` → `autoLayout()` separation (measure once, re-layout on resize) is a sound adaptation for interactive use that the paper's batch-oriented design doesn't address.

### RF-9: Table/Outline Decision Correctly Implemented (POSITIVE — post-RDR-006)
- **Status**: Verified
- **Source**: Code analysis (RelationLayout.java:307-324) + Paper §3.3
- **Finding**: The bottom-up recursive table/outline decision matches the paper exactly. Each child decides independently, then the parent checks `calculateTableColumnWidth() + nestedHorizontalInset <= width`. When a parent chooses table, `nestTableColumn()` correctly forces all children to table mode. The `calculateTableColumnWidth()` computation (sum of children + insets) matches the paper's description. **Note**: This positive classification reflects the post-RDR-006-Fix-1 state. Prior to RDR-006, the comparison was `tableWidth <= columnWidth()`, which did not account for nested insets and was incorrect per the paper.

### RF-10: Auto-Folding is a Kramer Extension (NEUTRAL)
- **Status**: Verified
- **Source**: Code analysis (Relation.java:41-49, RelationLayout.java:472-489)
- **Finding**: `Relation.getAutoFoldable()` skips single-child intermediate relations, flattening data to the grandchild's layout. Not described in the paper. This is a useful optimization for deeply nested schemas with single-child relations (common in GraphQL schemas). Does not conflict with any paper algorithm — operates before the main pipeline. Edge cases (interaction with table mode decisions, cardinality visibility to parent) are out of scope for this RDR but should be evaluated in a future alignment audit.
