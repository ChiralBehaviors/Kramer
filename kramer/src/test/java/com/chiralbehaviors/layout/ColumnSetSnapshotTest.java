// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;

import javafx.geometry.Insets;

class ColumnSetSnapshotTest {

    // --- ColumnSnapshot construction ---

    @Test
    void columnSnapshotStoresWidthAndFields() {
        var snap = new ColumnSnapshot(120.0, List.of("name", "age"));
        assertEquals(120.0, snap.width());
        assertEquals(List.of("name", "age"), snap.fieldNames());
    }

    @Test
    void columnSnapshotIsImmutable() {
        var mutable = new ArrayList<>(List.of("a", "b"));
        var snap = new ColumnSnapshot(50.0, mutable);
        mutable.add("c");
        assertEquals(List.of("a", "b"), snap.fieldNames(),
                     "ColumnSnapshot must copy field list on construction");
        assertThrows(UnsupportedOperationException.class,
                     () -> snap.fieldNames().add("x"));
    }

    // --- ColumnSetSnapshot construction ---

    @Test
    void columnSetSnapshotStoresColumnsAndHeight() {
        var c1 = new ColumnSnapshot(100.0, List.of("f1"));
        var c2 = new ColumnSnapshot(100.0, List.of("f2", "f3"));
        var snap = new ColumnSetSnapshot(List.of(c1, c2), 80.0);
        assertEquals(2, snap.columns().size());
        assertEquals(80.0, snap.height());
    }

    @Test
    void columnSetSnapshotIsImmutable() {
        var c1 = new ColumnSnapshot(100.0, List.of("f1"));
        var mutable = new ArrayList<>(List.of(c1));
        var snap = new ColumnSetSnapshot(mutable, 40.0);
        mutable.add(new ColumnSnapshot(50.0, List.of("f2")));
        assertEquals(1, snap.columns().size(),
                     "ColumnSetSnapshot must copy column list on construction");
        assertThrows(UnsupportedOperationException.class,
                     () -> snap.columns().add(c1));
    }

    // --- Round-trip: live ColumnSet → snapshot ---

    @Test
    void roundTripFromColumnSetCapturesState() {
        RelationStyle style = mockRelationStyle();

        ColumnSet cs = new ColumnSet();
        // cs starts with one empty Column; add two fields to it
        PrimitiveLayout p1 = makePrimitive("title", 120.0);
        PrimitiveLayout p2 = makePrimitive("author", 80.0);
        cs.add(p1);
        cs.add(p2);
        // Manually set column width via compress-like operation
        cs.getColumns().get(0).setWidth(200.0);

        ColumnSetSnapshot snap = cs.toSnapshot(42.5);

        assertEquals(42.5, snap.height(), 0.001);
        assertEquals(1, snap.columns().size());

        ColumnSnapshot colSnap = snap.columns().get(0);
        assertEquals(200.0, colSnap.width(), 0.001);
        assertEquals(List.of("title", "author"), colSnap.fieldNames());
    }

    @Test
    void roundTripWithMultipleColumns() {
        ColumnSet cs = new ColumnSet();
        // Add a field to the initial column, then manually add a second column
        PrimitiveLayout p1 = makePrimitive("description", 300.0);
        PrimitiveLayout p2 = makePrimitive("credits", 50.0);
        cs.add(p1);
        // Second column added by compress; simulate by accessing columns list
        Column col2 = new Column(100.0);
        col2.add(p2);
        cs.getColumns().add(col2);
        cs.getColumns().get(0).setWidth(300.0);

        ColumnSetSnapshot snap = cs.toSnapshot(60.0);

        assertEquals(2, snap.columns().size());
        assertEquals(List.of("description"), snap.columns().get(0).fieldNames());
        assertEquals(List.of("credits"), snap.columns().get(1).fieldNames());
        assertEquals(300.0, snap.columns().get(0).width(), 0.001);
        assertEquals(100.0, snap.columns().get(1).width(), 0.001);
    }

    // --- Infrastructure ---

    private static PrimitiveLayout makePrimitive(String name, double width) {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight()).thenReturn(20.0);
        when(labelStyle.width(anyString())).thenReturn(10.0);

        PrimitiveStyle primStyle = mock(PrimitiveStyle.class);
        when(primStyle.getLabelStyle()).thenReturn(labelStyle);
        when(primStyle.getHeight(anyDouble(), anyDouble())).thenReturn(20.0);
        when(primStyle.getListVerticalInset()).thenReturn(0.0);
        when(primStyle.getMinValueWidth()).thenReturn(30.0);
        when(primStyle.getMaxTablePrimitiveWidth()).thenReturn(350.0);
        when(primStyle.getVerticalHeaderThreshold()).thenReturn(1.5);
        when(primStyle.getVariableLengthThreshold()).thenReturn(2.0);
        when(primStyle.getOutlineSnapValueWidth()).thenReturn(0.0);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive(name), primStyle);
        layout.columnWidth = width;
        layout.dataWidth = width;
        layout.labelWidth = 0;
        return layout;
    }

    private static RelationStyle mockRelationStyle() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight()).thenReturn(20.0);
        when(labelStyle.width(anyString())).thenReturn(10.0);

        RelationStyle style = mock(RelationStyle.class);
        when(style.getLabelStyle()).thenReturn(labelStyle);
        when(style.getOutlineColumnMinWidth()).thenReturn(60.0);
        when(style.getColumnHorizontalInset()).thenReturn(0.0);
        when(style.getElementHorizontalInset()).thenReturn(0.0);
        when(style.getColumnVerticalInset()).thenReturn(0.0);
        when(style.getElementVerticalInset()).thenReturn(0.0);
        when(style.getNestedInsets()).thenReturn(new Insets(0));
        return style;
    }
}
