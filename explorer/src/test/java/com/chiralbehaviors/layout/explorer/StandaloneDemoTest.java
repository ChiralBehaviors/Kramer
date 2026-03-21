// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.explorer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.AutoLayout;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.query.InteractionHandler;
import com.chiralbehaviors.layout.query.LayoutQueryState;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Headless tests for StandaloneDemo interactive pipeline wiring.
 * Validates resize adaptation, query state integration, and layout lifecycle.
 */
class StandaloneDemoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private void runOnFxAndWait(Runnable task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new RuntimeException("FX task failed", error.get());
        }
    }

    private Relation buildSchema() {
        Relation sections = new Relation("sections");
        sections.addChild(new Primitive("section"));
        sections.addChild(new Primitive("enrollment"));

        Relation courses = new Relation("courses");
        courses.addChild(new Primitive("number"));
        courses.addChild(new Primitive("title"));
        courses.addChild(new Primitive("credits"));
        courses.addChild(sections);

        Relation depts = new Relation("departments");
        depts.addChild(new Primitive("name"));
        depts.addChild(new Primitive("building"));
        depts.addChild(courses);

        return depts;
    }

    private ArrayNode buildData() {
        ArrayNode depts = MAPPER.createArrayNode();

        ObjectNode cs = MAPPER.createObjectNode();
        cs.put("name", "Computer Science");
        cs.put("building", "Gates Hall");
        ArrayNode courses = MAPPER.createArrayNode();
        ObjectNode cs101 = MAPPER.createObjectNode();
        cs101.put("number", "CS101");
        cs101.put("title", "Intro to Programming");
        cs101.put("credits", 3);
        ArrayNode secs = MAPPER.createArrayNode();
        ObjectNode secA = MAPPER.createObjectNode();
        secA.put("section", "A");
        secA.put("enrollment", 45);
        secs.add(secA);
        ObjectNode secB = MAPPER.createObjectNode();
        secB.put("section", "B");
        secB.put("enrollment", 42);
        secs.add(secB);
        cs101.set("sections", secs);
        courses.add(cs101);

        ObjectNode cs201 = MAPPER.createObjectNode();
        cs201.put("number", "CS201");
        cs201.put("title", "Data Structures");
        cs201.put("credits", 4);
        ArrayNode secs2 = MAPPER.createArrayNode();
        ObjectNode secC = MAPPER.createObjectNode();
        secC.put("section", "A");
        secC.put("enrollment", 35);
        secs2.add(secC);
        cs201.set("sections", secs2);
        courses.add(cs201);

        cs.set("courses", courses);
        depts.add(cs);

        ObjectNode math = MAPPER.createObjectNode();
        math.put("name", "Mathematics");
        math.put("building", "Hilbert Hall");
        ArrayNode mathCourses = MAPPER.createArrayNode();
        ObjectNode calc = MAPPER.createObjectNode();
        calc.put("number", "MATH101");
        calc.put("title", "Calculus I");
        calc.put("credits", 4);
        ArrayNode calcSecs = MAPPER.createArrayNode();
        ObjectNode calcA = MAPPER.createObjectNode();
        calcA.put("section", "A");
        calcA.put("enrollment", 50);
        calcSecs.add(calcA);
        calc.set("sections", calcSecs);
        mathCourses.add(calc);
        math.set("courses", mathCourses);
        depts.add(math);

        return depts;
    }

    // -----------------------------------------------------------------------
    // Test: Layout initializes and renders with schema + data
    // -----------------------------------------------------------------------

    @Test
    void layoutInitializesWithSchemaAndData() throws Exception {
        runOnFxAndWait(() -> {
            Style style = new Style();
            LayoutQueryState qs = new LayoutQueryState(style);
            style.setStylesheet(qs);
            AutoLayout layout = new AutoLayout(null, style);

            Relation schema = buildSchema();
            ArrayNode data = buildData();

            layout.setRoot(schema);
            layout.measure(data);
            layout.updateItem(data);

            assertNotNull(layout.getRoot(), "Root should be set");
            assertNotNull(layout.getData(), "Data should be set");
        });
    }

    // -----------------------------------------------------------------------
    // Test: Resize triggers re-layout with different width
    // -----------------------------------------------------------------------

    @Test
    void resizeTriggersRelayout() throws Exception {
        runOnFxAndWait(() -> {
            Style style = new Style();
            LayoutQueryState qs = new LayoutQueryState(style);
            style.setStylesheet(qs);
            AutoLayout layout = new AutoLayout(null, style);

            Relation schema = buildSchema();
            ArrayNode data = buildData();

            layout.setRoot(schema);
            layout.measure(data);
            layout.updateItem(data);

            // Simulate initial size — triggers autoLayout
            layout.resize(800, 600);
            int childrenAt800 = layout.getChildren().size();
            assertTrue(childrenAt800 > 0,
                "Layout should have children after initial resize");

            // Resize to different width — should trigger re-layout
            layout.resize(400, 600);
            int childrenAt400 = layout.getChildren().size();
            assertTrue(childrenAt400 > 0,
                "Layout should have children after resize to 400");
        });
    }

    // -----------------------------------------------------------------------
    // Test: Layout in AnchorPane + BorderPane (same as demo structure)
    // -----------------------------------------------------------------------

    @Test
    void layoutInBorderPaneResizes() throws Exception {
        runOnFxAndWait(() -> {
            Style style = new Style();
            LayoutQueryState qs = new LayoutQueryState(style);
            style.setStylesheet(qs);
            AutoLayout layout = new AutoLayout(null, style);

            Relation schema = buildSchema();
            ArrayNode data = buildData();

            layout.setRoot(schema);
            layout.measure(data);
            layout.updateItem(data);

            // Replicate the demo scene structure — AutoLayout directly in BorderPane center
            var root = new BorderPane();
            root.setCenter(layout);

            // Create a scene to drive layout
            Scene scene = new Scene(root, 1000, 700);

            // Force layout pass
            root.applyCss();
            root.layout();

            // AutoLayout should have been resized by the AnchorPane
            double width = layout.getWidth();
            assertTrue(width > 100,
                "AutoLayout width should be > 100 after scene layout, got: " + width);
            assertTrue(layout.getChildren().size() > 0,
                "AutoLayout should have rendered children");
        });
    }

    // -----------------------------------------------------------------------
    // Test: QueryState change triggers re-layout
    // -----------------------------------------------------------------------

    @Test
    void queryStateChangeTriggersRelayout() throws Exception {
        runOnFxAndWait(() -> {
            Style style = new Style();
            LayoutQueryState qs = new LayoutQueryState(style);
            style.setStylesheet(qs);
            AutoLayout layout = new AutoLayout(null, style);

            // Wire change listener (same as demo)
            qs.addChangeListener(layout::autoLayout);
            InteractionHandler handler = new InteractionHandler(qs);

            Relation schema = buildSchema();
            ArrayNode data = buildData();

            layout.setRoot(schema);
            layout.measure(data);
            layout.updateItem(data);

            // Initial layout
            layout.resize(800, 600);

            // Toggle visibility of a field — should trigger re-layout via
            // queryState change listener
            SchemaPath namePath = new SchemaPath("departments", "name");
            assertDoesNotThrow(
                () -> handler.apply(
                    new com.chiralbehaviors.layout.query.LayoutInteraction
                        .ToggleVisible(namePath)),
                "Toggling visibility should not throw");
        });
    }
}
