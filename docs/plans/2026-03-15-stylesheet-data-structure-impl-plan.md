# RDR-009 Execution Plan: Immutable Phase Results & Stylesheet Data Structure

**Status**: Plan created 2026-03-15
**Epic**: Kramer-4lu (P2, epic)
**Source RDR**: /Users/hal.hildebrand/git/Kramer/docs/rdr/RDR-009-stylesheet-data-structure-design.md
**RDR Status**: accepted (2026-03-15)
**Closes**: Kramer-4k0 (F6 design document, already closed)

## Executive Summary

This plan implements RDR-009: replacing ~24 mutable fields across Kramer's `SchemaNodeLayout` sealed hierarchy with 4 immutable phase result records. The work proceeds in 3 independently shippable phases:

- **Phase A** extracts result type records using dual-write migration
- **Phase B** replaces mutable layout methods with result-returning methods and removes mutable fields
- **Phase C** (optional) introduces `SchemaPath` and `LayoutStylesheet` for per-path property overrides

A prerequisite bug fix (F7 style cache keys) is required before Phase A begins.

## Bead Hierarchy

### Epic
- **Kramer-4lu** (P2, epic): RDR-009: Immutable Phase Results & Stylesheet Data Structure

### Prerequisite
- **Kramer-e7h** (P2, bug): Fix F7 style cache keys -- String to SchemaNode identity
  - No blockers (ready to start)
  - Blocks: Kramer-rgy, Kramer-5bw (all Phase A tasks)

### Phase A: Extract Result Records
- **Kramer-rgy** (P2, task): Define 4 immutable result record types (MeasureResult, LayoutResult, CompressResult, HeightResult)
  - Depends: Kramer-e7h
  - Blocks: Kramer-20b
- **Kramer-5bw** (P2, task): Create TestLayouts test factory utility
  - Depends: Kramer-e7h
  - Blocks: Kramer-20b
  - Replaces ~103 direct field writes across 5 test files
- **Kramer-20b** (P2, feature): Extract PrimitiveLayout MeasureResult with dual-write
  - Depends: Kramer-rgy, Kramer-5bw
  - Blocks: Kramer-r57, Kramer-4dk
  - PrimitiveLayout first (simpler: 7 mutable fields, no children)
- **Kramer-r57** (P2, feature): Extract RelationLayout MeasureResult with dual-write
  - Depends: Kramer-20b
  - Blocks: Kramer-2ci
  - Hardest Phase A task: children list dual-write

### Phase B: Replace Mutable Methods
- **Kramer-4dk** (P2, feature): PrimitiveLayout result-returning layout/compress/cellHeight
  - Depends: Kramer-20b
  - Blocks: Kramer-2ci, Kramer-53g
- **Kramer-2ci** (P2, feature): RelationLayout result-returning layout/compress/cellHeight
  - Depends: Kramer-r57, Kramer-4dk
  - Blocks: Kramer-53g
  - Most complex task: children recursion + cellHeight dual lifecycle
- **Kramer-53g** (P2, feature): AutoLayout orchestration via return values + field removal
  - Depends: Kramer-4dk, Kramer-2ci
  - Blocks: Kramer-7wm
  - Removes all 24 mutable fields, eliminates clear()

### Phase C: LayoutStylesheet (Optional)
- **Kramer-7wm** (P2, feature): SchemaPath record + LayoutStylesheet interface
  - Depends: Kramer-53g
  - Blocks: Kramer-6un
- **Kramer-6un** (P2, feature): Wire SchemaPath through measure traversal + per-path overrides
  - Depends: Kramer-7wm
  - Terminal task

## Dependency Graph

```
[Kramer-e7h] Prereq: F7 cache key fix
    |          |
    v          v
[Kramer-rgy] [Kramer-5bw]
  A1: records   A2: test factories
    |          |
    +----+-----+
         |
         v
    [Kramer-20b]
    A3: PrimitiveLayout MeasureResult
       |              |
       v              v
  [Kramer-r57]   [Kramer-4dk]
  A4: Relation   B1: Primitive
  MeasureResult  result methods
       |              |
       +------+-------+
              |
              v
         [Kramer-2ci]
         B2: Relation result methods
              |
              v
         [Kramer-53g]
         B3: AutoLayout + field removal
              |
              v
         [Kramer-7wm]
         C1: SchemaPath + LayoutStylesheet
              |
              v
         [Kramer-6un]
         C2: Wire paths + overrides
```

