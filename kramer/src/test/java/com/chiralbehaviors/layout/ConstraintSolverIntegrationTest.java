// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Integration tests wiring ExhaustiveConstraintSolver into RelationLayout.layout().
 *
 * Tests: Kramer-7pp (solver wiring) + Kramer-m1i (integration scenarios).
 */
class ConstraintSolverIntegrationTest {

    private static final ConstraintSolver SOLVER = new ExhaustiveConstraintSolver();

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Minimal RelationStyle mock with all insets = 0. */
    private static RelationStyle mockStyle() {
        return TestLayouts.mockRelationStyle();
    }

    /** Minimal RelationStyle mock with a specific nestedHorizontalInset. */
    private static RelationStyle mockStyleWithNestedInset(double nestedInset) {
        RelationStyle style = mockStyle();
        when(style.getNestedHorizontalInset()).thenReturn(nestedInset);
        return style;
    }

    /** Build a simple Style mock that creates PrimitiveLayouts for Primitives. */
    private static Style mockStyleModel(Relation schema) {
        Style model = mock(Style.class);
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        for (var child : schema.getChildren()) {
            if (child instanceof Primitive p) {
                PrimitiveLayout pl = new PrimitiveLayout(p, primStyle);
                when(model.layout(p)).thenReturn(pl);
            }
        }
        when(model.layout((com.chiralbehaviors.layout.schema.SchemaNode) any())).thenAnswer(inv -> {
            var n = inv.getArgument(0);
            if (n instanceof Primitive p) return model.layout(p);
            return null;
        });
        when(model.getStylesheet()).thenReturn(null);
        return model;
    }

