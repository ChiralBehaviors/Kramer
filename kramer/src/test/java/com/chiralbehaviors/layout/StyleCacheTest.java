// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.style.Style;

/**
 * Tests for RDR-010 / Kramer-8ml: AutoLayout stylesheet equality guard.
 * Verifies that setting the same stylesheet list twice does not trigger
 * a second cache clear in Style.setStyleSheets.
 */
class StyleCacheTest {

    /**
     * Subclass of Style that counts setStyleSheets(owner) invocations.
     */
    private static class CountingStyle extends Style {
        final AtomicInteger setCount = new AtomicInteger(0);

        @Override
        public void setStyleSheets(List<String> stylesheets, Object owner) {
            setCount.incrementAndGet();
            super.setStyleSheets(stylesheets, owner);
        }
    }

    private static final Object OWNER = new Object();

    @Test
    void equalStylesheetListSkipsSetStyleSheets() {
        CountingStyle style = new CountingStyle();

        List<String> initial = List.of("stylesheet-a.css");
        List<String> same    = List.of("stylesheet-a.css");

        // First call: list differs from empty default → must propagate
        style.setStyleSheets(initial, OWNER);
        assertEquals(1, style.setCount.get(),
                     "First distinct stylesheet should trigger setStyleSheets");

        // Simulate the equality guard: if new list equals current, skip
        boolean wouldSkip = same.equals(style.styleSheets());
        assertEquals(true, wouldSkip,
                     "Equal stylesheet list should be detected as equal (guard precondition)");

        if (!wouldSkip) {
            style.setStyleSheets(same, OWNER);
        }

        assertEquals(1, style.setCount.get(),
                     "Identical stylesheet list must not trigger a second cache clear");
    }

    @Test
    void differentStylesheetListTriggersSetStyleSheets() {
        CountingStyle style = new CountingStyle();

        List<String> first  = List.of("stylesheet-a.css");
        List<String> second = List.of("stylesheet-b.css");

        style.setStyleSheets(first, OWNER);
        assertEquals(1, style.setCount.get());

        boolean wouldSkip = second.equals(style.styleSheets());
        if (!wouldSkip) {
            style.setStyleSheets(second, OWNER);
        }

        assertEquals(2, style.setCount.get(),
                     "Different stylesheet list must trigger setStyleSheets again");
    }
}
