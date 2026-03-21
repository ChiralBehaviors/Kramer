// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.query;

import java.util.function.Consumer;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;

import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

/**
 * Visual diagram of the schema's relation hierarchy. Renders a top-down
 * tree with relation boxes, primitive labels, and connecting lines.
 * Click on any node fires the {@link #setOnNodeClicked} callback.
 *
 * @author hhildebrand
 */
public final class SchemaDiagramView extends Pane {

    private static final double INDENT = 30;
    private static final double ROW_HEIGHT = 22;
    private static final double RELATION_PADDING = 6;

    private Consumer<SchemaPath> onNodeClicked;
    private double currentY;

    public SchemaDiagramView() {
        getStyleClass().add("schema-diagram-view");
    }

    public void setRoot(SchemaNode root) {
        getChildren().clear();
        currentY = 10;
        if (root == null) return;
        renderNode(root, new SchemaPath(root.getField()), 0, -1);
    }

    public void setOnNodeClicked(Consumer<SchemaPath> callback) {
        this.onNodeClicked = callback;
    }

    private void renderNode(SchemaNode node, SchemaPath path,
                             int depth, double parentY) {
        double x = depth * INDENT + 10;
        double y = currentY;

        Label label = new Label(node.getField());
        label.setLayoutX(x);
        label.setLayoutY(y);

        if (node instanceof Relation) {
            label.getStyleClass().add("diagram-relation");
            label.setStyle("-fx-font-weight: bold; -fx-padding: 2 6 2 6; "
                + "-fx-background-color: #e3f2fd; -fx-background-radius: 4;");
        } else {
            label.getStyleClass().add("diagram-primitive");
            label.setStyle("-fx-padding: 1 4 1 4; -fx-text-fill: #555;");
        }

        // Click handler
        label.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && onNodeClicked != null) {
                onNodeClicked.accept(path);
            }
        });
        label.setCursor(javafx.scene.Cursor.HAND);

        getChildren().add(label);

        // Connecting line from parent
        if (parentY >= 0) {
            Line line = new Line(x - 10, parentY + ROW_HEIGHT / 2,
                                  x - 2, y + ROW_HEIGHT / 2);
            line.setStroke(Color.LIGHTGRAY);
            getChildren().add(line);
        }

        currentY += ROW_HEIGHT;

        // Recurse into children
        if (node instanceof Relation r) {
            for (SchemaNode child : r.getChildren()) {
                renderNode(child, path.child(child.getField()),
                           depth + 1, y);
            }
        }
    }
}
