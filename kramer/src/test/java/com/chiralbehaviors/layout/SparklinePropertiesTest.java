// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.DefaultLayoutStylesheet;
import com.chiralbehaviors.layout.LayoutPropertyKeys;
import com.chiralbehaviors.layout.LayoutStylesheet;
import com.chiralbehaviors.layout.SchemaPath;

/**
 * Tests for Kramer-7c4: sparkline-specific LayoutStylesheet property constants.
 */
class SparklinePropertiesTest {

    // ---- constant values ----

    @Test
    void sparklineBandVisibleKeyHasCorrectValue() {
        assertEquals("sparkline-band-visible", LayoutPropertyKeys.SPARKLINE_BAND_VISIBLE);
    }

    @Test
    void sparklineEndMarkerKeyHasCorrectValue() {
        assertEquals("sparkline-end-marker", LayoutPropertyKeys.SPARKLINE_END_MARKER);
    }

    @Test
    void sparklineMinMaxMarkersKeyHasCorrectValue() {
        assertEquals("sparkline-min-max-markers", LayoutPropertyKeys.SPARKLINE_MIN_MAX_MARKERS);
    }

    @Test
    void sparklineLineWidthKeyHasCorrectValue() {
        assertEquals("sparkline-line-width", LayoutPropertyKeys.SPARKLINE_LINE_WIDTH);
    }

    @Test
    void sparklineBandOpacityKeyHasCorrectValue() {
        assertEquals("sparkline-band-opacity", LayoutPropertyKeys.SPARKLINE_BAND_OPACITY);
    }

    @Test
    void sparklineMinWidthKeyHasCorrectValue() {
        assertEquals("sparkline-min-width", LayoutPropertyKeys.SPARKLINE_MIN_WIDTH);
    }

    // ---- modifier checks ----

    @Test
    void sparklineConstantsArePublicStaticFinalString() throws NoSuchFieldException {
        for (String name : new String[]{
            "SPARKLINE_BAND_VISIBLE",
            "SPARKLINE_END_MARKER",
            "SPARKLINE_MIN_MAX_MARKERS",
            "SPARKLINE_LINE_WIDTH",
            "SPARKLINE_BAND_OPACITY",
            "SPARKLINE_MIN_WIDTH"
        }) {
            Field f = LayoutPropertyKeys.class.getDeclaredField(name);
            int mods = f.getModifiers();
            assertTrue(Modifier.isPublic(mods), name + " must be public");
            assertTrue(Modifier.isStatic(mods), name + " must be static");
            assertTrue(Modifier.isFinal(mods), name + " must be final");
            assertEquals(String.class, f.getType(), name + " must be String");
        }
    }

    // ---- default values via DefaultLayoutStylesheet ----

