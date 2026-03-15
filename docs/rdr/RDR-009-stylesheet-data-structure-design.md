# RDR-009: Stylesheet Data Structure Design

## Metadata
- **Type**: Architecture
- **Status**: draft
- **Priority**: P2
- **Created**: 2026-03-15
- **Related**: RDR-008 (F6), Kramer-4k0

## Problem Statement

Kramer's layout pipeline uses mutable fields on `SchemaNodeLayout` (and its sealed subtypes `PrimitiveLayout`, `RelationLayout`) to carry state across three pipeline phases: **measure**, **layout/compress**, and **build**. The paper (Bakke 2013 §3.5) describes a three-phase pipeline where each phase produces an immutable result consumed by the next, with a stylesheet providing configurable properties.

Currently Kramer has ~20 mutable fields across the hierarchy, mixed between data-dependent, width-dependent, and derived classifications. There is no schema path abstraction, no stylesheet data structure, and no separation of phase results. This makes it difficult to reason about state, debug layout issues, or extend the system.

This RDR addresses the 6 design questions identified in RDR-008 F6.

---

## Question 1: Schema Path Abstraction

### Current State

No path abstraction exists. Schema nodes are identified by field name (`getField()`), which is used as:
- CSS style class for JavaFX nodes
- Key for style caches in `Style` (F7 cache uses field name)
- Layout cache key (F8 cache uses `SchemaNode` object identity)

Field names are unique within a single JSON object but NOT unique within a schema tree — nested relations can reuse field names at different levels (e.g., `root.items.items`).

### Proposed Design

Introduce `SchemaPath` as a lightweight value type:

```java
public record SchemaPath(List<String> segments) {
    public SchemaPath(String... segments) {
        this(List.of(segments));
    }

    public SchemaPath child(String field) {
        var extended = new ArrayList<>(segments);
        extended.add(field);
        return new SchemaPath(List.copyOf(extended));
    }

    public String leaf() {
        return segments.getLast();
    }

    public String cssClass() {
        return leaf(); // CSS class uses leaf name (backward compatible)
    }

    @Override
    public String toString() {
        return String.join("/", segments);
    }
}
```

**Usage**: Built incrementally during `measure()` traversal. Each `SchemaNodeLayout` stores its `SchemaPath`. The stylesheet maps `SchemaPath` → property overrides.

**Backward compatibility**: `cssClass()` returns the leaf field name, preserving existing CSS behavior. The path is additive — old code that uses `getField()` continues to work. **Caveat**: The prerequisite F7 cache fix (changing style cache keys from field name to SchemaNode identity) means two primitives named "name" at different nesting levels will get separately computed styles. If those styles differ due to CSS specificity, the visual output changes. In practice this is unlikely — current CSS applies the same rules to all nodes with the same field name — but it should be verified when implementing the prerequisite fix.

---

## Question 2: LayoutStylesheet Interface

### Current State

Style properties are distributed across three classes:
- `PrimitiveStyle` — 7 configurable properties (maxTablePrimitiveWidth, variableLengthThreshold, outlineSnapValueWidth, verticalHeaderThreshold, minValueWidth, plus label/list insets)
- `RelationStyle` — 10+ configurable properties (outlineMaxLabelWidth, outlineColumnMinWidth, bulletText/Width, indentWidth, maxAverageCardinality, plus insets from 8 JavaFX regions)
- `Style` — CSS stylesheet list, style factory methods, layout observer

Properties are set programmatically via setters or derived from CSS. There is no per-path override mechanism.

### Proposed Design

```java
public interface LayoutStylesheet {
    /**
     * Get a property value for a schema path, with fallback to defaults.
     * Property names follow CSS convention: e.g., "max-table-primitive-width".
     */
    double getDouble(SchemaPath path, String property, double defaultValue);
    int getInt(SchemaPath path, String property, int defaultValue);
    String getString(SchemaPath path, String property, String defaultValue);

    /**
     * Convenience: get the full PrimitiveStyle for a path.
     * Returns default style with per-path overrides applied.
     */
    PrimitiveStyle primitiveStyle(SchemaPath path);

    /**
     * Convenience: get the full RelationStyle for a path.
     */
    RelationStyle relationStyle(SchemaPath path);
}
```

