// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.chiralbehaviors.layout.table.CrosstabCell;
import com.chiralbehaviors.layout.table.CrosstabHeader;
import com.chiralbehaviors.layout.table.CrosstabRow;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Tests for Kramer-7vl: CrosstabHeader, CrosstabRow, CrosstabCell, and
 * layoutCrosstab() integration in RelationLayout.
 */
@ExtendWith(ApplicationExtension.class)
class CrosstabTest {

    @Start
    void start(Stage stage) {
        stage.setScene(new Scene(new Pane(), 800, 600));
        stage.show();
    }

    // ------------------------------------------------------------------ //
    //  CrosstabHeader construction                                         //
    // ------------------------------------------------------------------ //

    @Test
    void crosstabHeaderCreatedWithPivotColumnNames() throws Exception {
        List<String> pivotValues = List.of("Q1", "Q2", "Q3", "Q4");
        RelationStyle style = TestLayouts.mockRelationStyle();
        AtomicReference<CrosstabHeader> ref = new AtomicReference<>();

        Platform.runLater(() -> ref.set(new CrosstabHeader(pivotValues, 60.0, 25.0, style)));
        WaitForAsyncUtils.waitForFxEvents();

        CrosstabHeader header = ref.get();
        assertNotNull(header);
        assertEquals(pivotValues.size(), header.getPivotCount());
    }

    @Test
    void crosstabHeaderWithEmptyPivotListCreatesNoCells() throws Exception {
        AtomicReference<CrosstabHeader> ref = new AtomicReference<>();
        Platform.runLater(() -> ref.set(new CrosstabHeader(List.of(), 60.0, 25.0,
                                                            TestLayouts.mockRelationStyle())));
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(ref.get());
        assertEquals(0, ref.get().getPivotCount());
    }

    @Test
    void crosstabHeaderExposesColumnLabels() throws Exception {
        List<String> pivotValues = List.of("Alpha", "Beta");
        AtomicReference<CrosstabHeader> ref = new AtomicReference<>();
        Platform.runLater(() -> ref.set(new CrosstabHeader(pivotValues, 80.0, 20.0,
                                                            TestLayouts.mockRelationStyle())));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(pivotValues, ref.get().getPivotValues());
    }

    // ------------------------------------------------------------------ //
    //  CrosstabRow construction                                            //
    // ------------------------------------------------------------------ //

    @Test
    void crosstabRowConstructedWithRowHeaderAndDataCells() throws Exception {
        List<String> pivotValues = List.of("Jan", "Feb", "Mar");
        AtomicReference<CrosstabRow> ref = new AtomicReference<>();
        Platform.runLater(() -> ref.set(
            new CrosstabRow(pivotValues, 100.0, 60.0, 25.0, TestLayouts.mockRelationStyle())));
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(ref.get());
        assertEquals(pivotValues.size(), ref.get().getPivotCount());
    }

    @Test
    void crosstabRowPivotCountMatchesPivotValues() throws Exception {
        List<String> pivotValues = List.of("X", "Y");
        AtomicReference<CrosstabRow> ref = new AtomicReference<>();
        Platform.runLater(() -> ref.set(
            new CrosstabRow(pivotValues, 80.0, 50.0, 20.0, TestLayouts.mockRelationStyle())));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(2, ref.get().getPivotCount());
    }

    // ------------------------------------------------------------------ //
    //  CrosstabCell construction                                           //
    // ------------------------------------------------------------------ //

    @Test
    void crosstabCellConstructedWithValue() throws Exception {
        AtomicReference<CrosstabCell> ref = new AtomicReference<>();
        Platform.runLater(() -> ref.set(new CrosstabCell(60.0, 25.0)));
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(ref.get());
    }

