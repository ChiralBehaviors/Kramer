// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.query;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Tests for SchemaDiagramView (Kramer-u3io P3 T3).
 */
@ExtendWith(ApplicationExtension.class)
class SchemaDiagramViewTest {

    private Stage testStage;

    @Start
    void start(Stage stage) {
        this.testStage = stage;
        stage.setScene(new Scene(new Pane(), 600, 400));
        stage.show();
    }

    private Relation buildSchema() {
        Relation root = new Relation("employees");
        root.addChild(new Primitive("name"));
        root.addChild(new Primitive("role"));
        Relation projects = new Relation("projects");
        projects.addChild(new Primitive("project"));
        projects.addChild(new Primitive("status"));
        root.addChild(projects);
        return root;
    }

    @Test
    void diagramShowsAllRelationNodes() {
        var ref = new AtomicReference<List<String>>();
        Platform.runLater(() -> {
            var view = new SchemaDiagramView();
            view.setRoot(buildSchema());
            attachToStage(view);
            ref.set(collectLabelTexts(view));
        });
        WaitForAsyncUtils.waitForFxEvents();

        var labels = ref.get();
        assertTrue(labels.contains("employees"), "Should show root relation");
        assertTrue(labels.contains("projects"), "Should show nested relation");
    }

    @Test
    void diagramShowsPrimitiveFields() {
        var ref = new AtomicReference<List<String>>();
        Platform.runLater(() -> {
            var view = new SchemaDiagramView();
            view.setRoot(buildSchema());
            attachToStage(view);
            ref.set(collectLabelTexts(view));
        });
        WaitForAsyncUtils.waitForFxEvents();

        var labels = ref.get();
        assertTrue(labels.contains("name"), "Should show primitive 'name'");
        assertTrue(labels.contains("role"), "Should show primitive 'role'");
        assertTrue(labels.contains("project"), "Should show nested primitive 'project'");
    }

    @Test
    void clickCallbackFiresWithPath() {
        var ref = new AtomicReference<SchemaPath>();
        Platform.runLater(() -> {
            var view = new SchemaDiagramView();
            view.setRoot(buildSchema());
            view.setOnNodeClicked(ref::set);
            attachToStage(view);

            // Find and click the "projects" label
            for (Node node : collectClickableNodes(view)) {
                if (node instanceof Label label && "projects".equals(label.getText())) {
                    label.fireEvent(new javafx.scene.input.MouseEvent(
                        javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                        0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY,
                        1, false, false, false, false, true, false, false,
                        false, false, false, null));
                    break;
                }
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(ref.get(), "Click callback should fire");
        assertTrue(ref.get().toString().contains("projects"),
            "Path should contain 'projects', got: " + ref.get());
    }

    @Test
    void setRootWithNullClearsDiagram() {
        var ref = new AtomicReference<Integer>();
        Platform.runLater(() -> {
            var view = new SchemaDiagramView();
            view.setRoot(buildSchema());
            view.setRoot(null);
            ref.set(view.getChildren().size());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(0, ref.get(), "Null root should clear diagram");
    }

    @Test
    void hasStyleClass() {
        var ref = new AtomicReference<Boolean>();
        Platform.runLater(() -> {
            var view = new SchemaDiagramView();
            ref.set(view.getStyleClass().contains("schema-diagram-view"));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get());
    }

    @Test
    void nestingDepthReflectedInLayout() {
        var ref = new AtomicReference<double[]>();
        Platform.runLater(() -> {
            var view = new SchemaDiagramView();
            view.setRoot(buildSchema());
            attachToStage(view);

            // Root "employees" should be at a different X than nested "projects"
            double rootX = findLabelX(view, "employees");
            double nestedX = findLabelX(view, "projects");
            ref.set(new double[] {rootX, nestedX});
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get()[1] > ref.get()[0],
            "Nested relation should be indented right of root");
    }

    // Helpers

    private void attachToStage(Node node) {
        Pane root = (Pane) testStage.getScene().getRoot();
        root.getChildren().setAll(node);
        root.applyCss();
        root.layout();
    }

    private List<String> collectLabelTexts(Node root) {
        List<String> texts = new ArrayList<>();
        collectLabelsRec(root, texts);
        return texts;
    }

    private void collectLabelsRec(Node node, List<String> out) {
        if (node instanceof Label label && label.getText() != null
                && !label.getText().isBlank()) {
            out.add(label.getText());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectLabelsRec(child, out);
            }
        }
    }

    private List<Node> collectClickableNodes(Node root) {
        List<Node> nodes = new ArrayList<>();
        collectClickableRec(root, nodes);
        return nodes;
    }

    private void collectClickableRec(Node node, List<Node> out) {
        if (node instanceof Label) out.add(node);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectClickableRec(child, out);
            }
        }
    }

    private double findLabelX(Node root, String text) {
        if (root instanceof Label label && text.equals(label.getText())) {
            return label.getLayoutX();
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                double x = findLabelX(child, text);
                if (x >= 0) return x + child.getLayoutX();
            }
        }
        return -1;
    }
}
