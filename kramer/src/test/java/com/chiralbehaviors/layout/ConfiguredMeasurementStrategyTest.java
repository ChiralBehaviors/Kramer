// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;

/**
 * Tests for Kramer-j5y: ConfiguredMeasurementStrategy — headless style metrics.
 */
class ConfiguredMeasurementStrategyTest {

    @Test
    void constructionDoesNotRequireJavaFxToolkit() {
        // If construction throws an IllegalStateException about toolkit not
        // initialized, the test will fail.  The mere fact that we can
        // instantiate proves the requirement.
        ConfiguredMeasurementStrategy strategy = new ConfiguredMeasurementStrategy();
        assertNotNull(strategy);
    }

    @Test
    void defaultConstructorProducesReasonableLineHeight() {
        ConfiguredMeasurementStrategy strategy = new ConfiguredMeasurementStrategy();
        PrimitiveStyle ps = strategy.measurePrimitiveStyle(new Primitive("x"), List.of());
        assertNotNull(ps);
        // default line height 20, inset top+bottom = 4+4 = 8 → height >= 20
        assertTrue(ps.getLabelStyle().getHeight() >= 20.0,
                   "default label height should be at least 20px");
    }

    @Test
    void measurePrimitiveStyleReturnsPrimitiveStyle() {
        ConfiguredMeasurementStrategy strategy = new ConfiguredMeasurementStrategy(18.0, 2.0);
        PrimitiveStyle result = strategy.measurePrimitiveStyle(new Primitive("field"),
                                                               List.of());
        assertNotNull(result, "measurePrimitiveStyle must return non-null");
        assertInstanceOf(PrimitiveStyle.class, result);
    }

    @Test
    void measurePrimitiveStyleUsesConfiguredLineHeight() {
        double lineHeight = 24.0;
        double inset = 3.0;
        ConfiguredMeasurementStrategy strategy = new ConfiguredMeasurementStrategy(lineHeight, inset);
        PrimitiveStyle result = strategy.measurePrimitiveStyle(new Primitive("f"), List.of());
        // height = lineHeight + top + bottom = 24 + 3 + 3 = 30
        assertEquals(lineHeight + inset + inset, result.getLabelStyle().getHeight(), 0.001,
                     "label height must reflect configured lineHeight and inset");
    }

    @Test
    void measureRelationStyleReturnsRelationStyle() {
        ConfiguredMeasurementStrategy strategy = new ConfiguredMeasurementStrategy(18.0, 2.0);
        RelationStyle result = strategy.measureRelationStyle(new Relation("rel"), List.of());
        assertNotNull(result, "measureRelationStyle must return non-null");
        assertInstanceOf(RelationStyle.class, result);
    }

    @Test
    void measureRelationStyleUsesConfiguredInsets() {
        double inset = 5.0;
        ConfiguredMeasurementStrategy strategy = new ConfiguredMeasurementStrategy(20.0, inset);
        RelationStyle result = strategy.measureRelationStyle(new Relation("r"), List.of());
        // row vertical inset = top + bottom = 5 + 5 = 10
        assertEquals(inset + inset, result.getRowVerticalInset(), 0.001,
                     "row vertical inset must reflect configured inset");
    }

    @Test
    void defaultConfigurationProducesReasonableRelationStyle() {
        ConfiguredMeasurementStrategy strategy = new ConfiguredMeasurementStrategy();
        RelationStyle result = strategy.measureRelationStyle(new Relation("x"), List.of());
        assertNotNull(result);
        // defaults: lineHeight=20, inset=4 → row vertical = 4+4=8
        assertEquals(8.0, result.getRowVerticalInset(), 0.001,
                     "default row vertical inset should be 8 (2 * 4)");
    }

    @Test
    void implementsMeasurementStrategy() {
        assertTrue(MeasurementStrategy.class.isAssignableFrom(
                ConfiguredMeasurementStrategy.class));
    }

    @Test
    void emptyStylesheetsListIsAccepted() {
        ConfiguredMeasurementStrategy strategy = new ConfiguredMeasurementStrategy();
        assertDoesNotThrow(() -> {
            strategy.measurePrimitiveStyle(new Primitive("p"), List.of());
            strategy.measureRelationStyle(new Relation("r"), List.of());
        });
    }
}