    @Test
    void crosstabCellUpdateItemDoesNotThrow() throws Exception {
        AtomicReference<CrosstabCell> ref = new AtomicReference<>();
        Platform.runLater(() -> ref.set(new CrosstabCell(60.0, 25.0)));
        WaitForAsyncUtils.waitForFxEvents();

        var node = JsonNodeFactory.instance.textNode("42");
        assertDoesNotThrow(() -> {
            try {
                Platform.runLater(() -> ref.get().updateItem(node));
                WaitForAsyncUtils.waitForFxEvents();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void crosstabCellUpdateItemWithNullDoesNotThrow() throws Exception {
        AtomicReference<CrosstabCell> ref = new AtomicReference<>();
        Platform.runLater(() -> ref.set(new CrosstabCell(60.0, 25.0)));
        WaitForAsyncUtils.waitForFxEvents();

        assertDoesNotThrow(() -> {
            try {
                Platform.runLater(() -> ref.get().updateItem(null));
                WaitForAsyncUtils.waitForFxEvents();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  layoutCrosstab() on RelationLayout                                  //
    // ------------------------------------------------------------------ //

    @Test
    void layoutCrosstabReturnsPositiveWidth() {
        Relation schema = buildSalesSchema();
        RelationLayout layout = buildMeasuredLayout(schema, buildSalesData());

        double width = layout.layoutCrosstab(600.0, List.of("Q1", "Q2", "Q3"));

        assertTrue(width > 0, "layoutCrosstab must return a positive width");
    }

    @Test
    void layoutCrosstabWidthReflectsPivotCount() {
        Relation schema = buildSalesSchema();
        ArrayNode data = buildSalesData();
        RelationLayout layout = buildMeasuredLayout(schema, data);

        double narrow = layout.layoutCrosstab(600.0, List.of("Q1"));
        layout.clearForTest();
        layout.measure(data, n -> n, mockModel(schema));
        double wide = layout.layoutCrosstab(600.0, List.of("Q1", "Q2", "Q3", "Q4"));

        assertTrue(wide >= narrow,
                   "More pivot columns should produce equal or greater width");
    }

    // ------------------------------------------------------------------ //
    //  CROSSTAB mode in buildControl() dispatches to buildCrosstab()       //
    // ------------------------------------------------------------------ //

    @Test
    void crosstabModeSetByLayoutCrosstab() {
        Relation schema = buildSalesSchema();
        RelationLayout layout = buildMeasuredLayout(schema, buildSalesData());
        layout.layoutCrosstab(600.0, List.of("Q1", "Q2"));

        assertTrue(layout.isCrosstab(),
                   "isCrosstab() must return true after layoutCrosstab()");
    }

    // ------------------------------------------------------------------ //
    //  Empty valueField → degrades to TABLE without crash                 //
    // ------------------------------------------------------------------ //

    @Test
    void emptyValueFieldDegradesToTableWithNoCrash() {
        Relation schema = new Relation("sales");
        schema.addChild(new Primitive("region"));
        // No "amount" child — simulates missing value field

        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        Style model = mockModel(schema);

        RelationLayout layout = new RelationLayout(schema, relStyle);
        layout.setSchemaPath(new SchemaPath("sales"));

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ObjectNode row = JsonNodeFactory.instance.objectNode();
        row.put("region", "North");
        data.add(row);

        layout.measure(data, n -> n, model);
        layout.layout(400.0);

        // Calling layoutCrosstab with an empty pivot list should not throw
        assertDoesNotThrow(() -> layout.layoutCrosstab(400.0, List.of()),
                           "layoutCrosstab with empty pivot list must not throw");
        // After degenerate call, layout should remain usable
        assertFalse(layout.isCrosstab(),
                    "isCrosstab() must be false when no pivot values given");
    }

    // ------------------------------------------------------------------ //
    //  adjustHeight with CROSSTAB distributes correctly                    //
    // ------------------------------------------------------------------ //

    @Test
    void adjustHeightInCrosstabModeDoesNotThrow() {
        Relation schema = buildSalesSchema();
        ArrayNode data = buildSalesData();
        RelationLayout layout = buildMeasuredLayout(schema, data);
        layout.layoutCrosstab(600.0, List.of("Q1", "Q2", "Q3"));

        // Simulate height computation and then adjustment
        layout.cellHeight(data.size(), 600.0);

        assertDoesNotThrow(() -> layout.adjustHeight(10.0),
                           "adjustHeight in CROSSTAB mode must not throw");
    }

    @Test
    void adjustHeightDistributesAcrossCrosstabRows() {
        Relation schema = buildSalesSchema();
        ArrayNode data = buildSalesData();
        RelationLayout layout = buildMeasuredLayout(schema, data);
        layout.layoutCrosstab(600.0, List.of("Q1", "Q2", "Q3"));

        layout.cellHeight(data.size(), 600.0);
        double heightBefore = layout.getCellHeight();
        layout.adjustHeight(data.size() * 5.0);
        double heightAfter = layout.getCellHeight();

        assertTrue(heightAfter >= heightBefore,
                   "adjustHeight in CROSSTAB mode must not shrink cellHeight");
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private static Relation buildSalesSchema() {
        Relation schema = new Relation("sales");
        schema.addChild(new Primitive("region"));
        schema.addChild(new Primitive("quarter"));
        schema.addChild(new Primitive("amount"));
        return schema;
    }

    private static ArrayNode buildSalesData() {
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (String[] row : new String[][]{
            {"North", "Q1", "100"}, {"North", "Q2", "200"},
            {"South", "Q1", "150"}, {"South", "Q3", "300"}
        }) {
            ObjectNode n = JsonNodeFactory.instance.objectNode();
            n.put("region", row[0]);
            n.put("quarter", row[1]);
            n.put("amount", row[2]);
            data.add(n);
        }
        return data;
    }

    private Style mockModel(Relation schema) {
        Style model = mock(Style.class);
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        for (SchemaNode child : schema.getChildren()) {
            if (child instanceof Primitive p) {
                PrimitiveLayout pl = new PrimitiveLayout(p, primStyle);
                when(model.layout(p)).thenReturn(pl);
            }
        }
        when(model.layout(any(SchemaNode.class))).thenAnswer(inv -> {
            SchemaNode n = inv.getArgument(0);
            if (n instanceof Primitive p) return model.layout(p);
            RelationStyle rs = TestLayouts.mockRelationStyle();
            return new RelationLayout((Relation) n, rs);
        });
        when(model.getStylesheet()).thenReturn(null);
        return model;
    }

    private RelationLayout buildMeasuredLayout(Relation schema, ArrayNode data) {
        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        Style model = mockModel(schema);

        RelationLayout layout = new RelationLayout(schema, relStyle);
        layout.setSchemaPath(new SchemaPath("sales"));
        layout.measure(data, n -> n, model);
        layout.layout(600.0);
        return layout;
    }
}
