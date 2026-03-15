// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import javafx.geometry.Insets;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;

/**
 * Tests for Kramer-8jq: F1 sensible maxTablePrimitiveWidth default.
 * Bakke 2013 Section 3.3 specifies a ~50 character cap on
 * variable-length primitive columns in table mode.
 */
class MaxTablePrimitiveWidthTest {

    /**
     * The default maxTablePrimitiveWidth should be 350.0 (not MAX_VALUE).
     */
    @Test
    void defaultCapIs350() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        PrimitiveStyle.PrimitiveTextStyle style =
            new PrimitiveStyle.PrimitiveTextStyle(labelStyle, new Insets(0), labelStyle);

        assertEquals(350.0, style.getMaxTablePrimitiveWidth(),
                     "Default maxTablePrimitiveWidth should be 350.0 per Bakke §3.3");
    }

    /**
     * With the default 350.0 cap, a primitive with dataWidth=600 should have
     * its table column width capped at 350.
     */
    @Test
    void capLimitsVariableLengthTableColumnWidth() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight()).thenReturn(20.0);
        when(labelStyle.width(anyString())).thenReturn(10.0);

        PrimitiveStyle.PrimitiveTextStyle style =
            new PrimitiveStyle.PrimitiveTextStyle(labelStyle, new Insets(0), labelStyle);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("description"), style);
        layout.dataWidth = 600;

        assertEquals(Style.snap(350.0), layout.tableColumnWidth(),
                     "tableColumnWidth() should be capped at 350");
        assertEquals(Style.snap(350.0), layout.calculateTableColumnWidth(),
                     "calculateTableColumnWidth() should be capped at 350");
    }

    /**
     * With the 350 cap, a flat relation with children
     * (courseNumber=50, title=200, description=600, credits=30)
     * should choose table mode at width=800 because the capped
     * tableWidth = 50+200+350+30 = 630 <= 800.
     *
     * Without the cap, tableWidth = 50+200+600+30 = 880 > 800
     * which would force outline mode.
     */
    @Test
    void catalogTableModeTriggers() {
        RelationStyle relStyle = mockRelationStyle();
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight()).thenReturn(20.0);
        when(labelStyle.width(anyString())).thenReturn(10.0);

        Relation catalog = new Relation("catalog");
        catalog.addChild(new Primitive("courseNumber"));
        catalog.addChild(new Primitive("title"));
        catalog.addChild(new Primitive("description"));
        catalog.addChild(new Primitive("credits"));

        RelationLayout layout = new RelationLayout(catalog, relStyle);

        // Use real PrimitiveTextStyle with default cap
        PrimitiveStyle.PrimitiveTextStyle primStyle =
            new PrimitiveStyle.PrimitiveTextStyle(labelStyle, new Insets(0), labelStyle);

        PrimitiveLayout courseNumber = new PrimitiveLayout(new Primitive("courseNumber"), primStyle);
        courseNumber.dataWidth = 50;
        courseNumber.columnWidth = 50;
        courseNumber.labelWidth = 10;

        PrimitiveLayout title = new PrimitiveLayout(new Primitive("title"), primStyle);
        title.dataWidth = 200;
        title.columnWidth = 200;
        title.labelWidth = 10;

        PrimitiveLayout description = new PrimitiveLayout(new Primitive("description"), primStyle);
        description.dataWidth = 600;
        description.columnWidth = 600;
        description.labelWidth = 10;

        PrimitiveLayout credits = new PrimitiveLayout(new Primitive("credits"), primStyle);
        credits.dataWidth = 30;
        credits.columnWidth = 30;
        credits.labelWidth = 10;

        layout.children.clear();
        layout.children.add(courseNumber);
        layout.children.add(title);
        layout.children.add(description);
        layout.children.add(credits);
        layout.averageChildCardinality = 1;
        layout.labelWidth = 10;

        assertEquals(4, layout.children.size(), "Guard: 4 children must be present");

        layout.layout(800.0);

        assertTrue(layout.isUseTable(),
                   "Table mode should trigger: capped tableWidth (630) <= 800");
    }

    private static RelationStyle mockRelationStyle() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight()).thenReturn(20.0);
        when(labelStyle.width(anyString())).thenReturn(10.0);

        RelationStyle style = mock(RelationStyle.class);
        when(style.getLabelStyle()).thenReturn(labelStyle);
        when(style.getOutlineHorizontalInset()).thenReturn(0.0);
        when(style.getOutlineCellHorizontalInset()).thenReturn(0.0);
        when(style.getSpanHorizontalInset()).thenReturn(0.0);
        when(style.getColumnHorizontalInset()).thenReturn(0.0);
        when(style.getElementHorizontalInset()).thenReturn(0.0);
        when(style.getMaxAverageCardinality()).thenReturn(10);
        when(style.getSpanVerticalInset()).thenReturn(0.0);
        when(style.getColumnVerticalInset()).thenReturn(0.0);
        when(style.getRowVerticalInset()).thenReturn(0.0);
        when(style.getRowCellVerticalInset()).thenReturn(0.0);
        when(style.getRowCellHorizontalInset()).thenReturn(0.0);
        when(style.getRowHorizontalInset()).thenReturn(0.0);
        when(style.getOutlineCellVerticalInset()).thenReturn(0.0);
        when(style.getOutlineVerticalInset()).thenReturn(0.0);
        when(style.getElementVerticalInset()).thenReturn(0.0);
        when(style.getNestedHorizontalInset()).thenReturn(0.0);
        when(style.getNestedInsets()).thenReturn(new Insets(0));
        when(style.getTableVerticalInset()).thenReturn(0.0);
        when(style.getOutlineMaxLabelWidth()).thenReturn(200.0);
        when(style.getOutlineColumnMinWidth()).thenReturn(60.0);
        when(style.getBulletText()).thenReturn("");
        when(style.getBulletWidth()).thenReturn(0.0);
        when(style.getIndentWidth()).thenReturn(0.0);
        return style;
    }
}
