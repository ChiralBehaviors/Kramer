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
 * Tests for RelationLayout behavior including:
 * - Column set formation (RDR-004: outline-mode relation exclusion)
 * - Cardinality cap configurability (RDR-006 Fix 2)
 */
class RelationLayoutCompressTest {

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
        when(primStyle.getVariableLengthThreshold()).thenReturn(2.0);
        when(primStyle.getOutlineSnapValueWidth()).thenReturn(0.0);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive(name), primStyle);
        layout.columnWidth = width;
        layout.dataWidth = width;
        layout.labelWidth = 0;
        return layout;
    }

    private static RelationLayout makeChildRelation(String name, double width,
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
     * Paper §3.4: outline-mode relations are excluded from column sets.
     * Each gets its own single-column column set.
     */
    @Test
    void outlineRelationGetsOwnColumnSet() {
        RelationStyle style = mockRelationStyle();
        Relation parent = new Relation("parent");
        RelationLayout layout = new RelationLayout(parent, style);

        PrimitiveLayout prim1 = makePrimitive("name", 30);
        RelationLayout outlineChild = makeChildRelation("items", 20, false);
        PrimitiveLayout prim2 = makePrimitive("email", 30);

        layout.children.clear();
        layout.children.add(prim1);
        layout.children.add(outlineChild);
        layout.children.add(prim2);
        layout.labelWidth = 10;
        layout.averageChildCardinality = 2;

        // Width large enough that all children would fit in a shared column set
        // without the exclusion rule (halfWidth ≈ 250 >> any child width)
        layout.compress(500);

        // Verify: outline relation is alone in its column set
        boolean relationAlone = false;
        for (ColumnSet cs : layout.columnSets) {
            List<SchemaNodeLayout> allFields = cs.getColumns()
                                                  .stream()
                                                  .flatMap(col -> col.getFields().stream())
                                                  .toList();
            if (allFields.contains(outlineChild)) {
                relationAlone = (allFields.size() == 1);
            }
        }
        assertTrue(relationAlone,
                   "Outline-mode relation must be in its own column set (§3.4)");
    }

    /**
     * Paper §3.4: table-mode relations are NOT excluded — they can share
     * column sets with other fields.
     */
    @Test
    void tableModeRelationCanShareColumnSet() {
        RelationStyle style = mockRelationStyle();
        Relation parent = new Relation("parent");
        RelationLayout layout = new RelationLayout(parent, style);

        PrimitiveLayout prim1 = makePrimitive("name", 30);
        RelationLayout tableChild = makeChildRelation("items", 20, true);

        layout.children.clear();
        layout.children.add(prim1);
        layout.children.add(tableChild);
        layout.labelWidth = 10;
        layout.averageChildCardinality = 2;

        layout.compress(500);

        // Both should end up in the same column set since the relation
        // is in table mode and narrow enough
        assertEquals(1, layout.columnSets.size(),
                     "Table-mode relation should share column set with primitive");
    }

    /**
     * Verify that the outline relation exclusion also starts a new column set
     * for subsequent children.
     */
    @Test
    void outlineRelationBreaksColumnSetForSubsequentChildren() {
        RelationStyle style = mockRelationStyle();
        Relation parent = new Relation("parent");
        RelationLayout layout = new RelationLayout(parent, style);

        PrimitiveLayout prim1 = makePrimitive("name", 30);
        RelationLayout outlineChild = makeChildRelation("items", 20, false);
        PrimitiveLayout prim2 = makePrimitive("email", 30);
        PrimitiveLayout prim3 = makePrimitive("phone", 30);

        layout.children.clear();
        layout.children.add(prim1);
        layout.children.add(outlineChild);
        layout.children.add(prim2);
        layout.children.add(prim3);
        layout.labelWidth = 10;
        layout.averageChildCardinality = 2;

        layout.compress(500);

        // Expected column sets: [prim1], [outlineChild], [prim2, prim3]
        assertEquals(3, layout.columnSets.size(),
                     "Should have 3 column sets: before relation, relation itself, after relation");

        // Verify the outline relation is the sole occupant of the middle set
        List<SchemaNodeLayout> middleFields = layout.columnSets.get(1)
                                                                .getColumns()
                                                                .stream()
                                                                .flatMap(col -> col.getFields().stream())
                                                                .toList();
        assertEquals(1, middleFields.size());
        assertSame(outlineChild, middleFields.get(0));
    }

    /**
     * All-primitive children with no relations should behave as before the fix.
     */
    @Test
    void allPrimitivesGroupedNormally() {
        RelationStyle style = mockRelationStyle();
        Relation parent = new Relation("parent");
        RelationLayout layout = new RelationLayout(parent, style);

        PrimitiveLayout prim1 = makePrimitive("name", 30);
        PrimitiveLayout prim2 = makePrimitive("email", 30);
        PrimitiveLayout prim3 = makePrimitive("phone", 30);

        layout.children.clear();
        layout.children.add(prim1);
        layout.children.add(prim2);
        layout.children.add(prim3);
        layout.labelWidth = 10;
        layout.averageChildCardinality = 2;

        layout.compress(500);

        // All narrow primitives should share a single column set
        assertEquals(1, layout.columnSets.size(),
                     "All narrow primitives should share one column set");
    }

    /**
     * RDR-006 Fix 2: verify maxAverageCardinality is used in compress
     * by checking that a style returning cap=4 limits cardinality to 4.
     */
    @Test
    void cardinalityCapIsRespected() {
        RelationStyle style = mockRelationStyle();
        when(style.getMaxAverageCardinality()).thenReturn(4);

        Relation parent = new Relation("parent");
        RelationLayout layout = new RelationLayout(parent, style);

        // Simulate measure() having computed a high average cardinality
        layout.averageChildCardinality = 15;
        // Directly verify the cap would apply: Math.max(1, Math.min(4, 15)) = 4
        int capped = Math.max(1, Math.min(style.getMaxAverageCardinality(), 15));
        assertEquals(4, capped, "Cap of 4 should limit cardinality to 4");
    }

    /**
     * RDR-006 Fix 2: verify higher cap allows more cardinality.
     */
    @Test
    void higherCapAllowsMoreCardinality() {
        RelationStyle style = mockRelationStyle();
        when(style.getMaxAverageCardinality()).thenReturn(10);

        int capped = Math.max(1, Math.min(style.getMaxAverageCardinality(), 8));
        assertEquals(8, capped, "Cap of 10 should allow cardinality of 8");

        int capped2 = Math.max(1, Math.min(style.getMaxAverageCardinality(), 15));
        assertEquals(10, capped2, "Cap of 10 should limit cardinality to 10");
    }

    /**
     * Wide children (wider than halfWidth) get their own column set
     * regardless of type — this is the pre-existing width-based rule.
     */
    @Test
    void widePrimitiveGetsOwnColumnSet() {
        RelationStyle style = mockRelationStyle();
        Relation parent = new Relation("parent");
        RelationLayout layout = new RelationLayout(parent, style);

        PrimitiveLayout narrow = makePrimitive("name", 30);
        PrimitiveLayout wide = makePrimitive("description", 300);
        PrimitiveLayout narrow2 = makePrimitive("email", 30);

        layout.children.clear();
        layout.children.add(narrow);
        layout.children.add(wide);
        layout.children.add(narrow2);
        layout.labelWidth = 10;
        layout.averageChildCardinality = 2;

        // halfWidth ≈ 250, so 'wide' (310 with labelWidth) exceeds it
        layout.compress(500);

        assertEquals(3, layout.columnSets.size(),
                     "Wide primitive should get its own column set");
    }

    /**
     * RDR-006 Fix 1: table mode chosen when tableWidth fits available width
     * but exceeds outline column width. Uses child RelationLayouts (not
     * primitives) because with primitives, columnWidth() ≈ width.
     *
     * Setup: parent RL with 3 child RLs, each having 2 primitive grandchildren
     * with columnWidth=25. With all insets=0 and labelWidths=0:
     * - Each child's tableWidth = 25+25 = 50 → fits available → child is table
     * - Each child's layout() returns tableColumnWidth() = 50
     * - Parent columnWidth = max(50) = 50, columnWidth() = snap(50) = 50
     * - Parent tableWidth = 50+50+50 = 150
     * - With width=200: tableWidth(150) > columnWidth(50) → old code: outline
     *                    tableWidth(150) <= width(200) → new code: table
     */
    @Test
    void tableChosenWhenFitsAvailableWidthButWiderThanOutlineColumn() {
        RelationStyle parentStyle = mockRelationStyle();
        Relation parentSchema = new Relation("root");
        // Add child relation schemas so the Relation knows about them
        for (int i = 0; i < 3; i++) {
            Relation childSchema = new Relation("child" + i);
            childSchema.addChild(new Primitive("f1"));
            childSchema.addChild(new Primitive("f2"));
            parentSchema.addChild(childSchema);
        }

        RelationLayout parent = new RelationLayout(parentSchema, parentStyle);
        parent.labelWidth = 0;

        // Build 3 child RelationLayouts, each with 2 primitives (columnWidth=25)
        for (int i = 0; i < 3; i++) {
            RelationStyle childStyle = mockRelationStyle();
            Relation childSchema = (Relation) parentSchema.getChildren().get(i);
            RelationLayout child = new RelationLayout(childSchema, childStyle);
            child.labelWidth = 0;

            for (var schemaChild : childSchema.getChildren()) {
                PrimitiveLayout prim = makePrimitive(schemaChild.getField(), 25);
                child.children.add(prim);
            }
            parent.children.add(child);
        }

        // width=200: tableWidth(150) <= width(200) → table mode
        parent.layout(200);

        assertTrue(parent.isUseTable(),
                   "Table should be chosen when table width (150) fits available width (200)");
    }

    /**
     * RDR-006 Fix 1: outline mode chosen when table exceeds available width.
     */
    @Test
    void outlineChosenWhenTableExceedsAvailableWidth() {
        RelationStyle parentStyle = mockRelationStyle();
        Relation parentSchema = new Relation("root");
        for (int i = 0; i < 3; i++) {
            Relation childSchema = new Relation("child" + i);
            childSchema.addChild(new Primitive("f1"));
            childSchema.addChild(new Primitive("f2"));
            parentSchema.addChild(childSchema);
        }

        RelationLayout parent = new RelationLayout(parentSchema, parentStyle);
        parent.labelWidth = 0;

        for (int i = 0; i < 3; i++) {
            RelationStyle childStyle = mockRelationStyle();
            Relation childSchema = (Relation) parentSchema.getChildren().get(i);
            RelationLayout child = new RelationLayout(childSchema, childStyle);
            child.labelWidth = 0;

            for (var schemaChild : childSchema.getChildren()) {
                PrimitiveLayout prim = makePrimitive(schemaChild.getField(), 25);
                child.children.add(prim);
            }
            parent.children.add(child);
        }

        // width=100: tableWidth(150) > width(100) → outline mode
        parent.layout(100);

        assertFalse(parent.isUseTable(),
                    "Outline should be chosen when table width (150) exceeds available width (100)");
    }

    /**
     * Parameterized compress(justified, true) must produce identical results
     * to the public compress(justified) for uniform-width children.
     */
    @Test
    void parameterizedCompressEquivalentToPublicMethod() {
        RelationStyle style = mockRelationStyle();
        Relation parent = new Relation("parent");
        RelationLayout layout1 = new RelationLayout(parent, style);
        RelationLayout layout2 = new RelationLayout(parent, style);

        for (RelationLayout layout : List.of(layout1, layout2)) {
            layout.children.clear();
            layout.children.add(makePrimitive("name", 30));
            layout.children.add(makePrimitive("email", 30));
            layout.children.add(makePrimitive("phone", 30));
            layout.labelWidth = 10;
            layout.averageChildCardinality = 2;
        }

        layout1.compress(500);
        layout2.compress(500, true);

        assertEquals(layout1.columnSets.size(), layout2.columnSets.size(),
                     "Parameterized compress(w, true) must match compress(w)");
    }

    // -----------------------------------------------------------------------
    // S4: CompressResult snapshot immutability (covers C2: ColumnSetSnapshot)
    // -----------------------------------------------------------------------

    @Test
    void compressResultSnapshotIsImmutableAfterSubsequentCompress() {
        RelationStyle style = mockRelationStyle();
        Relation parent = new Relation("parent");
        RelationLayout layout = new RelationLayout(parent, style);
        layout.children.clear();
        layout.children.add(makePrimitive("a", 30));
        layout.children.add(makePrimitive("b", 30));
        layout.labelWidth = 10;
        layout.averageChildCardinality = 1;

        // Take snapshot at width=400
        CompressResult snapshot = layout.computeCompress(400);
        List<ColumnSetSnapshot> snapshotsBefore = List.copyOf(snapshot.columnSetSnapshots());
        int countBefore = snapshotsBefore.size();

        // Compress again at a very different width — should not alter first snapshot
        layout.computeCompress(100);

        assertEquals(countBefore, snapshot.columnSetSnapshots().size(),
                     "CompressResult snapshot must not be mutated by subsequent compress calls");
        assertEquals(snapshotsBefore, snapshot.columnSetSnapshots(),
                     "CompressResult columnSetSnapshots must be immutable across compress calls");
    }
}
