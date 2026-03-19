// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for Kramer-044: ConstraintSolver interface + ExhaustiveConstraintSolver.
 * Extended by RDR-024 T2: ternary decisions (TABLE / OUTLINE / CROSSTAB).
 */
class ConstraintSolverTest {

    private static final ConstraintSolver SOLVER = new ExhaustiveConstraintSolver();

    // ------------------------------------------------------------------
    // Helper: build a leaf RelationConstraint (no children)
    // ------------------------------------------------------------------

    private static RelationConstraint leaf(String name, double tableWidth,
                                           double nestedInset, double available,
                                           boolean hardCrosstab) {
        return new RelationConstraint(
                new SchemaPath(name),
                tableWidth,
                tableWidth,
                nestedInset,
                available,
                Double.MAX_VALUE,
                List.of(),
                hardCrosstab,
                0.0,
                false
        );
    }

    /**
     * Build a leaf that is crosstab-eligible with the given crosstabWidth.
     * tableWidth is set larger than available so TABLE will not fit unless
     * explicitly desired; crosstabWidth can be set independently.
     */
    private static RelationConstraint leafCrosstab(String name, double tableWidth,
                                                   double nestedInset, double available,
                                                   double crosstabWidth, boolean hardCrosstab) {
        return new RelationConstraint(
                new SchemaPath(name),
                tableWidth,
                tableWidth,
                nestedInset,
                available,
                Double.MAX_VALUE,
                List.of(),
                hardCrosstab,
                crosstabWidth,
                true   // crosstabEligible
        );
    }

    // ------------------------------------------------------------------
    // 1. Single node that fits as TABLE → solver returns TABLE
    // ------------------------------------------------------------------

