// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for Kramer-bdv: BADGE rendering mode auto-detection and CSS class assignment.
 */
class BadgeRenderModeTest {

    // ---- Auto-detection: cardinality threshold ----

    /**
     * Low-cardinality text field (< 10 distinct values) must be auto-detected as BADGE.
     */
    @Test
    void lowCardinalityTextAutoDetectedAsBadge() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("status"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // 5 distinct values, repeated to create a realistic sample
        String[] values = { "active", "inactive", "pending", "deleted", "archived" };
        for (int i = 0; i < 25; i++) {
            data.add(values[i % values.length]);
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BADGE, layout.getRenderMode(),
                     "Low-cardinality field (5 distinct) must be auto-detected as BADGE");
    }

    /**
     * High-cardinality text field (>= 10 distinct values) must stay TEXT.
     */
    @Test
    void highCardinalityTextStaysText() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("country"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // 12 distinct values — at or above threshold
        String[] values = {
            "USA", "UK", "France", "Germany", "Japan",
            "Canada", "Australia", "Brazil", "India", "China",
            "Mexico", "Spain"
        };
        for (String v : values) {
            data.add(v);
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.TEXT, layout.getRenderMode(),
                     "High-cardinality field (12 distinct) must remain TEXT");
    }

    /**
     * Exactly 9 distinct values (< 10 default threshold) must be auto-detected as BADGE.
     */
    @Test
    void exactlyNineDistinctValuesBecomesBadge() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("priority"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 1; i <= 9; i++) {
            data.add("p" + i);
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BADGE, layout.getRenderMode(),
                     "Exactly 9 distinct values (< threshold 10) must be BADGE");
    }

    /**
     * Exactly 10 distinct values (== threshold) must stay TEXT (threshold is exclusive).
     */
    @Test
    void exactlyTenDistinctValuesStaysText() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("category"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 1; i <= 10; i++) {
            data.add("cat" + i);
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.TEXT, layout.getRenderMode(),
                     "Exactly 10 distinct values (== threshold) must remain TEXT");
    }

    /**
     * All-numeric fields must not be promoted to BADGE — BAR takes precedence.
     */
    @Test
    void numericFieldStaysBarNotBadge() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("score"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // Only 3 distinct numeric values — would qualify for BADGE cardinality-wise,
        // but numeric auto-detection must take priority and yield BAR.
        data.add(1.0);
        data.add(2.0);
        data.add(3.0);
        data.add(1.0);

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BAR, layout.getRenderMode(),
                     "All-numeric field must remain BAR even if cardinality is low");
    }

    // ---- Stylesheet threshold override ----

    /**
     * badge-cardinality-threshold stylesheet property overrides the default of 10.
     * Setting threshold=3 means a field with 4 distinct values must stay TEXT.
     */
    @Test
    void badgeCardinalityThresholdHonored() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("status"), style);

        // Build a mock stylesheet that returns threshold=3 for badge-cardinality-threshold
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("status");
        sheet.setOverride(path, "badge-cardinality-threshold", 3);

        layout.buildPaths(path, null);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // 4 distinct values — below default (10) but at/above custom threshold (3)
        data.add("a");
        data.add("b");
        data.add("c");
        data.add("d");

        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(sheet);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.TEXT, layout.getRenderMode(),
                     "4 distinct values >= threshold 3 must remain TEXT");
    }

    /**
     * badge-cardinality-threshold=15 makes a 12-distinct-value field become BADGE.
     */
    @Test
    void highThresholdPromotsLargerCardinality() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("region"), style);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("region");
        sheet.setOverride(path, "badge-cardinality-threshold", 15);

        layout.buildPaths(path, null);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 1; i <= 12; i++) {
            data.add("region-" + i);
        }

        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(sheet);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BADGE, layout.getRenderMode(),
                     "12 distinct values < threshold 15 must become BADGE");
    }

    // ---- Explicit render-mode=badge stylesheet override ----

    /**
     * Explicit render-mode=badge in stylesheet forces BADGE even for numeric data.
     */
    @Test
    void explicitRenderModeBadgeOverridesNumericAutoDetection() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("code"), style);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("code");
        sheet.setOverride(path, "render-mode", "badge");

        layout.buildPaths(path, null);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // Numeric data — would normally be BAR, but explicit override wins
        data.add(1.0);
        data.add(2.0);
        data.add(3.0);

        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(sheet);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BADGE, layout.getRenderMode(),
                     "Explicit render-mode=badge must override numeric auto-detection (BAR)");
    }

    /**
     * Explicit render-mode=badge forces BADGE for a high-cardinality field
     * that would normally stay TEXT.
     */
    @Test
    void explicitRenderModeBadgeOverridesHighCardinality() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("tag"), style);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("tag");
        sheet.setOverride(path, "render-mode", "badge");

        layout.buildPaths(path, null);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 1; i <= 20; i++) {
            data.add("tag-" + i);
        }

        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(sheet);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BADGE, layout.getRenderMode(),
                     "Explicit render-mode=badge must override high-cardinality TEXT fallback");
    }

    // ---- CSS class assignment: badge-{sorted-index} ----

    /**
     * badge-{sorted-index} CSS class is assigned using sorted position in distinct value set.
     * Values "pending", "active", "inactive" sorted: ["active"=0, "inactive"=1, "pending"=2].
     */
    @Test
    void badgeSortedIndexStoredInMeasureResult() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("status"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("pending");
        data.add("active");
        data.add("inactive");
        data.add("pending");
        data.add("active");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BADGE, layout.getRenderMode());

        // Sorted: ["active", "inactive", "pending"]
        List<String> badgeValues = layout.getBadgeValues();
        assertNotNull(badgeValues, "getBadgeValues() must not be null after BADGE detection");
        assertEquals(3, badgeValues.size());
        assertEquals("active",   badgeValues.get(0), "index 0 must be 'active'");
        assertEquals("inactive", badgeValues.get(1), "index 1 must be 'inactive'");
        assertEquals("pending",  badgeValues.get(2), "index 2 must be 'pending'");
    }

    /**
     * getBadgeValues() returns null (or empty) when render mode is TEXT.
     */
    @Test
    void badgeValuesNullForTextMode() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("name"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 1; i <= 15; i++) {
            data.add("value-" + i);
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.TEXT, layout.getRenderMode());

        List<String> badgeValues = layout.getBadgeValues();
        assertTrue(badgeValues == null || badgeValues.isEmpty(),
                   "getBadgeValues() must be null or empty when mode is TEXT");
    }

    /**
     * badgeIndex() returns the correct sorted position for a known value.
     */
    @Test
    void badgeIndexReturnsCorrectSortedPosition() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("severity"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // 4 distinct values
        data.add("high");
        data.add("critical");
        data.add("low");
        data.add("medium");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BADGE, layout.getRenderMode());

        // Sorted: ["critical"=0, "high"=1, "low"=2, "medium"=3]
        assertEquals(0, layout.badgeIndex("critical"), "critical must be index 0");
        assertEquals(1, layout.badgeIndex("high"),     "high must be index 1");
        assertEquals(2, layout.badgeIndex("low"),      "low must be index 2");
        assertEquals(3, layout.badgeIndex("medium"),   "medium must be index 3");
    }

    /**
     * badgeIndex() returns -1 for values not in the sorted set (beyond-threshold fallback).
     */
    @Test
    void badgeIndexReturnsMinus1ForUnknownValue() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("status"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("active");
        data.add("inactive");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BADGE, layout.getRenderMode());

        assertEquals(-1, layout.badgeIndex("unknown"),
                     "Unknown value must return -1 (TEXT fallback)");
        assertEquals(-1, layout.badgeIndex(null),
                     "null must return -1 (TEXT fallback)");
    }

    // ---- PrimitiveBadgeStyle class structure ----

    /**
     * PrimitiveBadgeStyle exists as a public static inner class of PrimitiveStyle.
     */
    @Test
    void primitiveBadgeStyleExistsAsStaticInnerClass() {
        Class<?> enclosing = PrimitiveStyle.PrimitiveBadgeStyle.class.getEnclosingClass();
        assertNotNull(enclosing, "PrimitiveBadgeStyle must be an inner class");
        assertEquals(PrimitiveStyle.class, enclosing,
                     "PrimitiveBadgeStyle must be enclosed in PrimitiveStyle");
        assertTrue(java.lang.reflect.Modifier.isStatic(PrimitiveStyle.PrimitiveBadgeStyle.class.getModifiers()),
                   "PrimitiveBadgeStyle must be a static inner class");
        assertTrue(java.lang.reflect.Modifier.isPublic(PrimitiveStyle.PrimitiveBadgeStyle.class.getModifiers()),
                   "PrimitiveBadgeStyle must be public");
    }

    @Test
    void primitiveBadgeStyleExtendsPrimitiveStyle() {
        assertTrue(PrimitiveStyle.class.isAssignableFrom(PrimitiveStyle.PrimitiveBadgeStyle.class),
                   "PrimitiveBadgeStyle must extend PrimitiveStyle");
    }

    /**
     * PrimitiveBadgeStyle.getHeight() returns single-line height (same as TEXT).
     */
    @Test
    void badgeStyleGetHeightReturnsSingleLine() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight(anyDouble())).thenReturn(20.0);
        when(labelStyle.getHeight()).thenReturn(20.0);

        PrimitiveStyle.PrimitiveBadgeStyle badgeStyle =
            new PrimitiveStyle.PrimitiveBadgeStyle(labelStyle, javafx.geometry.Insets.EMPTY, labelStyle);

        double h1 = badgeStyle.getHeight(100.0, 50.0);
        double h2 = badgeStyle.getHeight(9999.0, 1.0);
        assertEquals(h1, h2, 1e-9,
                     "BADGE height must not depend on maxWidth or justified arguments");
        assertEquals(20.0, h1, 1e-9, "BADGE height must equal single-line height");
    }

    /**
     * PRIMITIVE_BADGE_CLASS constant is "primitive-badge".
     */
    @Test
    void primitiveBadgeClassConstant() {
        assertEquals("primitive-badge", PrimitiveStyle.PrimitiveBadgeStyle.PRIMITIVE_BADGE_CLASS);
    }
}
