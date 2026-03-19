// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Tests for width justification fix (Kramer-on4g): nested relations must not
 * starve sibling primitives.
 */
@ExtendWith(ApplicationExtension.class)
class WidthJustificationTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    @Start
    void start(Stage stage) {
        stage.setScene(new Scene(new Pane(), 800, 600));
        stage.show();
    }

    private Relation buildSchema() {
        Relation schema = new Relation("employees");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("role"));
        schema.addChild(new Primitive("department"));
        schema.addChild(new Primitive("email"));

        Relation projects = new Relation("projects");
        projects.addChild(new Primitive("project"));
        projects.addChild(new Primitive("status"));
        projects.addChild(new Primitive("hours"));
        schema.addChild(projects);
        return schema;
    }

    private ObjectNode row(String name, String role, String dept, String email,
                            Object[][] projects) {
        ObjectNode obj = NF.objectNode();
        obj.put("name", name);
        obj.put("role", role);
        obj.put("department", dept);
        obj.put("email", email);
        ArrayNode projArr = NF.arrayNode();
        for (Object[] p : projects) {
            ObjectNode proj = NF.objectNode();
            proj.put("project", (String) p[0]);
            proj.put("status", (String) p[1]);
            proj.put("hours", (int) p[2]);
            projArr.add(proj);
        }
        obj.set("projects", projArr);
        return obj;
    }

    private ArrayNode buildData() {
        ArrayNode data = NF.arrayNode();
        data.add(row("Frank", "Frontend Dev", "Web", "frank@example.com",
            new Object[][] {
                {"Dashboard UI", "Active", 180},
                {"Component Library", "Active", 95},
                {"A11y Audit", "Planning", 0}
            }));
        data.add(row("Grace", "Data Analyst", "Analytics", "grace@example.com",
            new Object[][] {
                {"ETL Pipeline", "Active", 300},
                {"Data Warehouse", "Planning", 20}
            }));
        data.add(row("Hiroshi", "SRE Lead", "Infrastructure", "hiroshi@example.com",
            new Object[][] {
                {"K8s Migration", "Active", 400},
                {"Incident Response", "Active", 120}
            }));
        return data;
    }

    /**
     * Run the full pipeline: measure → layout → justify at given width.
     */
    private RelationLayout measureLayoutJustify(Relation schema, Style model,
                                                 ArrayNode data, double width) {
        RelationLayout layout = new RelationLayout(schema, model.style(schema));
        layout.measure(data, n -> n, model);
        layout.layout(width);
        layout.justify(width);
        return layout;
    }

    /**
     * Core invariant: after justification, no primitive column should
     * be narrower than its label width.
     */
    @Test
    void primitiveColumnsGetAtLeastLabelWidth() {
        var ref = new AtomicReference<String>();
        Platform.runLater(() -> {
            try {
                Relation schema = buildSchema();
                Style model = new Style();
                RelationLayout layout = measureLayoutJustify(
                    schema, model, buildData(), 2000.0);

                StringBuilder failures = new StringBuilder();
                for (SchemaNodeLayout child : layout.getChildren()) {
                    double justified = child.getJustifiedWidth();
                    double labelW = child.getLabelWidth();
                    if (justified < labelW) {
                        failures.append(String.format(
                            "'%s' justified=%.1f < label=%.1f; ",
                            child.getField(), justified, labelW));
                    }
                }
                ref.set(failures.toString());
            } catch (Exception e) {
                ref.set("EXCEPTION: " + e.getMessage());
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(ref.get(), "Test should have run");
        assertTrue(ref.get().isEmpty(),
            "All children must get >= labelWidth: " + ref.get());
    }

    /**
     * 4 primitives should get at least 20% of total width collectively.
     */
    @Test
    void nestedRelationDoesNotDominateWidth() {
        var ref = new AtomicReference<double[]>();
        Platform.runLater(() -> {
            try {
                Relation schema = buildSchema();
                Style model = new Style();
                RelationLayout layout = measureLayoutJustify(
                    schema, model, buildData(), 2000.0);

                double totalPrimitive = 0, relationW = 0;
                for (SchemaNodeLayout child : layout.getChildren()) {
                    if (child instanceof RelationLayout) {
                        relationW = child.getJustifiedWidth();
                    } else {
                        totalPrimitive += child.getJustifiedWidth();
                    }
                }
                ref.set(new double[] {totalPrimitive, relationW});
            } catch (Exception e) {
                ref.set(new double[] {-1, -1});
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        double[] widths = ref.get();
        assertNotNull(widths, "Test should have run");
        assertTrue(widths[0] > 0, "Primitives should have positive width, got " + widths[0]);
        double primitiveRatio = widths[0] / (widths[0] + widths[1]);
        assertTrue(primitiveRatio >= 0.20,
            String.format("4 primitives should get >= 20%% of width, got %.1f%% (prim=%.0f, rel=%.0f)",
                primitiveRatio * 100, widths[0], widths[1]));
    }

    /**
     * At narrow widths, the layout MUST transition to outline mode.
     * Table mode with 7+ truncated columns is unreadable.
     */
    @Test
    void narrowWidthTriggersOutlineMode() {
        var ref = new AtomicReference<Boolean>();
        Platform.runLater(() -> {
            try {
                Relation schema = buildSchema();
                Style model = new Style();
                RelationLayout layout = new RelationLayout(schema, model.style(schema));
                layout.measure(buildData(), n -> n, model);
                // 600px should trigger outline — not enough for 7 readable columns
                layout.layout(600.0);
                ref.set(layout.isUseTable());
            } catch (Exception e) {
                ref.set(null);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(ref.get(), "Test should have run");
        assertFalse(ref.get(), "At 600px with 7 columns, layout should use outline, not table");
    }

    /**
     * At wide widths, table mode should be chosen.
     */
    @Test
    void wideWidthUsesTableMode() {
        var ref = new AtomicReference<Boolean>();
        Platform.runLater(() -> {
            try {
                Relation schema = buildSchema();
                Style model = new Style();
                RelationLayout layout = new RelationLayout(schema, model.style(schema));
                layout.measure(buildData(), n -> n, model);
                // 1200px should be wide enough for table mode
                layout.layout(1200.0);
                ref.set(layout.isUseTable());
            } catch (Exception e) {
                ref.set(null);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(ref.get(), "Test should have run");
        assertTrue(ref.get(), "At 1200px, layout should use table mode");
    }

    /**
     * Every child should get a positive width after justification.
     */
    @Test
    void allChildrenGetPositiveWidth() {
        var ref = new AtomicReference<String>();
        Platform.runLater(() -> {
            try {
                Relation schema = buildSchema();
                Style model = new Style();
                ArrayNode data = NF.arrayNode();
                data.add(row("A", "B", "C", "D",
                    new Object[][] {{"X", "Y", 1}}));
                RelationLayout layout = measureLayoutJustify(
                    schema, model, data, 2000.0);

                StringBuilder failures = new StringBuilder();
                for (SchemaNodeLayout child : layout.getChildren()) {
                    if (child.getJustifiedWidth() <= 0) {
                        failures.append(child.getField()).append(" ");
                    }
                }
                ref.set(failures.toString());
            } catch (Exception e) {
                ref.set("EXCEPTION: " + e.getMessage());
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(ref.get(), "Test should have run");
        assertTrue(ref.get().isEmpty(),
            "All children should have positive width: " + ref.get());
    }
}