**Implementation**: `DefaultLayoutStylesheet` wraps the existing `Style` object and its CSS-derived styles. Per-path overrides are stored in a `Map<SchemaPath, Map<String, Object>>`. The `primitiveStyle()` and `relationStyle()` methods return cached style objects with overrides applied.

**Phase property distinction**: Not needed at the stylesheet level. Properties are declarative (e.g., "max-table-primitive-width = 350"). The pipeline phases read properties; they don't write them. Phase-specific computed state (data widths, cardinalities) stays on the layout objects, not the stylesheet.

---

## Question 3: Call Site Inventory — Mutable Field Catalog

### SchemaNodeLayout (base, 6 mutable fields)

| Field | Type | Phase Set | Classification | Reset by clear() |
|-------|------|-----------|---------------|-------------------|
| `columnHeaderIndentation` | double | nestTableColumn | Width-dependent | Yes |
| `columnWidth` | double | measure | Data-dependent | No |
| `height` | double | cellHeight, adjustHeight | Derived | Yes |
| `justifiedWidth` | double | compress, justify | Width-dependent | Yes |
| `labelWidth` | double | measure | Schema-dependent | No |
| `rootLevel` | boolean | autoLayout (temporary) | Pipeline flag | No |

### PrimitiveLayout (7 mutable fields + 4 inherited)

| Field | Type | Phase Set | Classification | Reset by clear() |
|-------|------|-----------|---------------|-------------------|
| `averageCardinality` | int | measure | Data-dependent | No |
| `dataWidth` | double | measure | Data-dependent | No |
| `isVariableLength` | boolean | measure | Data-dependent | **No** (intentional) |
| `maxWidth` | double | measure | Data-dependent | No |
| `cellHeight` | double | cellHeight | Height-phase intermediate | No (but recalculated when `height` is reset) |
| `useVerticalHeader` | boolean | nestTableColumn | Width-dependent | Yes |
| `style` | PrimitiveStyle | constructor | Immutable | — |

### RelationLayout (11 mutable fields + 4 inherited)

| Field | Type | Phase Set | Classification | Reset by clear() |
|-------|------|-----------|---------------|-------------------|
| `averageChildCardinality` | int | measure | Data-dependent | No |
| `cellHeight` | double | compress, cellHeight | Derived | No |
| `children` | List\<SNL\> | measure | Schema+Data | No |
| `columnHeaderHeight` | double | columnHeaderHeight (lazy) | Derived | Yes |
| `columnSets` | List\<CS\> | compress | Width-dependent (CompressResult) | No* |
| `extractor` | Function | fold (during measure) | Data-dependent | No |
| `maxCardinality` | int | measure | Data-dependent | No |
| `resolvedCardinality` | int | cellHeight | Derived | No |
| `tableColumnWidth` | double | nestTableColumn | Width-dependent | Yes |
| `useTable` | boolean | layout | Width-dependent | Yes |
| `style` | RelationStyle | constructor | Immutable | — |

*\* `columnSets` is not reset by `clear()` but IS rebuilt at the start of every `compress()` call (line 235: `columnSets.clear()`). It does not survive a resize — it belongs in CompressResult, not MeasureResult.*

**Total: 24 mutable fields** (6 base + 7 primitive + 11 relation). RDR-008 estimated "~50 mutable fields" — the overcount likely included method-local state, the inherited fields counted separately per subclass, and accessor methods conflated with fields.

### Classification Summary

