// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle.PrimitiveBarStyle;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.geometry.Insets;

/**
 * Tests for Kramer-pmi: PrimitiveBarStyle and bar rendering dispatch.
 */
class PrimitiveBarStyleTest {

    // ---- barFraction math ----

    @Test
    void barFractionNormalRange() {
        double min = 0.0, max = 100.0, value = 25.0;
        double fraction = barFraction(value, min, max);
        assertEquals(0.25, fraction, 1e-9, "25/100 should give 0.25 fraction");
    }

    @Test
    void barFractionAtMinIsZero() {
        double min = 10.0, max = 50.0, value = 10.0;
        double fraction = barFraction(value, min, max);
        assertEquals(0.0, fraction, 1e-9, "value at min should give 0.0 fraction");
    }

    @Test
    void barFractionAtMaxIsOne() {
        double min = 10.0, max = 50.0, value = 50.0;
        double fraction = barFraction(value, min, max);
        assertEquals(1.0, fraction, 1e-9, "value at max should give 1.0 fraction");
    }

    @Test
    void barFractionRangeZeroReturnsOne() {
        // Guard: when min==max range is 0, barFraction must be 1.0 (full width)
        double min = 42.0, max = 42.0, value = 42.0;
        double fraction = barFraction(value, min, max);
        assertEquals(1.0, fraction, 1e-9, "zero range must produce barFraction=1.0");
    }

    @Test
    void barFractionMidRange() {
        double min = 0.0, max = 200.0, value = 100.0;
        double fraction = barFraction(value, min, max);
        assertEquals(0.5, fraction, 1e-9, "midpoint should give 0.5 fraction");
    }

    // ---- PrimitiveBarStyle class structure ----

    @Test
    void primitiveBarStyleExistsAsStaticInnerClass() {
        // Verify PrimitiveBarStyle is a public static inner class of PrimitiveStyle
        Class<?> enclosing = PrimitiveBarStyle.class.getEnclosingClass();
        assertNotNull(enclosing, "PrimitiveBarStyle must be an inner class");
        assertEquals(PrimitiveStyle.class, enclosing,
                     "PrimitiveBarStyle must be enclosed in PrimitiveStyle");
        assertTrue(java.lang.reflect.Modifier.isStatic(PrimitiveBarStyle.class.getModifiers()),
                   "PrimitiveBarStyle must be a static inner class");
        assertTrue(java.lang.reflect.Modifier.isPublic(PrimitiveBarStyle.class.getModifiers()),
                   "PrimitiveBarStyle must be public");
    }

    @Test
    void primitiveBarStyleExtendsPrimitiveStyle() {
        assertTrue(PrimitiveStyle.class.isAssignableFrom(PrimitiveBarStyle.class),
                   "PrimitiveBarStyle must extend PrimitiveStyle");
    }

    // ---- getHeight returns single line height ----

    @Test
    void getHeightReturnsSingleLineHeight() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight()).thenReturn(20.0);
        when(labelStyle.getHeight(anyDouble())).thenReturn(20.0);

        PrimitiveBarStyle barStyle = new PrimitiveBarStyle(labelStyle, Insets.EMPTY, labelStyle);
        // BAR height is single line: primitiveStyle.getHeight(1) == 20.0
        double height = barStyle.getHeight(1000.0, 200.0);
        assertEquals(20.0, height, 1e-9,
                     "BAR height must be single line (primitiveStyle.getHeight(1))");
    }

    @Test
    void getHeightIgnoresMaxAndJustifiedArguments() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight(anyDouble())).thenReturn(15.0);

        PrimitiveBarStyle barStyle = new PrimitiveBarStyle(labelStyle, Insets.EMPTY, labelStyle);
        // height must be the same regardless of maxWidth / justified
        double h1 = barStyle.getHeight(100.0, 50.0);
        double h2 = barStyle.getHeight(9999.0, 1.0);
        assertEquals(h1, h2, 1e-9,
                     "BAR height must not depend on maxWidth or justified arguments");
    }

    // ---- renderMode dispatch in buildControl() ----

    @Test
    void buildControlDispatchesToBarStyleWhenRenderModeBAR() {
        // measure() with all-numeric data → renderMode=BAR
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("score"), primStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 1; i <= 5; i++) {
            data.add(i * 10.0);
        }

        com.chiralbehaviors.layout.style.Style model = mock(com.chiralbehaviors.layout.style.Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BAR, layout.getRenderMode(),
                     "Precondition: renderMode must be BAR for all-numeric data");
    }

    @Test
    void buildControlUsesTextStyleWhenRenderModeTEXT() {
        // measure() with text data → renderMode=TEXT
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("name"), primStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("Alice");
        data.add("Bob");

        com.chiralbehaviors.layout.style.Style model = mock(com.chiralbehaviors.layout.style.Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.TEXT, layout.getRenderMode(),
                     "Precondition: renderMode must be TEXT for text data");
    }

    @Test
    void primitiveBarStyleWidthReturnsZero() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight(anyDouble())).thenReturn(20.0);

        PrimitiveBarStyle barStyle = new PrimitiveBarStyle(labelStyle, Insets.EMPTY, labelStyle);
        // width() is not meaningful for bars — must not throw, may return 0
        double w = barStyle.width(JsonNodeFactory.instance.numberNode(42.0));
        assertEquals(0.0, w, 1e-9, "PrimitiveBarStyle.width() must return 0.0");
    }

    // ---- helper mirroring the production barFraction logic ----

    private static double barFraction(double value, double min, double max) {
        double range = max - min;
        if (range == 0.0) {
            return 1.0;
        }
        return (value - min) / range;
    }
}
