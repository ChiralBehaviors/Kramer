// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for Kramer-872: AutoLayout.allConverged() short-circuit.
 *
 * allConverged() returns true only when every PrimitiveLayout in the
 * schema tree has a frozen (converged) result cached.
 */
class AutoLayoutConvergenceTest {

    private static ArrayNode buildVariableDataset(int count) {
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        int longCount = Math.max(1, count / 10);
        int shortCount = count - longCount;
        for (int i = 0; i < shortCount; i++) {
            arr.add("x");
        }
        for (int i = 0; i < longCount; i++) {
            arr.add("x".repeat(20));
        }
        return arr;
    }

    private static Style modelWithSheet(LayoutStylesheet sheet) {
        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(sheet);
        return model;
    }

    private static DefaultLayoutStylesheet fastConvergeSheet(SchemaPath... paths) {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        for (SchemaPath path : paths) {
            sheet.setOverride(path, "stat-min-samples", 5);
            sheet.setOverride(path, "stat-convergence-k", 2);
        }
        return sheet;
    }

    // --- Test 1: PrimitiveLayout not converged before any measure ---

    @Test
    void primitiveLayoutNotConvergedBeforeMeasure() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        layout.setSchemaPath(new SchemaPath("text"));

        assertFalse(layout.isConverged(), "PrimitiveLayout must not be converged before first measure");
    }

    // --- Test 2: some primitives not converged — RelationLayout not converged ---

    @Test
    void relationNotConvergedWhenSomeChildNotConverged() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        RelationStyle relStyle = TestLayouts.mockRelationStyle();

        Primitive p1 = new Primitive("a");
        Primitive p2 = new Primitive("b");
        Relation relation = new Relation("root");
        relation.addChild(p1);
        relation.addChild(p2);

        PrimitiveLayout pl1 = new PrimitiveLayout(p1, primStyle);
        PrimitiveLayout pl2 = new PrimitiveLayout(p2, primStyle);

        SchemaPath path1 = new SchemaPath("root", "a");
        SchemaPath path2 = new SchemaPath("root", "b");
        pl1.setSchemaPath(path1);
        pl2.setSchemaPath(path2);

        // Only converge pl1
        Style m = modelWithSheet(fastConvergeSheet(path1));
        ArrayNode data = buildVariableDataset(6);
        for (int i = 0; i < 2; i++) {
            pl1.measure(data, n -> n, m);
        }

        assertTrue(pl1.isConverged(), "pl1 should have converged");
        assertFalse(pl2.isConverged(), "pl2 was never measured — not converged");

        RelationLayout rl = new RelationLayout(relation, relStyle);
        rl.setSchemaPath(new SchemaPath("root"));
        rl.children.add(pl1);
        rl.children.add(pl2);

        assertFalse(rl.isConverged(), "RelationLayout must not be converged when any child is not");
    }

    // --- Test 3: all primitives converged — RelationLayout converged ---

    @Test
    void relationConvergedWhenAllChildrenConverged() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        RelationStyle relStyle = TestLayouts.mockRelationStyle();

        Primitive p1 = new Primitive("a");
        Primitive p2 = new Primitive("b");
        Relation relation = new Relation("root");
        relation.addChild(p1);
        relation.addChild(p2);

        PrimitiveLayout pl1 = new PrimitiveLayout(p1, primStyle);
        PrimitiveLayout pl2 = new PrimitiveLayout(p2, primStyle);

        SchemaPath path1 = new SchemaPath("root", "a");
        SchemaPath path2 = new SchemaPath("root", "b");
        pl1.setSchemaPath(path1);
        pl2.setSchemaPath(path2);

        Style m = modelWithSheet(fastConvergeSheet(path1, path2));
        ArrayNode data = buildVariableDataset(6);
        for (int i = 0; i < 2; i++) {
            pl1.measure(data, n -> n, m);
            pl2.measure(data, n -> n, m);
        }

        assertTrue(pl1.isConverged(), "pl1 should be converged");
        assertTrue(pl2.isConverged(), "pl2 should be converged");

        RelationLayout rl = new RelationLayout(relation, relStyle);
        rl.setSchemaPath(new SchemaPath("root"));
        rl.children.add(pl1);
        rl.children.add(pl2);

        assertTrue(rl.isConverged(), "RelationLayout must be converged when all children are");
    }

    // --- Test 4: mixed primitive/relation tree ---

    @Test
    void mixedTreeConvergedOnlyWhenAllPrimitivesConverged() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        RelationStyle relStyle = TestLayouts.mockRelationStyle();

        Primitive p1 = new Primitive("name");
        Primitive p2 = new Primitive("value");
        Relation inner = new Relation("details");
        inner.addChild(p2);
        Relation outer = new Relation("root");
        outer.addChild(p1);
        outer.addChild(inner);

        PrimitiveLayout pl1 = new PrimitiveLayout(p1, primStyle);
        PrimitiveLayout pl2 = new PrimitiveLayout(p2, primStyle);
        SchemaPath pathP1 = new SchemaPath("root", "name");
        SchemaPath pathP2 = new SchemaPath("root", "details", "value");
        pl1.setSchemaPath(pathP1);
        pl2.setSchemaPath(pathP2);

        ArrayNode data = buildVariableDataset(6);

        // Converge pl1 only
        Style m1 = modelWithSheet(fastConvergeSheet(pathP1));
        for (int i = 0; i < 2; i++) {
            pl1.measure(data, n -> n, m1);
        }
        assertTrue(pl1.isConverged());
        assertFalse(pl2.isConverged());

        RelationLayout innerRl = new RelationLayout(inner, relStyle);
        innerRl.setSchemaPath(new SchemaPath("root", "details"));
        innerRl.children.add(pl2);

        RelationLayout outerRl = new RelationLayout(outer, relStyle);
        outerRl.setSchemaPath(new SchemaPath("root"));
        outerRl.children.add(pl1);
        outerRl.children.add(innerRl);

        assertFalse(outerRl.isConverged(), "Outer not converged: inner child pl2 not converged");

        // Now converge pl2 as well
        Style m2 = modelWithSheet(fastConvergeSheet(pathP2));
        for (int i = 0; i < 2; i++) {
            pl2.measure(data, n -> n, m2);
        }
        assertTrue(pl2.isConverged());

        assertTrue(outerRl.isConverged(), "Outer converged once all descendants converged");
    }

    // --- Test 5: empty RelationLayout is vacuously converged ---

    @Test
    void emptyRelationLayoutIsConvergedVacuously() {
        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        Relation r = new Relation("empty");
        RelationLayout rl = new RelationLayout(r, relStyle);
        rl.setSchemaPath(new SchemaPath("empty"));

        assertTrue(rl.isConverged(), "Empty RelationLayout is vacuously converged (allMatch on empty stream)");
    }
}
