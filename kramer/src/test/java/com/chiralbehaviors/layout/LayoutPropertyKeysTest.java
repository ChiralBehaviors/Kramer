// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

public class LayoutPropertyKeysTest {

    @Test
    void visibleHasCorrectValue() {
        assertEquals("visible", LayoutPropertyKeys.VISIBLE);
    }

    @Test
    void renderModeHasCorrectValue() {
        assertEquals("render-mode", LayoutPropertyKeys.RENDER_MODE);
    }

    @Test
    void hideIfEmptyHasCorrectValue() {
        assertEquals("hide-if-empty", LayoutPropertyKeys.HIDE_IF_EMPTY);
    }

    @Test
    void sortFieldsHasCorrectValue() {
        assertEquals("sort-fields", LayoutPropertyKeys.SORT_FIELDS);
    }

    @Test
    void constantsArePublicStaticFinalString() throws NoSuchFieldException {
        for (String name : new String[]{"VISIBLE", "RENDER_MODE", "HIDE_IF_EMPTY", "SORT_FIELDS"}) {
            Field f = LayoutPropertyKeys.class.getDeclaredField(name);
            int mods = f.getModifiers();
            assertTrue(Modifier.isPublic(mods), name + " must be public");
            assertTrue(Modifier.isStatic(mods), name + " must be static");
            assertTrue(Modifier.isFinal(mods), name + " must be final");
            assertEquals(String.class, f.getType(), name + " must be String");
        }
    }

    @Test
    void classIsNotInstantiable() {
        var ctors = LayoutPropertyKeys.class.getDeclaredConstructors();
        assertEquals(1, ctors.length);
        assertTrue(Modifier.isPrivate(ctors[0].getModifiers()));
    }
}
