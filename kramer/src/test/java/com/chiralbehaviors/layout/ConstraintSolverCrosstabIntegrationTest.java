// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for CROSSTAB-aware constraint solving with mixed
 * TABLE/OUTLINE/CROSSTAB schemas.
 *
 * <p>Tests verify the full constraint-tree → solver → mode-assignment pipeline
 * for schemas containing CROSSTAB-eligible nodes alongside TABLE/OUTLINE nodes.
 * {@link RelationConstraint} objects are constructed directly (replicating the
 * logic of {@code AutoLayout.buildConstraintTree}) so no JavaFX toolkit is required.
 *
 * <p>Crosstab eligibility is determined by {@code crosstabEligible=true} and a
 * non-zero {@code crosstabWidth}. The available widths are set so each test can
 * control which modes fit.
 */
class ConstraintSolverCrosstabIntegrationTest {

    private static final ConstraintSolver SOLVER = new ExhaustiveConstraintSolver();

    // -----------------------------------------------------------------------
    // Test 1: CROSSTAB assigned when eligible and TABLE does not fit
    // 3-level schema: root, child A (crosstab-eligible), child B (no pivot).
    // Child A tableWidth is too wide but crosstabWidth fits — solver picks CROSSTAB.
    // The solver prefers TABLE over CROSSTAB as a tie-break when both fit, so
    // CROSSTAB is only chosen when TABLE is infeasible but CROSSTAB is feasible.
    // Child B: TABLE fits → TABLE.
    // -----------------------------------------------------------------------

    @Test
    void crosstabAssignedWhenEligibleAndFits() {
        SchemaPath rootPath = new SchemaPath("root");
        SchemaPath pathA    = rootPath.child("childA");
        SchemaPath pathB    = rootPath.child("childB");

        double availableWidth = 200.0;

        // Child A: tableWidth=500 exceeds availableWidth=200 so TABLE is infeasible.
        // crosstabWidth=150 fits → solver must choose CROSSTAB (only column-mode that fits).
        RelationConstraint childA = new RelationConstraint(
            pathA,
            /*tableWidth*/ 500.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 150.0,
            /*crosstabEligible*/ true
        );

        // Child B: not eligible for CROSSTAB, tableWidth=80 fits
        RelationConstraint childB = new RelationConstraint(
            pathB,
            /*tableWidth*/ 80.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 0.0,
            /*crosstabEligible*/ false
        );

        RelationConstraint root = new RelationConstraint(
            rootPath,
            /*tableWidth*/ 600.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(childA, childB),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 0.0,
            /*crosstabEligible*/ false
        );

        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);

