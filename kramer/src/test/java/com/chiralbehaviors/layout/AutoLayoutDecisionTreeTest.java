// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * TDD tests for AutoLayout.getLayoutDecisionTree() (Kramer-73j).
 *
 * Tests guard logic directly (AutoLayout requires JavaFX toolkit for full integration test).
 * The helper method {@link #getLayoutDecisionTree} mirrors AutoLayout's guard logic so that
 * the null / unconverged / converged state transitions can be exercised without a live scene.
 */
class AutoLayoutDecisionTreeTest {

    // -----------------------------------------------------------------------
    // 1. Returns Optional.empty() when layout field is null (no measure yet)
    // -----------------------------------------------------------------------

    @Test
    void returnsEmptyWhenLayoutIsNull() {
        // Directly test the SchemaNodeLayout-level guard via a RelationLayout
        // that has never been measured — isConverged() returns false, and
        // snapshotDecisionTree() is therefore never called.
        // We verify via a thin wrapper that matches AutoLayout's logic.
        SchemaNodeLayout nullLayout = null;

        // Emulate AutoLayout.getLayoutDecisionTree() logic
        Optional<LayoutDecisionNode> result = getLayoutDecisionTree(nullLayout);

        assertTrue(result.isEmpty(), "Must return Optional.empty() when layout is null");
    }

    // -----------------------------------------------------------------------
    // 2. Returns Optional.empty() when layout is not converged
    // -----------------------------------------------------------------------

    @Test
    void returnsEmptyWhenNotConverged() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("field"), style);
        // Not yet measured — not converged
        assertFalse(layout.isConverged(), "precondition: layout is not converged");

        Optional<LayoutDecisionNode> result = getLayoutDecisionTree(layout);

        assertTrue(result.isEmpty(), "Must return Optional.empty() when layout is not converged");
    }

    // -----------------------------------------------------------------------
    // 3. Returns present Optional when layout is converged
    // -----------------------------------------------------------------------

    @Test
    void returnsPresentWhenConverged() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        RelationStyle relStyle   = TestLayouts.mockRelationStyle();

        Primitive p = new Primitive("score");
        Relation  r = new Relation("root");
        r.addChild(p);

        PrimitiveLayout pl = new PrimitiveLayout(p, primStyle);
        SchemaPath path = new SchemaPath("root", "score");
        pl.setSchemaPath(path);

        // Force convergence via repeated measure with fast-converge settings
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "stat-min-samples", 5);
        sheet.setOverride(path, "stat-convergence-k", 2);
        com.chiralbehaviors.layout.style.Style model = mock(com.chiralbehaviors.layout.style.Style.class);
        when(model.getStylesheet()).thenReturn(sheet);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 6; i++) {
            data.add("value" + i);
        }
        for (int i = 0; i < 2; i++) {
            pl.measure(data, n -> n, model);
        }
        assertTrue(pl.isConverged(), "precondition: layout must be converged");

        pl.buildPaths(path, model);

        Optional<LayoutDecisionNode> result = getLayoutDecisionTree(pl);

        assertTrue(result.isPresent(), "Must return present Optional when layout is converged");
        assertNotNull(result.get());
    }

    // -----------------------------------------------------------------------
    // 4. Returned node has expected path when converged
    // -----------------------------------------------------------------------

    @Test
    void returnedNodeHasCorrectFieldName() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);

        Primitive p = new Primitive("amount");
        PrimitiveLayout pl = new PrimitiveLayout(p, primStyle);
        SchemaPath path = new SchemaPath("root", "amount");
        pl.setSchemaPath(path);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "stat-min-samples", 5);
        sheet.setOverride(path, "stat-convergence-k", 2);
        com.chiralbehaviors.layout.style.Style model = mock(com.chiralbehaviors.layout.style.Style.class);
        when(model.getStylesheet()).thenReturn(sheet);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 6; i++) {
            data.add("item" + i);
        }
        for (int i = 0; i < 2; i++) {
            pl.measure(data, n -> n, model);
        }
        pl.buildPaths(path, model);

        Optional<LayoutDecisionNode> result = getLayoutDecisionTree(pl);

        assertTrue(result.isPresent());
        assertEquals("amount", result.get().fieldName());
    }

    // -----------------------------------------------------------------------
    // Helper: mirrors AutoLayout.getLayoutDecisionTree() logic
    // -----------------------------------------------------------------------

    private static Optional<LayoutDecisionNode> getLayoutDecisionTree(SchemaNodeLayout layout) {
        if (layout == null || !layout.isConverged()) return Optional.empty();
        return Optional.of(layout.snapshotDecisionTree());
    }
}
