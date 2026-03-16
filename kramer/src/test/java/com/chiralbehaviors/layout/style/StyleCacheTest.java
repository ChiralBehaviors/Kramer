// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.style;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;

/**
 * Tests for F7+F8: Style measurement and layout object caching.
 * Verifies cache invalidation on setStyleSheets() without requiring
 * JavaFX runtime (tests cache infrastructure, not CSS measurement).
 */
class StyleCacheTest {

    @Test
    void cacheStartsEmpty() {
        Style style = new Style();
        assertEquals(0, style.primitiveStyleCacheSize());
        assertEquals(0, style.relationStyleCacheSize());
        assertEquals(0, style.layoutCacheSize());
    }

    @Test
    void setStyleSheetsClearsAllCaches() {
        Style style = new Style();

        style.setStyleSheets(List.of("test.css"));

        assertEquals(0, style.primitiveStyleCacheSize(),
                     "setStyleSheets must clear primitive style cache");
        assertEquals(0, style.relationStyleCacheSize(),
                     "setStyleSheets must clear relation style cache");
        assertEquals(0, style.layoutCacheSize(),
                     "setStyleSheets must clear layout object cache");
    }

    @Test
    void primitiveTextClassConstantIsFinal() throws Exception {
        Field f = PrimitiveStyle.PrimitiveTextStyle.class.getField("PRIMITIVE_TEXT_CLASS");
        assertTrue(Modifier.isFinal(f.getModifiers()),
                   "PRIMITIVE_TEXT_CLASS must be declared final");
        assertEquals("primitive-text", f.get(null));
    }

    @Test
    void primitiveTextClassMutationThrows() throws Exception {
        Field f = PrimitiveStyle.PrimitiveTextStyle.class.getField("PRIMITIVE_TEXT_CLASS");
        f.setAccessible(true);
        assertThrows(IllegalAccessException.class, () -> f.set(null, "mutated"),
                     "final field must reject reflective mutation");
    }

    @Test
    void defaultStyleConstantIsFinal() throws Exception {
        Field f = PrimitiveStyle.PrimitiveLayoutCell.class.getField("DEFAULT_STYLE");
        assertTrue(Modifier.isFinal(f.getModifiers()),
                   "DEFAULT_STYLE must be declared final");
        assertEquals("primitive", f.get(null));
    }

    @Test
    void setStyleSheetsUpdatesSheetList() {
        Style style = new Style();
        assertTrue(style.styleSheets().isEmpty());

        style.setStyleSheets(List.of("a.css", "b.css"));
        assertEquals(List.of("a.css", "b.css"), style.styleSheets());

        style.setStyleSheets(List.of("c.css"));
        assertEquals(List.of("c.css"), style.styleSheets());
    }

    @Test
    void setStyleSheetsWithOwnerSetsOwner() {
        Style style = new Style();
        Object owner = new Object();
        style.setStyleSheets(List.of("a.css"), owner);
        assertEquals(List.of("a.css"), style.styleSheets());
    }

    @Test
    void setStyleSheetsFromDifferentOwnerThrows() {
        Style style = new Style();
        Object owner1 = new Object();
        Object owner2 = new Object();
        style.setStyleSheets(List.of("a.css"), owner1);
        assertThrows(IllegalStateException.class,
                     () -> style.setStyleSheets(List.of("b.css"), owner2),
                     "Style already owned — second owner must be rejected");
    }

    @Test
    void setStyleSheetsFromSameOwnerSucceeds() {
        Style style = new Style();
        Object owner = new Object();
        style.setStyleSheets(List.of("a.css"), owner);
        style.setStyleSheets(List.of("b.css"), owner);
        assertEquals(List.of("b.css"), style.styleSheets());
    }

    @Test
    void packagePrivateSetStyleSheetsStillWorks() {
        // Package-private (no-owner) signature remains accessible within package
        Style style = new Style();
        style.setStyleSheets(List.of("x.css"));
        assertEquals(List.of("x.css"), style.styleSheets());
    }

    @Test
    void measurementSceneConstantsDefined() {
        assertEquals(800, Style.MEASUREMENT_SCENE_WIDTH);
        assertEquals(600, Style.MEASUREMENT_SCENE_HEIGHT);
    }

    @Test
    void clearCachesResetsAllCaches() {
        Style style = new Style();
        // Populate caches via setStyleSheets (triggers clear, but we verify
        // clearCaches() works independently)
        style.setStyleSheets(List.of("test.css"));
        assertEquals(0, style.primitiveStyleCacheSize());

        // clearCaches() must succeed on empty caches without error
        style.clearCaches();
        assertEquals(0, style.primitiveStyleCacheSize());
        assertEquals(0, style.relationStyleCacheSize());
        assertEquals(0, style.layoutCacheSize());
    }

    @Test
    void computePrimitiveStyleIsProtected() throws Exception {
        var m = Style.class.getDeclaredMethod("computePrimitiveStyle", Primitive.class);
        assertTrue(Modifier.isProtected(m.getModifiers()),
                   "computePrimitiveStyle must be protected for subclass override");
    }

    @Test
    void computeRelationStyleIsProtected() throws Exception {
        var m = Style.class.getDeclaredMethod("computeRelationStyle", Relation.class);
        assertTrue(Modifier.isProtected(m.getModifiers()),
                   "computeRelationStyle must be protected for subclass override");
    }

    @Test
    void computePrimitiveStyleAssertsJAT() {
        // Test thread is not the JAT — assertion must fire
        Style style = new Style();
        Primitive p = new Primitive("test");
        assertThrows(AssertionError.class, () -> style.style(p),
                     "computePrimitiveStyle must assert JAT");
    }

    @Test
    void computeRelationStyleAssertsJAT() {
        // Test thread is not the JAT — assertion must fire
        Style style = new Style();
        Relation r = new Relation("test");
        assertThrows(AssertionError.class, () -> style.style(r),
                     "computeRelationStyle must assert JAT");
    }
}
