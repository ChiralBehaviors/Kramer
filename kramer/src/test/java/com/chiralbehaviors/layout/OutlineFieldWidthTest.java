// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Headless unit tests for outline mode field widths.
 *
 * <p>FALSIFIABLE: these tests MUST fail if the outline gives data values
 * zero or negative width. Uses package-private access to columnSets to
 * inspect actual column widths after compress().
 */
class OutlineFieldWidthTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    private Relation      schema;
    private RelationStyle relStyle;
    private Style         model;

    @BeforeEach
    void setUp() {
        schema = new Relation("employees");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("role"));
        schema.addChild(new Primitive("department"));
        schema.addChild(new Primitive("email"));

        Relation projects = new Relation("projects");
        projects.addChild(new Primitive("project"));
        projects.addChild(new Primitive("status"));
        projects.addChild(new Primitive("hours"));
        schema.addChild(projects);

        relStyle = TestLayouts.mockRelationStyle();
        // Realistic insets matching actual CSS measurement
        when(relStyle.getElementHorizontalInset()).thenReturn(8.0);
        when(relStyle.getColumnHorizontalInset()).thenReturn(8.0);
        when(relStyle.getOutlineCellHorizontalInset()).thenReturn(8.0);
        when(relStyle.getSpanHorizontalInset()).thenReturn(4.0);
        when(relStyle.getOutlineHorizontalInset()).thenReturn(4.0);
        when(relStyle.getOutlineColumnMinWidth()).thenReturn(60.0);
        // Realistic label widths: ~7px per character
        when(relStyle.getLabelStyle().width(anyString())).thenAnswer(inv -> {
            String text = inv.getArgument(0);
            return text.length() * 7.0;
        });
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        // Realistic label widths for primitives too
        when(primStyle.getLabelStyle().width(anyString())).thenAnswer(inv -> {
            String text = inv.getArgument(0);
            return text.length() * 7.0;
        });

        model = mock(Style.class);
        for (SchemaNode child : schema.getChildren()) {
            if (child instanceof Primitive p) {
                PrimitiveLayout pl = new PrimitiveLayout(p, primStyle);
                when(model.layout(p)).thenReturn(pl);
            } else if (child instanceof Relation r) {
                RelationLayout rl = new RelationLayout(r, relStyle);
                when(model.layout(r)).thenReturn(rl);
                for (SchemaNode gc : r.getChildren()) {
                    if (gc instanceof Primitive p) {
                        PrimitiveLayout gpl = new PrimitiveLayout(p, primStyle);
                        when(model.layout(p)).thenReturn(gpl);
                    }
                }
            }
        }
        when(model.layout(any(SchemaNode.class))).thenAnswer(inv -> {
            SchemaNode n = inv.getArgument(0);
            if (n instanceof Primitive p) return model.layout(p);
            return model.layout((Relation) n);
        });
    }

    private ArrayNode buildData() {
        ArrayNode data = NF.arrayNode();
        for (String[] emp : new String[][] {
            {"Frank Thompson", "Frontend Developer", "Web Engineering", "frank@example.com"},
            {"Grace Liu", "Data Engineer", "Analytics", "grace@example.com"},
            {"Hiroshi Tanaka", "SRE Lead", "Infrastructure", "hiroshi@example.com"}
        }) {
            ObjectNode row = NF.objectNode();
            row.put("name", emp[0]);
            row.put("role", emp[1]);
            row.put("department", emp[2]);
            row.put("email", emp[3]);
            ArrayNode projs = NF.arrayNode();
            ObjectNode proj = NF.objectNode();
            proj.put("project", "Dashboard UI");
            proj.put("status", "Active");
            proj.put("hours", 180);
            projs.add(proj);
            row.set("projects", projs);
            data.add(row);
        }
        return data;
    }

    /**
     * After measure → layout (outline mode) → compress, every column must
     * give field values positive width. fieldWidth = columnWidth - labelWidth
     * - insets. If fieldWidth ≤ 0, data is invisible.
     */
    @Test
    void outlineFieldWidthIsPositive() {
        RelationLayout layout = new RelationLayout(schema, relStyle);
        layout.measure(buildData(), n -> n, model);

        // Force outline by using a narrow width
        double width = 300.0;
        layout.layout(width);
        assertFalse(layout.isUseTable(), "Should be outline at " + width);
        // compress() builds column sets — called by AutoLayout.autoLayout()
        layout.compress(width, true);

        double lw = layout.getLabelWidth();
        double elemInset = relStyle.getElementHorizontalInset();
        double colInset = relStyle.getColumnHorizontalInset();

        StringBuilder failures = new StringBuilder();
        for (int i = 0; i < layout.columnSets.size(); i++) {
            ColumnSet cs = layout.columnSets.get(i);
            for (Column col : cs.getColumns()) {
                double colWidth = col.getWidth();
                double fieldWidth = colWidth - lw - elemInset - colInset;
                if (fieldWidth < 0) {
                    failures.append(String.format(
                        "cs[%d]: colWidth=%.1f lw=%.1f fieldWidth=%.1f; ",
                        i, colWidth, lw, fieldWidth));
                }
            }
        }

        // Diagnostic: dump all column widths
        StringBuilder diag = new StringBuilder("labelWidth=" + lw + " ");
        for (int j = 0; j < layout.columnSets.size(); j++) {
            ColumnSet cs2 = layout.columnSets.get(j);
            for (Column col2 : cs2.getColumns()) {
                diag.append(String.format("cs[%d].colW=%.1f fw=%.1f; ",
                    j, col2.getWidth(),
                    col2.getWidth() - lw - elemInset - colInset));
            }
        }

        assertTrue(failures.isEmpty(),
            "All outline columns must have positive fieldWidth:\n" + failures
            + "\nDiag: " + diag);
    }

    /**
     * Sweep across widths: in outline mode, fieldWidth must be positive.
     */
    @Test
    void outlineFieldWidthPositiveAcrossWidths() {
        RelationLayout layout = new RelationLayout(schema, relStyle);
        layout.measure(buildData(), n -> n, model);

        double elemInset = relStyle.getElementHorizontalInset();
        double colInset = relStyle.getColumnHorizontalInset();
        StringBuilder failures = new StringBuilder();

        for (double w = 200; w <= 800; w += 25) {
            layout.layout(w);
            if (layout.isUseTable()) continue;
            layout.compress(w, true);

            double lw = layout.getLabelWidth();
            for (int i = 0; i < layout.columnSets.size(); i++) {
                ColumnSet cs = layout.columnSets.get(i);
                for (Column col : cs.getColumns()) {
                    double colWidth = col.getWidth();
                    double fieldWidth = colWidth - lw - elemInset - colInset;
                    if (fieldWidth < 0) {
                        failures.append(String.format(
                            "w=%.0f cs[%d]: colWidth=%.1f lw=%.1f fw=%.1f; ",
                            w, i, colWidth, lw, fieldWidth));
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(),
            "Outline fieldWidth must be positive at all widths:\n"
            + failures.toString().replace("; ", ";\n"));
    }

    /**
     * The label width used in outline mode must not exceed the column width.
     * This is the ROOT CAUSE: if labelWidth >= columnWidth, data gets 0 space.
     */
    @Test
    void labelWidthDoesNotExceedColumnWidth() {
        RelationLayout layout = new RelationLayout(schema, relStyle);
        layout.measure(buildData(), n -> n, model);
        layout.layout(300.0);
        if (layout.isUseTable()) return;
        layout.compress(300.0, true);

        double lw = layout.getLabelWidth();
        for (ColumnSet cs : layout.columnSets) {
            for (Column col : cs.getColumns()) {
                assertTrue(col.getWidth() > lw,
                    String.format("Column width %.1f must exceed label width %.1f",
                        col.getWidth(), lw));
            }
        }
    }
}
