// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import javafx.geometry.Insets;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Consolidated test factory for layout objects. Replaces per-file
 * makePrimitive/mockRelationStyle/mockPrimitiveStyle helpers.
 */
public final class TestLayouts {

    private TestLayouts() {}

    /**
     * Create a mock RelationStyle with all insets set to 0.
     */
    public static RelationStyle mockRelationStyle() {
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

    /**
     * Create a mock PrimitiveStyle where width(JsonNode) returns
     * charWidth * text.length().
     */
    public static PrimitiveStyle mockPrimitiveStyle(double charWidth) {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight()).thenReturn(20.0);
        when(labelStyle.width(anyString())).thenReturn(10.0);

        PrimitiveStyle primStyle = mock(PrimitiveStyle.class);
        when(primStyle.getLabelStyle()).thenReturn(labelStyle);
        when(primStyle.getHeight(anyDouble(), anyDouble())).thenReturn(20.0);
        when(primStyle.getListVerticalInset()).thenReturn(0.0);
        when(primStyle.getMinValueWidth()).thenReturn(30.0);
        when(primStyle.getMaxTablePrimitiveWidth()).thenReturn(Double.MAX_VALUE);
        when(primStyle.getVerticalHeaderThreshold()).thenReturn(1.5);
        when(primStyle.getVariableLengthThreshold()).thenReturn(2.0);
        when(primStyle.getOutlineSnapValueWidth()).thenReturn(0.0);
        when(primStyle.width(any(JsonNode.class))).thenAnswer(inv -> {
            JsonNode node = inv.getArgument(0);
            String text = node.isTextual() ? node.textValue() : node.toString();
            return charWidth * text.length();
        });
        return primStyle;
    }

    /**
     * Create a PrimitiveLayout with pre-set widths (bypasses measure()).
     */
    public static PrimitiveLayout makePrimitive(String name, double width) {
        return makePrimitive(name, width, width, 0);
    }

    /**
     * Create a PrimitiveLayout with pre-set dataWidth, columnWidth, and labelWidth.
     */
    public static PrimitiveLayout makePrimitive(String name, double dataWidth,
                                                 double columnWidth,
                                                 double labelWidth) {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight()).thenReturn(20.0);
        when(labelStyle.width(anyString())).thenReturn(10.0);

        PrimitiveStyle primStyle = mock(PrimitiveStyle.class);
        when(primStyle.getLabelStyle()).thenReturn(labelStyle);
        when(primStyle.getHeight(anyDouble(), anyDouble())).thenReturn(20.0);
        when(primStyle.getListVerticalInset()).thenReturn(0.0);
        when(primStyle.getMinValueWidth()).thenReturn(30.0);
        when(primStyle.getMaxTablePrimitiveWidth()).thenReturn(Double.MAX_VALUE);
        when(primStyle.getVerticalHeaderThreshold()).thenReturn(1.5);
        when(primStyle.getVariableLengthThreshold()).thenReturn(2.0);
        when(primStyle.getOutlineSnapValueWidth()).thenReturn(0.0);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive(name), primStyle);
        layout.columnWidth = columnWidth;
        layout.dataWidth = dataWidth;
        layout.labelWidth = labelWidth;
        return layout;
    }

    /**
     * Create a child RelationLayout with pre-set widths and table mode.
     */
    public static RelationLayout makeChildRelation(String name, double width,
                                                     boolean useTable) {
        RelationStyle childStyle = mockRelationStyle();
        RelationLayout layout = new RelationLayout(new Relation(name), childStyle);
        layout.columnWidth = width;
        layout.useTable = useTable;
        layout.tableColumnWidth = width;
        layout.labelWidth = 0;
        layout.maxCardinality = 3;
        layout.averageChildCardinality = 2;
        return layout;
    }

    /**
     * Create a RelationLayout with pre-set children, labelWidth, and cardinality.
     */
    public static RelationLayout makeRelation(String name, double labelWidth,
                                               int averageChildCardinality,
                                               PrimitiveLayout... children) {
        RelationStyle style = mockRelationStyle();
        Relation schema = new Relation(name);
        for (var c : children) {
            schema.addChild(new Primitive(c.getField()));
        }
        RelationLayout layout = new RelationLayout(schema, style);
        layout.children.clear();
        for (var c : children) {
            layout.children.add(c);
        }
        layout.labelWidth = labelWidth;
        layout.averageChildCardinality = averageChildCardinality;
        return layout;
    }
}