## Critical Path

```
e7h -> rgy -> 20b -> r57 -> 2ci -> 53g -> 7wm -> 6un
(T0)   (A1)   (A3)   (A4)   (B2)   (B3)   (C1)   (C2)
```

Length: 8 tasks in sequence. The critical path runs through RelationLayout in every phase because it is always structurally harder than PrimitiveLayout.

## Parallelization Opportunities

1. **After T0 (Kramer-e7h)**: A1 and A2 can execute in parallel
   - A1 defines result records (production code)
   - A2 creates test factory utility (test code)
   - No code overlap between these tasks

2. **After A3 (Kramer-20b)**: A4 and B1 can potentially overlap
   - A4 extracts RelationLayout MeasureResult
   - B1 starts PrimitiveLayout result-returning methods
   - These touch different files, but B1 should wait for A3 to stabilize
   - RECOMMENDED: start B1 only after A4 is 50%+ complete

3. **Phase C**: Entirely additive. Can be deferred indefinitely without affecting A+B.

## Phase Breakdown with Rationale

### Prerequisite: F7 Cache Key Fix (Kramer-e7h)

**Why first**: The existing `primitiveStyleCache` and `relationStyleCache` in Style.java use field name (String) as HashMap keys. When the same field name appears at different nesting levels (e.g., "name" under both "person" and "address"), the second lookup returns the first's cached style. The F8 layout cache already uses `IdentityHashMap<SchemaNode, ...>` correctly. This must be fixed before Phase C introduces SchemaPath-based lookups, and it should be fixed before any Phase A work to establish a clean baseline.

**Risk**: LOW. The bug is latent -- CSS classes derive from leaf field name, so different nodes with the same name get identical CSS rules today.

**Files**: Style.java (lines 124-126, 170, 213)

### Phase A: Extract Result Records

**Why this order**: PrimitiveLayout before RelationLayout because:
- PrimitiveLayout has 7 mutable fields vs RelationLayout's 11
- PrimitiveLayout has no children complexity
- The `isVariableLength` lifecycle is the clearest demonstration of value
- RelationLayout's MeasureResult depends on children having MeasureResults

**Dual-write rationale**: During migration, `measure()` populates BOTH the new record AND existing mutable fields. This means all existing code continues to work unchanged. Readers migrate from fields to records in Phase B. At no point is existing functionality broken.

**Test factory rationale**: RF-1 established that ALL 103+ external field accesses are in test code. Creating TestLayouts utility early:
- Reduces per-test boilerplate (each test file has nearly identical helper methods)
- Makes Phase A field changes safe (factory methods abstract over field access)
- Is forward-compatible with Phase B (factories can return pre-measured layouts)

### Phase B: Replace Mutable Methods

**Why sealed hierarchy matters**: `SchemaNodeLayout` is `sealed permits PrimitiveLayout, RelationLayout`. Changing abstract method signatures affects both subtypes simultaneously. The plan uses a parallel API approach: add new result-returning methods alongside existing ones, migrate callers, then remove old methods.

**AutoLayout orchestration**: The final B3 task changes AutoLayout from:
```
layout.autoLayout(width, ...) // mutates internal state
```
to:
```
measureResult = layout.measure(data)          // cached
layoutResult = layout.computeLayout(width, measureResult)
compressResult = layout.computeCompress(width, layoutResult)
heightResult = layout.computeHeight(compressResult)
control = layout.buildControl(heightResult, ...)
```

This makes data flow explicit and eliminates `clear()`.

### Phase C: LayoutStylesheet (Optional)

**Why optional**: Phases A+B deliver the core benefit (immutable results, explicit data flow). Phase C adds per-path property overrides, which is a user-facing API extension. The system is fully functional without it.

**Why SchemaPath is necessary for correctness**: RF-3 proved field names are NOT unique within schema trees. Test fixture `columns.json` has "name" at 7+ levels. Without SchemaPath, per-node property overrides cannot distinguish between them.

## Risk Matrix

