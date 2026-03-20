---
title: Layout E2E Test Framework — Implementation Plan
date: 2026-03-20
status: draft
epic: Kramer-4whh
design: docs/plans/2026-03-20-layout-e2e-test-framework-design.md
---

# Layout E2E Test Framework — Implementation Plan

## Executive Summary

Consolidate 4 scattered layout test files into a layered test framework with
shared infrastructure (LayoutTestHarness + LayoutTestResult), reusable fixtures
(LayoutFixtures), and 4 focused test classes that validate layout invariants
across all fixture types. Delete the originals, keep OutlineFieldWidthTest
(headless column math).

## Bead Hierarchy

```
Kramer-4whh (epic)  Layout E2E Test Framework
  Kramer-jyn0 (task)  Phase 1: LayoutTestHarness + LayoutTestResult
  Kramer-ip1m (task)  Phase 2: LayoutFixtures         [depends on jyn0]
  Kramer-r618 (task)  Phase 3: Test classes            [depends on jyn0, ip1m]
  Kramer-60rl (task)  Phase 4: Cleanup + verify        [depends on r618]
```

## Dependency Graph

```
Kramer-jyn0  ──►  Kramer-ip1m  ──►  Kramer-r618  ──►  Kramer-60rl
     └────────────────────────────────────┘
```

Critical path: jyn0 → ip1m → r618 → 60rl (strictly sequential).

## Phase 1: Infrastructure — Kramer-jyn0

### Goal
Create LayoutTestHarness and LayoutTestResult in a new test package.

### Files

| File | Action |
|------|--------|
| `kramer/src/test/java/com/chiralbehaviors/layout/test/LayoutTestHarness.java` | CREATE |
| `kramer/src/test/java/com/chiralbehaviors/layout/test/LayoutTestResult.java` | CREATE |
| `kramer/src/test/java/com/chiralbehaviors/layout/test/LayoutTestHarnessTest.java` | CREATE |

### TDD Steps

1. **Write failing test first** (`LayoutTestHarnessTest.java`):
   - `trivialSchemaProducesResult()`: Create a Relation("items") with one
     Primitive("id"), one-row ArrayNode. Construct LayoutTestHarness(stage,
     schema, data). Call `run(800, 600)`. Assert result is non-null,
     `isTableMode()` returns a boolean, `getRenderedTexts()` is non-empty.
   - `harnessReturnsFieldWidths()`: Same trivial schema at 800px. Assert
     `getFieldWidths()` contains key "id" with positive value.

2. **Implement LayoutTestResult** (record or class):
   ```
   - boolean isTableMode()
   - List<String> getRenderedTexts()           // all Labeled.getText()
   - List<String> getDataTexts(Set<String> fieldNames)  // texts minus headers
   - Map<String, Double> getFieldWidths()      // field -> justifiedWidth
   - List<String> getZeroWidthLabels()         // labels with width <= 0
   - List<String> getTruncatedLabels()         // labels with ellipsis
   ```

3. **Implement LayoutTestHarness**:
   ```java
   public class LayoutTestHarness {
       private final Stage stage;
       private final Relation schema;
       private final ArrayNode data;

       public LayoutTestHarness(Stage stage, Relation schema, ArrayNode data) { ... }

       public LayoutTestResult run(double width, double height) {
           // Platform.runLater + CountDownLatch
           // 1. Style model = new Style()
           // 2. RelationLayout layout = new RelationLayout(schema, model.style(schema))
           // 3. layout.measure(data, n -> n, model)
           // 4. FocusController on dummy Pane
           // 5. LayoutCell<?> control = layout.autoLayout(width, height, controller, model)
           //    Signature: SchemaNodeLayout.autoLayout(double width, double availableHeight,
           //               FocusTraversal<?> parentTraversal, Style model)
           // 6. Attach control.getNode() to stage scene root
           // 7. control.updateItem(data)
           // 8. root.applyCss() + root.layout()
           // 9. Collect scene graph info into LayoutTestResult
       }
   }
   ```
   - Must use `@ExtendWith(ApplicationExtension.class)` on test class with
     `@Start` to obtain Stage, then pass Stage to harness.
   - `run()` can be called multiple times (for resize tests in Phase 3).
   - The harness stores the RelationLayout after run() so `isTableMode()` can
     query `layout.isUseTable()` and field widths can query
     `child.getJustifiedWidth()`.

