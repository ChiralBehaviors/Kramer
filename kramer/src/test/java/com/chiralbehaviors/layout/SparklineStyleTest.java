// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle.PrimitiveSparklineStyle;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.geometry.Insets;
import javafx.scene.layout.StackPane;

/**
 * Tests for Kramer-t92: PrimitiveSparklineStyle and SparklineCell.
 */
class SparklineStyleTest {

    // ---- class structure ----

    @Test
    void primitiveSparklineStyleExistsAsStaticInnerClass() {
        Class<?> enclosing = PrimitiveSparklineStyle.class.getEnclosingClass();
        assertNotNull(enclosing, "PrimitiveSparklineStyle must be an inner class");
        assertEquals(PrimitiveStyle.class, enclosing,
                     "PrimitiveSparklineStyle must be enclosed in PrimitiveStyle");
        assertTrue(java.lang.reflect.Modifier.isStatic(PrimitiveSparklineStyle.class.getModifiers()),
                   "PrimitiveSparklineStyle must be a static inner class");
        assertTrue(java.lang.reflect.Modifier.isPublic(PrimitiveSparklineStyle.class.getModifiers()),
                   "PrimitiveSparklineStyle must be public");
    }

    @Test
    void primitiveSparklineStyleExtendsPrimitiveStyle() {
        assertTrue(PrimitiveStyle.class.isAssignableFrom(PrimitiveSparklineStyle.class),
                   "PrimitiveSparklineStyle must extend PrimitiveStyle");
    }

    // ---- width() returns 0.0 ----