- **Data-dependent** (9): Set in `measure()`, persist across layout/compress. These capture the data distribution (widths, cardinalities, variable-length classification).
- **Width-dependent** (7): Set in `layout()`/`compress()`/`nestTableColumn()`. Change when available width changes. Reset by `clear()`.
- **Derived** (6): Computed from other fields during `cellHeight()`, `columnHeaderHeight()`. Could be computed on-demand.
- **Schema-dependent** (1): `labelWidth` — same for same schema.
- **Pipeline flag** (1): `rootLevel` — temporary, set/unset within `autoLayout()`.

---

## Question 4: Interactive Resize Path

### Current Architecture Already Supports This

The current code **already separates measurement from layout**:

```
AutoLayout.resize(width)
  └─ autoLayout(data, width)
     ├─ if (layout == null) → measure(data)   // ONLY on first call or stylesheet change
     └─ layout.autoLayout(width, ...)          // Always runs
        ├─ layout(width)      // determines table vs outline
        ├─ compress(width)    // distributes width
        ├─ calculateRootHeight()
        └─ buildControl()     // creates JavaFX nodes
```

The `layout` field persists across resizes. Only the width-dependent fields (7 of 24) are recomputed. The data-dependent fields (9 of 24) survive from the original `measure()` call.

### Immutable Phase Result Model (4 types)

An immutable model would formalize this existing pattern. Research finding RF-2 established that `cellHeight` has a dual lifecycle (set by `compress()` in outline mode vs `calculateTableHeight()` in table mode) and `resolvedCardinality` is call-site-specific. This requires 4 result types, not 2:

```java
// Phase 1 — produced by measure(), cached across resizes
record MeasureResult(
    double labelWidth,
    double columnWidth,        // natural width from data
    double dataWidth,
    double maxWidth,
    int averageCardinality,
    boolean isVariableLength,
    // RelationLayout-specific:
    int averageChildCardinality,
    int maxCardinality,
    Function<JsonNode, JsonNode> extractor,
    List<MeasureResult> childResults
) {}

// Phase 2 — produced by layout(), determines table vs outline
record LayoutResult(
    boolean useTable,
    boolean useVerticalHeader,    // PrimitiveLayout
    double tableColumnWidth,      // table mode
    double columnHeaderIndentation,
    double constrainedColumnWidth, // width-constrained (may differ from measure)
    List<LayoutResult> childResults
) {}

// Phase 3 — produced by compress(), distributes width
record CompressResult(
    double justifiedWidth,
    List<ColumnSet> columnSets,   // outline mode only
    double cellHeight,            // outline path sets this here
    List<CompressResult> childResults
) {}

// Phase 4 — produced by cellHeight()/calculateRootHeight()
record HeightResult(
    double height,
    double cellHeight,            // table path sets this here
    int resolvedCardinality,
    double columnHeaderHeight,
    List<HeightResult> childResults
) {}
```

**Migration path**: Each result type replaces a specific subset of mutable fields. `MeasureResult` replaces the fields that survive `clear()`. `LayoutResult` replaces the table/outline decision fields. `CompressResult` replaces width-distribution fields (including `columnSets` which is rebuilt per compress, not per measure). `HeightResult` replaces sizing fields.

### Impact on Resize

With immutable phase results:
- `measure()` → `MeasureResult` (cached, reused across resizes)
- `layout(width, measureResult)` → `LayoutResult` (recomputed per width)
- `compress(justified, layoutResult)` → `CompressResult` (recomputed per width)
- `calculateHeight(compressResult)` → `HeightResult` (recomputed per width)
- `buildControl(heightResult)` → `LayoutCell` (recomputed per width)

This is structurally identical to the current architecture but makes the data flow explicit. The current `autoLayout()` method on `SchemaNodeLayout` orchestrates these phases — with immutable results, it would return a composite result or `AutoLayout` would orchestrate the phases directly.

---

## Question 5: Migration Strategy

### Recommendation: Incremental (3 phases)