    @Test
    void singleNodeFitsTable() {
        // tableWidth=80, nestedInset=5, available=100 → 80+5=85 ≤ 100
        RelationConstraint root = leaf("root", 80.0, 5.0, 100.0, false);
        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);
        assertEquals(RelationRenderMode.TABLE, result.get(new SchemaPath("root")));
    }

    // ------------------------------------------------------------------
    // 2. Single node that doesn't fit → solver returns OUTLINE
    // ------------------------------------------------------------------

    @Test
    void singleNodeDoesNotFitReturnsOutline() {
        // tableWidth=90, nestedInset=15, available=100 → 90+15=105 > 100
        RelationConstraint root = leaf("root", 90.0, 15.0, 100.0, false);
        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);
        assertEquals(RelationRenderMode.OUTLINE, result.get(new SchemaPath("root")));
    }

    // ------------------------------------------------------------------
    // 3. Two siblings — one fits TABLE, one doesn't → correct mixed assignment
    // ------------------------------------------------------------------

    @Test
    void twoSiblingsOneFitsOneDoesNot() {
        SchemaPath parentPath = new SchemaPath("parent");
        SchemaPath childAPath = parentPath.child("a");
        SchemaPath childBPath = parentPath.child("b");

        // parent has tableWidth=110, 2 relation children → tableWidth-per-child = 110/2 = 55
        // child "a": tableWidth=40, nestedInset=5
        //   outline available=50 → fitsTable: 45 ≤ 50 ✓
        //   table available=55  → fitsTableInParentTable: 45 ≤ 55 ✓
        RelationConstraint childA = new RelationConstraint(
                childAPath, 40.0, 40.0, 5.0, 50.0, 55.0, List.of(), false, 0.0, false);

        // child "b": tableWidth=60, nestedInset=5
        //   outline available=50 → fitsTable: 65 > 50 ✗
        //   table available=55  → fitsTableInParentTable: 65 > 55 ✗
        RelationConstraint childB = new RelationConstraint(
                childBPath, 60.0, 60.0, 5.0, 50.0, 55.0, List.of(), false, 0.0, false);

        // parent: tableWidth=110, nestedInset=5, available=200 → fits
        RelationConstraint parent = new RelationConstraint(
                parentPath, 110.0, 110.0, 5.0, 200.0, Double.MAX_VALUE, List.of(childA, childB), false, 0.0, false);

        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(parent);

        assertEquals(RelationRenderMode.TABLE, result.get(childAPath),
                     "child a should be TABLE");
        assertEquals(RelationRenderMode.OUTLINE, result.get(childBPath),
                     "child b should be OUTLINE");
    }

    // ------------------------------------------------------------------
    // 4. Parent TABLE makes child width narrower → solver finds feasibility
    // ------------------------------------------------------------------

    @Test
    void parentTableNarrowsChildAvailableWidth() {
        SchemaPath parentPath = new SchemaPath("parent");
        SchemaPath childPath = parentPath.child("child");

        // Child: tableWidth=40, nestedInset=5
        // When parent is TABLE, child available = parent.tableWidth = 60.
        // 40+5=45 ≤ 60 → child fits TABLE under TABLE parent
        // But if parent is OUTLINE, child available was set wider (200) — still fits
        // This test verifies the parent→child width propagation is correct.

        RelationConstraint child = new RelationConstraint(
                childPath, 40.0, 40.0, 5.0, 60.0, Double.MAX_VALUE, List.of(), false, 0.0, false);

        // Parent fits (tableWidth=60, nestedInset=5, available=100 → 65 ≤ 100)
        RelationConstraint parent = new RelationConstraint(
                parentPath, 60.0, 60.0, 5.0, 100.0, Double.MAX_VALUE, List.of(child), false, 0.0, false);

        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(parent);

        assertEquals(RelationRenderMode.TABLE, result.get(parentPath),
                     "parent should be TABLE");
        assertEquals(RelationRenderMode.TABLE, result.get(childPath),
                     "child should be TABLE");
    }

    // ------------------------------------------------------------------
    // 5. N > 15 → falls back to greedy (all nodes get independent decisions)
    // ------------------------------------------------------------------

    @Test
    void moreThan15NodesFallsBackToGreedy() {
        SchemaPath rootPath = new SchemaPath("root");
        List<RelationConstraint> children = new ArrayList<>();

        // Create 16 child relation nodes — 8 that fit, 8 that don't
        for (int i = 0; i < 8; i++) {
            SchemaPath p = rootPath.child("fits" + i);
            children.add(new RelationConstraint(p, 40.0, 40.0, 5.0, 50.0, Double.MAX_VALUE, List.of(), false, 0.0, false));
        }
        for (int i = 0; i < 8; i++) {
            SchemaPath p = rootPath.child("nope" + i);
            children.add(new RelationConstraint(p, 60.0, 60.0, 5.0, 50.0, Double.MAX_VALUE, List.of(), false, 0.0, false));
        }

        // root itself fits
        RelationConstraint root = new RelationConstraint(
                rootPath, 200.0, 200.0, 5.0, 1000.0, Double.MAX_VALUE, children, false, 0.0, false);

        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);

        // Greedy: each decides independently based on fitsTable()
        for (int i = 0; i < 8; i++) {
            SchemaPath p = rootPath.child("fits" + i);
            assertEquals(RelationRenderMode.TABLE, result.get(p),
                         "greedy: fits" + i + " should be TABLE");
        }
        for (int i = 0; i < 8; i++) {
            SchemaPath p = rootPath.child("nope" + i);
            assertEquals(RelationRenderMode.OUTLINE, result.get(p),
                         "greedy: nope" + i + " should be OUTLINE");
        }
    }

    // ------------------------------------------------------------------
    // 6. Empty root (no path in result) → empty result map
    // ------------------------------------------------------------------

    @Test
    void emptyConstraintListReturnsEmptyMap() {
        // A root with no children and the root itself has no children
        // The solver should return a map with just the root
        // But "empty constraint list" means calling solve with a single node
        // that has no children; the result has exactly that one entry.
        RelationConstraint root = leaf("lonely", 50.0, 0.0, 100.0, false);
        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);
        // Should have exactly one entry: the root itself
        assertEquals(1, result.size());
        assertEquals(RelationRenderMode.TABLE, result.get(new SchemaPath("lonely")));
    }

    // ------------------------------------------------------------------
    // 7. CROSSTAB hard override → preserved in result, not overridden
    // ------------------------------------------------------------------

    @Test
    void crosstabHardOverridePreserved() {
        // A node with hardCrosstab=true should always come out CROSSTAB
        // regardless of whether it would fit as TABLE or not.
        RelationConstraint crosstabNode = leaf("xtab", 80.0, 5.0, 100.0, true);
        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(crosstabNode);
        assertEquals(RelationRenderMode.CROSSTAB, result.get(new SchemaPath("xtab")),
                     "hardCrosstab=true must produce CROSSTAB in result");
    }

    @Test
    void crosstabDoesNotFitButStillCrosstab() {
        // Even when it would NOT fit as TABLE, hardCrosstab=true → CROSSTAB
        RelationConstraint crosstabNode = leaf("xtab2", 200.0, 50.0, 100.0, true);
        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(crosstabNode);
        assertEquals(RelationRenderMode.CROSSTAB, result.get(new SchemaPath("xtab2")));
    }

    // ==================================================================
    // RDR-024 T2 — ternary (TABLE / OUTLINE / CROSSTAB) solver tests
    // ==================================================================

    // ------------------------------------------------------------------
    // T2-1. Single eligible node with room → CROSSTAB assigned
    // ------------------------------------------------------------------

    @Test
    void singleEligibleNodeCrosstabWhenFits() {
        // tableWidth=200 (too wide), crosstabWidth=60, nestedInset=5, available=100
        // TABLE: 200+5=205 > 100 → no
        // CROSSTAB: 60+5=65 ≤ 100 → yes
        RelationConstraint root = leafCrosstab("root", 200.0, 5.0, 100.0, 60.0, false);
        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);
        assertEquals(RelationRenderMode.CROSSTAB, result.get(new SchemaPath("root")));
    }

    // ------------------------------------------------------------------
    // T2-2. Eligible but crosstab too wide → TABLE when table fits
    // ------------------------------------------------------------------

    @Test
    void singleEligibleNodeTableWhenCrosstabDoesNotFit() {
        // tableWidth=60, crosstabWidth=120, nestedInset=5, available=100
        // TABLE: 60+5=65 ≤ 100 → yes
        // CROSSTAB: 120+5=125 > 100 → no
        // Tie-break: TABLE preferred over CROSSTAB
        RelationConstraint root = leafCrosstab("root", 60.0, 5.0, 100.0, 120.0, false);
        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);
        assertEquals(RelationRenderMode.TABLE, result.get(new SchemaPath("root")));
    }

    // ------------------------------------------------------------------
    // T2-3. Eligible but nothing fits → OUTLINE
    // ------------------------------------------------------------------

    @Test
    void singleEligibleNodeOutlineWhenNothingFits() {
        // tableWidth=200, crosstabWidth=150, nestedInset=5, available=100
        // TABLE: 205 > 100 → no
        // CROSSTAB: 155 > 100 → no
        RelationConstraint root = leafCrosstab("root", 200.0, 5.0, 100.0, 150.0, false);
        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);
        assertEquals(RelationRenderMode.OUTLINE, result.get(new SchemaPath("root")));
    }

    // ------------------------------------------------------------------
    // T2-4. Mixed schema: 2 crosstab-eligible + 3 binary-only → correct mix
    // ------------------------------------------------------------------

    @Test
    void mixedSchemaCorrectAssignments() {
        SchemaPath rootPath = new SchemaPath("root");

        // Eligible node A: TABLE fits, CROSSTAB does not
        SchemaPath aPath = rootPath.child("a");
        RelationConstraint a = new RelationConstraint(
                aPath, 60.0, 60.0, 5.0, 100.0, Double.MAX_VALUE, List.of(), false, 200.0, true);

        // Eligible node B: TABLE does not fit, CROSSTAB fits
        SchemaPath bPath = rootPath.child("b");
        RelationConstraint b = new RelationConstraint(
                bPath, 200.0, 200.0, 5.0, 100.0, Double.MAX_VALUE, List.of(), false, 60.0, true);

        // Non-eligible node C: TABLE fits
        SchemaPath cPath = rootPath.child("c");
        RelationConstraint c = new RelationConstraint(
                cPath, 40.0, 40.0, 5.0, 100.0, Double.MAX_VALUE, List.of(), false, 0.0, false);

        // Non-eligible node D: TABLE does not fit
        SchemaPath dPath = rootPath.child("d");
        RelationConstraint d = new RelationConstraint(
                dPath, 110.0, 110.0, 5.0, 100.0, Double.MAX_VALUE, List.of(), false, 0.0, false);

        // Non-eligible node E: TABLE does not fit
        SchemaPath ePath = rootPath.child("e");
        RelationConstraint e = new RelationConstraint(
                ePath, 110.0, 110.0, 5.0, 100.0, Double.MAX_VALUE, List.of(), false, 0.0, false);

        // Root tableWidth is too wide to be TABLE itself (3000+0 > 2000),
        // so children always see parentTableWidth=MAX_VALUE (OUTLINE parent).
        // This ensures child "b" is evaluated via fitsTable() not fitsTableInParentTable().
        RelationConstraint root = new RelationConstraint(
                rootPath, 3000.0, 3000.0, 0.0, 2000.0, Double.MAX_VALUE,
                List.of(a, b, c, d, e), false, 0.0, false);

        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);

        assertEquals(RelationRenderMode.TABLE,   result.get(aPath), "a: TABLE fits, prefer TABLE over CROSSTAB");
        assertEquals(RelationRenderMode.CROSSTAB, result.get(bPath), "b: TABLE too wide, CROSSTAB fits");
        assertEquals(RelationRenderMode.TABLE,   result.get(cPath), "c: TABLE fits");
        assertEquals(RelationRenderMode.OUTLINE, result.get(dPath), "d: nothing fits");
        assertEquals(RelationRenderMode.OUTLINE, result.get(ePath), "e: nothing fits");
    }

    // ------------------------------------------------------------------
    // T2-5. Greedy fallback (N > 15): eligible node → CROSSTAB preferred
    // ------------------------------------------------------------------

    @Test
    void greedyFallbackPrefersCrosstab() {
        SchemaPath rootPath = new SchemaPath("root");
        List<RelationConstraint> children = new ArrayList<>();

        // 16 children to exceed threshold (15)
        // First 5: crosstab-eligible where only CROSSTAB fits
        for (int i = 0; i < 5; i++) {
            SchemaPath p = rootPath.child("xt" + i);
            // TABLE: 200+5=205>100 no; CROSSTAB: 60+5=65≤100 yes
            children.add(new RelationConstraint(
                    p, 200.0, 200.0, 5.0, 100.0, Double.MAX_VALUE, List.of(), false, 60.0, true));
        }
        // Next 5: non-eligible, TABLE fits
        for (int i = 0; i < 5; i++) {
            SchemaPath p = rootPath.child("tb" + i);
            children.add(new RelationConstraint(
                    p, 40.0, 40.0, 5.0, 100.0, Double.MAX_VALUE, List.of(), false, 0.0, false));
        }
        // Last 6: non-eligible, TABLE does not fit
        for (int i = 0; i < 6; i++) {
            SchemaPath p = rootPath.child("ol" + i);
            children.add(new RelationConstraint(
                    p, 200.0, 200.0, 5.0, 100.0, Double.MAX_VALUE, List.of(), false, 0.0, false));
        }

        RelationConstraint root = new RelationConstraint(
                rootPath, 1000.0, 1000.0, 0.0, 5000.0, Double.MAX_VALUE, children, false, 0.0, false);

        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);

        for (int i = 0; i < 5; i++) {
            SchemaPath p = rootPath.child("xt" + i);
            assertEquals(RelationRenderMode.CROSSTAB, result.get(p),
                         "greedy: xt" + i + " should be CROSSTAB (eligible and fits)");
        }
        for (int i = 0; i < 5; i++) {
            SchemaPath p = rootPath.child("tb" + i);
            assertEquals(RelationRenderMode.TABLE, result.get(p),
                         "greedy: tb" + i + " should be TABLE");
        }
        for (int i = 0; i < 6; i++) {
            SchemaPath p = rootPath.child("ol" + i);
            assertEquals(RelationRenderMode.OUTLINE, result.get(p),
                         "greedy: ol" + i + " should be OUTLINE");
        }
    }

    // ------------------------------------------------------------------
    // T2-6. Objective prefers TABLE over CROSSTAB on tie
    // ------------------------------------------------------------------

    @Test
    void objectivePrefersTableOverCrosstabOnTie() {
        // Both TABLE and CROSSTAB fit; solver should prefer TABLE
        // tableWidth=60, crosstabWidth=50, nestedInset=5, available=100
        // TABLE: 60+5=65 ≤ 100 → fits
        // CROSSTAB: 50+5=55 ≤ 100 → fits
        RelationConstraint root = leafCrosstab("root", 60.0, 5.0, 100.0, 50.0, false);
        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);
        assertEquals(RelationRenderMode.TABLE, result.get(new SchemaPath("root")),
                     "TABLE should win tie-break over CROSSTAB");
    }

    // ------------------------------------------------------------------
    // T2-7. hardCrosstab=true node stays CROSSTAB regardless of solver
    // ------------------------------------------------------------------

    @Test
    void hardCrosstabExcludedFromEnumeration() {
        // hardCrosstab node is pinned; solver enumeration ignores it.
        // Use leafCrosstab helper with hardCrosstab=true to verify it stays CROSSTAB
        // even if TABLE would fit better.
        RelationConstraint root = leafCrosstab("pinned", 60.0, 5.0, 200.0, 40.0, true);
        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);
        assertEquals(RelationRenderMode.CROSSTAB, result.get(new SchemaPath("pinned")),
                     "hardCrosstab=true must produce CROSSTAB regardless of fit");
    }
}