### Key API References
- `SchemaNodeLayout.autoLayout(double width, double availableHeight, FocusTraversal<?> parentTraversal, Style model)` — synchronous pipeline: layout → compress → calculateRootHeight → distributeExtraHeight → buildControl
- `RelationLayout.isUseTable()` — mode query after layout()
- `FocusController<>(Pane)` — focus traversal root (pattern from FullPipelineLayoutTest line 177)

### Test Command
```bash
mvn -pl kramer test -Dtest="com.chiralbehaviors.layout.test.LayoutTestHarnessTest"
```

---

## Phase 2: LayoutFixtures — Kramer-ip1m

### Goal
Create reusable schema + data factories for all test classes.

### Files

| File | Action |
|------|--------|
| `kramer/src/test/java/com/chiralbehaviors/layout/test/LayoutFixtures.java` | CREATE |
| `kramer/src/test/java/com/chiralbehaviors/layout/test/LayoutFixturesTest.java` | CREATE |

### TDD Steps

1. **Write failing test first** (`LayoutFixturesTest.java`):
   - `flatFixtureIsValid()`: Assert `flatSchema()` has 3 children, `flatData()`
     has 10 rows, `flatFieldNames()` contains "id", "name", "value".
   - `nestedFixtureIsValid()`: Assert `nestedSchema()` has 5 children (4 prims
     + 1 relation), `nestedData()` has 5 rows, field names correct.
   - `deepFixtureIsValid()`: Assert `deepSchema()` has 2 children (1 prim +
     1 relation with nested relation), `deepData()` is non-empty.
   - `wideFixtureIsValid()`: Assert `wideSchema()` has >= 10 children,
     `wideData()` is non-empty.
   - `allFixturesRenderWithoutException()`: For each fixture, construct
     LayoutTestHarness and call `run(800, 600)`. Assert non-null result with
     non-empty renderedTexts. (Requires `@ExtendWith(ApplicationExtension.class)`.)

2. **Implement LayoutFixtures** (static factory methods):

   **flatSchema/flatData/flatFieldNames**:
   - Relation("items") with Primitive("id"), Primitive("name"), Primitive("value")
   - 10 rows: `{id: 1, name: "Item 1", value: 100}` ... `{id: 10, name: "Item 10", value: 1000}`
   - Field names: `Set.of("id", "name", "value")`

   **nestedSchema/nestedData/nestedFieldNames**:
   - Relation("employees") with name, role, department, email + nested
     Relation("projects") with project, status, hours
   - 5 rows (same data as FullPipelineLayoutTest.buildData)
   - Field names: `Set.of("name", "role", "department", "email", "project", "status", "hours")`

   **deepSchema/deepData/deepFieldNames**:
   - Relation("org") with name + Relation("teams") with team +
     Relation("members") with person, role
   - Same data as LayoutTransitionTest.resizeSweepDeeplyNested
   - Field names: `Set.of("name", "team", "person", "role")`

   **wideSchema/wideData/wideFieldNames**:
   - Relation("records") with 12 primitives: id, title, category, status,
     priority, assignee, created, updated, estimate, actual, progress, notes
   - 5 rows of realistic data
   - Field names: all 12 field names

### Test Command
```bash
mvn -pl kramer test -Dtest="com.chiralbehaviors.layout.test.LayoutFixturesTest"
```

---

## Phase 3: Test Classes — Kramer-r618

### Goal
Create 4 test classes that validate layout invariants using the harness and
fixtures. Each test class uses `@ExtendWith(ApplicationExtension.class)` with
`@Start` to get a Stage, constructs LayoutTestHarness per fixture.

### Files

| File | Action |
|------|--------|
| `kramer/src/test/java/com/chiralbehaviors/layout/test/LayoutModeSelectionTest.java` | CREATE |
| `kramer/src/test/java/com/chiralbehaviors/layout/test/LayoutDataVisibilityTest.java` | CREATE |
| `kramer/src/test/java/com/chiralbehaviors/layout/test/LayoutWidthFairnessTest.java` | CREATE |
| `kramer/src/test/java/com/chiralbehaviors/layout/test/LayoutResizeTest.java` | CREATE |

### 3a. LayoutModeSelectionTest