**Phase A: Extract MeasureResult** (LOW risk for PrimitiveLayout, MEDIUM for RelationLayout)
1. Create `MeasureResult` record for `PrimitiveLayout` (6 scalar fields — straightforward)
2. Have `measure()` populate both the record AND existing fields (dual-write)
3. Gradually migrate readers from fields to record
4. Remove mutable fields once all readers use the record
5. Repeat for `RelationLayout` — **this is structurally harder**: the `children` list contains mutable `SchemaNodeLayout` objects that are shared with the layout cache. Dual-write for children means building a `List<MeasureResult>` alongside the mutable `children` list. The mutable list is consumed by `layout()`/`compress()` which call methods on the child objects directly. During migration, the mutable `children` list must be retained until Phase B replaces `layout()`/`compress()` with result-returning methods. The dual-write for `RelationLayout` should build `childResults` from children's MeasureResults after all children are measured.
6. Create test-setup factory methods (e.g., `TestLayouts.primitive(name, dataWidth, ...)`) to replace the ~140 direct field writes in test code

**Phase B: Extract LayoutResult** (MEDIUM risk)
1. Create `LayoutResult` record
2. Have `layout()`/`compress()` populate both record and fields
3. Migrate `buildControl()` and `cellHeight()` to read from record
4. Remove mutable fields

**Phase C: Introduce LayoutStylesheet** (MEDIUM risk)
1. Create `DefaultLayoutStylesheet` wrapping existing `Style`
2. Add `SchemaPath` to layout objects during measure
3. Wire stylesheet property lookups through `LayoutStylesheet` interface
4. Add per-path override support
5. Migrate programmatic style setters to stylesheet API

**Why not big-bang**: The sealed hierarchy (`SchemaNodeLayout permits PrimitiveLayout, RelationLayout`) means all changes to the base class affect both subtypes. Incremental migration with dual-write avoids breaking the pipeline while in-flight. Each phase can be validated with the existing 142-test suite.

**Phase ordering**: A → B → C. Each phase is independently shippable. Phase C is optional — the system works with just A+B (immutable results) without the stylesheet interface.

---

## Question 6: Interaction with F1-F5 Changes

### All F1-F5 changes are independent of F6

| Feature | What it changed | F6 interaction |
|---------|----------------|----------------|
| F1 (maxTablePrimitiveWidth) | Default value on `PrimitiveStyle` | None — property stays on PrimitiveStyle, accessed via stylesheet in Phase C |
| F2+F3 (column sets) | Deferred | None |
| F4 (outlineSnapValueWidth) | New property on `PrimitiveStyle`, logic in `compress()` | None — compress reads from style, F6 doesn't change this |
| F5 (variableLengthThreshold) | Moved constant to `PrimitiveStyle` | None — already on style object, will route through stylesheet |
| F7 (style cache) | HashMap in `Style` | Phase C subsumes this — stylesheet caches styles internally |
| F8 (layout cache) | IdentityHashMap in `Style` | Phase A makes this cleaner — MeasureResult is immutable, no stale-state risk |

**Key insight**: F1-F5 moved configuration FROM code TO style objects. F6 would move it one step further: FROM style objects TO a stylesheet data structure. The direction is consistent; no rework needed.

---

## Risks and Considerations

| Risk | Severity | Mitigation |
|------|----------|------------|
| Dual-write bugs during migration | Medium | Extensive test suite (142 tests) catches regressions |
| Performance regression from record allocation | Low | Java records are heap-allocated objects (not JEP 401 value types). However, schema trees are small (typically 5-20 nodes) and records are created once per measure/layout cycle. Profile after Phase A. |
| `isVariableLength` lifecycle complexity | Medium | This field intentionally survives `clear()`. MeasureResult makes this explicit — it's part of the measure result, not mutable state. |
| LayoutStylesheet API design may be premature | Medium | Phase C is optional. Phases A+B deliver the core benefit (immutable results) without the stylesheet API. |
| Sealed hierarchy constrains refactoring | Low | Only 2 concrete subtypes. Changes are manageable. |

## Success Criteria

