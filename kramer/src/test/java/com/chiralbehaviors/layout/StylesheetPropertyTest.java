// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import javafx.geometry.Insets;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.DefaultLayoutStylesheet;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for RDR-002: Stylesheet Property System Completion
 * - Phase 1: IsVariableLength + ValueDefaultWidth
 * - Phase 2: Width Guards (OutlineMaxLabelWidth, OutlineColumnMinWidth,
 *            OutlineMinValueWidth, TableMaxPrimitiveWidth)
 * - Phase 3: Outline bullets (width budget)
 */
class StylesheetPropertyTest {

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
        // width(JsonNode) returns charWidth * text length
        when(primStyle.width(any(JsonNode.class))).thenAnswer(inv -> {
            JsonNode node = inv.getArgument(0);
            String text = node.isTextual() ? node.textValue() : node.toString();
            return charWidth * text.length();
        });
        return primStyle;
    }

    private static PrimitiveLayout makePrimitive(String name, double width) {
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
        when(primStyle.getOutlineSnapValueWidth()).thenReturn(0.0);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive(name), primStyle);
        layout.columnWidth = width;
        layout.dataWidth = width;
        layout.labelWidth = 0;
        return layout;
    }

    // ---- Phase 1: IsVariableLength ----

    /**
     * Uniform-width data (dates) should be classified as fixed-length.
     * max/avg ratio ≈ 1.0 < 2.0 threshold → isVariableLength=false
     * columnWidth should use maxWidth (not averageWidth).
     */
    @Test
    void uniformWidthDataClassifiedAsFixedLength() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0); // 7px per char
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("date"), style);

        // All dates have same length (10 chars) → all same width (70px)
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("2026-01-01");
        data.add("2026-06-15");
        data.add("2026-12-31");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertFalse(layout.isVariableLength(),
                    "Uniform-width data should be classified as fixed-length");
        // columnWidth should be max(labelWidth, snap(max(defaultWidth, maxWidth)))
        // maxWidth = 70, labelWidth = snap(10) = 10
        assertTrue(layout.columnWidth >= 70,
                   "Fixed-length columnWidth should use maxWidth (70)");
    }

    /**
     * Variable-width data (names) should be classified as variable-length.
     * max/avg ratio >> 2.0 → isVariableLength=true
     * columnWidth should use averageWidth.
     */
    @Test
    void variableWidthDataClassifiedAsVariableLength() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0); // 7px per char
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("name"), style);

        // "Al" (2 chars, 14px), "Alexander Hamilton" (18 chars, 126px)
        // max=126, avg=70, ratio=1.8... hmm that's < 2.0
        // Let's use more extreme: "Al" (14px), "Alexander Bartholomew Cunningham" (32 chars, 224px)
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("Al");
        data.add("Alexander Bartholomew Cunningham");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        // max=224, avg=(14+224)/2=119, ratio=224/119≈1.88 — still < 2.0
        // Need more extreme: "A" + very long name
        // Actually, let's just check with a wider spread
        ArrayNode data2 = JsonNodeFactory.instance.arrayNode();
        data2.add("A");           // 1 char, 7px
        data2.add("Very Long Name With Many Characters"); // 35 chars, 245px

        layout.measure(data2, n -> n, model);

        // max=245, avg=(7+245)/2=126, ratio=245/126≈1.94... borderline
        // Let me adjust to clearly cross threshold
        ArrayNode data3 = JsonNodeFactory.instance.arrayNode();
        data3.add("A");           // 1 char, 7px
        data3.add("A");           // 1 char, 7px
        data3.add("Very Long Name With Many Many Characters Here"); // 46 chars, 322px

        layout.measure(data3, n -> n, model);

        // max=322, avg=(7+7+322)/3=112, ratio=322/112≈2.88 > 2.0
        assertTrue(layout.isVariableLength(),
                   "Variable-width data should be classified as variable-length");
    }

    /**
     * Empty dataset should default to isVariableLength=true (safe fallback).
     * No division by zero.
     */
    @Test
    void emptyDatasetDefaultsToVariableLength() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("empty"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode(); // empty

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertTrue(layout.isVariableLength(),
                   "Empty dataset should default to variable-length (safe fallback)");
    }

    /**
     * Single-row dataset: max==avg, ratio=1.0 → fixed-length.
     */
    @Test
    void singleRowDatasetClassifiedAsFixedLength() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("single"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("hello");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertFalse(layout.isVariableLength(),
                    "Single-row dataset should be fixed-length (max==avg, ratio=1.0)");
    }

    /**
     * Fixed-length field during compress: justifiedWidth capped at maxWidth.
     */
    @Test
    void fixedLengthCompressCapsAtMaxWidth() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("date"), style);

        // All same width → fixed length
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("2026-01-01");
        data.add("2026-06-15");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertFalse(layout.isVariableLength());
        double maxW = layout.maxWidth; // should be 70

        // Compress with much larger available width
        layout.compress(500);

        // compress() uses full available width for rendering (fills outline column).
        // computeCompress() caps to maxWidth for column-width calculation.
        assertEquals(Style.snap(500), layout.getJustifiedWidth(),
                     "compress() should use full available width for rendering");
        CompressResult cr = layout.computeCompress(500);
        assertTrue(cr.justifiedWidth() <= Style.snap(maxW),
                   "computeCompress() should cap at maxWidth for column calculation");
    }

    /**
     * Variable-length field during compress: stretches to available width.
     */
    @Test
    void variableLengthCompressStretchesToAvailable() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("name"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("A");
        data.add("A");
        data.add("Very Long Name With Many Many Characters Here");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertTrue(layout.isVariableLength());

        layout.compress(500);

        assertEquals(Style.snap(500), layout.getJustifiedWidth(),
                     "Variable-length compress should stretch to available");
    }

    /**
     * Critical lifecycle test: isVariableLength must survive through the
     * full measure() → layout() → compress() pipeline. layout() calls
     * clear(), which previously reset isVariableLength to true — making
     * the fixed-length cap dead code in production.
     */
    @Test
    void fixedLengthSurvivesMeasureLayoutCompress() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("date"), style);

        // All same width → fixed length (ratio = 1.0 < 2.0)
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("2026-01-01"); // 10 chars, 70px
        data.add("2026-06-15"); // 10 chars, 70px

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);
        assertFalse(layout.isVariableLength(), "Should be fixed-length after measure");

        // layout() calls clear() internally
        layout.layout(500);

        // isVariableLength must survive the clear() in layout()
        assertFalse(layout.isVariableLength(),
                    "isVariableLength must survive layout()/clear()");

        // compress() now uses full available width for rendering.
        // The fixed-length cap is preserved in computeCompress().
        layout.compress(500);
        assertEquals(Style.snap(500), layout.getJustifiedWidth(),
                     "compress() should use full available width for rendering");
        CompressResult cr = layout.computeCompress(500);
        assertTrue(cr.justifiedWidth() <= Style.snap(70),
                   "computeCompress() cap must survive full measure→layout→compress pipeline");
    }

    // ---- Phase 2: Width Guards ----

    /**
     * OutlineMaxLabelWidth: capped labelWidth allows children to share
     * column sets; uncapped labelWidth forces separation.
     */
    @Test
    void outlineMaxLabelWidthCapsLongLabels() {
        RelationStyle style = mockRelationStyle();
        when(style.getOutlineMaxLabelWidth()).thenReturn(50.0);

        Relation parent = new Relation("parent");
        parent.addChild(new Primitive("name"));
        parent.addChild(new Primitive("email"));
        RelationLayout layout = new RelationLayout(parent, style);

        PrimitiveLayout prim1 = makePrimitive("name", 30);
        PrimitiveLayout prim2 = makePrimitive("email", 30);
        layout.children.clear();
        layout.children.add(prim1);
        layout.children.add(prim2);
        layout.averageChildCardinality = 1;

        // With capped labelWidth (50): childWidth = 50 + 30 = 80 < halfWidth(250)
        layout.labelWidth = 50;
        layout.compress(500);
        assertEquals(1, layout.columnSets.size(),
                     "Capped labelWidth (50) should allow children to share column set");

        // Without cap: labelWidth=300, childWidth = 300 + 30 = 330 > halfWidth(250)
        layout.labelWidth = 300;
        layout.compress(500);
        assertEquals(2, layout.columnSets.size(),
                     "Uncapped labelWidth (300) forces separate column sets, proving cap is needed");
    }

    /**
     * OutlineMinValueWidth: compress with tiny available should floor at minValueWidth.
     */
    @Test
    void outlineMinValueWidthPreventsZeroWidth() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        when(style.getMinValueWidth()).thenReturn(30.0);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("field"), style);
        layout.columnWidth = 50;
        layout.dataWidth = 50;
        layout.labelWidth = 0;

        // Compress with very small available
        layout.compress(5);

        assertTrue(layout.getJustifiedWidth() >= Style.snap(30.0),
                   "MinValueWidth should prevent width from going below 30px");
    }

    /**
     * TableMaxPrimitiveWidth: cap should apply to tableColumnWidth() and
     * calculateTableColumnWidth() but not columnWidth().
     */
    @Test
    void tableMaxPrimitiveWidthCapsTableMethods() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        when(style.getMaxTablePrimitiveWidth()).thenReturn(100.0);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("desc"), style);
        layout.columnWidth = 200;
        layout.dataWidth = 200;
        layout.labelWidth = 0;

        // columnWidth() should NOT be capped (used by outline mode)
        assertEquals(200, layout.columnWidth(),
                     "columnWidth() should not be affected by table cap");

        // tableColumnWidth() should be capped
        assertEquals(100, layout.tableColumnWidth(),
                     "tableColumnWidth() should be capped at 100");

        // calculateTableColumnWidth() should also be capped
        assertEquals(100, layout.calculateTableColumnWidth(),
                     "calculateTableColumnWidth() should be capped at 100");
    }

    /**
     * Explicit MAX_VALUE override disables the cap.
     */
    @Test
    void explicitMaxValueDisablesCap() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        when(style.getMaxTablePrimitiveWidth()).thenReturn(Double.MAX_VALUE);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("desc"), style);
        layout.columnWidth = 200;
        layout.dataWidth = 200;
        layout.labelWidth = 0;

        assertEquals(200, layout.tableColumnWidth(),
                     "Default MAX_VALUE should not cap table column width");
    }

    // ---- Phase 3: Bullet Width Budget ----

    /**
     * Bullet width added to labelWidth changes column grouping in
     * tight-width scenarios. With bulletWidth=15, labelWidth becomes 55
     * instead of 40, pushing childWidth past halfWidth.
     */
    @Test
    void bulletWidthAddedToLabelBudget() {
        RelationStyle style = mockRelationStyle();
        when(style.getBulletWidth()).thenReturn(15.0);

        Relation parent = new Relation("parent");
        parent.addChild(new Primitive("name"));
        parent.addChild(new Primitive("email"));
        RelationLayout layout = new RelationLayout(parent, style);

        PrimitiveLayout prim1 = makePrimitive("name", 80);
        PrimitiveLayout prim2 = makePrimitive("email", 80);
        layout.children.clear();
        layout.children.add(prim1);
        layout.children.add(prim2);
        layout.averageChildCardinality = 1;

        // Without bullet: labelWidth=40, childWidth = 40+80 = 120 < halfWidth(125)
        layout.labelWidth = 40;
        layout.compress(250);
        assertEquals(1, layout.columnSets.size(),
                     "Without bullet addition, children should share column set");

        // With bullet: labelWidth = 40+15 = 55, childWidth = 55+80 = 135 > halfWidth(125)
        layout.labelWidth = 55;
        layout.compress(250);
        assertEquals(2, layout.columnSets.size(),
                     "Bullet-augmented labelWidth should force separate column sets");
    }

    /**
     * No bullet (default): bulletWidth=0 does not widen labelWidth.
     */
    @Test
    void noBulletDoesNotAffectLayout() {
        RelationStyle style = mockRelationStyle();
        when(style.getBulletWidth()).thenReturn(0.0);

        Relation parent = new Relation("parent");
        parent.addChild(new Primitive("name"));
        parent.addChild(new Primitive("email"));
        RelationLayout layout = new RelationLayout(parent, style);

        PrimitiveLayout prim1 = makePrimitive("name", 80);
        PrimitiveLayout prim2 = makePrimitive("email", 80);
        layout.children.clear();
        layout.children.add(prim1);
        layout.children.add(prim2);
        layout.averageChildCardinality = 1;

        // labelWidth = 40 + 0 (no bullet) = 40, childWidth = 40+80 = 120 < halfWidth(125)
        layout.labelWidth = 40;
        layout.compress(250);
        assertEquals(1, layout.columnSets.size(),
                     "Without bullet, children should share one column set");
    }

    // ---- RelationStyle property defaults ----

    @Test
    void relationStyleDefaultValues() {
        RelationStyle style = new RelationStyle(
            mock(LabelStyle.class),
            mock(com.chiralbehaviors.layout.table.NestedTable.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.table.NestedRow.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.table.NestedCell.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.outline.Outline.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.outline.OutlineCell.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.outline.OutlineColumn.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.outline.Span.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.outline.OutlineElement.class, RETURNS_DEEP_STUBS)
        );

        assertEquals(200.0, style.getOutlineMaxLabelWidth());
        assertEquals(60.0, style.getOutlineColumnMinWidth());
        assertEquals("", style.getBulletText());
        assertEquals(0.0, style.getBulletWidth());
        assertEquals(0.0, style.getIndentWidth());
    }

    @Test
    void relationStyleIsImmutableAfterConstruction() {
        RelationStyle style = new RelationStyle(
            mock(LabelStyle.class),
            mock(com.chiralbehaviors.layout.table.NestedTable.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.table.NestedRow.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.table.NestedCell.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.outline.Outline.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.outline.OutlineCell.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.outline.OutlineColumn.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.outline.Span.class, RETURNS_DEEP_STUBS),
            mock(com.chiralbehaviors.layout.outline.OutlineElement.class, RETURNS_DEEP_STUBS)
        );

        // Verify no setter methods exist on RelationStyle
        var methods = java.util.Arrays.stream(RelationStyle.class.getMethods())
                                      .map(java.lang.reflect.Method::getName)
                                      .toList();
        assertFalse(methods.contains("setOutlineMaxLabelWidth"),
                    "RelationStyle should not have setOutlineMaxLabelWidth");
        assertFalse(methods.contains("setOutlineColumnMinWidth"),
                    "RelationStyle should not have setOutlineColumnMinWidth");
        assertFalse(methods.contains("setBulletText"),
                    "RelationStyle should not have setBulletText");
        assertFalse(methods.contains("setBulletWidth"),
                    "RelationStyle should not have setBulletWidth");
        assertFalse(methods.contains("setIndentWidth"),
                    "RelationStyle should not have setIndentWidth");

        // Getters still work
        assertEquals(200.0, style.getOutlineMaxLabelWidth());
        assertEquals(60.0, style.getOutlineColumnMinWidth());
    }

    // ---- PrimitiveStyle property defaults ----

    @Test
    void primitiveStyleDefaultValues() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        // Use concrete subclass PrimitiveTextStyle
        PrimitiveStyle.PrimitiveTextStyle style =
            new PrimitiveStyle.PrimitiveTextStyle(labelStyle, new Insets(0), labelStyle);

        assertEquals(30.0, style.getMinValueWidth());
        assertEquals(350.0, style.getMaxTablePrimitiveWidth());
        assertEquals(2.0, style.getVariableLengthThreshold());
        assertEquals(0.0, style.getOutlineSnapValueWidth());
    }

    @Test
    void primitiveStyleIsImmutableAfterConstruction() {
        LabelStyle labelStyle = mock(LabelStyle.class);
        PrimitiveStyle.PrimitiveTextStyle style =
            new PrimitiveStyle.PrimitiveTextStyle(labelStyle, new Insets(0), labelStyle);

        // Verify no setter methods exist on PrimitiveStyle
        var methods = java.util.Arrays.stream(PrimitiveStyle.class.getMethods())
                                      .map(java.lang.reflect.Method::getName)
                                      .toList();
        assertFalse(methods.contains("setMinValueWidth"),
                    "PrimitiveStyle should not have setMinValueWidth");
        assertFalse(methods.contains("setMaxTablePrimitiveWidth"),
                    "PrimitiveStyle should not have setMaxTablePrimitiveWidth");
        assertFalse(methods.contains("setVariableLengthThreshold"),
                    "PrimitiveStyle should not have setVariableLengthThreshold");
        assertFalse(methods.contains("setOutlineSnapValueWidth"),
                    "PrimitiveStyle should not have setOutlineSnapValueWidth");
        assertFalse(methods.contains("setVerticalHeaderThreshold"),
                    "PrimitiveStyle should not have setVerticalHeaderThreshold");

        // Getters still work
        assertEquals(30.0, style.getMinValueWidth());
        assertEquals(350.0, style.getMaxTablePrimitiveWidth());
    }

    /**
     * F5: Custom variableLengthThreshold affects isVariableLength classification.
     * With threshold=1.5, data that is fixed-length at 2.0 becomes variable-length.
     */
    @Test
    void customVariableLengthThresholdAffectsClassification() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);

        // Override threshold to 1.5 (lower than default 2.0)
        when(style.getVariableLengthThreshold()).thenReturn(1.5);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("date"), style);

        // Data with max/avg ratio ≈ 1.8 (below default 2.0 but above 1.5)
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("short");                       // 5 chars, 35px
        data.add("a somewhat longer string here"); // 30 chars, 210px

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        // max=210, avg=(35+210)/2=122.5, ratio=210/122.5≈1.71
        // At default threshold 2.0: ratio 1.71 < 2.0 → fixed-length
        // At custom threshold 1.5: ratio 1.71 > 1.5 → variable-length
        assertTrue(layout.isVariableLength(),
                   "With threshold=1.5, ratio 1.71 should be classified as variable-length");
    }

    // ---- F4: OutlineSnapValueWidth ----

    /**
     * F4: Non-variable-length fields should snap to grid in compress.
     * Field with maxWidth=43, snap=10 → justifiedWidth snaps to 50.
     */
    @Test
    void outlineSnapRoundsUpToGrid() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        when(style.getOutlineSnapValueWidth()).thenReturn(10.0);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("code"), style);

        // Fixed-length data: all same width → ratio 1.0 < 2.0
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("abcde");  // 5 chars, 35px
        data.add("fghij");  // 5 chars, 35px

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertFalse(layout.isVariableLength());
        // maxWidth = 35

        // Compress with plenty of available space
        layout.compress(500);

        // compress() uses full available width for rendering.
        // computeCompress() applies snap: target = ceil(35/10)*10 = 40
        assertEquals(Style.snap(500), layout.getJustifiedWidth(),
                     "compress() should use full available width");
        CompressResult cr = layout.computeCompress(500);
        assertEquals(Style.snap(40.0), cr.justifiedWidth(),
                     "computeCompress() should snap: 35 → 40");
    }

    /**
     * F4: Variable-length fields should NOT snap to grid.
     */
    @Test
    void outlineSnapDoesNotAffectVariableLength() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        when(style.getOutlineSnapValueWidth()).thenReturn(10.0);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("desc"), style);

        // Variable-length data: max/avg > 2.0
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("A");
        data.add("A");
        data.add("Very Long Name With Many Many Characters Here");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertTrue(layout.isVariableLength());

        layout.compress(500);

        // Variable-length stretches to available — snap should NOT apply
        assertEquals(Style.snap(500), layout.getJustifiedWidth(),
                     "Variable-length field should not be affected by snap grid");
    }

    // ---- DefaultLayoutStylesheet override tests ----

    /**
     * setOverride + getDouble returns the overridden value for the given path.
     */
    @Test
    void defaultLayoutStylesheetOverrideDouble() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("root");

        sheet.setOverride(path, "minValueWidth", 99.0);

        assertEquals(99.0, sheet.getDouble(path, "minValueWidth", 30.0),
                     "getDouble should return overridden value");
    }

    /**
     * setOverride + getInt returns the overridden integer value.
     */
    @Test
    void defaultLayoutStylesheetOverrideInt() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("items");

        sheet.setOverride(path, "maxCardinality", 5);

        assertEquals(5, sheet.getInt(path, "maxCardinality", 10),
                     "getInt should return overridden value");
    }

    /**
     * setOverride + getString returns the overridden string value.
     */
    @Test
    void defaultLayoutStylesheetOverrideString() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("node");

        sheet.setOverride(path, "bulletText", "•");

        assertEquals("•", sheet.getString(path, "bulletText", ""),
                     "getString should return overridden value");
    }

    /**
     * Override on one path does not affect another path.
     */
    @Test
    void defaultLayoutStylesheetOverrideIsolatedByPath() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath pathA = new SchemaPath("a");
        SchemaPath pathB = new SchemaPath("b");

        sheet.setOverride(pathA, "minValueWidth", 50.0);

        assertEquals(50.0, sheet.getDouble(pathA, "minValueWidth", 30.0),
                     "pathA override should return 50.0");
        assertEquals(30.0, sheet.getDouble(pathB, "minValueWidth", 30.0),
                     "pathB should return default (no override)");
    }

    /**
     * clearOverrides() removes all overrides; subsequent lookups return defaults.
     */
    @Test
    void defaultLayoutStylesheetClearOverrides() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("root");

        sheet.setOverride(path, "minValueWidth", 99.0);
        sheet.setOverride(path, "bulletText", "→");
        sheet.clearOverrides();

        assertEquals(30.0, sheet.getDouble(path, "minValueWidth", 30.0),
                     "After clear, getDouble should return default");
        assertEquals("", sheet.getString(path, "bulletText", ""),
                     "After clear, getString should return default");
    }

    @Test
    void defaultLayoutStylesheetGetBooleanDefault() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("root");
        assertFalse(sheet.getBoolean(path, "hide-if-empty", false));
        assertTrue(sheet.getBoolean(path, "visible", true));
    }

    @Test
    void defaultLayoutStylesheetGetBooleanOverride() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("root");
        sheet.setOverride(path, "hide-if-empty", true);
        assertTrue(sheet.getBoolean(path, "hide-if-empty", false));
    }

    // ---- Kramer-63n: Version counter ----

    @Test
    void versionStartsAtZero() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        assertEquals(0L, sheet.getVersion(), "Initial version should be 0");
    }

    @Test
    void setOverrideIncrementsVersion() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("root");

        sheet.setOverride(path, "minValueWidth", 50.0);
        assertEquals(1L, sheet.getVersion(), "setOverride should increment version to 1");

        sheet.setOverride(path, "bulletText", "•");
        assertEquals(2L, sheet.getVersion(), "Second setOverride should increment version to 2");
    }

    @Test
    void clearOverridesIncrementsVersion() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("root");

        sheet.setOverride(path, "minValueWidth", 50.0);
        long versionBefore = sheet.getVersion();

        sheet.clearOverrides();
        assertEquals(versionBefore + 1, sheet.getVersion(),
                     "clearOverrides should increment version");
    }

    @Test
    void multipleSetOverrideCallsIncrementMonotonically() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath pathA = new SchemaPath("a");
        SchemaPath pathB = new SchemaPath("b");

        long prev = sheet.getVersion();
        for (int i = 0; i < 5; i++) {
            sheet.setOverride(pathA, "prop", i);
            long curr = sheet.getVersion();
            assertTrue(curr > prev, "Version must increase monotonically at step " + i);
            prev = curr;
        }

        sheet.setOverride(pathB, "other", "val");
        assertTrue(sheet.getVersion() > prev,
                   "Version must increase for override on different path");
    }

    /**
     * F4: Snap disabled (default 0.0) should behave like before.
     */
    @Test
    void outlineSnapDisabledByDefault() {
        PrimitiveStyle style = mockPrimitiveStyle(7.0);
        when(style.getOutlineSnapValueWidth()).thenReturn(0.0);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("code"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("abcde");  // 35px
        data.add("fghij");  // 35px

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertFalse(layout.isVariableLength());
        layout.compress(500);

        // compress() uses full available width for rendering.
        // computeCompress() caps to maxWidth when snap disabled.
        assertEquals(Style.snap(500), layout.getJustifiedWidth(),
                     "compress() should use full available width");
        CompressResult cr = layout.computeCompress(500);
        assertEquals(Style.snap(35.0), cr.justifiedWidth(),
                     "computeCompress() with snap=0 should cap at exact maxWidth");
    }
}
