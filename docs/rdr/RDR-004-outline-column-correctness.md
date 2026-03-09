---
title: "Outline Column Set Correctness"
id: RDR-004
type: Bug
status: closed
priority: P1
author: Hal Hildebrand
reviewed-by: self
created: 2026-03-08
accepted_date: 2026-03-08
closed_date: 2026-03-08
close_reason: implemented
related_issues: []
---

# RDR-004: Outline Column Set Correctness

> Revise during planning; lock at implementation.
> If wrong, abandon code and iterate RDR.

## Problem Statement

When a child `RelationLayout` is in outline mode and its width is less than `halfWidth`, Kramer's `RelationLayout.compress()` incorrectly groups it with adjacent primitive children in the same column set. The existing width-based check (`childWidth > halfWidth`) correctly handles wide children of any type, but does not handle narrow outline-mode relation children. Per Bakke 2013 §3.4, outline-mode relations must be excluded from multi-field column sets and placed in their own single-column column set.

**Note**: The paper also recommends dynamic programming for optimal column partitioning (vs. Kramer's greedy slide-right heuristic). This is a known quality limitation but not a correctness defect — the greedy approach produces acceptable results for typical field counts (3-8 fields, 2-3 columns). DP optimization is out of scope for this RDR.

## Context

### Current Code

`RelationLayout.compress()` lines 204-226:
```java
for (SchemaNodeLayout child : children) {
    double childWidth = labelWidth + child.layoutWidth();
    if (childWidth > halfWidth || current == null) {
        current = new ColumnSet();
        columnSets.add(current);
        current.add(child);
        if (childWidth > halfWidth) {
            current = null;
        }
    } else {
        current.add(child);
    }
}
```

No `instanceof` check, no `child.getNode().isRelation()` test. The infrastructure exists (`SchemaNode.isRelation()`) but is not used.

### Column Partitioning

`ColumnSet.compress()` + `Column.slideRight()` use iterative greedy balancing. Fields start in column 0, then slide right one-by-one if it reduces max height. This is a local optimum, not guaranteed global.

## Research Findings

### Finding 1: Paper's Exact Column Set Rule (§3.4)

**Source**: Bakke 2013, §3.4 "Columns in Outline Layouts" (indexed in T3 docs__kramer-research)

> "any relation field in an outline is excluded from participating in a set of multiple columns if that would cause it to be rendered as an outline. There is no requirement that the field would actually have to be rendered as a nested table if excluded, but if the excluded field is still rendered as an outline, that outline layout is still subject to the usual heuristics about whether to use columns or not at that next level."

> "To populate column sets, the algorithm iterates over outline fields in the order they appear in the schema, assigning each to the current column set. If an excluded field is encountered, it is assigned to a new column set of its own, and a new current column set started."

**Key nuance**: The exclusion rule is NOT "all relations get their own column set." It's "relations that would be rendered as outline (not table) get their own column set." A relation that fits as a table could potentially share a column set. However, in practice, the paper's Figure 3(e) shows Sample Reading List and Sections (both relations) each in their own single-column column sets.

**Status**: Verified against paper text.

### Finding 2: Current Kramer Implementation

**Source**: `RelationLayout.compress()` lines 204-226

The code uses `childWidth > halfWidth` as the sole criterion for starting a new ColumnSet. The `halfWidth` is computed as `(available / 2.0) - columnHorizontalInset - elementHorizontalInset`.

Key observation: `child.layoutWidth()` returns the outline column width for RelationLayout children (computed by `RelationLayout.layout()` which recurses). For a relation child that chose table mode (`useTable == true`), `layoutWidth()` returns `tableColumnWidth()` — which can be quite narrow. Such a narrow table-mode relation COULD fall below `halfWidth` and be grouped into a column set with primitives.

**Impact**: A narrow outline-mode relation (`useTable == false`) gets grouped with primitives in the same column set instead of being isolated. **Correction (see Finding 5)**: the initial assessment that table-mode relations should also be isolated was incorrect — the paper's exclusion rule applies only to outline-mode relations. Table-mode relations may correctly share column sets.

**Status**: Confirmed via code read. No test coverage exists for `compress()`, `ColumnSet`, or `Column` classes.

### Finding 3: Column Partitioning Algorithm

**Source**: Bakke 2013, §3.4

> "A better approach is to split the columns in such a way as to minimize the total vertical space consumed; this can be done easily with a dynamic programming routine."

> "Instead, as before, we make the decision of where to begin new columns only once for the entire layout."

Kramer's `ColumnSet.compress()` uses iterative greedy slide-right:
1. All fields start in column 0
2. Empty columns created: `IntStream.range(1, count).forEach(i -> columns.add(new Column(columnWidth)))`
3. Outer `do...while` loop repeats until no height reduction
4. Inner loop calls `Column.slideRight()` for each adjacent pair
5. `slideRight()` moves rightmost field from left column to right column if `left.without() >= right.with()`

The greedy property: fields only move right, never left. A field that was "slid right" past its optimal position cannot be recovered. For N fields across K columns, the greedy approach is O(N*K) per pass, with multiple passes until convergence.

DP alternative: O(N^2 * K) with guaranteed global optimum. For typical field counts (3-8 fields, 2-3 columns), this is negligible.

**Status**: Verified. Greedy is correct for monotonically-ordered field heights. Can be suboptimal when a tall field is sandwiched between short fields.

### Finding 4: Zero Test Coverage

No tests exist for:
- `ColumnSet.compress()` or `Column.slideRight()`
- `RelationLayout.compress()` column set formation
- Any scenario with outline columns

The smoke tests (`Smoke`, `PrimArraySmoke`, `SmokeTable`) exercise layout but don't assert column set structure or count.

**Status**: Confirmed via grep of test directory. Critical gap for a correctness fix.

### Finding 5: Paper's Nuanced Exclusion Rule

**Source**: Bakke 2013, §3.4 "Columns in Outline Layouts"

> "any relation field in an outline is excluded from participating in a set of multiple columns **if that would cause it to be rendered as an outline**."

The key phrase is "if that would cause it to be rendered as an outline." The exclusion is conditional on rendering mode, not type:
- If a relation child has `useTable == true` (fits as table), it is NOT excluded — it can participate in a column set
- If a relation child has `useTable == false` (rendered as outline), it IS excluded — gets its own column set

**Retraction of Finding 2 impact statement**: Finding 2 incorrectly stated that "even a compact table should span the full column set width, not share space with adjacent primitives." This was based on an initial reading before the conditional nature of the exclusion rule was understood. The paper explicitly permits table-mode relations to share column sets. Only outline-mode relations are excluded.

This is MORE nuanced than a simple "all relations get own column set" fix. The correct fix must check `!child.useTable` rather than unconditionally checking `child instanceof RelationLayout`.

The `useTable` flag is reliably set before `compress()` reads it. This ordering is guaranteed by `SchemaNodeLayout.autoLayout()` (lines 155-157), which calls `layout()` — setting `useTable` on all children via recursive `nestTableColumn()` calls — before calling `compress()`.

```java
boolean excluded = (child instanceof RelationLayout rl) && !rl.isUseTable();
```

**Status**: Verified against paper text and code ordering.

## Proposed Solution

### Fix 1: Conditional Column Set Exclusion (~5 lines)

In `RelationLayout.compress()`, exclude outline-mode relations from column sets (per paper §3.4, Finding 5):

```java
for (SchemaNodeLayout child : children) {
    double childWidth = labelWidth + child.layoutWidth();
    // Paper §3.4: relations rendered as outline are excluded from column sets
    boolean excluded = (child instanceof RelationLayout rl) && !rl.isUseTable();
    if (excluded || childWidth > halfWidth || current == null) {
        current = new ColumnSet();
        columnSets.add(current);
        current.add(child);
        if (excluded || childWidth > halfWidth) {
            current = null; // force next child to start new set
        }
    } else {
        current.add(child);
    }
}
```

Note: Relations with `useTable == true` (compact tables) can still share column sets with primitives. Only outline-mode relations are excluded. This matches the paper's nuanced rule.

## Implementation Plan

1. Add `public boolean isUseTable()` accessor to `RelationLayout` (exposes `useTable` for the conditional check)
2. Add conditional exclusion in `RelationLayout.compress()`: `(child instanceof RelationLayout rl) && !rl.isUseTable()`
3. Add unit tests for column set formation covering:
   - Outline-mode relation gets its own column set
   - Table-mode relation can share column sets with primitives
   - Outline relation breaks column set for subsequent children
   - All-primitives grouping (regression guard)
   - Wide primitive gets own column set (pre-existing rule)

## Test Plan

- **Scenario**: Narrow outline-mode relation between primitives → **Verify**: relation gets own column set (not grouped)
- **Scenario**: Narrow table-mode relation with primitives → **Verify**: table-mode relation shares column set (positive case for Finding 5)
- **Scenario**: Outline relation between primitives → **Verify**: subsequent children start a new column set
- **Scenario**: All narrow primitives → **Verify**: all grouped in one column set (regression guard)
- **Scenario**: Wide primitive between narrow ones → **Verify**: wide gets own column set (pre-existing width rule)
- **Scenario**: Existing layouts → **Verify**: no regressions

## References

- Bakke 2013, §3.4 (Columns in Outline Layouts)
- T3: `kramer-gap-analysis-outline-columns`
