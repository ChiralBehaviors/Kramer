// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for Kramer-044: ConstraintSolver interface + ExhaustiveConstraintSolver.
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
                nestedInset,
                available,
                Double.MAX_VALUE,
                List.of(),
                hardCrosstab
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
                childAPath, 40.0, 5.0, 50.0, 55.0, List.of(), false);

        // child "b": tableWidth=60, nestedInset=5
        //   outline available=50 → fitsTable: 65 > 50 ✗
        //   table available=55  → fitsTableInParentTable: 65 > 55 ✗
        RelationConstraint childB = new RelationConstraint(
                childBPath, 60.0, 5.0, 50.0, 55.0, List.of(), false);

        // parent: tableWidth=110, nestedInset=5, available=200 → fits
        RelationConstraint parent = new RelationConstraint(
                parentPath, 110.0, 5.0, 200.0, Double.MAX_VALUE, List.of(childA, childB), false);

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
                childPath, 40.0, 5.0, 60.0, Double.MAX_VALUE, List.of(), false);

        // Parent fits (tableWidth=60, nestedInset=5, available=100 → 65 ≤ 100)
        RelationConstraint parent = new RelationConstraint(
                parentPath, 60.0, 5.0, 100.0, Double.MAX_VALUE, List.of(child), false);

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
            children.add(new RelationConstraint(p, 40.0, 5.0, 50.0, Double.MAX_VALUE, List.of(), false));
        }
        for (int i = 0; i < 8; i++) {
            SchemaPath p = rootPath.child("nope" + i);
            children.add(new RelationConstraint(p, 60.0, 5.0, 50.0, Double.MAX_VALUE, List.of(), false));
        }

        // root itself fits
        RelationConstraint root = new RelationConstraint(
                rootPath, 200.0, 5.0, 1000.0, Double.MAX_VALUE, children, false);

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
}
