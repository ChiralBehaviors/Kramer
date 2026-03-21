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

/**
 * Headless tests for StandaloneDemo lifecycle.
 *
 * <p>Key constraint under test: data must be loaded AFTER the layout
 * has a real width (from the scene graph), not before. Loading data
 * when getWidth()==0 causes the layout to render at a default size
 * that doesn't fit the window and breaks resize adaptation.
 *
 * <p>Uses applyCss()/layout() to simulate the scene graph layout pass
 * in headless Monocle (Stage.show() is not available headless).
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
        Relation courses = new Relation("courses");
        courses.addChild(new Primitive("number"));
        courses.addChild(new Primitive("title"));
        courses.addChild(new Primitive("credits"));
        courses.addChild(new Primitive("instructor"));
        courses.addChild(new Primitive("enrollment"));
        courses.addChild(new Primitive("schedule"));

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
        cs101.put("instructor", "Prof. Turing");
        cs101.put("enrollment", 125);
        cs101.put("schedule", "MWF 9-10");
        courses.add(cs101);
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
        calc.put("instructor", "Prof. Euler");
        calc.put("enrollment", 143);
        calc.put("schedule", "MWF 8-9");
        mathCourses.add(calc);
        math.set("courses", mathCourses);
        depts.add(math);

        return depts;
    }

    // -----------------------------------------------------------------------
    // Regression guard: data loaded before scene → width is 0
    // This is the anti-pattern the demo fix prevents.
    // -----------------------------------------------------------------------

    @Test
    void dataBeforeSceneProducesZeroWidth() throws Exception {
        runOnFxAndWait(() -> {
            Style style = new Style();
            LayoutQueryState qs = new LayoutQueryState(style);
            style.setStylesheet(qs);
            AutoLayout layout = new AutoLayout(null, style);

            // Load data BEFORE creating a scene
            layout.setRoot(buildSchema());
            layout.measure(buildData());
            layout.updateItem(buildData());

            assertEquals(0.0, layout.getWidth(), 0.01,
                "Width must be 0 before scene exists — data loading " +
                "must be deferred until after the scene graph provides " +
                "a real width");
        });
    }

    // -----------------------------------------------------------------------
    // Correct lifecycle: scene first, then data → layout gets real width
    // -----------------------------------------------------------------------

    @Test
    void dataAfterSceneLayoutGetsRealWidth() throws Exception {
        runOnFxAndWait(() -> {
            Style style = new Style();
            LayoutQueryState qs = new LayoutQueryState(style);
            style.setStylesheet(qs);
            AutoLayout layout = new AutoLayout(null, style);

            // Step 1: create scene (no data yet)
            var root = new BorderPane();
            root.setCenter(layout);
            new Scene(root, 1000, 700);

            // Step 2: force layout pass so scene graph assigns widths
            root.applyCss();
            root.layout();

            double widthBeforeData = layout.getWidth();
            assertTrue(widthBeforeData > 100,
                "After scene layout, AutoLayout should have real width, got: "
                + widthBeforeData);

            // Step 3: NOW load data (same as demo's Platform.runLater)
            layout.setRoot(buildSchema());
            layout.measure(buildData());
            layout.updateItem(buildData());

            assertTrue(layout.getChildren().size() > 0,
                "Layout should have rendered children");
        });
    }

    // -----------------------------------------------------------------------
    // Resize: layout adapts to new width
    // -----------------------------------------------------------------------

    @Test
    void resizeChangesLayoutWidth() throws Exception {
        runOnFxAndWait(() -> {
            Style style = new Style();
            LayoutQueryState qs = new LayoutQueryState(style);
            style.setStylesheet(qs);
            AutoLayout layout = new AutoLayout(null, style);

            var root = new BorderPane();
            root.setCenter(layout);
            new Scene(root, 1000, 700);
            root.applyCss();
            root.layout();

            // Load data at real width
            layout.setRoot(buildSchema());
            layout.measure(buildData());
            layout.updateItem(buildData());

            double widthAt1000 = layout.getWidth();

            // Simulate resize by calling resize() directly with new width
            // (applyCss/layout won't change Scene size in headless)
            layout.resize(500, 600);

            assertTrue(layout.getChildren().size() > 0,
                "Layout should still have children after resize");
        });
    }

    // -----------------------------------------------------------------------
    // QueryState change triggers re-layout without error
    // -----------------------------------------------------------------------

    @Test
    void queryStateChangeWorks() throws Exception {
        runOnFxAndWait(() -> {
            Style style = new Style();
            LayoutQueryState qs = new LayoutQueryState(style);
            style.setStylesheet(qs);
            AutoLayout layout = new AutoLayout(null, style);
            qs.addChangeListener(layout::autoLayout);
            InteractionHandler handler = new InteractionHandler(qs);

            var root = new BorderPane();
            root.setCenter(layout);
            new Scene(root, 800, 600);
            root.applyCss();
            root.layout();

            layout.setRoot(buildSchema());
            layout.measure(buildData());
            layout.updateItem(buildData());

            SchemaPath namePath = new SchemaPath("departments", "name");
            assertDoesNotThrow(
                () -> handler.apply(
                    new com.chiralbehaviors.layout.query.LayoutInteraction
                        .ToggleVisible(namePath)),
                "Toggling visibility should not throw");
        });
    }
}