**Replaces**: LayoutTransitionTest (transitionIsMonotonic, outlineModeAtNarrowWidth,
tableModeJustAboveThreshold, outlineModeJustBelowThreshold, flatSchemaUsesTableAtReasonableWidth,
readableWidthAccountsForNestedColumns)

**TDD — write tests first, then no new implementation needed (harness does the work)**:

- `modeIsTableAtWideWidth(fixture)`: For each fixture, run at 1500px, assert
  `isTableMode() == true`.
- `modeIsOutlineAtNarrowWidth()`: For nested + deep fixtures, run at 300px,
  assert `isTableMode() == false`. (Flat/wide may still be table at 300px.)
- `transitionIsMonotonic(fixture)`: For each fixture, sweep 300–1500px step 50.
  Once `isTableMode()` becomes true, it must stay true for all wider widths.
- `thresholdBoundary()`: For nested fixture, find the transition width via
  binary search or sweep. Verify table at threshold+50, outline at threshold-50.

**Estimated test count**: 4–6 (parameterized across fixtures where applicable).

### 3b. LayoutDataVisibilityTest

**Replaces**: FullPipelineLayoutTest (assertDataVisible, widthSweep,
noZeroWidthDataLabels), OutlineDataVisibilityTest (outlineModeShowsDataValues,
tableModeShowsDataValues, coldStartResizeRendersData, dataLabelsHaveNonZeroWidth)

**TDD**:

- `dataTextsPresent(fixture, width)`: For each fixture at widths {400, 800, 1200},
  run harness. Get `getDataTexts(fixture.fieldNames())`. Assert non-empty —
  data values must be present, not just field name headers.
- `noZeroWidthDataLabels(fixture, width)`: For each fixture at {400, 800, 1200},
  assert `getZeroWidthLabels()` filtered to known data values is empty.
- `widthSweepDataSurvives(fixture)`: For nested fixture, sweep 300–1500 step 50.
  At every width, assert `getRenderedTexts()` is non-empty and contains at least
  some data text prefixes.

**Estimated test count**: 4–6 (parameterized).

### 3c. LayoutWidthFairnessTest

**Replaces**: WidthJustificationTest (primitiveColumnsGetAtLeastLabelWidth,
nestedRelationDoesNotDominateWidth, allChildrenGetPositiveWidth),
LayoutTransitionTest (tableModeAllColumnsGetLabelWidth, tableModeNoChildDominates)

**TDD**:

- `everyChildGetsAtLeastLabelWidth()`: For nested fixture at 2000px, run harness.
  For each entry in `getFieldWidths()`, assert justified >= label width.
  (LayoutTestResult needs to expose label widths too, or the harness stores the
  RelationLayout for direct child iteration.)
- `noChildDominatesWidth()`: For nested fixture at 2000px, assert no single
  child's justified width exceeds 80% of total children width sum.
- `allChildrenPositiveWidth()`: For nested fixture at 2000px, assert all field
  widths > 0.

**Note**: These tests only run in table mode. The harness result's `isTableMode()`
must be true; skip/fail otherwise.

**Estimated test count**: 3–4.

### 3d. LayoutResizeTest

**Replaces**: OutlineDataVisibilityTest.singleInstanceResizePreservesData

**TDD**:

- `resizePreservesData(fixture)`: For each fixture:
  1. Create ONE LayoutTestHarness instance
  2. Call `run(1200, 600)` — wide mode
  3. Call `run(400, 600)` — narrow mode (same harness instance)
  4. Assert second result has non-empty `getRenderedTexts()` and contains
     data text prefixes from the fixture
- `resizeNarrowToWide(fixture)`: Reverse direction — start at 400, resize to
  1200. Data must survive.

**Estimated test count**: 4–8 (2 directions x 4 fixtures, or parameterized).

### Test Command
```bash
mvn -pl kramer test -Dtest="com.chiralbehaviors.layout.test.Layout*Test"
```

---

## Phase 4: Cleanup — Kramer-60rl

### Goal
Delete superseded test files and verify full suite passes.

### Files

