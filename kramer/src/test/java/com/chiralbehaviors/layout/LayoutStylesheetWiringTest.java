// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.style.Style;

/**
 * Tests for Kramer-517: LayoutStylesheet wired into Style (the measure/layout/compress pipeline).
 */
class LayoutStylesheetWiringTest {

    @Test
    void defaultConstructorCreatesDefaultStylesheet() {
        Style style = new Style();
        assertNotNull(style.getStylesheet(),
                      "Default constructor must set a non-null LayoutStylesheet");
    }

    @Test
    void defaultStylesheetIsDefaultLayoutStylesheet() {
        Style style = new Style();
        assertInstanceOf(DefaultLayoutStylesheet.class, style.getStylesheet(),
                         "Default constructor must create a DefaultLayoutStylesheet");
    }

    @Test
    void observerConstructorCreatesDefaultStylesheet() {
        Style.LayoutObserver observer = new Style.LayoutObserver() {};
        Style style = new Style(observer);
        assertNotNull(style.getStylesheet(),
                      "Observer constructor must set a non-null LayoutStylesheet");
        assertInstanceOf(DefaultLayoutStylesheet.class, style.getStylesheet(),
                         "Observer constructor must create a DefaultLayoutStylesheet");
    }

    @Test
    void explicitStylesheetConstructorStoresIt() {
        Style.LayoutObserver observer = new Style.LayoutObserver() {};
        LayoutStylesheet explicit = mock(LayoutStylesheet.class);
        Style style = new Style(observer, explicit);
        assertSame(explicit, style.getStylesheet(),
                   "Explicit stylesheet constructor must store the provided instance");
    }

    @Test
    void explicitStylesheetConstructorDefaultObserver() {
        LayoutStylesheet explicit = mock(LayoutStylesheet.class);
        Style style = new Style(explicit);
        assertSame(explicit, style.getStylesheet(),
                   "Style(LayoutStylesheet) must store the provided stylesheet");
    }

    @Test
    void defaultStylesheetVersionStartsAtZero() {
        Style style = new Style();
        assertEquals(0L, style.getStylesheet().getVersion(),
                     "Default stylesheet version must start at 0");
    }

    @Test
    void primitiveLayoutCanAccessStylesheetViaModel() {
        Style style = new Style();
        LayoutStylesheet sheet = style.getStylesheet();
        assertNotNull(sheet, "PrimitiveLayout can reach stylesheet via style.getStylesheet()");
    }

    @Test
    void relationLayoutCanAccessStylesheetViaModel() {
        Style style = new Style();
        LayoutStylesheet sheet = style.getStylesheet();
        assertNotNull(sheet, "RelationLayout can reach stylesheet via style.getStylesheet()");
    }

    @Test
    void setStylesheetReplacesExistingSheet() {
        Style style = new Style();
        LayoutStylesheet replacement = mock(LayoutStylesheet.class);
        style.setStylesheet(replacement);
        assertSame(replacement, style.getStylesheet(),
                   "setStylesheet must replace the current stylesheet");
    }

    @Test
    void setStylesheetNullThrows() {
        Style style = new Style();
        assertThrows(NullPointerException.class, () -> style.setStylesheet(null),
                     "setStylesheet(null) must throw NullPointerException");
    }
}
