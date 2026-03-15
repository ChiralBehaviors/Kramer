// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.style;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

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
    void setStyleSheetsUpdatesSheetList() {
        Style style = new Style();
        assertTrue(style.styleSheets().isEmpty());

        style.setStyleSheets(List.of("a.css", "b.css"));
        assertEquals(List.of("a.css", "b.css"), style.styleSheets());

        style.setStyleSheets(List.of("c.css"));
        assertEquals(List.of("c.css"), style.styleSheets());
    }
}
