// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import javafx.geometry.Insets;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;

/**
 * Evaluation tests for Kramer-avn (F2+F3): Compare layout quality with and
 * without the halfWidth guard in column set formation.
 *
 * Decision criterion (RDR-008): If no scenario shows meaningful height
 * regression (>20px AND >10%) when the guard is removed, defer both F2 and F3.
 */
class ColumnSetEvaluationTest {

    record CompressResult(int columnSetCount, double cellHeight,
                          List<Integer> fieldsPerSet) {}

    // --- Scenario 1: Catalog-like (mixed widths) ---

    @Test
    void catalogLikeAt400() {
        var guard = runCompress(catalogLikeLayout(), 400, true);
        var noGuard = runCompress(catalogLikeLayout(), 400, false);
        reportAndAssert("Catalog-like@400", guard, noGuard);
    }

    @Test
    void catalogLikeAt600() {
        var guard = runCompress(catalogLikeLayout(), 600, true);
        var noGuard = runCompress(catalogLikeLayout(), 600, false);
        reportAndAssert("Catalog-like@600", guard, noGuard);
    }

    @Test
    void catalogLikeAt800() {
        var guard = runCompress(catalogLikeLayout(), 800, true);
        var noGuard = runCompress(catalogLikeLayout(), 800, false);
        reportAndAssert("Catalog-like@800", guard, noGuard);
    }

    // --- Scenario 2: Uniform narrows (control) ---

    @Test
    void uniformNarrowsAt300() {
        var guard = runCompress(uniformNarrowLayout(), 300, true);
        var noGuard = runCompress(uniformNarrowLayout(), 300, false);
        assertEquals(guard.columnSetCount, noGuard.columnSetCount,
                     "Uniform narrows: guard should never fire");
        assertEquals(guard.cellHeight, noGuard.cellHeight, 0.01,
                     "Uniform narrows: identical height expected");
    }

    @Test
    void uniformNarrowsAt500() {
        var guard = runCompress(uniformNarrowLayout(), 500, true);
        var noGuard = runCompress(uniformNarrowLayout(), 500, false);
        assertEquals(guard.columnSetCount, noGuard.columnSetCount,
                     "Uniform narrows: guard should never fire");
        assertEquals(guard.cellHeight, noGuard.cellHeight, 0.01,
                     "Uniform narrows: identical height expected");
    }

    // --- Scenario 3: Extreme mixed (stress test) ---

    @Test
    void extremeMixedAt500() {
        var guard = runCompress(extremeMixedLayout(), 500, true);
        var noGuard = runCompress(extremeMixedLayout(), 500, false);
        reportAndAssert("ExtremeMixed@500", guard, noGuard);
    }

    @Test
    void extremeMixedAt800() {
        var guard = runCompress(extremeMixedLayout(), 800, true);
        var noGuard = runCompress(extremeMixedLayout(), 800, false);
        reportAndAssert("ExtremeMixed@800", guard, noGuard);
    }

    // --- Scenario 4: Two mediums ---

    @Test
    void twoMediumsAt500() {
        var guard = runCompress(twoMediumLayout(), 500, true);
        var noGuard = runCompress(twoMediumLayout(), 500, false);
        reportAndAssert("TwoMediums@500", guard, noGuard);
    }

    // --- Layout factories ---

    private RelationLayout catalogLikeLayout() {
        // short(40), medium-variable(180), wide-variable(300), short(15)
        return makeLayout(
            makePrimitive("courseNumber", 40),
            makePrimitive("title", 180),
            makePrimitive("description", 300),
            makePrimitive("credits", 15)
        );
    }

    private RelationLayout uniformNarrowLayout() {
        return makeLayout(
            makePrimitive("f1", 30),
            makePrimitive("f2", 30),
            makePrimitive("f3", 30),
            makePrimitive("f4", 30),
            makePrimitive("f5", 30),
            makePrimitive("f6", 30)
        );
    }

    private RelationLayout extremeMixedLayout() {
        // 1 wide (400px) + 4 narrow (30px each)
        return makeLayout(
            makePrimitive("description", 400),
            makePrimitive("f1", 30),
            makePrimitive("f2", 30),
            makePrimitive("f3", 30),
            makePrimitive("f4", 30)
        );
    }

    private RelationLayout twoMediumLayout() {
        return makeLayout(
            makePrimitive("bio", 200),
            makePrimitive("address", 200),
            makePrimitive("f1", 30),
            makePrimitive("f2", 30),
            makePrimitive("f3", 30)
        );
    }

    // --- Infrastructure ---

    private RelationLayout makeLayout(PrimitiveLayout... children) {
        RelationStyle style = mockRelationStyle();
        Relation schema = new Relation("parent");
        for (var c : children) {
            schema.addChild(new Primitive(c.getField()));
        }
        RelationLayout layout = new RelationLayout(schema, style);
        layout.children.clear();
        for (var c : children) {
            layout.children.add(c);
        }
        layout.labelWidth = 10;
        layout.averageChildCardinality = 1;
        return layout;
    }

    private CompressResult runCompress(RelationLayout layout, double width,
                                       boolean useHalfWidthGuard) {
        layout.columnSets.clear();
        layout.compress(width, useHalfWidthGuard);
        return new CompressResult(
            layout.columnSets.size(),
            layout.cellHeight,
            layout.columnSets.stream()
                .map(cs -> cs.getColumns().stream()
                    .mapToInt(c -> c.getFields().size()).sum())
                .toList()
        );
    }

    private void reportAndAssert(String label, CompressResult guard,
                                 CompressResult noGuard) {
        double delta = noGuard.cellHeight - guard.cellHeight;
        double pct = guard.cellHeight > 0
                     ? (delta / guard.cellHeight) * 100.0 : 0.0;
        String msg = String.format(
            "%s: guard={cs=%d, h=%.1f, fields=%s}, noGuard={cs=%d, h=%.1f, fields=%s}, delta=%.1fpx (%.1f%%)",
            label,
            guard.columnSetCount, guard.cellHeight, guard.fieldsPerSet,
            noGuard.columnSetCount, noGuard.cellHeight, noGuard.fieldsPerSet,
            delta, pct);

        // Log for decision analysis
        System.out.println("[EVALUATION] " + msg);

        // The test passes either way — this is measurement, not assertion.
        // Data is captured for the T3 decision gate.
        assertTrue(true, msg);
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
        when(primStyle.getMaxTablePrimitiveWidth()).thenReturn(350.0);
        when(primStyle.getVerticalHeaderThreshold()).thenReturn(1.5);
        when(primStyle.getVariableLengthThreshold()).thenReturn(2.0);

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
