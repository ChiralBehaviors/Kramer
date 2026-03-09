---
title: "Outline Column Set Correctness"
id: RDR-004
type: Bug
status: implemented
priority: P1
author: Hal Hildebrand
reviewed-by: self
created: 2026-03-08
related_issues: []
---

# RDR-004: Outline Column Set Correctness

> Revise during planning; lock at implementation.
> If wrong, abandon code and iterate RDR.

## Problem Statement

The paper (§3.4) mandates that relation fields are excluded from column sets — each relation gets its own single-column column set. Kramer's `RelationLayout.compress()` uses a purely width-based threshold (`childWidth > halfWidth`) with no type check. A narrow relation field could be incorrectly grouped into a column set alongside primitive fields.

Additionally, the paper describes dynamic programming for optimal column partitioning to minimize vertical space. Kramer uses a greedy slide-right heuristic that can produce suboptimal results.

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

**Impact**: A relation with `useTable=true` and narrow table width gets grouped with primitives. This violates the paper's visual semantics — even a compact table should span the full column set width, not share space with adjacent primitives.

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

Re-reading the paper more carefully: the exclusion is conditional on whether the relation "would be rendered as an outline." This means:
- If a relation child has `useTable == true` (fits as table), it is NOT excluded — it can participate in a column set
- If a relation child has `useTable == false` (rendered as outline), it IS excluded — gets its own column set

This is MORE nuanced than the simple "all relations get own column set" fix proposed in the RDR. The correct fix should check `!child.useTable` rather than `child instanceof RelationLayout`.

However, at the time `compress()` runs, `useTable` has already been set by the prior `layout()` call. So the check is feasible:

```java
boolean excluded = (child instanceof RelationLayout rl) && !rl.isUseTable();
```

**Status**: Verified. This changes the proposed fix — it should be conditional on useTable, not unconditional on type.

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

### Fix 2: Dynamic Programming Partitioning (Optional)

Replace the slide-right heuristic in `ColumnSet.compress()` with DP:

```java
// DP over field assignments to columns to minimize max column height
// State: dp[i][j] = min max-height using fields 0..i-1 across j columns
// Transition: try all possible split points for the last column
```

Field counts per column set are typically small (single digits), so DP is fast. However, the greedy approach works well in practice — this is a quality improvement, not a correctness fix.

## Implementation Plan

1. Add `instanceof RelationLayout` check in `RelationLayout.compress()` — force relation children into their own column sets
2. Add test: schema with a narrow relation child verifies it gets isolated column set
3. (Optional) Replace slide-right with DP in `ColumnSet.compress()`
4. Add test: verify column partitioning optimality for known field height distributions

## Test Plan

- **Scenario**: Schema with narrow relation + wide primitives → **Verify**: relation gets own column set (not grouped)
- **Scenario**: Schema with multiple primitives of varying heights → **Verify**: column partitioning minimizes max height
- **Scenario**: Existing layouts → **Verify**: no regressions (wide relations already get own column sets via width threshold)

## References

- Bakke 2013, §3.4 (Columns in Outline Layouts)
- T3: `kramer-gap-analysis-outline-columns`
