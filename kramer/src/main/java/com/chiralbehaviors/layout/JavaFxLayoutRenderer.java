// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import com.chiralbehaviors.layout.schema.SchemaNode;

/**
 * Proof-of-concept {@link LayoutRenderer} that produces a JavaFX {@link Node}
 * tree from a {@link LayoutDecisionNode}.
 * <p>
 * Leaf nodes are rendered as a {@link Label} whose text is the scalar value of
 * {@code data}. Interior nodes are rendered as a {@link VBox} that contains the
 * rendered children in order. Both node types receive a CSS style class derived
 * from the sanitized {@code fieldName}.
 * <p>
 * This renderer demonstrates the {@link AbstractLayoutRenderer} protocol and is
 * intentionally simple. The production rendering path continues to use
 * {@code buildControl()} via the existing layout engine.
 */
public class JavaFxLayoutRenderer extends AbstractLayoutRenderer<Node> {

    @Override
    protected Node renderPrimitive(LayoutDecisionNode node, JsonNode data) {
        var label = new Label(data != null ? SchemaNode.asText(data) : "");
        label.getStyleClass().add(SchemaPath.sanitize(node.fieldName()));
        return label;
    }

    @Override
    protected Node renderRelation(LayoutDecisionNode node, JsonNode data, List<Node> children) {
        var container = new VBox();
        container.getStyleClass().add(SchemaPath.sanitize(node.fieldName()));
        container.getChildren().addAll(children);
        return container;
    }
}