| Task | Risk | Key Risk | Mitigation |
|------|------|----------|------------|
| T0 (e7h) | LOW | Visual output change | CSS classes still use field name leaf; verify with integration tests |
| A1 (rgy) | LOW | Over-engineering records | Follow RDR-009 Q4 design exactly; records are additive |
| A2 (5bw) | LOW | Incomplete factory coverage | Count field writes before/after; target 100% replacement |
| A3 (20b) | LOW-MED | isVariableLength lifecycle | Explicit in MeasureResult; test survival through pipeline |
| A4 (r57) | MEDIUM | Children list dual-write | Mutable children retained until Phase B; childResults built post-loop |
| B1 (4dk) | MEDIUM | Sealed hierarchy constraint | Parallel API approach avoids breaking RelationLayout |
| B2 (2ci) | MED-HIGH | Children recursion + cellHeight dual lifecycle | Most complex task; extensive TDD; sequential-thinking required |
| B3 (53g) | MEDIUM | Large changeset (remove 24 fields) | All callers already migrated in B1/B2; this is cleanup |
| C1 (7wm) | MEDIUM | Premature API design | Optional; can defer or revise without affecting A+B |
| C2 (6un) | MEDIUM | measure() signature change | Propagates through sealed hierarchy; fold() path needs care |

## Test Migration Surface

103 direct field writes across 5 test files:

| File | Field Writes | Key Patterns |
|------|-------------|--------------|
| VerticalHeaderTest.java | 37 | dataWidth, labelWidth, columnWidth, tableColumnWidth, children |
| RelationLayoutCompressTest.java | 26 | columnWidth, dataWidth, labelWidth, children, averageChildCardinality, useTable, tableColumnWidth |
| StylesheetPropertyTest.java | 20 | columnWidth, dataWidth, labelWidth, averageChildCardinality |
| MaxTablePrimitiveWidthTest.java | 15 | dataWidth, columnWidth, labelWidth, children, averageChildCardinality |
| ColumnSetEvaluationTest.java | 5 | columnWidth, dataWidth, labelWidth, children, averageChildCardinality |

All 5 files have local `makePrimitive()`, `makeChildRelation()`, and `mockRelationStyle()` helper methods that are nearly identical. TestLayouts consolidates these.

## Branch and PR Strategy

Each phase is independently shippable and gets its own feature branch and PR:

- **Phase A branch**: `feature/Kramer-4lu-phase-a-extract-result-records`
  - Includes: T0 (prerequisite fix) + A1 + A2 + A3 + A4
  - PR gate: all 142+ tests pass, dual-write verified

- **Phase B branch**: `feature/Kramer-4lu-phase-b-result-returning-methods`
  - Includes: B1 + B2 + B3
  - PR gate: all tests pass, no mutable fields remain, clear() eliminated

- **Phase C branch**: `feature/Kramer-4lu-phase-c-layout-stylesheet`
  - Includes: C1 + C2
  - PR gate: all tests pass, per-path overrides work, SchemaPath constructed during measure

## Success Criteria (from RDR-009)

- [ ] SC-1: MeasureResult captures all fields surviving clear()
- [ ] SC-2: LayoutResult/CompressResult/HeightResult capture width-dependent + height-phase fields; columnSets in CompressResult
- [ ] SC-3: clear() eliminated or trivial
- [ ] SC-4: Resize path provably does not re-measure
- [ ] SC-5: All existing tests pass at each migration phase
- [ ] SC-6: isVariableLength lifecycle explicit in MeasureResult
- [ ] SC-7: F7 style cache keys changed to SchemaNode identity
- [ ] SC-8: cellHeight dual-lifecycle modeled in separate result types

## Key Files Reference

| File | Lines | Role |
|------|-------|------|
| SchemaNodeLayout.java | 312 | Sealed base: 6 mutable fields, Fold record, Indent enum |
| PrimitiveLayout.java | 300 | Final subtype: 7 mutable fields, measure/layout/compress/cellHeight |
| RelationLayout.java | 628 | Final subtype: 11 mutable fields, children recursion, table/outline |
| AutoLayout.java | 273 | Pipeline orchestrator: measure → layout → compress → height → build |
| Style.java | 292 | Style caches: primitiveStyleCache, relationStyleCache (F7 bug), layoutCache |

## Continuation State

After each task completes, update nx T2 memory:
```
project: Kramer
title: rdr-009-continuation-state.md
```

Track: current task, completed tasks, blocking issues, next actions, test count.
