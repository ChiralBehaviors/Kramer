// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

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
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

/**
 * Falsifiable test: verifies that DATA VALUES are actually rendered in the
 * scene graph with non-zero width. A layout that shows labels but empty
 * value cells MUST fail this test.
 */
@ExtendWith(ApplicationExtension.class)
class OutlineDataVisibilityTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;
    private Stage testStage;

    @Start
    void start(Stage stage) {
        this.testStage = stage;
        stage.setScene(new Scene(new AnchorPane(), 800, 600));
        stage.show();
    }

    private Relation buildNestedSchema() {
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

    private ArrayNode buildData() {
        ArrayNode data = NF.arrayNode();
        ObjectNode emp = NF.objectNode();
        emp.put("name", "Frank Thompson");
        emp.put("role", "Frontend Developer");
        emp.put("department", "Web Engineering");
        emp.put("email", "frank@example.com");
        ArrayNode projects = NF.arrayNode();
        ObjectNode proj = NF.objectNode();
        proj.put("project", "Dashboard UI");
        proj.put("status", "Active");
        proj.put("hours", 180);
        projects.add(proj);
        emp.set("projects", projects);
        data.add(emp);
        return data;
    }

    /**
     * Set up AutoLayout at given width, run full pipeline (measure → autoLayout
     * with solver), wait for rendering, then collect all visible label texts
     * with their rendered widths.
     */
    private record LabelInfo(String text, double width) {}

    private List<LabelInfo> renderAndCollect(double layoutWidth, double layoutHeight)
            throws Exception {
        var ref = new AtomicReference<List<LabelInfo>>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                Relation schema = buildNestedSchema();
                Style model = new Style();
                AutoLayout layout = new AutoLayout(schema, model);
                AnchorPane.setTopAnchor(layout, 0.0);
                AnchorPane.setLeftAnchor(layout, 0.0);
                AnchorPane.setBottomAnchor(layout, 0.0);
                AnchorPane.setRightAnchor(layout, 0.0);

                AnchorPane root = (AnchorPane) testStage.getScene().getRoot();
                root.getChildren().setAll(layout);
                testStage.setWidth(layoutWidth);
                testStage.setHeight(layoutHeight);

                JsonNode data = buildData();
                layout.measure(data);
                layout.updateItem(data);
                // autoLayout() posts to Platform.runLater internally, so we
                // schedule our collection AFTER it via another runLater
                layout.autoLayout();

                Platform.runLater(() -> {
                    // One more runLater to ensure VirtualFlow has materialized cells
                    Platform.runLater(() -> {
                        root.applyCss();
                        root.layout();
                        List<LabelInfo> labels = new ArrayList<>();
                        collectLabels(layout, labels);
                        ref.set(labels);
                        latch.countDown();
                    });
                });
            } catch (Exception e) {
                ref.set(List.of(new LabelInfo("EXCEPTION: " + e.getMessage(), 0)));
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Layout timed out");
        return ref.get();
    }

    private void collectLabels(Node node, List<LabelInfo> out) {
        if (node instanceof Labeled labeled && labeled.getText() != null
                && !labeled.getText().isBlank()) {
            out.add(new LabelInfo(labeled.getText(), labeled.getWidth()));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectLabels(child, out);
            }
        }
    }

    // -------------------------------------------------------------------
    // FALSIFIABLE TESTS: if data is invisible, these MUST fail
    // -------------------------------------------------------------------

    /**
     * At outline-mode width (narrow), data values must appear for each
     * primitive field — not just labels.
     */
    @Test
    void outlineModeShowsDataValues() throws Exception {
        List<LabelInfo> labels = renderAndCollect(500, 600);

        assertFalse(labels.isEmpty(), "Scene must contain visible labels");

        // These are data VALUES (not field names). If the outline only shows
        // field labels but no data, this test fails.
        String[] dataValues = {"Frank", "Frontend", "Web", "frank@"};
        StringBuilder missing = new StringBuilder();
        for (String value : dataValues) {
            boolean found = labels.stream()
                .anyMatch(l -> l.text().contains(value));
            if (!found) {
                missing.append("'").append(value).append("' ");
            }
        }

        if (!missing.isEmpty()) {
            // Dump all found labels for debugging
            StringBuilder dump = new StringBuilder("\nAll rendered labels:\n");
            for (LabelInfo l : labels) {
                dump.append(String.format("  '%s' (w=%.0f)\n", l.text(), l.width()));
            }
            fail("Missing data values in outline mode: " + missing + dump);
        }
    }

    /**
     * At table-mode width (wide), data values must appear.
     */
    @Test
    void tableModeShowsDataValues() throws Exception {
        List<LabelInfo> labels = renderAndCollect(1400, 600);

        assertFalse(labels.isEmpty(), "Scene must contain visible labels");

        String[] dataValues = {"Frank", "Frontend", "Dashboard", "Active", "180"};
        StringBuilder missing = new StringBuilder();
        for (String value : dataValues) {
            boolean found = labels.stream()
                .anyMatch(l -> l.text().contains(value));
            if (!found) {
                missing.append("'").append(value).append("' ");
            }
        }

        if (!missing.isEmpty()) {
            StringBuilder dump = new StringBuilder("\nAll rendered labels:\n");
            for (LabelInfo l : labels) {
                dump.append(String.format("  '%s' (w=%.0f)\n", l.text(), l.width()));
            }
            fail("Missing data values in table mode: " + missing + dump);
        }
    }

    /**
     * REGRESSION TEST for the cold-start bug: setRoot → measure → updateItem
     * followed by a resize must build the control tree with data visible.
     * Before the fix, setContent() updated dataSnapshot on cold start
     * (control==null), causing the subsequent resize → autoLayout to see
     * "no changes" and skip data population.
     */
    @Test
    void coldStartResizeRendersData() throws Exception {
        List<LabelInfo> labels = renderAndCollect(1200, 600);

        // The render pipeline does setRoot → measure → updateItem → resize.
        // Data values must be present after this sequence.
        boolean hasData = labels.stream().anyMatch(l ->
            l.text().contains("Frank") || l.text().contains("Dashboard"));

        assertTrue(hasData,
            "After cold-start + resize, data must be visible. Labels: "
            + labels.stream().map(LabelInfo::text).toList());
    }

    /**
     * THE KEY TEST: ONE AutoLayout instance starts wide (table mode), then
     * resizes narrow (outline mode). Data must survive the transition.
     * This is the EXACT user scenario: drag window narrower.
     */
    @Test
    void singleInstanceResizePreservesData() throws Exception {
        var ref = new AtomicReference<List<LabelInfo>>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                Relation schema = buildNestedSchema();
                Style model = new Style();
                AutoLayout autoLayout = new AutoLayout(schema, model);
                AnchorPane.setTopAnchor(autoLayout, 0.0);
                AnchorPane.setLeftAnchor(autoLayout, 0.0);
                AnchorPane.setBottomAnchor(autoLayout, 0.0);
                AnchorPane.setRightAnchor(autoLayout, 0.0);

                AnchorPane root = (AnchorPane) testStage.getScene().getRoot();
                root.getChildren().setAll(autoLayout);

                // Start WIDE
                testStage.setWidth(1200);
                testStage.setHeight(600);

                JsonNode data = buildData();
                autoLayout.measure(data);
                autoLayout.updateItem(data);
                autoLayout.autoLayout();

                // Wait for table mode to fully build, then resize narrow
                Platform.runLater(() -> Platform.runLater(() -> {
                    // Resize to narrow — triggers outline mode
                    testStage.setWidth(500);

                    // Wait for outline to build
                    Platform.runLater(() -> Platform.runLater(() -> Platform.runLater(() -> {
                        root.applyCss();
                        root.layout();
                        // One more cycle for VirtualFlow cells
                        Platform.runLater(() -> {
                            List<LabelInfo> labels = new ArrayList<>();
                            collectLabels(autoLayout, labels);
                            ref.set(labels);
                            latch.countDown();
                        });
                    })));
                }));
            } catch (Exception e) {
                ref.set(List.of(new LabelInfo("EXCEPTION: " + e.getMessage(), 0)));
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out");
        List<LabelInfo> labels = ref.get();

        assertFalse(labels.isEmpty(),
            "After resize, scene graph must not be empty");

        String[] dataValues = {"Frank", "Frontend", "Web", "frank@"};
        StringBuilder missing = new StringBuilder();
        for (String value : dataValues) {
            boolean found = labels.stream().anyMatch(l -> l.text().contains(value));
            if (!found) {
                missing.append("'").append(value).append("' ");
            }
        }

        if (!missing.isEmpty()) {
            StringBuilder dump = new StringBuilder("\nAfter wide→narrow resize, rendered labels:\n");
            for (LabelInfo l : labels) {
                dump.append(String.format("  '%s' (w=%.0f)\n", l.text(), l.width()));
            }
            fail("Data lost during resize: " + missing + dump);
        }
    }

    /**
     * Data labels must have non-zero rendered width. A label with text
     * "Frank Thompson" but width=0 is invisible to the user.
     */
    @Test
    void dataLabelsHaveNonZeroWidth() throws Exception {
        List<LabelInfo> labels = renderAndCollect(1400, 600);

        List<LabelInfo> zeroWidthData = labels.stream()
            .filter(l -> l.width() <= 0)
            .filter(l -> l.text().contains("Frank") || l.text().contains("Dashboard")
                      || l.text().contains("Active") || l.text().contains("180"))
            .toList();

        assertTrue(zeroWidthData.isEmpty(),
            "Data labels must have width > 0. Zero-width: " + zeroWidthData);
    }
}
