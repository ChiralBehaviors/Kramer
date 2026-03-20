// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusController;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * Standalone full-pipeline layout test. Exercises the COMPLETE rendering
 * path SYNCHRONOUSLY via SchemaNodeLayout.autoLayout():
 * measure → layout → compress → buildControl → updateItem.
 * <p>
 * No AutoLayout, no Platform.runLater, no async chains. Direct synchronous
 * pipeline that can't leak state to other test classes.
 * <p>
 * FALSIFIABLE: if data values are missing from the rendered control tree,
 * these tests FAIL with a diagnostic dump.
 */
@ExtendWith(ApplicationExtension.class)
class FullPipelineLayoutTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;
    private Stage testStage;

    @Start
    void start(Stage stage) {
        this.testStage = stage;
        stage.setScene(new Scene(new Pane(), 1200, 600));
        stage.show();
    }

    // -------------------------------------------------------------------
    // Schema + Data (same as demo app)
    // -------------------------------------------------------------------

    private static Relation buildSchema() {
        Relation root = new Relation("employees");
        root.addChild(new Primitive("name"));
        root.addChild(new Primitive("role"));
        root.addChild(new Primitive("department"));
        root.addChild(new Primitive("email"));

        Relation projects = new Relation("projects");
        projects.addChild(new Primitive("project"));
        projects.addChild(new Primitive("status"));
        projects.addChild(new Primitive("hours"));
        root.addChild(projects);
        return root;
    }

    private static ObjectNode emp(String name, String role, String dept,
                                   String email, Object[]... projs) {
        ObjectNode obj = NF.objectNode();
        obj.put("name", name);
        obj.put("role", role);
        obj.put("department", dept);
        obj.put("email", email);
        ArrayNode pa = NF.arrayNode();
        for (Object[] p : projs) {
            ObjectNode po = NF.objectNode();
            po.put("project", (String) p[0]);
            po.put("status", (String) p[1]);
            po.put("hours", (int) p[2]);
            pa.add(po);
        }
        obj.set("projects", pa);
        return obj;
    }

    private static ArrayNode buildData() {
        ArrayNode data = NF.arrayNode();
        data.add(emp("Eva Johansson", "Backend Engineer", "Services",
            "eva@example.com",
            new Object[]{"Auth Service", "Active", 260},
            new Object[]{"Rate Limiter", "Complete", 55},
            new Object[]{"Monitoring", "Active", 85}));
        data.add(emp("Frank Osei", "Frontend Engineer", "Web",
            "frank@example.com",
            new Object[]{"Dashboard UI", "Active", 180},
            new Object[]{"Component Library", "Active", 95}));
        data.add(emp("Grace Liu", "Data Engineer", "Analytics",
            "grace@example.com",
            new Object[]{"ETL Pipeline", "Active", 300},
            new Object[]{"Data Warehouse", "Planning", 20}));
        data.add(emp("Hiro Tanaka", "SRE", "Infrastructure",
            "hiro@example.com",
            new Object[]{"K8s Migration", "Active", 400},
            new Object[]{"Incident Response", "Active", 120}));
        data.add(emp("Ines Garcia", "Product Manager", "Product",
            "ines@example.com",
            new Object[]{"Q2 Roadmap", "Complete", 60},
            new Object[]{"Customer Interviews", "Active", 40}));
        return data;
    }

    // First-record values guaranteed in viewport
    private static final String[] EXPECTED_VALUES = {
        "Eva Johansson", "Backend Engineer", "Services",
        "Frank Osei", "Frontend Engineer", "Web",
        "Active", "Complete"
    };

    // -------------------------------------------------------------------
    // Scene graph traversal
    // -------------------------------------------------------------------

    record LabelInfo(String text, double width) {}

    private static List<LabelInfo> collectLabels(Node root) {
        List<LabelInfo> labels = new ArrayList<>();
        collectRec(root, labels);
        return labels;
    }

    private static void collectRec(Node node, List<LabelInfo> out) {
        if (node instanceof Labeled labeled && labeled.getText() != null
                && !labeled.getText().isBlank()) {
            out.add(new LabelInfo(labeled.getText(), labeled.getWidth()));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectRec(child, out);
            }
        }
    }

    // -------------------------------------------------------------------
    // Synchronous full pipeline
    // -------------------------------------------------------------------

    /**
     * Run measure → autoLayout (synchronous) → updateItem at the given
     * width. Returns labels from the built control tree.
     */
    private List<LabelInfo> runPipeline(double width) {
        var ref = new AtomicReference<List<LabelInfo>>();
        Platform.runLater(() -> {
            try {
                Style model = new Style();
                Relation schema = buildSchema();
                RelationLayout layout = new RelationLayout(schema,
                    model.style(schema));
                ArrayNode data = buildData();

                // measure
                layout.measure(data, n -> n, model);

                // Create a focus controller rooted at a dummy pane
                Pane focusRoot = new Pane();
                var controller = new FocusController<>(focusRoot);

                // synchronous full pipeline: layout → compress →
                // calculateRootHeight → buildControl
                LayoutCell<? extends Region> control =
                    layout.autoLayout(width, 600, controller, model);

                // Put control into the scene so VirtualFlow materializes cells
                Region node = control.getNode();
                node.setMinSize(width, 600);
                node.setPrefSize(width, 600);
                node.setMaxSize(width, 600);
                Pane root = (Pane) testStage.getScene().getRoot();
                root.getChildren().setAll(node);

                // populate data
                control.updateItem(data);

                // Force layout pass for VirtualFlow cell materialization
                root.applyCss();
                root.layout();

                ref.set(collectLabels(node));
            } catch (Exception e) {
                ref.set(List.of(new LabelInfo("EXCEPTION: " + e, 0)));
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        return ref.get();
    }

    private void assertDataVisible(List<LabelInfo> labels, double width) {
        assertNotNull(labels, "Pipeline returned null at " + width);
        assertFalse(labels.isEmpty(), "No labels at " + width);
        assertTrue(labels.stream().noneMatch(l -> l.text().startsWith("EXCEPTION")),
            labels.getFirst().text());

        StringBuilder missing = new StringBuilder();
        for (String expected : EXPECTED_VALUES) {
            String prefix = expected.length() > 4
                ? expected.substring(0, 4) : expected;
            boolean found = labels.stream()
                .anyMatch(l -> l.text().contains(prefix));
            if (!found) {
                missing.append("'").append(expected).append("' ");
            }
        }

        if (!missing.isEmpty()) {
            StringBuilder dump = new StringBuilder();
            dump.append(String.format("\nAt %.0fpx — missing: %s\n", width, missing));
            dump.append("Rendered labels:\n");
            labels.stream().limit(40).forEach(l ->
                dump.append(String.format("  '%s' (w=%.0f)\n", l.text(), l.width())));
            fail(dump.toString());
        }
    }

    // -------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------

    @Test
    void tableModeWide() {
        assertDataVisible(runPipeline(1200), 1200);
    }

    @Test
    void tableModeNarrow() {
        assertDataVisible(runPipeline(800), 800);
    }

    @Test
    void outlineMode() {
        assertDataVisible(runPipeline(400), 400);
    }

    @Test
    void transitionWidth() {
        assertDataVisible(runPipeline(600), 600);
    }

    @Test
    void widthSweep() {
        for (double w = 400; w <= 1200; w += 100) {
            assertDataVisible(runPipeline(w), w);
        }
    }

    @Test
    void noZeroWidthDataLabels() {
        for (double w : new double[] {400, 800, 1200}) {
            List<LabelInfo> labels = runPipeline(w);
            List<LabelInfo> zeroWidth = labels.stream()
                .filter(l -> l.width() <= 0)
                .filter(l -> l.text().contains("Eva") || l.text().contains("Frank")
                          || l.text().contains("Dashboard"))
                .toList();
            assertTrue(zeroWidth.isEmpty(),
                String.format("At %.0fpx: zero-width data labels: %s", w, zeroWidth));
        }
    }
}