    @Test
    void sparklineBandVisibleDefaultTrue() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("values");
        assertTrue(sheet.getBoolean(path, LayoutPropertyKeys.SPARKLINE_BAND_VISIBLE, true),
                   "sparkline-band-visible default must be true");
    }

    @Test
    void sparklineEndMarkerDefaultTrue() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("values");
        assertTrue(sheet.getBoolean(path, LayoutPropertyKeys.SPARKLINE_END_MARKER, true),
                   "sparkline-end-marker default must be true");
    }

    @Test
    void sparklineMinMaxMarkersDefaultFalse() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("values");
        assertFalse(sheet.getBoolean(path, LayoutPropertyKeys.SPARKLINE_MIN_MAX_MARKERS, false),
                    "sparkline-min-max-markers default must be false");
    }

    @Test
    void sparklineLineWidthDefaultOne() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("values");
        assertEquals(1.0, sheet.getDouble(path, LayoutPropertyKeys.SPARKLINE_LINE_WIDTH, 1.0),
                     1e-9, "sparkline-line-width default must be 1.0");
    }

    @Test
    void sparklineBandOpacityDefaultPointFifteen() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("values");
        assertEquals(0.15, sheet.getDouble(path, LayoutPropertyKeys.SPARKLINE_BAND_OPACITY, 0.15),
                     1e-9, "sparkline-band-opacity default must be 0.15");
    }

    // ---- stylesheet override honored ----

    @Test
    void sparklineBandVisibleOverrideFalse() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("values");
        sheet.setOverride(path, LayoutPropertyKeys.SPARKLINE_BAND_VISIBLE, false);
        assertFalse(sheet.getBoolean(path, LayoutPropertyKeys.SPARKLINE_BAND_VISIBLE, true),
                    "Override false for sparkline-band-visible must be honored");
    }

    @Test
    void sparklineEndMarkerOverrideFalse() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("values");
        sheet.setOverride(path, LayoutPropertyKeys.SPARKLINE_END_MARKER, false);
        assertFalse(sheet.getBoolean(path, LayoutPropertyKeys.SPARKLINE_END_MARKER, true),
                    "Override false for sparkline-end-marker must be honored");
    }

    @Test
    void sparklineMinMaxMarkersOverrideTrue() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("values");
        sheet.setOverride(path, LayoutPropertyKeys.SPARKLINE_MIN_MAX_MARKERS, true);
        assertTrue(sheet.getBoolean(path, LayoutPropertyKeys.SPARKLINE_MIN_MAX_MARKERS, false),
                   "Override true for sparkline-min-max-markers must be honored");
    }

    @Test
    void sparklineLineWidthOverride() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("values");
        sheet.setOverride(path, LayoutPropertyKeys.SPARKLINE_LINE_WIDTH, 2.5);
        assertEquals(2.5, sheet.getDouble(path, LayoutPropertyKeys.SPARKLINE_LINE_WIDTH, 1.0),
                     1e-9, "Override 2.5 for sparkline-line-width must be honored");
    }

    @Test
    void sparklineBandOpacityOverride() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("values");
        sheet.setOverride(path, LayoutPropertyKeys.SPARKLINE_BAND_OPACITY, 0.5);
        assertEquals(0.5, sheet.getDouble(path, LayoutPropertyKeys.SPARKLINE_BAND_OPACITY, 0.15),
                     1e-9, "Override 0.5 for sparkline-band-opacity must be honored");
    }

    @Test
    void overrideOnOnePathDoesNotAffectAnother() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath pathA = new SchemaPath("values");
        SchemaPath pathB = new SchemaPath("other");
        sheet.setOverride(pathA, LayoutPropertyKeys.SPARKLINE_BAND_VISIBLE, false);
        // pathB must still see the default
        assertTrue(sheet.getBoolean(pathB, LayoutPropertyKeys.SPARKLINE_BAND_VISIBLE, true),
                   "Override on pathA must not affect pathB");
    }

    // ---- PrimitiveLayout.getStylesheet() wires through ----

    @Test
    void primitiveLayoutGetStylesheetReturnsStylesheetFromBuildControl() {
        com.chiralbehaviors.layout.style.PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new com.chiralbehaviors.layout.schema.Primitive("v"), primStyle);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        com.chiralbehaviors.layout.style.Style model = mock(com.chiralbehaviors.layout.style.Style.class);
        when(model.getStylesheet()).thenReturn(sheet);

        // Before buildControl: should be null or a no-op default
        // After buildControl: getStylesheet() returns what model.getStylesheet() returned

        // Trigger buildControl to wire the stylesheet
        com.chiralbehaviors.layout.cell.control.FocusTraversal<?> ft =
            mock(com.chiralbehaviors.layout.cell.control.FocusTraversal.class);

        // measure + shape to make buildControl work without NPE
        com.fasterxml.jackson.databind.node.ArrayNode data =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        com.fasterxml.jackson.databind.node.ArrayNode series =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        for (int i = 1; i <= 3; i++) series.add((double) i);
        data.add(series);

        layout.measure(data, n -> n, model);
        layout.justify(100.0);
        layout.cellHeight(1, 100.0);
        layout.buildControl(ft, model);

        assertSame(sheet, layout.getStylesheet(),
                   "PrimitiveLayout.getStylesheet() must return the stylesheet from the last buildControl call");
    }
}
