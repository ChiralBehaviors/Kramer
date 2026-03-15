# Implementation Plan: F1 — Set sensible maxTablePrimitiveWidth default

**Bead**: Kramer-8jq (F1: Set sensible maxTablePrimitiveWidth default)
**RDR**: RDR-008 (accepted 2026-03-14) — Phase 1 Critical Behavioral Fix
**Date**: 2026-03-15

## Executive Summary

Change `PrimitiveStyle.maxTablePrimitiveWidth` from `Double.MAX_VALUE` to `350.0`, aligning with Bakke 2013 Section 3.3 which specifies a cap "on the order of 50 characters." This is the most impactful behavioral divergence from the paper — without the cap, variable-length text fields inflate table width and force outline mode, producing unnecessarily verbose layouts.

This is a **breaking change**. Existing users who rely on uncapped table column widths can restore prior behavior via `setMaxTablePrimitiveWidth(Double.MAX_VALUE)`.

## Scope

- 2 source files modified (PrimitiveStyle.java, PrimitiveLayout.java)
- 1 test fixture created (catalog.json)
- 1 new test class created (MaxTablePrimitiveWidthTest.java)
- 1 existing test class updated (StylesheetPropertyTest.java)

## Dependency Graph

```
Kramer-5o0 (fixture)
    |
    v
Kramer-1kn (failing tests)
    |
    v
Kramer-xf6 (implementation + existing test update)
    |
    v
Kramer-2qj (refactoring)
    |
    v
Kramer-8jq (F1 complete) ──blocks──> Kramer-53d (F7), Kramer-avn (F2+F3),
                                       Kramer-b56 (F5), Kramer-9qj (F4)
```

**Critical path**: 5o0 -> 1kn -> xf6 -> 2qj -> 8jq (linear, no parallelization)

## Task Breakdown

### Task 1: Kramer-5o0 — Create catalog.json test fixture

**Type**: Data creation (no code changes)
**Dependencies**: None (first task, currently ready)
**File**: `kramer/src/test/resources/catalog.json`

**Content specification**:
A JSON array of 6-8 course records, each an object with 4 fields:
- `courseNumber`: short string, fixed-length (e.g., "CS101") — renders ~35-50px
- `title`: medium string, moderate variance (e.g., "Intro to CS") — renders ~100-250px
- `description`: long string, high variance — average ~600px, max >1200px
- `credits`: short string, fixed-length (e.g., "3") — renders ~10-30px

**Requirements for downstream tests**:
- The `description` field must trigger variable-length classification: `maxWidth / averageWidth > 2.0`
- Include at least one short description (~20 chars) and one long description (~200+ chars)
- The `description` field's average rendered width must exceed 350px at ~7px/char

**Verification**:
```bash
python3 -m json.tool kramer/src/test/resources/catalog.json
```

**Acceptance criteria**:
- [ ] Valid JSON, parseable by Jackson
- [ ] 6-8 records with all 4 fields present
- [ ] Description field has max/avg text length ratio > 2.0
- [ ] Representative of Bakke 2013 Figure 1 course catalog

---

### Task 2: Kramer-1kn — Write failing tests for 350.0 default and table mode

**Type**: Test creation (TDD red phase)
**Dependencies**: Kramer-5o0 (fixture must exist)
**File**: `kramer/src/test/java/com/chiralbehaviors/layout/MaxTablePrimitiveWidthTest.java`

**Tests**:

1. **`defaultCapIs350()`**
   - Instantiate real `PrimitiveStyle.PrimitiveTextStyle(labelStyle, new Insets(0), labelStyle)`
   - Assert `style.getMaxTablePrimitiveWidth() == 350.0`
   - FAILS NOW: actual is `Double.MAX_VALUE`

2. **`capLimitsVariableLengthTableColumnWidth()`**
   - Create `PrimitiveLayout` with `dataWidth = 600`
   - Use real `PrimitiveTextStyle` (default cap = 350.0 after implementation)
   - Assert `layout.tableColumnWidth() == Style.snap(350.0)`
   - Assert `layout.calculateTableColumnWidth() == Style.snap(350.0)`
   - FAILS NOW: returns `Style.snap(600)` because cap is `Double.MAX_VALUE`

3. **`catalogTableModeTriggers()`**
   - Build `Relation("catalog")` with 4 `Primitive` children
   - Create `RelationLayout` with mock `RelationStyle` (zero insets)
   - Set up children with `dataWidth` values: courseNumber=50, title=200, description=600, credits=30
   - Call `layout.layout(800.0)`
   - With 350 cap: `tableWidth = 50+200+350+30 = 630 <= 800` --> table mode
   - With MAX_VALUE: `tableWidth = 50+200+600+30 = 880 > 800` --> outline mode
   - Assert `layout.isUseTable() == true`
   - FAILS NOW: outline mode chosen because description is uncapped at 600

**Test command**:
```bash
mvn -pl kramer test -Dtest=MaxTablePrimitiveWidthTest
```

**Expected result**: All 3 tests compile but FAIL (assertions against current MAX_VALUE default)

**Acceptance criteria**:
- [ ] Test class compiles cleanly
- [ ] All 3 tests fail with clear assertion messages
- [ ] Tests follow existing mock patterns from `StylesheetPropertyTest`
- [ ] No dependency on JavaFX runtime (pure unit tests with mocks)

---

### Task 3: Kramer-xf6 — Implement default change and update existing tests

**Type**: Implementation (TDD green phase)
**Dependencies**: Kramer-1kn (failing tests must exist)

**Source change**:

File: `kramer/src/main/java/com/chiralbehaviors/layout/style/PrimitiveStyle.java`
Line 185:
```java
// BEFORE:
private double maxTablePrimitiveWidth = Double.MAX_VALUE;

// AFTER:
private double maxTablePrimitiveWidth = 350.0;
```

**Existing test updates**:

File: `kramer/src/test/java/com/chiralbehaviors/layout/StylesheetPropertyTest.java`

a. `primitiveStyleDefaultValues()` (line 521):
```java
// BEFORE:
assertEquals(Double.MAX_VALUE, style.getMaxTablePrimitiveWidth());

// AFTER:
assertEquals(350.0, style.getMaxTablePrimitiveWidth());
```

b. `tableMaxPrimitiveWidthDefaultNoCap()` (line 383):
- RENAME method to `explicitMaxValueDisablesCap()`
- Update Javadoc: "Explicit MAX_VALUE override disables the cap"
- The mock returns `Double.MAX_VALUE` explicitly, so the assertion body is unchanged
- This tests the migration path: `setMaxTablePrimitiveWidth(Double.MAX_VALUE)`

c. `mockPrimitiveStyle()` (line 73) and `makePrimitive()` (line 94):
- NO CHANGES needed. These helpers explicitly mock `getMaxTablePrimitiveWidth()` to return `Double.MAX_VALUE`. They test behavior under explicit override, not the default. All tests using them pass unchanged.

**Test command**:
```bash
mvn -pl kramer test
```

**Expected result**: ALL tests pass — new `MaxTablePrimitiveWidthTest` tests + updated `StylesheetPropertyTest` + all other existing tests

**Acceptance criteria**:
- [ ] `mvn -pl kramer test` passes with 0 failures
- [ ] All 3 new tests in MaxTablePrimitiveWidthTest pass
- [ ] All existing tests in StylesheetPropertyTest pass
- [ ] All other test classes unaffected

---

### Task 4: Kramer-2qj — Eliminate PrimitiveLayout table width method duplication

**Type**: Refactoring (TDD refactor phase)
**Dependencies**: Kramer-xf6 (all tests must be passing first)

**Source change**:

File: `kramer/src/main/java/com/chiralbehaviors/layout/PrimitiveLayout.java`
Lines 86-89:
```java
// BEFORE:
@Override
public double calculateTableColumnWidth() {
    return Math.min(dataWidth, style.getMaxTablePrimitiveWidth());
}

// AFTER:
@Override
public double calculateTableColumnWidth() {
    return tableColumnWidth();
}
```

`tableColumnWidth()` at line 247-249 remains unchanged — it is the canonical implementation.

**Why only PrimitiveLayout**: In `RelationLayout`, `calculateTableColumnWidth()` computes the sum of children's table widths (used for table-mode decision), while `tableColumnWidth()` returns a cached width with indentation adjustments. They serve different purposes and are not duplicated.

**Test command**:
```bash
mvn -pl kramer test
```

**Expected result**: ALL tests pass (no behavioral change, pure refactoring)

**Acceptance criteria**:
- [ ] `mvn -pl kramer test` passes with 0 failures
- [ ] `calculateTableColumnWidth()` delegates to `tableColumnWidth()`
- [ ] No behavioral change (tests prove this)

## Risk Analysis

| Risk | Severity | Mitigation |
|------|----------|------------|
| 350.0 value incorrect for actual font metrics | Medium | Value is from approved design; can be recalibrated after RDR-007 Phase 3 measurement corrections. Setter provides runtime override. |
| Breaking change for existing users | Medium | `setMaxTablePrimitiveWidth(Double.MAX_VALUE)` restores prior behavior. Document in release notes. |
| Table mode test with mocks may not reflect real layout pipeline | Low | Test exercises the real `RelationLayout.layout()` code path. Mock-only values are insets (set to 0), which is conservative. |
| Refactoring in Task 4 accidentally changes behavior | Low | All tests must pass before and after. The two methods have identical bodies. |

## Downstream Impact

Completion of Kramer-8jq (F1) unblocks 4 features:
- **Kramer-avn** (F2+F3): Column set formation + DP partitioning — uses catalog fixture as regression baseline
- **Kramer-b56** (F5): VARIABLE_LENGTH_THRESHOLD configurability — depends on correct default being in place
- **Kramer-53d** (F7): Style measurement caching — regression baseline needed
- **Kramer-9qj** (F4): OutlineSnapValueWidth — depends on correct measurement foundation

## Files Modified

| File | Task | Change Type |
|------|------|------------|
| `kramer/src/test/resources/catalog.json` | 5o0 | NEW |
| `kramer/src/test/java/.../MaxTablePrimitiveWidthTest.java` | 1kn | NEW |
| `kramer/src/main/java/.../style/PrimitiveStyle.java` | xf6 | MODIFY (line 185) |
| `kramer/src/test/java/.../StylesheetPropertyTest.java` | xf6 | MODIFY (lines 383, 521) |
| `kramer/src/main/java/.../PrimitiveLayout.java` | 2qj | MODIFY (lines 86-89) |

## Test Commands Summary

```bash
# Task 1 — verify fixture
python3 -m json.tool kramer/src/test/resources/catalog.json

# Task 2 — verify tests compile but fail
mvn -pl kramer test -Dtest=MaxTablePrimitiveWidthTest

# Task 3 — verify all tests pass after implementation
mvn -pl kramer test

# Task 4 — verify all tests still pass after refactoring
mvn -pl kramer test

# Full project verification
mvn clean install
```
