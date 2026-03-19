// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for RDR-024 T1: crosstabWidth and crosstabEligible fields on RelationConstraint,
 * and the fitsCrosstab() / fitsCrosstabInParentTable() convenience methods.
 */
class RelationConstraintCrosstabTest {

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    /** Build a leaf constraint with all crosstab fields specified. */
    private static RelationConstraint leaf(String name,
                                           double tableWidth,
                                           double nestedInset,
                                           double availableOutline,
                                           double availableTable,
                                           boolean hardCrosstab,
                                           double crosstabWidth,
                                           boolean crosstabEligible) {
        return new RelationConstraint(
                new SchemaPath(name),
                tableWidth,
                nestedInset,
                availableOutline,
                availableTable,
                List.of(),
                hardCrosstab,
                crosstabWidth,
                crosstabEligible
        );
    }

    // ------------------------------------------------------------------
    // 1. Non-eligible defaults → fitsCrosstab() false
    // ------------------------------------------------------------------

    @Test
    void nonEligibleDefaults() {
        RelationConstraint rc = leaf("node", 100.0, 5.0, 200.0, Double.MAX_VALUE,
                                    false, 0.0, false);
        assertFalse(rc.crosstabEligible());
        assertEquals(0.0, rc.crosstabWidth());
        assertFalse(rc.fitsCrosstab(),            "ineligible → fitsCrosstab false");
        assertFalse(rc.fitsCrosstabInParentTable(),"ineligible → fitsCrosstabInParentTable false");
    }

    // ------------------------------------------------------------------
    // 2. Eligible + fits outline → fitsCrosstab() true
    // ------------------------------------------------------------------

    @Test
    void eligibleFitsOutline() {
        // crosstabWidth=100, nestedInset=5 → 105 ≤ 200 (availableOutline) → fits
        RelationConstraint rc = leaf("node", 100.0, 5.0, 200.0, Double.MAX_VALUE,
                                    false, 100.0, true);
        assertTrue(rc.crosstabEligible());
        assertTrue(rc.fitsCrosstab(), "100+5=105 ≤ 200 → fits outline");
    }

    // ------------------------------------------------------------------
    // 3. Eligible but crosstabWidth too large → fitsCrosstab() false
    // ------------------------------------------------------------------

    @Test
    void eligibleDoesNotFitOutline() {
        // crosstabWidth=300, nestedInset=5 → 305 > 200 → does not fit
        RelationConstraint rc = leaf("node", 100.0, 5.0, 200.0, Double.MAX_VALUE,
                                    false, 300.0, true);
        assertTrue(rc.crosstabEligible());
        assertFalse(rc.fitsCrosstab(), "300+5=305 > 200 → does not fit outline");
    }

    // ------------------------------------------------------------------
    // 4. Eligible + fits parent table width → fitsCrosstabInParentTable() true
    // ------------------------------------------------------------------

    @Test
    void eligibleFitsParentTable() {
        // crosstabWidth=100, nestedInset=5 → 105 ≤ 200 (availableTable) → fits
        RelationConstraint rc = leaf("node", 80.0, 5.0, 50.0, 200.0,
                                    false, 100.0, true);
        assertTrue(rc.fitsCrosstabInParentTable(), "100+5=105 ≤ 200 → fits parent table");
        // outline does NOT fit: 105 > 50
        assertFalse(rc.fitsCrosstab(), "100+5=105 > 50 → does not fit outline");
    }

    // ------------------------------------------------------------------
    // 5. hardCrosstab is independent of fitsCrosstab() — solver pins, not fitsCrosstab
    // ------------------------------------------------------------------

    @Test
    void hardCrosstabExcludesFromEligibility() {
        // hardCrosstab=true, crosstabEligible=true, fits both widths
        RelationConstraint rc = leaf("node", 80.0, 5.0, 200.0, 300.0,
                                    true, 100.0, true);
        assertTrue(rc.hardCrosstab());
        // fitsCrosstab checks crosstabEligible + geometry only; hardCrosstab is independent
        assertTrue(rc.fitsCrosstab(),             "hardCrosstab doesn't affect fitsCrosstab");
        assertTrue(rc.fitsCrosstabInParentTable(),"hardCrosstab doesn't affect fitsCrosstabInParentTable");
    }

    // ------------------------------------------------------------------
    // 6. Existing fitsTable() / fitsTableInParentTable() still work with extended record
    // ------------------------------------------------------------------

    @Test
    void existingFitsTableUnchanged() {
        // tableWidth=80, nestedInset=5, availableOutline=100 → 85 ≤ 100 → fitsTable
        // tableWidth=80, nestedInset=5, availableTable=90   → 85 ≤ 90  → fitsTableInParentTable
        RelationConstraint rc = leaf("node", 80.0, 5.0, 100.0, 90.0,
                                    false, 0.0, false);
        assertTrue(rc.fitsTable(),             "80+5=85 ≤ 100 → fitsTable");
        assertTrue(rc.fitsTableInParentTable(),"80+5=85 ≤ 90  → fitsTableInParentTable");

        // Now one that does not fit
        RelationConstraint tight = leaf("tight", 100.0, 5.0, 100.0, 90.0,
                                       false, 0.0, false);
        assertFalse(tight.fitsTable(),              "100+5=105 > 100 → does not fitsTable");
        assertFalse(tight.fitsTableInParentTable(), "100+5=105 > 90  → does not fitsTableInParentTable");
    }

    // ------------------------------------------------------------------
    // 7. childrenListDefensiveCopy — List.copyOf still works with 9-field record
    // ------------------------------------------------------------------

    @Test
    void childrenListDefensiveCopy() {
        SchemaPath parentPath = new SchemaPath("parent");
        SchemaPath childPath  = parentPath.child("child");

        RelationConstraint child = new RelationConstraint(
                childPath, 40.0, 0.0, 100.0, Double.MAX_VALUE,
                List.of(), false, 0.0, false
        );

        // Use a mutable list to verify defensive copy
        var mutable = new java.util.ArrayList<RelationConstraint>();
        mutable.add(child);

        RelationConstraint parent = new RelationConstraint(
                parentPath, 100.0, 0.0, 200.0, Double.MAX_VALUE,
                mutable, false, 0.0, false
        );

        // Mutate original list — should not affect record
        mutable.clear();

        assertEquals(1, parent.children().size(), "children() should be a defensive copy");
        assertSame(child, parent.children().get(0));
    }

    // ------------------------------------------------------------------
    // 8. Exact boundary conditions for fitsCrosstab
    // ------------------------------------------------------------------

    @Test
    void fitsCrosstabBoundaryExact() {
        // crosstabWidth + nestedInset == availableWidthAsOutline → should fit (≤)
        RelationConstraint exact = leaf("node", 0.0, 5.0, 105.0, Double.MAX_VALUE,
                                       false, 100.0, true);
        assertTrue(exact.fitsCrosstab(), "100+5=105 == 105 → boundary fits (<=)");

        // One unit over
        RelationConstraint over = leaf("node", 0.0, 5.0, 104.9, Double.MAX_VALUE,
                                      false, 100.0, true);
        assertFalse(over.fitsCrosstab(), "100+5=105 > 104.9 → over boundary does not fit");
    }
}
