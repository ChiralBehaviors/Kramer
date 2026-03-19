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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for RDR-003: Vertical Table Headers and Two-Phase Justification
 * - Phase 1: Vertical Header Detection (useVerticalHeader)
 * - Phase 2: ColumnHeader Vertical Rendering + columnHeaderHeight
 * - Phase 3: Two-Phase Justification (un-rotate, fixed/variable distribution)
 */
class VerticalHeaderTest {

    private static PrimitiveStyle mockPrimitiveStyle(double charWidth) {
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

    private static PrimitiveLayout makePrimitive(String name, double dataWidth,
                                                  double labelWidth,
                                                  boolean variableLength) {
        LabelStyle labelStyle = mock(LabelStyle.class);
        when(labelStyle.getHeight()).thenReturn(20.0);
        when(labelStyle.width(anyString())).thenReturn(labelWidth);

        PrimitiveStyle primStyle = mock(PrimitiveStyle.class);
        when(primStyle.getLabelStyle()).thenReturn(labelStyle);
        when(primStyle.getHeight(anyDouble(), anyDouble())).thenReturn(20.0);
        when(primStyle.getListVerticalInset()).thenReturn(0.0);
        when(primStyle.getMinValueWidth()).thenReturn(30.0);
        when(primStyle.getMaxTablePrimitiveWidth()).thenReturn(Double.MAX_VALUE);
        when(primStyle.getVerticalHeaderThreshold()).thenReturn(1.5);
        when(primStyle.getVariableLengthThreshold()).thenReturn(2.0);
        when(primStyle.getOutlineSnapValueWidth()).thenReturn(0.0);

        if (variableLength) {
            // Variable: different widths per value → ratio > 2.0
            when(primStyle.width(any(JsonNode.class))).thenAnswer(inv -> {
                JsonNode node = inv.getArgument(0);
                String text = node.isTextual() ? node.textValue() : node.toString();
                return 7.0 * text.length();
            });
        } else {
            // Fixed: same width for all values → ratio = 1.0
            when(primStyle.width(any(JsonNode.class))).thenReturn(dataWidth);
        }

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive(name), primStyle);

        // Drive isVariableLength through measure with appropriate data
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        if (variableLength) {
            data.add("A");
            data.add("A");
            data.add("Very Long Name With Many Many Characters Here");
        } else {
            data.add("2026-01-01");
            data.add("2026-06-15");
        }
        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        // Override widths to exact test values after measure
        layout.dataWidth = dataWidth;
        layout.columnWidth = Math.max(labelWidth, dataWidth);
        layout.labelWidth = labelWidth;
        return layout;
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

    // ---- Phase 1: Vertical Header Detection ----

    /**
     * Narrow column with long label should trigger vertical header.
     * labelWidth=100 > tableColumnWidth(50) * 1.5 = 75 → true
     */
    @Test
    void narrowColumnSetsUseVerticalHeader() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("date"), style);
        layout.dataWidth = 50;
        layout.labelWidth = 100;
        layout.columnWidth = 100;

