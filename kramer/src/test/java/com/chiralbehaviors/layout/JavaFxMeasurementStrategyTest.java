// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;

/**
 * Tests for Kramer-5z8: JavaFxMeasurementStrategy structural contract.
 * Non-JAT tests verify the class structure and delegation; JAT-assertion
 * tests verify that measurement methods gate on the FX application thread.
 */
class JavaFxMeasurementStrategyTest {

    @Test
    void implementsMeasurementStrategy() {
        assertTrue(MeasurementStrategy.class.isAssignableFrom(JavaFxMeasurementStrategy.class),
                   "JavaFxMeasurementStrategy must implement MeasurementStrategy");
    }

    @Test
    void isConcreteClass() {
        assertFalse(java.lang.reflect.Modifier.isAbstract(
                JavaFxMeasurementStrategy.class.getModifiers()),
                "JavaFxMeasurementStrategy must be a concrete class");
    }

    @Test
    void defaultConstructorExists() throws Exception {
        var ctor = JavaFxMeasurementStrategy.class.getConstructor();
        assertNotNull(ctor, "JavaFxMeasurementStrategy must have a no-arg constructor");
    }

    @Test
    void measurePrimitiveStyleAssertsJAT() {
        // Test thread is not the JAT — assertion must fire (requires -ea)
        var strategy = new JavaFxMeasurementStrategy();
        var p = new Primitive("test");
        assertThrows(AssertionError.class,
                     () -> strategy.measurePrimitiveStyle(p, List.of()),
                     "measurePrimitiveStyle must assert JavaFX Application Thread");
    }

    @Test
    void measureRelationStyleAssertsJAT() {
        // Test thread is not the JAT — assertion must fire (requires -ea)
        var strategy = new JavaFxMeasurementStrategy();
        var r = new Relation("test");
        assertThrows(AssertionError.class,
                     () -> strategy.measureRelationStyle(r, List.of()),
                     "measureRelationStyle must assert JavaFX Application Thread");
    }

    @Test
    void measurePrimitiveStyleWithNullStylesheetsAssertsJAT() {
        var strategy = new JavaFxMeasurementStrategy();
        var p = new Primitive("test");
        // Even with null stylesheets, JAT assertion fires first
        assertThrows(AssertionError.class,
                     () -> strategy.measurePrimitiveStyle(p, null),
                     "JAT assertion must fire before any null-check");
    }

    @Test
    void measureRelationStyleWithNullStylesheetsAssertsJAT() {
        var strategy = new JavaFxMeasurementStrategy();
        var r = new Relation("test");
        assertThrows(AssertionError.class,
                     () -> strategy.measureRelationStyle(r, null),
                     "JAT assertion must fire before any null-check");
    }
}
