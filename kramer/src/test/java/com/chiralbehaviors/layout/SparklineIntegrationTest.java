// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.layout.StackPane;

/**
 * Integration tests for Kramer-71q: SPARKLINE dispatch in buildControl().
 *
 * Verifies three scenarios from the bead specification:
 *   1. Array-of-numbers detected as SPARKLINE and buildControl produces sparkline cell
 *   2. Explicit render-mode=sparkline via stylesheet produces sparkline cell
 *   3. Scalar numeric stays BAR (not SPARKLINE)
 */
class SparklineIntegrationTest {

    // ---- 1. Array-of-numbers → SPARKLINE + buildControl produces sparkline cell ----

    @Test
    void arrayOfNumbersDetectedAsSparklineAndBuildsSparklineCell() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("series"), primStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int row = 0; row < 4; row++) {
            ArrayNode series = JsonNodeFactory.instance.arrayNode();
            for (int i = 1; i <= 5; i++) {
                series.add((double) (row * 5 + i));
            }
            data.add(series);
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);
        layout.justify(200.0);
        layout.cellHeight(1, 200.0);

        assertEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode(),
                     "Array-of-numbers must be detected as SPARKLINE");

        @SuppressWarnings("unchecked")
        FocusTraversal<?> ft = mock(FocusTraversal.class);
        LayoutCell<?> cell = layout.buildControl(ft, model);

        assertNotNull(cell, "buildControl() must return a non-null cell for SPARKLINE");
        assertInstanceOf(StackPane.class, cell.getNode(),
                         "buildControl() for SPARKLINE must produce a StackPane (sparkline cell)");
    }

    // ---- 2. Explicit render-mode=sparkline via stylesheet produces sparkline cell ----

    @Test
    void explicitSparklineStylesheetOverrideProducesSparklineCell() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("metric"), primStyle);

        SchemaPath path = new SchemaPath("metric");
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "render-mode", "sparkline");
        layout.buildPaths(path, null);

        // Plain scalar text data — would normally be TEXT, but override forces SPARKLINE
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("alpha");
        data.add("beta");
        data.add("gamma");

        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(sheet);
        layout.measure(data, n -> n, model);
        layout.justify(200.0);
        layout.cellHeight(1, 200.0);

        assertEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode(),
                     "Explicit render-mode=sparkline must force SPARKLINE mode");

        @SuppressWarnings("unchecked")
        FocusTraversal<?> ft = mock(FocusTraversal.class);
        LayoutCell<?> cell = layout.buildControl(ft, model);

        assertNotNull(cell, "buildControl() must return a non-null cell");
        assertInstanceOf(StackPane.class, cell.getNode(),
                         "Explicit render-mode=sparkline must produce a StackPane (sparkline cell)");
    }

    // ---- 3. Scalar numeric → BAR (not SPARKLINE) ----

    @Test
    void scalarNumericStaysBar() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("score"), primStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add(10.0);
        data.add(20.0);
        data.add(30.0);
        data.add(40.0);

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BAR, layout.getRenderMode(),
                     "Scalar numeric values must stay BAR, not upgrade to SPARKLINE");
        assertNotEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode(),
                        "Scalar numeric must not be SPARKLINE");
    }

    // ---- 4. SPARKLINE preempts avgCard>1 guard ----

    @Test
    void sparklinePreemptsAvgCardGuard() {
        // Array-of-numbers has avgCard>1 by nature; SPARKLINE must preempt the guard
        // so that buildControl() produces a sparkline cell, not a PrimitiveList.
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("ts"), primStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int row = 0; row < 3; row++) {
            ArrayNode series = JsonNodeFactory.instance.arrayNode();
            for (int i = 0; i < 5; i++) {
                series.add((double) i);
            }
            data.add(series);
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);
        layout.justify(200.0);
        layout.cellHeight(1, 200.0);

        assertEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode(),
                     "Precondition: array-of-numbers must be SPARKLINE");

        // avgCard > 1 because each value is an array; without preemption, PrimitiveList would be returned
        MeasureResult mr = layout.getMeasureResult();
        assertNotNull(mr);
        assertTrue(mr.averageCardinality() > 1,
                   "Precondition: averageCardinality must be > 1 for array-valued data");

        @SuppressWarnings("unchecked")
        FocusTraversal<?> ft = mock(FocusTraversal.class);
        LayoutCell<?> cell = layout.buildControl(ft, model);

        assertNotNull(cell);
        assertInstanceOf(StackPane.class, cell.getNode(),
                         "SPARKLINE must preempt avgCard>1 guard — must return StackPane, not PrimitiveList");
    }
}