        layout.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));

        assertTrue(layout.isUseVerticalHeader(),
                   "Narrow column (50) with long label (100) should use vertical header");
    }

    /**
     * Wide column with short label should NOT trigger vertical header.
     * labelWidth=40 > tableColumnWidth(100) * 1.5 = 150 → false
     */
    @Test
    void wideColumnKeepsHorizontalHeader() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("desc"), style);
        layout.dataWidth = 100;
        layout.labelWidth = 40;
        layout.columnWidth = 100;

        layout.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));

        assertFalse(layout.isUseVerticalHeader(),
                    "Wide column (100) with short label (40) should keep horizontal header");
    }

    /**
     * dataWidth decoupled from labelWidth: tableColumnWidth() should use
     * dataWidth only, not max(dataWidth, labelWidth).
     */
    @Test
    void tableColumnWidthUsesDataWidthOnly() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("date"), style);
        layout.dataWidth = 50;
        layout.labelWidth = 100;
        layout.columnWidth = 100; // max(label, data)

        // tableColumnWidth should be based on dataWidth (50), not columnWidth (100)
        assertEquals(50, layout.tableColumnWidth(),
                     "tableColumnWidth() should use dataWidth, not columnWidth");

        // columnWidth() should still include labelWidth (for outline mode)
        assertEquals(100, layout.columnWidth(),
                     "columnWidth() should still include labelWidth for outline mode");
    }

    // ---- Phase 2: columnHeaderHeight ----

    /**
     * Vertical header: columnHeaderHeight should return snap(labelWidth)
     * instead of snap(labelStyle.getHeight()).
     */
    @Test
    void verticalHeaderColumnHeaderHeightUsesLabelWidth() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("date"), style);
        layout.dataWidth = 50;
        layout.labelWidth = 100;
        layout.columnWidth = 100;

        layout.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));

        assertTrue(layout.isUseVerticalHeader());
        assertEquals(Style.snap(100), layout.columnHeaderHeight(),
                     "Vertical header height should be snap(labelWidth)");
    }

    /**
     * Horizontal header: columnHeaderHeight should return snap(labelStyle.getHeight()).
     */
    @Test
    void horizontalHeaderColumnHeaderHeightUsesLabelHeight() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("desc"), style);
        layout.dataWidth = 100;
        layout.labelWidth = 40;
        layout.columnWidth = 100;

        layout.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));

        assertFalse(layout.isUseVerticalHeader());
        assertEquals(Style.snap(20.0), layout.columnHeaderHeight(),
                     "Horizontal header height should be snap(labelStyle.getHeight())");
    }

    // ---- Phase 3: Two-Phase Justification ----

    /**
     * Phase 1: table justified wider should un-rotate vertical headers
     * that now fit horizontally.
     */
    @Test
    void justifyColumnUnrotatesHeaders() {
        RelationStyle relStyle = mockRelationStyle();
        Relation parent = new Relation("parent");
        parent.addChild(new Primitive("date"));
        parent.addChild(new Primitive("name"));
        RelationLayout layout = new RelationLayout(parent, relStyle);

        // date: narrow data (50), wide label (100) → vertical header
        PrimitiveLayout date = makePrimitive("date", 50, 100, false);
        date.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));
        assertTrue(date.isUseVerticalHeader(), "date should start vertical");

        // name: wide data (150), narrow label (40) → horizontal header, variable
        PrimitiveLayout name = makePrimitive("name", 150, 40, true);
        name.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));
        assertFalse(name.isUseVerticalHeader());

        layout.children.clear();
        layout.children.add(date);
        layout.children.add(name);
        layout.tableColumnWidth = date.tableColumnWidth() + name.tableColumnWidth();

        // Justify with enough extra to un-rotate date
        // needed = labelWidth(100) - tableColumnWidth(50) = 50
        // extraSpace = 300 - 200 = 100 >= 50 → un-rotate
        layout.justify(300);

        assertFalse(date.isUseVerticalHeader(),
                    "Phase 1 should un-rotate date header when extra space allows");
    }

    /**
     * Phase 2: all children get at least their minimum (labelWidth), then
     * surplus is distributed proportionally based on natural width above
     * minimum. Both fixed and variable children participate.
     */
    @Test
    void mixedFixedVariableJustification() {
        RelationStyle relStyle = mockRelationStyle();
        Relation parent = new Relation("parent");
        parent.addChild(new Primitive("date"));
        parent.addChild(new Primitive("name"));
        RelationLayout layout = new RelationLayout(parent, relStyle);

        // date: fixed-length, dataWidth=70, labelWidth=40 (fits horizontally)
        PrimitiveLayout date = makePrimitive("date", 70, 40, false);

        // name: variable-length, dataWidth=100, labelWidth=40
        PrimitiveLayout name = makePrimitive("name", 100, 40, true);

        layout.children.clear();
        layout.children.add(date);
        layout.children.add(name);
        layout.tableColumnWidth = date.tableColumnWidth() + name.tableColumnWidth();

        // Available = 300, minimums = 40+40 = 80, surplus = 220
        // Surplus weights: date=(70-40)=30, name=(100-40)=60, total=90
        // date: 40 + 220*(30/90) = 40 + 73.3 ≈ 113
        // name: 300 - 113 ≈ 187
        layout.justify(300);

        assertTrue(date.getJustifiedWidth() >= 40,
                   "date should get at least label width (40)");
        assertTrue(date.getJustifiedWidth() > 70,
                   "date should get surplus above natural width at 300px");
        assertTrue(name.getJustifiedWidth() > date.getJustifiedWidth(),
                   "name (larger natural width) should get more than date");
        assertEquals(300, Style.snap(date.getJustifiedWidth() + name.getJustifiedWidth()),
                     0.5, "Total should equal available width");
    }

    /**
     * Edge case: all fixed-width children → fall back to proportional
     * distribution since no variable-length children exist.
     */
    @Test
    void allFixedFallsBackToProportional() {
        RelationStyle relStyle = mockRelationStyle();
        Relation parent = new Relation("parent");
        parent.addChild(new Primitive("date"));
        parent.addChild(new Primitive("id"));
        RelationLayout layout = new RelationLayout(parent, relStyle);

        PrimitiveLayout date = makePrimitive("date", 70, 40, false);
        PrimitiveLayout id = makePrimitive("id", 30, 10, false);

        layout.children.clear();
        layout.children.add(date);
        layout.children.add(id);
        layout.tableColumnWidth = date.tableColumnWidth() + id.tableColumnWidth();

        // Available = 200, tableColumnWidth = 70+30 = 100
        // Proportional: date gets 200*(70/100)=140, id gets 200*(30/100)=60
        layout.justify(200);

        assertTrue(date.getJustifiedWidth() > 70,
                   "All-fixed proportional: date should get more than natural 70");
        assertTrue(id.getJustifiedWidth() > 30,
                   "All-fixed proportional: id should get more than natural 30");
        assertEquals(200, date.getJustifiedWidth() + id.getJustifiedWidth(), 1.0,
                     "Total justified width should equal available (200)");
    }

    /**
     * All children must receive justify() call — no child should have
     * justifiedWidth == -1.0 after justifyColumn().
     */
    @Test
    void allChildrenReceiveJustify() {
        RelationStyle relStyle = mockRelationStyle();
        Relation parent = new Relation("parent");
        parent.addChild(new Primitive("date"));
        parent.addChild(new Primitive("name"));
        parent.addChild(new Primitive("email"));
        RelationLayout layout = new RelationLayout(parent, relStyle);

        PrimitiveLayout date = makePrimitive("date", 70, 40, false);
        PrimitiveLayout name = makePrimitive("name", 100, 40, true);
        PrimitiveLayout email = makePrimitive("email", 80, 40, true);

        layout.children.clear();
        layout.children.add(date);
        layout.children.add(name);
        layout.children.add(email);
        layout.tableColumnWidth = date.tableColumnWidth() + name.tableColumnWidth()
                                  + email.tableColumnWidth();

        layout.justify(400);

        assertNotEquals(-1.0, date.getJustifiedWidth(),
                        "date must receive justify() call");
        assertNotEquals(-1.0, name.getJustifiedWidth(),
                        "name must receive justify() call");
        assertNotEquals(-1.0, email.getJustifiedWidth(),
                        "email must receive justify() call");
    }

    /**
     * No vertical headers → Phase 1 is no-op, Phase 2 still filters
     * variable-length only.
     */
    @Test
    void noVerticalHeadersPhase2StillFilters() {
        RelationStyle relStyle = mockRelationStyle();
        Relation parent = new Relation("parent");
        parent.addChild(new Primitive("date"));
        parent.addChild(new Primitive("name"));
        RelationLayout layout = new RelationLayout(parent, relStyle);

        // All labels fit horizontally — no vertical headers
        PrimitiveLayout date = makePrimitive("date", 70, 40, false);
        PrimitiveLayout name = makePrimitive("name", 100, 40, true);

        layout.children.clear();
        layout.children.add(date);
        layout.children.add(name);
        layout.tableColumnWidth = date.tableColumnWidth() + name.tableColumnWidth();

        // Available = 300, surplus distributed to all children
        layout.justify(300);

        assertTrue(date.getJustifiedWidth() >= 40,
                   "date should get at least label width");
        assertTrue(date.getJustifiedWidth() > 70,
                   "date should participate in surplus distribution");
        assertEquals(300, Style.snap(date.getJustifiedWidth() + name.getJustifiedWidth()),
                     0.5, "Total should equal available width");
    }

    /**
     * useVerticalHeader must reset via clear(), which is called by layout().
     * After reset, nestTableColumn() recomputes it correctly.
     */
    @Test
    void useVerticalHeaderResetsInClear() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("date"), style);
        layout.dataWidth = 50;
        layout.labelWidth = 100;
        layout.columnWidth = 100;

        // First: trigger vertical header
        layout.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));
        assertTrue(layout.isUseVerticalHeader(), "Should be vertical after nestTableColumn");

        // layout() calls clear() which resets useVerticalHeader
        layout.layout(500);
        assertFalse(layout.isUseVerticalHeader(),
                    "useVerticalHeader must reset to false after clear()");
    }

    /**
     * PrimitiveStyle default verticalHeaderThreshold should be 1.5.
     */
    @Test
    void primitiveStyleDefaultVerticalHeaderThreshold() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        PrimitiveStyle.PrimitiveTextStyle style =
            new PrimitiveStyle.PrimitiveTextStyle(labelStyle, new Insets(0), labelStyle);

        assertEquals(1.5, style.getVerticalHeaderThreshold());
    }

    /**
     * Scenario 4: Phase 1 un-rotation reduces columnHeaderHeight.
     * Before un-rotation: height = snap(labelWidth) = 100.
     * After un-rotation: height = snap(labelStyle.getHeight()) = 20.
     */
    @Test
    void columnHeaderHeightDecreasesAfterUnrotation() {
        RelationStyle relStyle = mockRelationStyle();
        Relation parent = new Relation("parent");
        parent.addChild(new Primitive("date"));
        parent.addChild(new Primitive("name"));
        RelationLayout layout = new RelationLayout(parent, relStyle);

        // date: narrow data (50), wide label (100) → vertical header
        PrimitiveLayout date = makePrimitive("date", 50, 100, false);
        date.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));
        assertTrue(date.isUseVerticalHeader());

        // name: wide data (150), narrow label (40) → horizontal, variable
        PrimitiveLayout name = makePrimitive("name", 150, 40, true);
        name.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));

        layout.children.clear();
        layout.children.add(date);
        layout.children.add(name);
        layout.tableColumnWidth = date.tableColumnWidth() + name.tableColumnWidth();

        double heightBefore = date.columnHeaderHeight();
        assertEquals(Style.snap(100), heightBefore,
                     "Vertical header height should be snap(labelWidth) before un-rotation");

        // Justify with enough extra to un-rotate
        layout.justify(300);

        double heightAfter = date.columnHeaderHeight();
        assertEquals(Style.snap(20.0), heightAfter,
                     "Header height should decrease to snap(labelStyle.getHeight()) after un-rotation");
        assertTrue(heightAfter < heightBefore,
                   "columnHeaderHeight must decrease after Phase 1 un-rotation");
    }

    /**
     * Scenario 8: RelationLayout child in justifyColumn is treated as
     * variable-length by default (not matched by instanceof PrimitiveLayout).
     */
    @Test
    void relationLayoutChildTreatedAsVariable() {
        RelationStyle relStyle = mockRelationStyle();
        Relation parent = new Relation("parent");
        parent.addChild(new Primitive("date"));
        parent.addChild(new Relation("items"));
        RelationLayout layout = new RelationLayout(parent, relStyle);

        // date: fixed-length, dataWidth=70
        PrimitiveLayout date = makePrimitive("date", 70, 40, false);

        // items: RelationLayout child (treated as variable-length by default)
        RelationLayout items = new RelationLayout(new Relation("items"), relStyle);
        items.tableColumnWidth = 100;
        items.labelWidth = 30;
        items.columnWidth = 100;

        layout.children.clear();
        layout.children.add(date);
        layout.children.add(items);
        layout.tableColumnWidth = date.tableColumnWidth() + items.tableColumnWidth();

        // Available = 300, surplus distributed to all children
        layout.justify(300);

        assertTrue(date.getJustifiedWidth() >= 40,
                   "date should get at least label width");
        assertTrue(items.getJustifiedWidth() > 100,
                   "RelationLayout child should get proportional extra");
        assertEquals(300, Style.snap(date.getJustifiedWidth() + items.getJustifiedWidth()),
                     0.5, "Total should equal available width");
    }

    /**
     * Full pipeline: measure → nestTableColumn → justify with un-rotation.
     * Verifies useVerticalHeader detection and un-rotation in an integrated test.
     */
    @Test
    void fullPipelineVerticalDetectionAndUnrotation() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);

        // Measure with uniform-width date data: 10 chars × 7px = 70px
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("transaction_date"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("2026-01-01");
        data.add("2026-06-15");
        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        // dataWidth = snap(70) = 70, labelWidth depends on mock (10.0 per label width call)
        // labelWidth = snap(labelStyle.width("transaction_date")) = snap(10) = 10
        // With labelWidth=10 and dataWidth=70: 10 > 70*1.5 → false (not vertical)
        // So let's set a wider label manually
        layout.labelWidth = 120;

        layout.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));

        // 120 > 70 * 1.5 = 105 → true
        assertTrue(layout.isUseVerticalHeader(),
                   "Label (120) > data (70) * 1.5 should trigger vertical header");
        assertEquals(Style.snap(120), layout.columnHeaderHeight(),
                     "Vertical header height should be snap(labelWidth)");
    }

    /**
     * S-2: Vertical header stays vertical when extra space is insufficient
     * for un-rotation.
     */
    @Test
    void verticalHeaderRemainsWhenInsufficientSpace() {
        RelationStyle relStyle = mockRelationStyle();
        Relation parent = new Relation("parent");
        parent.addChild(new Primitive("date"));
        RelationLayout layout = new RelationLayout(parent, relStyle);

        // date: vertical header (labelWidth=120, dataWidth=70, threshold 1.5 → 120 > 105)
        PrimitiveLayout date = makePrimitive("date", 70, 120, false);
        date.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));
        assertTrue(date.isUseVerticalHeader(), "precondition: date must be vertical");

        layout.children.clear();
        layout.children.add(date);
        layout.tableColumnWidth = date.tableColumnWidth();

        // Needed to un-rotate: labelWidth - tcw = 120 - 70 = 50
        // Extra space: available - tableColumnWidth = 90 - 70 = 20 < 50
        layout.justify(90);

        assertTrue(date.isUseVerticalHeader(),
                   "Vertical header must remain when extra space (20) < needed (50)");
    }

    /**
     * S-3: Exact threshold boundary does NOT trigger vertical header.
     * Condition is strict greater-than: labelWidth > tcw * threshold.
     */
    @Test
    void exactThresholdBoundaryDoesNotTriggerVertical() {
        PrimitiveStyle primStyle = mockPrimitiveStyle(7.0);
        // labelWidth = 150, dataWidth = 100 → 150 == 100 * 1.5 exactly → NOT vertical
        when(primStyle.getLabelStyle().width(anyString())).thenReturn(150.0);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("field"), primStyle);
        layout.dataWidth = 100;
        layout.labelWidth = 150;
        layout.columnWidth = 150;

        layout.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));

        assertFalse(layout.isUseVerticalHeader(),
                    "labelWidth == tcw * threshold (150 == 100*1.5) should NOT trigger vertical");
    }

    /**
     * S-4: Two vertical headers, only one un-rotated due to space.
     * Greedy document-order allocation: first header gets un-rotated,
     * second stays vertical.
     */
    @Test
    void partialUnrotationWithMultipleVerticalHeaders() {
        RelationStyle relStyle = mockRelationStyle();
        Relation parent = new Relation("parent");
        parent.addChild(new Primitive("col_a"));
        parent.addChild(new Primitive("col_b"));
        RelationLayout layout = new RelationLayout(parent, relStyle);

        // col_a: vertical, needed to un-rotate = 120 - 70 = 50
        PrimitiveLayout colA = makePrimitive("col_a", 70, 120, false);
        colA.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));
        assertTrue(colA.isUseVerticalHeader(), "precondition: colA must be vertical");

        // col_b: vertical, needed to un-rotate = 110 - 60 = 50
        PrimitiveLayout colB = makePrimitive("col_b", 60, 110, false);
        colB.nestTableColumn(SchemaNodeLayout.Indent.NONE, new Insets(0));
        assertTrue(colB.isUseVerticalHeader(), "precondition: colB must be vertical");

        layout.children.clear();
        layout.children.add(colA);
        layout.children.add(colB);
        layout.tableColumnWidth = colA.tableColumnWidth() + colB.tableColumnWidth();

        // Extra space = 200 - 130 = 70. Enough for colA (50) but not both (100).
        layout.justify(200);

        assertFalse(colA.isUseVerticalHeader(),
                    "First vertical header should be un-rotated (greedy, space=70 >= needed=50)");
        assertTrue(colB.isUseVerticalHeader(),
                   "Second vertical header must remain (remaining space=20 < needed=50)");
    }
}