1. MeasureResult record captures all fields that survive `clear()`: base (`columnWidth`, `labelWidth`), PrimitiveLayout (`averageCardinality`, `dataWidth`, `isVariableLength`, `maxWidth`), RelationLayout (`averageChildCardinality`, `maxCardinality`, `extractor`, `children`/`childResults`)
2. LayoutResult, CompressResult, HeightResult records capture all width-dependent + height-phase fields per the Q3 catalog. `columnSets` belongs in CompressResult (rebuilt per compress, not surviving from measure). `cellHeight` split: outline path in CompressResult, table path in HeightResult.
3. `clear()` method eliminated or trivial (no mutable state to reset)
4. Resize path provably doesn't re-measure (same as today, but explicit)
5. All existing tests pass at each migration phase
6. `isVariableLength` lifecycle is explicit in MeasureResult, not a comment
7. F7 style cache keys changed from field name to SchemaNode identity (prerequisite fix)
8. `cellHeight` dual-lifecycle (outline vs table) is explicitly modeled in separate result types

## Research Findings

### RF-1: External Field Access Inventory (Confidence: HIGH)
Zero production code directly accesses mutable fields from outside the sealed hierarchy. ALL 140+ external direct field accesses are in test code (setup writes). The migration surface is entirely in tests — production consumers already use public accessor methods. Migration requires test-setup factory methods or builders to replace direct field injection.

### RF-2: Cross-Phase Field Dependencies (Confidence: HIGH)
- Only `RelationLayout.columnWidth` has a genuine dual-write across phases (measure and layout). Cleanly separable as `naturalColumnWidth` vs `constrainedColumnWidth`.
- **12 fields** (not just `isVariableLength`) survive `clear()` by design — they ARE the implicit MeasureResult.
- `layout()` calls `clear()` recursively on children, resetting only geometry fields while preserving all measure-phase data. Clean read-measure/write-layout pattern.
- The children problem is the hardest structural issue. Current design uses one mutable object per SchemaNode across all phases. Records require either recursive record trees (recommended) or a flat registry. This is a significant call-structure refactoring.
- **Revised classification**: 4 result types needed (MeasureResult, LayoutResult, CompressResult, HeightResult), not the 2 originally proposed. `cellHeight` has dual lifecycle (outline vs table path).

### RF-3: SchemaPath Uniqueness Validation (Confidence: HIGH)
- Field names are **NOT unique** within schema trees. Test fixture `columns.json` has `name` at 7+ levels, `id` at 6+ levels.
- **F7 style cache bug found**: `primitiveStyleCache` and `relationStyleCache` use field name as key (`HashMap<String, ...>`). When the same field name appears at different nesting levels, the second lookup returns the first's cached style. The F8 layout cache correctly uses `IdentityHashMap<SchemaNode, ...>`.
- The bug is **latent** — CSS classes derive from leaf field name alone, so different "name" primitives get the same CSS rules. It only manifests if CSS had context-dependent rules for the same field name.
- **SchemaPath is necessary** for correctness. The F7 cache key should be changed to SchemaNode identity (matching F8) as a prerequisite fix.

## Recommendation

Proceed with incremental migration (Phases A → B → C), with one revision based on research:

**Pre-requisite fix**: Change F7 style cache keys from field name to SchemaNode identity (matching F8 layout cache). This fixes the latent cache bug from RF-3.

**Phase A revision**: Extract 4 result types (MeasureResult, LayoutResult, CompressResult, HeightResult) rather than the 2 originally proposed. The `cellHeight` dual-lifecycle (RF-2) requires separating compress-phase and height-phase results.

**Phase A starting point**: PrimitiveLayout remains the natural first target — it has fewer fields, no children complexity, and the `isVariableLength` lifecycle makes the benefit tangible.

Phase C (LayoutStylesheet with SchemaPath) should follow A+B. SchemaPath is confirmed necessary (RF-3) but not urgent since the cache bug is latent.
