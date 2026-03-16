// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;

/**
 * Tests for Kramer-cte: MeasurementStrategy interface contract.
 * Verifies structural requirements without requiring JavaFX runtime.
 */
class MeasurementStrategyTest {

    @Test
    void interfaceExists() {
        assertTrue(MeasurementStrategy.class.isInterface(),
                   "MeasurementStrategy must be an interface");
    }

    @Test
    void measurePrimitiveStyleMethodExists() throws Exception {
        Method m = MeasurementStrategy.class.getMethod(
                "measurePrimitiveStyle", Primitive.class, List.class);
        assertNotNull(m, "measurePrimitiveStyle(Primitive, List) must exist");
    }

    @Test
    void measureRelationStyleMethodExists() throws Exception {
        Method m = MeasurementStrategy.class.getMethod(
                "measureRelationStyle", Relation.class, List.class);
        assertNotNull(m, "measureRelationStyle(Relation, List) must exist");
    }

    @Test
    void measurePrimitiveStyleReturnsPrimitiveStyle() throws Exception {
        Method m = MeasurementStrategy.class.getMethod(
                "measurePrimitiveStyle", Primitive.class, List.class);
        assertEquals(PrimitiveStyle.class, m.getReturnType(),
                     "measurePrimitiveStyle must return PrimitiveStyle");
    }

    @Test
    void measureRelationStyleReturnsRelationStyle() throws Exception {
        Method m = MeasurementStrategy.class.getMethod(
                "measureRelationStyle", Relation.class, List.class);
        assertEquals(RelationStyle.class, m.getReturnType(),
                     "measureRelationStyle must return RelationStyle");
    }

    @Test
    void measurePrimitiveStyleIsPublicAbstract() throws Exception {
        Method m = MeasurementStrategy.class.getMethod(
                "measurePrimitiveStyle", Primitive.class, List.class);
        assertTrue(Modifier.isPublic(m.getModifiers()),
                   "measurePrimitiveStyle must be public");
        assertFalse(m.isDefault(),
                    "measurePrimitiveStyle must not be a default method");
    }

    @Test
    void measureRelationStyleIsPublicAbstract() throws Exception {
        Method m = MeasurementStrategy.class.getMethod(
                "measureRelationStyle", Relation.class, List.class);
        assertTrue(Modifier.isPublic(m.getModifiers()),
                   "measureRelationStyle must be public");
        assertFalse(m.isDefault(),
                    "measureRelationStyle must not be a default method");
    }
}