| File | Action |
|------|--------|
| `kramer/src/test/java/com/chiralbehaviors/layout/FullPipelineLayoutTest.java` | DELETE |
| `kramer/src/test/java/com/chiralbehaviors/layout/OutlineDataVisibilityTest.java` | DELETE |
| `kramer/src/test/java/com/chiralbehaviors/layout/WidthJustificationTest.java` | DELETE |
| `kramer/src/test/java/com/chiralbehaviors/layout/LayoutTransitionTest.java` | DELETE |
| `kramer/src/test/java/com/chiralbehaviors/layout/OutlineFieldWidthTest.java` | KEEP |

### Steps

1. Run `mvn -pl kramer test` with all old + new tests coexisting. Verify green.
2. Delete the 4 old test files.
3. Run `mvn -pl kramer clean install`. Verify green.
4. Compare test count before/after. Should be similar (27 old → ~22–28 new).

### Test Command
```bash
mvn -pl kramer clean install
```

---

## Risk Factors

| Risk | Mitigation |
|------|------------|
| VirtualFlow timing — cells may not materialize in single applyCss/layout pass | Use the proven CountDownLatch + nested Platform.runLater pattern from FullPipelineLayoutTest |
| LayoutTestHarness re-run semantics — second run() on same instance may see stale state | Clear stage scene root children before each run(); create fresh RelationLayout each time |
| Wide fixture (12 columns) may never reach table mode at test widths | Use wider test width (2000px) for fairness tests; mode selection tests accept outline at narrow widths |
| Package visibility — LayoutTestResult needs access to RelationLayout.isUseTable(), child justified widths | These are public methods; no access issue |

## Parallelization

Phases are strictly sequential. Within Phase 3, the 4 test classes are
independent and could be developed in parallel by separate agents, each taking
one test class. However, all 4 depend on the same harness and fixtures, so
parallel development only saves time if the agent can reference the completed
Phase 1+2 artifacts.

## Test Count Estimate

| Source | Count |
|--------|-------|
| **Old tests (deleted)** | |
| FullPipelineLayoutTest | 6 |
| OutlineDataVisibilityTest | 5 |
| WidthJustificationTest | 5 |
| LayoutTransitionTest | 11 |
| **Old total** | **27** |
| **New tests (created)** | |
| LayoutTestHarnessTest | 2 |
| LayoutFixturesTest | 5 |
| LayoutModeSelectionTest | 4–6 |
| LayoutDataVisibilityTest | 4–6 |
| LayoutWidthFairnessTest | 3–4 |
| LayoutResizeTest | 4–8 |
| **New total** | **22–31** |

## Execution Context for Each Bead

### Kramer-jyn0 (Phase 1)
- Search keywords: `LayoutTestHarness`, `autoLayout`, `FocusController`, `Platform.runLater`, `CountDownLatch`
- Reference: `FullPipelineLayoutTest.runPipeline()` (lines 162–206) for the pipeline pattern
- Reference: `SchemaNodeLayout.autoLayout(double, double, FocusTraversal, Style)` for the entry point
- Use `mcp__plugin_nx_sequential-thinking__sequentialthinking` for designing the LayoutTestResult API
- Branch: `feature/Kramer-jyn0-layout-test-harness`

### Kramer-ip1m (Phase 2)
- Search keywords: `LayoutFixtures`, `Relation`, `Primitive`, `buildSchema`, `buildData`
- Reference: `FullPipelineLayoutTest.buildSchema/buildData` for nested fixture
- Reference: `LayoutTransitionTest.buildFlatSchema` for flat fixture
- Reference: `LayoutTransitionTest.resizeSweepDeeplyNested` for deep fixture
- Branch: `feature/Kramer-ip1m-layout-fixtures`

### Kramer-r618 (Phase 3)
- Search keywords: `LayoutModeSelectionTest`, `LayoutDataVisibilityTest`, `LayoutWidthFairnessTest`, `LayoutResizeTest`
- Reference: `LayoutTransitionTest.transitionIsMonotonic` for monotonic sweep pattern
- Reference: `WidthJustificationTest.primitiveColumnsGetAtLeastLabelWidth` for fairness checks
- Reference: `OutlineDataVisibilityTest.singleInstanceResizePreservesData` for resize pattern
- SPAWN parallel agents for each test class if context permits
- Branch: `feature/Kramer-r618-layout-test-classes`

### Kramer-60rl (Phase 4)
- Verify `mvn -pl kramer test` green before deleting
- Delete files, verify `mvn -pl kramer clean install`
- Branch: `feature/Kramer-60rl-test-cleanup`