    /** Build test data: an array with one row having values for each field. */
    private static ArrayNode singleRow(String... fieldValues) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        ObjectNode row = JsonNodeFactory.instance.objectNode();
        for (int i = 0; i + 1 < fieldValues.length; i += 2) {
            row.put(fieldValues[i], fieldValues[i + 1]);
        }
        array.add(row);
        return array;
    }

    // -----------------------------------------------------------------------
    // Test 1: Solver parity — simple schema that fits as TABLE
    // Both greedy and solver should produce TABLE for a layout wide enough.
    // -----------------------------------------------------------------------

    @Test
    void solverParityWithGreedyForSimpleSchema() {
        // Build: root relation with two primitives.
        // At width=800 both greedy and solver should choose TABLE.
        Relation schema = new Relation("catalog");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("code"));

        RelationStyle relStyle = mockStyle();
        Style model = mockStyleModel(schema);
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = singleRow("name", "Alice", "code", "A1");
        layout.measure(data, n -> n, model);

        // Set schema path so solver can look it up
        SchemaPath rootPath = new SchemaPath("catalog");
        layout.setSchemaPath(rootPath);

        // --- Greedy run (no solver) ---
        layout.layout(800.0);
        boolean greedyTable = layout.isUseTable();

        // --- Solver run ---
        layout.layout(800.0); // reset
        RelationConstraint constraint = new RelationConstraint(
            rootPath,
            layout.calculateTableColumnWidth(),
            relStyle.getNestedHorizontalInset(),
            800.0,
            Double.MAX_VALUE,
            List.of(),
            false,
            0.0,
            false
        );
        Map<SchemaPath, RelationRenderMode> solverMap = SOLVER.solve(constraint);
        layout.setSolverResults(solverMap);
        try {
            layout.layout(800.0);
        } finally {
            layout.setSolverResults(null);
        }
        boolean solverTable = layout.isUseTable();

        assertEquals(greedyTable, solverTable,
            "Solver and greedy should agree on TABLE/OUTLINE for a simple schema");
        assertTrue(solverTable, "Wide enough schema should be TABLE");
    }

    // -----------------------------------------------------------------------
    // Test 2: Solver uses map to force TABLE even when greedy width check is borderline
    // We construct a solver map that says TABLE, then verify layout() obeys it.
    // -----------------------------------------------------------------------

    @Test
    void solverMapObeyed_forceTableViaMap() {
        // Schema with one primitive "val" with value "x".
        // After measure: dataWidth = 7 (charWidth=7 * 1 char), labelWidth = 10.
        // calculateTableColumnWidth() = 7.
        //
        // We use a RelationStyle with nestedHorizontalInset=20 to make
        // greedy check (tableWidth=7 + nestedInset=20 = 27) > width=15 → OUTLINE.
        // width=15 satisfies: available = 15 - 10 - 0 = 5 > 0 (assert passes).
        // Solver map overrides to TABLE.
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("val"));

        RelationStyle relStyle = mockStyleWithNestedInset(20.0);
        Style model = mockStyleModel(schema);
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = singleRow("val", "x");
        layout.measure(data, n -> n, model);

        SchemaPath rootPath = new SchemaPath("items");
        layout.setSchemaPath(rootPath);

        double width = 15.0; // available=5>0; tableWidth(7)+nestedInset(20)=27>15 → greedy OUTLINE

        // Greedy: at width=15, 7+20=27 > 15 → OUTLINE
        layout.layout(width);
        assertFalse(layout.isUseTable(), "Greedy: tableWidth+nestedInset > width → should be OUTLINE");

        // Solver map says TABLE: force TABLE despite width constraint
        Map<SchemaPath, RelationRenderMode> forceTable = Map.of(
            rootPath, RelationRenderMode.TABLE
        );
        layout.setSolverResults(forceTable);
        try {
            layout.layout(width);
        } finally {
            layout.setSolverResults(null);
        }
        assertTrue(layout.isUseTable(),
            "Solver map TABLE override: layout should choose TABLE regardless of width");
    }

    // -----------------------------------------------------------------------
    // Test 3: Solver map forces OUTLINE on a node that greedy would make TABLE
    // -----------------------------------------------------------------------

    @Test
    void solverMapObeyed_forceOutlineViaMap() {
        Relation schema = new Relation("catalog");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("code"));

        RelationStyle relStyle = mockStyle();
        Style model = mockStyleModel(schema);
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = singleRow("name", "Alice", "code", "A1");
        layout.measure(data, n -> n, model);

        SchemaPath rootPath = new SchemaPath("catalog");
        layout.setSchemaPath(rootPath);

        // Greedy at 800: TABLE
        layout.layout(800.0);
        assertTrue(layout.isUseTable(), "Greedy at 800 should be TABLE");

        // Solver says OUTLINE
        Map<SchemaPath, RelationRenderMode> forceOutline = Map.of(
            rootPath, RelationRenderMode.OUTLINE
        );
        layout.setSolverResults(forceOutline);
        try {
            layout.layout(800.0);
        } finally {
            layout.setSolverResults(null);
        }
        assertFalse(layout.isUseTable(),
            "Solver map OUTLINE override: layout should choose OUTLINE despite fitting");
    }

    // -----------------------------------------------------------------------
    // Test 4: N > 15 schema falls back to greedy via solver — no crash
    // -----------------------------------------------------------------------

    @Test
    void largeSchemaFallsBackToGreedyNoCrash() {
        // Build a flat schema with 16 child Relation nodes in the constraint tree.
        // The solver falls back to greedy for > 15 variable nodes; we verify no crash.
        SchemaPath rootPath = new SchemaPath("root");
        List<RelationConstraint> children = new ArrayList<>();

        // 16 children, alternating fits/no-fits
        for (int i = 0; i < 8; i++) {
            SchemaPath p = rootPath.child("fits" + i);
            children.add(new RelationConstraint(p, 40.0, 0.0, 100.0, Double.MAX_VALUE, List.of(), false, 0.0, false));
        }
        for (int i = 0; i < 8; i++) {
            SchemaPath p = rootPath.child("nope" + i);
            children.add(new RelationConstraint(p, 120.0, 0.0, 100.0, Double.MAX_VALUE, List.of(), false, 0.0, false));
        }

        RelationConstraint root = new RelationConstraint(
            rootPath, 200.0, 0.0, 1000.0, Double.MAX_VALUE, children, false, 0.0, false
        );

        // Must not throw; greedy fallback runs for the 16 variable nodes
        Map<SchemaPath, RelationRenderMode> result = assertDoesNotThrow(
            () -> SOLVER.solve(root),
            "Solver should not throw for N > 15"
        );

        assertNotNull(result);
        assertEquals(17, result.size(), "Root + 16 children = 17 entries");

        // Greedy decisions: "fits" nodes (tableWidth=40 ≤ 100) → TABLE
        for (int i = 0; i < 8; i++) {
            assertEquals(RelationRenderMode.TABLE,
                result.get(rootPath.child("fits" + i)),
                "greedy: fits" + i + " should be TABLE");
        }
        // "nope" nodes (tableWidth=120 > 100) → OUTLINE
        for (int i = 0; i < 8; i++) {
            assertEquals(RelationRenderMode.OUTLINE,
                result.get(rootPath.child("nope" + i)),
                "greedy: nope" + i + " should be OUTLINE");
        }
    }

    // -----------------------------------------------------------------------
    // Test 5: CROSSTAB nodes are preserved through solver
    // -----------------------------------------------------------------------

    @Test
    void crosstabNodesPreservedThroughSolver() {
        // Build a constraint tree with a CROSSTAB root and a non-crosstab child.
        SchemaPath rootPath = new SchemaPath("xtab");
        SchemaPath childPath = rootPath.child("sub");

        RelationConstraint child = new RelationConstraint(
            childPath, 50.0, 0.0, 100.0, Double.MAX_VALUE, List.of(), false, 0.0, false
        );
        RelationConstraint root = new RelationConstraint(
            rootPath, 100.0, 0.0, 200.0, Double.MAX_VALUE, List.of(child), true, 0.0, false  // hardCrosstab=true
        );

        Map<SchemaPath, RelationRenderMode> result = SOLVER.solve(root);

        // Root is CROSSTAB (pinned)
        assertEquals(RelationRenderMode.CROSSTAB, result.get(rootPath),
            "CROSSTAB root must be preserved");
        // Child (non-crosstab, fits TABLE) should be TABLE
        assertEquals(RelationRenderMode.TABLE, result.get(childPath),
            "Non-crosstab child that fits should be TABLE");
    }

    // -----------------------------------------------------------------------
    // Test 6: setSolverResults propagates to children; clearing restores greedy
    // -----------------------------------------------------------------------

    @Test
    void setSolverResultsClearedRestoresGreedy() {
        Relation schema = new Relation("catalog");
        schema.addChild(new Primitive("name"));

        RelationStyle relStyle = mockStyle();
        Style model = mockStyleModel(schema);
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = singleRow("name", "Alice");
        layout.measure(data, n -> n, model);

        SchemaPath rootPath = new SchemaPath("catalog");
        layout.setSchemaPath(rootPath);

        // Set solver results then clear them
        Map<SchemaPath, RelationRenderMode> forceOutline = Map.of(
            rootPath, RelationRenderMode.OUTLINE
        );
        layout.setSolverResults(forceOutline);
        layout.setSolverResults(null); // clear

        // Now layout should use greedy — at 800 should be TABLE
        layout.layout(800.0);
        assertTrue(layout.isUseTable(),
            "After clearing solver results, greedy should produce TABLE at 800");
    }
}
