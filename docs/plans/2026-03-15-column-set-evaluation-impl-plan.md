# Implementation Plan: F2+F3 Evaluate-First Column Set Formation

**Date**: 2026-03-15
**Parent Bead**: Kramer-avn (F2+F3: Column set formation correction + DP partitioning)
**Parent Epic**: Kramer-abn (RDR-008: Core Algorithm Reevaluation & Bakke 2013 Alignment)
**RDR**: RDR-008, findings RF-4 (column set formation) and RF-5 (greedy vs DP partitioning)

## Executive Summary

RDR-008 identifies two coupled algorithm divergences from Bakke 2013:
- **F2**: Kramer adds a `childWidth > halfWidth` guard to column set formation that is not in the paper
- **F3**: Kramer uses greedy slide-right column partitioning instead of the paper's DP algorithm

The `halfWidth` guard compensates for the greedy partitioner's limitations. Removing it without DP may degrade layouts. The RDR specifies an evaluation criterion: implement DP only if tests demonstrate the greedy partitioner produces suboptimal column splits.

This plan implements an **evaluate-first** approach: write tests that compare layout quality with and without the halfWidth guard (both using the existing greedy partitioner), then make a data-driven decision on whether DP implementation is warranted.

## Approved Design (from relay)

1. Write evaluation tests comparing current (halfWidth guard + greedy) vs paper (no halfWidth guard) behavior
2. Measure whether removing halfWidth + keeping greedy produces acceptable or equivalent layouts
3. Based on results: either implement DP (if greedy is suboptimal) or defer Kramer-avn

## Key Code Locations

| Component | File | Lines | Description |
|-----------|------|-------|-------------|
| Column set formation | `kramer/src/main/java/com/chiralbehaviors/layout/RelationLayout.java` | 224-262 | `compress()` method, halfWidth guard at line 244 |
| Column partitioning | `kramer/src/main/java/com/chiralbehaviors/layout/ColumnSet.java` | 51-97 | `compress()` method with greedy slide-right |
| Column slide-right | `kramer/src/main/java/com/chiralbehaviors/layout/Column.java` | 76-92 | `slideRight()` — moves last field from left to right column |
| Existing tests | `kramer/src/test/java/com/chiralbehaviors/layout/RelationLayoutCompressTest.java` | all | 9 tests, mock-based patterns |
| Test fixture | `kramer/src/test/resources/catalog.json` | all | 7 course records for width reference |

## Dependency Graph

```
Kramer-84s (T1: Extract parameterized compress)
    |
    v
Kramer-6zx (T2: Write evaluation tests)
    |
    v
Kramer-3jv (T3: Decision gate)
    |
    +--- [DEFER] --> Kramer-avn deferred, evaluation tests retained as baseline
    |
    +--- [IMPLEMENT] --> New beads: T4 (DP design) -> T5 (DP impl) -> T6 (remove guard)
```

**Critical path**: T1 -> T2 -> T3. All tasks are sequential; no parallelization within the evaluation phase.

## Phase 1: Test Infrastructure

### Task 1 — Kramer-84s: Extract parameterized compress method

**Purpose**: Enable testing column set formation with and without the halfWidth guard without changing production behavior.

**TDD sequence**:
1. Write failing test in `RelationLayoutCompressTest`:
   ```java
   @Test
   void parameterizedCompressEquivalentToPublicMethod() {
       // calls compress(500, true) — won't compile until method exists
   }
   ```
2. Extract method in `RelationLayout.java`:
   ```java
   // Package-private for testing
   void compress(double justified, boolean useHalfWidthGuard) { ... }

   @Override
   public void compress(double justified) {
       compress(justified, true);
   }
   ```
3. In the parameterized method, the halfWidth guard condition becomes:
   ```java
   if (excluded || (useHalfWidthGuard && childWidth > halfWidth) || current == null) {
   ```
4. Verify all 9 existing tests pass unchanged.

**Files modified**:
- `kramer/src/main/java/com/chiralbehaviors/layout/RelationLayout.java`
- `kramer/src/test/java/com/chiralbehaviors/layout/RelationLayoutCompressTest.java`