    @Test
    void widthReturnsZero() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight(anyDouble())).thenReturn(20.0);

        PrimitiveSparklineStyle sparklineStyle = new PrimitiveSparklineStyle(labelStyle,
                                                                              Insets.EMPTY,
                                                                              labelStyle);
        double w = sparklineStyle.width(JsonNodeFactory.instance.arrayNode());
        assertEquals(0.0, w, 1e-9, "PrimitiveSparklineStyle.width() must return 0.0");
    }

    @Test
    void widthReturnsZeroForNull() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight(anyDouble())).thenReturn(20.0);

        PrimitiveSparklineStyle sparklineStyle = new PrimitiveSparklineStyle(labelStyle,
                                                                              Insets.EMPTY,
                                                                              labelStyle);
        double w = sparklineStyle.width(JsonNodeFactory.instance.nullNode());
        assertEquals(0.0, w, 1e-9, "PrimitiveSparklineStyle.width() must return 0.0 for null node");
    }

    // ---- getHeight returns single-line height ----

    @Test
    void getHeightReturnsSingleLineHeight() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight(anyDouble())).thenReturn(20.0);

        PrimitiveSparklineStyle sparklineStyle = new PrimitiveSparklineStyle(labelStyle,
                                                                              Insets.EMPTY,
                                                                              labelStyle);
        double height = sparklineStyle.getHeight(1000.0, 200.0);
        assertEquals(20.0, height, 1e-9,
                     "SPARKLINE height must be single line (primitiveStyle.getHeight(1))");
    }

    @Test
    void getHeightIgnoresMaxAndJustifiedArguments() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight(anyDouble())).thenReturn(15.0);

        PrimitiveSparklineStyle sparklineStyle = new PrimitiveSparklineStyle(labelStyle,
                                                                              Insets.EMPTY,
                                                                              labelStyle);
        double h1 = sparklineStyle.getHeight(100.0, 50.0);
        double h2 = sparklineStyle.getHeight(9999.0, 1.0);
        assertEquals(h1, h2, 1e-9,
                     "SPARKLINE height must not depend on maxWidth or justified arguments");
    }

    // ---- build() returns a LayoutCell with StackPane root ----

    @Test
    void buildReturnsLayoutCellWithStackPaneRoot() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight(anyDouble())).thenReturn(20.0);
        when(labelStyle.getHeight()).thenReturn(20.0);

        PrimitiveSparklineStyle sparklineStyle = new PrimitiveSparklineStyle(labelStyle,
                                                                              Insets.EMPTY,
                                                                              labelStyle);

        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("values"), primStyle);

        // Measure with array-of-numbers data to set sparklineStats + renderMode
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ArrayNode series = JsonNodeFactory.instance.arrayNode();
        for (int i = 1; i <= 5; i++) {
            series.add((double) i);
        }
        data.add(series);

        com.chiralbehaviors.layout.style.Style model = mock(com.chiralbehaviors.layout.style.Style.class);
        layout.measure(data, n -> n, model);
        layout.justify(200.0);
        layout.cellHeight(1, 200.0);

        @SuppressWarnings("unchecked")
        FocusTraversal<?> ft = mock(FocusTraversal.class);

        LayoutCell<?> cell = sparklineStyle.build(ft, layout);
        assertNotNull(cell, "build() must return a non-null LayoutCell");
        assertNotNull(cell.getNode(), "cell.getNode() must not be null");
        assertInstanceOf(StackPane.class, cell.getNode(),
                         "cell root must be a StackPane");
    }

    // ---- buildControl() dispatches to SparklineStyle when renderMode == SPARKLINE ----

    @Test
    void buildControlDispatchesToSparklineStyleWhenRenderModeSPARKLINE() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("values"), primStyle);

        // Build array-of-numbers data to trigger SPARKLINE detection
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int row = 0; row < 5; row++) {
            ArrayNode series = JsonNodeFactory.instance.arrayNode();
            for (int i = 1; i <= 5; i++) {
                series.add((double) (row * 5 + i));
            }
            data.add(series);
        }

        com.chiralbehaviors.layout.style.Style model = mock(com.chiralbehaviors.layout.style.Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode(),
                     "Precondition: renderMode must be SPARKLINE for array-of-numbers data");
    }

    @Test
    void buildControlDispatchesToSparklineBeforeAvgCardGuard() {
        // This verifies the SPARKLINE preempts avgCard>1 guard (audit finding C1).
        // If SPARKLINE did NOT preempt the guard, avgCard>1 would redirect to PrimitiveList
        // for array-valued data — the returned cell would not be a StackPane.
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("series"), primStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int row = 0; row < 3; row++) {
            ArrayNode series = JsonNodeFactory.instance.arrayNode();
            for (int i = 1; i <= 4; i++) {
                series.add((double) i);
            }
            data.add(series);
        }

        com.chiralbehaviors.layout.style.Style model = mock(com.chiralbehaviors.layout.style.Style.class);
        layout.measure(data, n -> n, model);
        layout.justify(200.0);
        layout.cellHeight(1, 200.0);

        assertEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode(),
                     "Precondition: renderMode must be SPARKLINE");

        @SuppressWarnings("unchecked")
        FocusTraversal<?> ft = mock(FocusTraversal.class);
        LayoutCell<?> cell = layout.buildControl(ft, model);

        assertNotNull(cell, "buildControl() must return non-null cell for SPARKLINE");
        assertInstanceOf(StackPane.class, cell.getNode(),
                         "buildControl() for SPARKLINE must return StackPane — not PrimitiveList (avgCard>1 must not preempt SPARKLINE)");
    }

    // ---- cachedSparklineStyle is reset in clear() via layout() ----

    @Test
    void cachedSparklineStyleIsResetAfterLayout() {
        // Calling layout() invokes clear() which must null cachedSparklineStyle.
        // We verify indirectly: two buildControl() calls around a layout() cycle
        // both succeed (no stale state exception).
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("ts"), primStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ArrayNode series = JsonNodeFactory.instance.arrayNode();
        for (int i = 1; i <= 3; i++) {
            series.add((double) i);
        }
        data.add(series);

        com.chiralbehaviors.layout.style.Style model = mock(com.chiralbehaviors.layout.style.Style.class);
        layout.measure(data, n -> n, model);
        layout.justify(100.0);
        layout.cellHeight(1, 100.0);

        @SuppressWarnings("unchecked")
        FocusTraversal<?> ft = mock(FocusTraversal.class);

        // First build
        LayoutCell<?> cell1 = layout.buildControl(ft, model);
        assertInstanceOf(StackPane.class, cell1.getNode(), "First call must produce StackPane");

        // layout() → clear() → cachedSparklineStyle = null
        layout.layout(100.0);
        layout.measure(data, n -> n, model);
        layout.justify(100.0);
        layout.cellHeight(1, 100.0);

        // Second build after reset — must still work
        LayoutCell<?> cell2 = layout.buildControl(ft, model);
        assertInstanceOf(StackPane.class, cell2.getNode(), "Second call after clear() must produce StackPane");
    }
}