        assertEquals(3, result.size(), "root + childA + childB = 3 entries");
        // TABLE is infeasible (500 > 200), CROSSTAB fits (150 ≤ 200) → CROSSTAB chosen
        assertEquals(RelationRenderMode.CROSSTAB, result.get(pathA),
            "Child A TABLE does not fit but CROSSTAB does → CROSSTAB");
        // Child B has no CROSSTAB eligibility; TABLE fits → TABLE
        assertEquals(RelationRenderMode.TABLE, result.get(pathB),
            "Child B TABLE fits → TABLE");
    }

    // -----------------------------------------------------------------------
    // Test 2: CROSSTAB degrades to TABLE when too narrow for CROSSTAB but TABLE fits
    // Same schema as test 1, but available width only fits TABLE, not CROSSTAB.
    // Expect: A = TABLE (not CROSSTAB), B = TABLE.
    // -----------------------------------------------------------------------

    @Test
    void crosstabDegradesToTableWhenTooNarrow() {
        SchemaPath rootPath = new SchemaPath("root");
        SchemaPath pathA    = rootPath.child("childA");
        SchemaPath pathB    = rootPath.child("childB");

        // Width allows TABLE (tableWidth=100) but not CROSSTAB (crosstabWidth=300)
        double availableWidth = 200.0;

        RelationConstraint childA = new RelationConstraint(
            pathA,
            /*tableWidth*/ 100.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 300.0,   // crosstabWidth > availableWidth → does not fit
            /*crosstabEligible*/ true
        );

        RelationConstraint childB = new RelationConstraint(
            pathB,
            /*tableWidth*/ 80.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 0.0,
            /*crosstabEligible*/ false
        );

        RelationConstraint root = new RelationConstraint(
            rootPath,
            /*tableWidth*/ 200.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(childA, childB),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 0.0,
            /*crosstabEligible*/ false
        );

        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);

        // CROSSTAB does not fit (300 > 200), so solver must not assign CROSSTAB to A
        assertNotEquals(RelationRenderMode.CROSSTAB, result.get(pathA),
            "Child A CROSSTAB does not fit → solver must not assign CROSSTAB");
        // TABLE fits (100 ≤ 200) → TABLE is preferred over OUTLINE
        assertEquals(RelationRenderMode.TABLE, result.get(pathA),
            "Child A tableWidth fits → should degrade to TABLE");
    }

    // -----------------------------------------------------------------------
    // Test 3: CROSSTAB degrades to OUTLINE when nothing fits
    // Very narrow width: neither TABLE nor CROSSTAB fits.
    // Expect: A = OUTLINE, B = OUTLINE.
    // -----------------------------------------------------------------------

    @Test
    void crosstabDegradesToOutlineWhenNothingFits() {
        SchemaPath rootPath = new SchemaPath("root");
        SchemaPath pathA    = rootPath.child("childA");
        SchemaPath pathB    = rootPath.child("childB");

        // Width too narrow for either TABLE (tableWidth=100) or CROSSTAB (crosstabWidth=200)
        double availableWidth = 50.0;

        RelationConstraint childA = new RelationConstraint(
            pathA,
            /*tableWidth*/ 100.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 200.0,
            /*crosstabEligible*/ true
        );

        RelationConstraint childB = new RelationConstraint(
            pathB,
            /*tableWidth*/ 80.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 0.0,
            /*crosstabEligible*/ false
        );

        RelationConstraint root = new RelationConstraint(
            rootPath,
            /*tableWidth*/ 200.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(childA, childB),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 0.0,
            /*crosstabEligible*/ false
        );

        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);

        assertEquals(RelationRenderMode.OUTLINE, result.get(pathA),
            "Child A: TABLE and CROSSTAB both exceed available width → OUTLINE");
        assertEquals(RelationRenderMode.OUTLINE, result.get(pathB),
            "Child B: TABLE exceeds available width → OUTLINE");
    }

    // -----------------------------------------------------------------------
    // Test 4: hardCrosstab=true pins CROSSTAB regardless of width
    // Width is very narrow, but hardCrosstab is set on child A.
    // Expect: A = CROSSTAB (pinned), B = OUTLINE (does not fit).
    // -----------------------------------------------------------------------

    @Test
    void hardCrosstabPinnedRegardlessOfWidth() {
        SchemaPath rootPath = new SchemaPath("root");
        SchemaPath pathA    = rootPath.child("childA");
        SchemaPath pathB    = rootPath.child("childB");

        // Very narrow — nothing fits, but A has hardCrosstab=true
        double availableWidth = 30.0;

        RelationConstraint childA = new RelationConstraint(
            pathA,
            /*tableWidth*/ 200.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(),
            /*hardCrosstab*/ true,     // pinned: solver must keep CROSSTAB
            /*crosstabWidth*/ 250.0,
            /*crosstabEligible*/ true
        );

        RelationConstraint childB = new RelationConstraint(
            pathB,
            /*tableWidth*/ 80.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 0.0,
            /*crosstabEligible*/ false
        );

        RelationConstraint root = new RelationConstraint(
            rootPath,
            /*tableWidth*/ 300.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(childA, childB),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 0.0,
            /*crosstabEligible*/ false
        );

        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);

        assertEquals(RelationRenderMode.CROSSTAB, result.get(pathA),
            "Child A has hardCrosstab=true → solver must assign CROSSTAB regardless of width");
        // Child B: TABLE (80) > available (30) → OUTLINE
        assertEquals(RelationRenderMode.OUTLINE, result.get(pathB),
            "Child B does not fit TABLE at narrow width → OUTLINE");
    }

    // -----------------------------------------------------------------------
    // Test 5: Mixed eligibility — 3 children with different CROSSTAB eligibility
    // Child A: eligible, tableWidth does NOT fit, crosstabWidth fits → CROSSTAB.
    // Child B: eligible, crosstabWidth does NOT fit, tableWidth fits → TABLE.
    // Child C: not eligible, tableWidth fits → TABLE (or OUTLINE).
    //
    // The solver prefers TABLE over CROSSTAB as a tie-break when both fit, so
    // CROSSTAB is only chosen when TABLE is infeasible but CROSSTAB is feasible.
    // -----------------------------------------------------------------------

    @Test
    void mixedEligibilityCorrectAssignments() {
        SchemaPath rootPath = new SchemaPath("report");
        SchemaPath pathA    = rootPath.child("sales");     // eligible, table too wide, crosstab fits
        SchemaPath pathB    = rootPath.child("inventory"); // eligible, crosstab too wide, table fits
        SchemaPath pathC    = rootPath.child("summary");   // not eligible, table fits

        double availableWidth = 400.0;

        // A: eligible. tableWidth=600 does NOT fit (600 > 400), crosstabWidth=200 fits (200 ≤ 400).
        // With TABLE infeasible and CROSSTAB feasible, solver assigns CROSSTAB.
        RelationConstraint childA = new RelationConstraint(
            pathA,
            /*tableWidth*/ 600.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 200.0,
            /*crosstabEligible*/ true
        );

        // B: eligible, crosstabWidth=700 does NOT fit, tableWidth=90 fits → TABLE
        RelationConstraint childB = new RelationConstraint(
            pathB,
            /*tableWidth*/ 90.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 700.0,
            /*crosstabEligible*/ true
        );

        // C: not eligible, tableWidth=70 fits → TABLE
        RelationConstraint childC = new RelationConstraint(
            pathC,
            /*tableWidth*/ 70.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 0.0,
            /*crosstabEligible*/ false
        );

        RelationConstraint root = new RelationConstraint(
            rootPath,
            /*tableWidth*/ 800.0,
            /*nestedHorizontalInset*/ 0.0,
            /*availableWidthAsOutline*/ availableWidth,
            /*availableWidthAsTable*/ Double.MAX_VALUE,
            /*children*/ List.of(childA, childB, childC),
            /*hardCrosstab*/ false,
            /*crosstabWidth*/ 0.0,
            /*crosstabEligible*/ false
        );

        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);

        assertEquals(4, result.size(), "root + 3 children = 4 entries");

        // A: TABLE infeasible, CROSSTAB feasible → CROSSTAB
        assertEquals(RelationRenderMode.CROSSTAB, result.get(pathA),
            "Child A TABLE infeasible, CROSSTAB fits → CROSSTAB");

        // B: CROSSTAB infeasible, TABLE fits → TABLE
        assertEquals(RelationRenderMode.TABLE, result.get(pathB),
            "Child B CROSSTAB too wide, TABLE fits → TABLE");

        // C: not eligible, TABLE fits → TABLE
        assertEquals(RelationRenderMode.TABLE, result.get(pathC),
            "Child C not eligible, TABLE fits → TABLE");
    }
}