**Test command**: `mvn -pl kramer test -Dtest=RelationLayoutCompressTest`

**Acceptance criteria**:
- [ ] `compress(double)` delegates to `compress(double, true)` — no behavior change
- [ ] `compress(double, false)` skips halfWidth guard in column set formation
- [ ] All 9 existing RelationLayoutCompressTest tests pass
- [ ] New test confirms uniform-width children produce identical results with both parameter values

## Phase 2: Evaluation Tests

### Task 2 — Kramer-6zx: Write halfWidth guard evaluation tests

**Purpose**: Produce concrete, measurable evidence comparing layout quality with and without the halfWidth guard across realistic schema configurations.

**New file**: `kramer/src/test/java/com/chiralbehaviors/layout/ColumnSetEvaluationTest.java`

**Test pattern**: Each scenario constructs a `RelationLayout` with mock styles (zero insets, following `RelationLayoutCompressTest` patterns), runs `compress(width, true)` and `compress(width, false)`, and compares:
- Number of column sets produced
- Fields per column set
- Total `cellHeight` (the layout quality metric)
- Per-column-set height breakdown

#### Test Scenarios

**Scenario 1: "Catalog-like"** — Models the catalog.json schema in outline mode
- Children: short(40px), medium-variable(180px), wide-variable(300px), short(15px)
- Test widths: 400, 600, 800
- Rationale: Realistic field width distribution. The 300px "description" field triggers the halfWidth guard at narrower widths.

**Scenario 2: "Uniform narrows"** — Control case
- Children: 6 primitives at 30px each
- Test widths: 300, 500
- Expected: Identical results with and without guard (no field exceeds halfWidth)
- Rationale: Validates that the parameterized method produces equivalent results when the guard doesn't fire.

**Scenario 3: "Extreme mixed"** — Stress test for greedy partitioner
- Children: 1 primitive at 400px, 4 primitives at 30px each
- Test widths: 500, 800
- Rationale: Maximum width disparity. Without guard, all fields in one column set; greedy partitioner must handle 400px + 30px fields together.

**Scenario 4: "Two mediums"** — Moderate mix
- Children: 2 primitives at 200px, 3 primitives at 30px
- Test width: 500
- Rationale: Middle ground — less extreme than Scenario 3, tests whether moderate width differences matter.

#### Measurement Protocol

For each test, the assertion messages include exact numeric values:
```
"Catalog-like at 600: guard={csCount=2, height=40.0}, noGuard={csCount=1, height=60.0}, delta=+20.0px (+50.0%)"
```

This format enables the T3 decision gate to extract structured comparison data from test output.

#### Helper Method (sketch)

```java
record CompressResult(int columnSetCount, double totalCellHeight, List<Integer> fieldsPerSet) {}

private CompressResult runCompress(RelationLayout layout, double width, boolean useHalfWidthGuard) {
    // Reset layout state
    layout.columnSets.clear();
    layout.compress(width, useHalfWidthGuard);
    return new CompressResult(
        layout.columnSets.size(),
        layout.getCellHeight(),
        layout.columnSets.stream()
            .map(cs -> cs.getColumns().stream()
                .mapToInt(c -> c.getFields().size()).sum())
            .toList()
    );
}
```

**Files created**:
- `kramer/src/test/java/com/chiralbehaviors/layout/ColumnSetEvaluationTest.java`

**Test command**: `mvn -pl kramer test -Dtest=ColumnSetEvaluationTest`

**Acceptance criteria**:
- [ ] At least 8 test methods covering 4 scenarios at various widths
- [ ] Each test captures column set count, fields-per-set, and total cellHeight for both modes
- [ ] Assertion messages contain exact numeric comparison data
- [ ] Tests are pure unit tests (no JavaFX runtime, mock-based)
- [ ] Scenario 2 (uniform narrows) produces identical results with and without guard (validates test infrastructure)
- [ ] All tests compile and pass

