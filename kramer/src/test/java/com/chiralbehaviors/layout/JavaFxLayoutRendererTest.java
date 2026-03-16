// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

class JavaFxLayoutRendererTest extends ApplicationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void start(Stage stage) {
        // No UI setup needed; we just need the toolkit initialized.
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static LayoutDecisionNode leaf(String name) {
        var path = new SchemaPath(name);
        return new LayoutDecisionNode(path, path.leaf(), null, null, null, null, null, null);
    }

    private static LayoutDecisionNode parent(String name, List<LayoutDecisionNode> children) {
        var path = new SchemaPath(name);
        return new LayoutDecisionNode(path, path.leaf(), null, null, null, null, null, children);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void leafNodeRendersAsLabel() {
        var renderer = new JavaFxLayoutRenderer();
        var node = leaf("score");
        var data = MAPPER.getNodeFactory().textNode("42");

        var result = renderer.render(node, data);

        assertInstanceOf(Label.class, result, "leaf must render as Label");
        assertEquals("42", ((Label) result).getText());
    }

    @Test
    void leafNodeHasFieldNameStyleClass() {
        var renderer = new JavaFxLayoutRenderer();
        var node = leaf("score");
        var data = MAPPER.getNodeFactory().textNode("99");

        var result = (Label) renderer.render(node, data);

        assertTrue(result.getStyleClass().contains("score"),
                   "Label must carry the sanitized field name as a style class");
    }

    @Test
    void relationNodeRendersAsVBox() {
        var child = leaf("name");
        var root = parent("person", List.of(child));

        ObjectNode data = MAPPER.createObjectNode();
        data.put("name", "Alice");

        var renderer = new JavaFxLayoutRenderer();
        var result = renderer.render(root, data);

        assertInstanceOf(VBox.class, result, "relation must render as VBox");
        var vbox = (VBox) result;
        assertEquals(1, vbox.getChildren().size());
        assertInstanceOf(Label.class, vbox.getChildren().get(0));
    }

    @Test
    void relationNodeHasFieldNameStyleClass() {
        var child = leaf("x");
        var root = parent("container", List.of(child));

        var renderer = new JavaFxLayoutRenderer();
        var result = (VBox) renderer.render(root, MAPPER.createObjectNode());

        assertTrue(result.getStyleClass().contains("container"),
                   "VBox must carry the sanitized field name as a style class");
    }

    @Test
    void childDataExtractedViaFieldName() {
        var child = leaf("status");
        var root = parent("item", List.of(child));

        ObjectNode data = MAPPER.createObjectNode();
        data.put("status", "active");
        data.put("other", "ignored");

        var renderer = new JavaFxLayoutRenderer();
        var vbox = (VBox) renderer.render(root, data);
        var label = (Label) vbox.getChildren().get(0);

        assertEquals("active", label.getText(), "child must receive value of 'status' field");
    }

    @Test
    void nullDataProducesEmptyLabel() {
        var renderer = new JavaFxLayoutRenderer();
        var node = leaf("value");

        var result = (Label) renderer.render(node, null);

        assertEquals("", result.getText(), "null data must produce empty Label text");
    }

    @Test
    void multipleChildrenAddedToVBox() {
        var child1 = leaf("first");
        var child2 = leaf("second");
        var root = parent("row", List.of(child1, child2));

        ObjectNode data = MAPPER.createObjectNode();
        data.put("first", "A");
        data.put("second", "B");

        var renderer = new JavaFxLayoutRenderer();
        var vbox = (VBox) renderer.render(root, data);

        assertEquals(2, vbox.getChildren().size());
        assertEquals("A", ((Label) vbox.getChildren().get(0)).getText());
        assertEquals("B", ((Label) vbox.getChildren().get(1)).getText());
    }
}
