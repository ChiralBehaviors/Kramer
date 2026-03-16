// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for Kramer-8tu: RelationRenderMode and PrimitiveRenderMode enum migration.
 */
class RenderModeTest {

    @Test
    void relationRenderModeValues() {
        var values = RelationRenderMode.values();
        assertEquals(4, values.length);
        assertNotNull(RelationRenderMode.valueOf("AUTO"));
        assertNotNull(RelationRenderMode.valueOf("TABLE"));
        assertNotNull(RelationRenderMode.valueOf("OUTLINE"));
        assertNotNull(RelationRenderMode.valueOf("CROSSTAB"));
    }

    @Test
    void primitiveRenderModeValues() {
        var values = PrimitiveRenderMode.values();
        assertEquals(4, values.length);
        assertNotNull(PrimitiveRenderMode.valueOf("TEXT"));
        assertNotNull(PrimitiveRenderMode.valueOf("BAR"));
        assertNotNull(PrimitiveRenderMode.valueOf("BADGE"));
        assertNotNull(PrimitiveRenderMode.valueOf("SPARKLINE"));
    }

    @Test
    void layoutResultWithTableModeUseTableReturnsTrue() {
        var result = new LayoutResult(RelationRenderMode.TABLE, PrimitiveRenderMode.TEXT,
                                      false, 100.0, 0.0, 90.0, List.of());
        assertTrue(result.useTable());
    }

    @Test
    void layoutResultWithOutlineModeUseTableReturnsFalse() {
        var result = new LayoutResult(RelationRenderMode.OUTLINE, PrimitiveRenderMode.TEXT,
                                      false, 0.0, 0.0, 80.0, List.of());
        assertFalse(result.useTable());
    }

    @Test
    void layoutResultAutoModeUseTableReturnsFalse() {
        var result = new LayoutResult(RelationRenderMode.AUTO, PrimitiveRenderMode.TEXT,
                                      false, 0.0, 0.0, 80.0, List.of());
        assertFalse(result.useTable());
    }

    @Test
    void layoutResultCrosstabModeUseTableReturnsFalse() {
        var result = new LayoutResult(RelationRenderMode.CROSSTAB, PrimitiveRenderMode.TEXT,
                                      false, 0.0, 0.0, 80.0, List.of());
        assertFalse(result.useTable());
    }

    @Test
    void layoutResultAccessorsWork() {
        var children = List.<LayoutResult>of();
        var result = new LayoutResult(RelationRenderMode.TABLE, PrimitiveRenderMode.BAR,
                                      true, 120.0, 5.0, 110.0, children);
        assertEquals(RelationRenderMode.TABLE, result.relationMode());
        assertEquals(PrimitiveRenderMode.BAR, result.primitiveMode());
        assertTrue(result.useVerticalHeader());
        assertEquals(120.0, result.tableColumnWidth());
        assertEquals(5.0, result.columnHeaderIndentation());
        assertEquals(110.0, result.constrainedColumnWidth());
        assertTrue(result.childResults().isEmpty());
    }
}