## Phase 3: Decision Gate

### Task 3 — Kramer-3jv: Analyze evaluation results, act on decision

**Purpose**: Make the data-driven go/no-go decision on F2+F3 based on concrete test evidence.

**Decision criteria**:

| Outcome | Condition | Action |
|---------|-----------|--------|
| **DEFER both** | No scenario shows meaningful height regression when guard is removed. "Meaningful" = more than one element height (20px at test font size) AND more than 10% height increase. | Defer Kramer-avn. Keep halfWidth guard. Evaluation tests remain as regression baseline. |
| **IMPLEMENT DP** | At least one scenario shows the greedy partitioner produces significantly taller layouts without the guard (>20px AND >10% height increase). | Create implementation beads for DP algorithm. F2 (guard removal) follows F3 (DP implementation). |

**Output by outcome**:

**If DEFER**:
1. Update Kramer-avn status to `deferred` with evaluation evidence summary
2. Add evaluation results addendum to RDR-008
3. Evaluation tests remain in codebase as regression baseline for future reconsideration
4. The halfWidth guard stays in production code

**If IMPLEMENT DP**:
1. Create new task beads:
   - T4: Design DP column partitioning algorithm (task, blocks T5)
   - T5: Implement DP in ColumnSet.compress() (task, blocks T6)
   - T6: Remove halfWidth guard and verify with evaluation tests (task)
2. Update Kramer-avn with decision and new subtask links
3. Add implementation notes to RDR-008

**Files modified**:
- `docs/rdr/RDR-008-algorithm-reevaluation-bakke-alignment.md` (evaluation results addendum)

**Test command**: `mvn -pl kramer test -Dtest=ColumnSetEvaluationTest`

**Acceptance criteria**:
- [ ] Evaluation results analyzed with specific numeric evidence per scenario
- [ ] Decision documented with measurable justification (not subjective)
- [ ] Kramer-avn bead updated with decision outcome
- [ ] RDR-008 addendum written with evaluation data table
- [ ] If defer: Kramer-avn marked deferred with traceable evidence
- [ ] If implement: follow-on beads created with correct dependencies

## Risk Factors

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| All scenarios show identical results (guard never fires) | Medium | Low — evaluation succeeds, just with a definitive "defer" outcome | Scenario 3 specifically designed with extreme widths to trigger the guard |
| Mock-based tests don't capture real-world CSS inset effects | Low | Medium — real layouts may differ from zero-inset test results | Insets are additive and don't change relative column set formation; the halfWidth guard operates on pre-inset widths |
| The parameterized method extraction introduces subtle behavioral differences | Low | High — would invalidate all evaluation results | Existing 9 tests serve as regression suite; new equivalence test in T1 |
| Test state reset between compress(true) and compress(false) is incomplete | Medium | High — comparison data is invalid if prior state leaks | Use fresh RelationLayout instances for each comparison (construct, don't reuse) |

## Parallelization Opportunities

**Within this plan**: None. T1 -> T2 -> T3 is strictly sequential; each task depends on the previous.

**With other work**: T1 and T2 can execute in parallel with other Kramer work that does not modify `RelationLayout.java`, `ColumnSet.java`, or `Column.java`. Specifically:
- F4 (OutlineSnapValueWidth) — independent, modifies PrimitiveLayout only
- F5 (configurable VARIABLE_LENGTH_THRESHOLD) — independent, modifies PrimitiveLayout only

## Summary of Beads

| Bead ID | Title | Type | Priority | Depends On | Blocks |
|---------|-------|------|----------|------------|--------|
| Kramer-avn | F2+F3: Column set formation + DP partitioning | feature | P2 | Kramer-8jq (closed) | Kramer-4k0 |
| Kramer-84s | T1: Extract parameterized compress method | task | P2 | (none) | Kramer-6zx |
| Kramer-6zx | T2: Write evaluation tests | task | P2 | Kramer-84s | Kramer-3jv |
| Kramer-3jv | T3: Decision gate | task | P2 | Kramer-6zx | (conditional) |
