// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.query;

import java.util.function.Consumer;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A panel displaying a {@link TreeView} of the schema tree with per-field
 * visibility checkboxes, state badges (sort, filter, type), and per-Relation
 * hide-if-empty checkboxes.
 * <p>
 * Dispatches {@link LayoutInteraction.ToggleVisible} and
 * {@link LayoutInteraction.SetHideIfEmpty} events via an
 * {@link InteractionHandler}.
 *
 * @author hhildebrand
 */
public final class FieldSelectorPanel extends VBox {

    private final TreeView<SchemaNode> treeView;
    private final InteractionHandler handler;
    private final LayoutQueryState queryState;
    private Consumer<SchemaPath> onFieldSelected;

    public FieldSelectorPanel(InteractionHandler handler,
                               LayoutQueryState queryState) {
        this.handler = handler;
        this.queryState = queryState;
        this.treeView = new TreeView<>();
        getStyleClass().add("field-selector-panel");
        treeView.setCellFactory(tv -> new FieldTreeCell());
        VBox.setVgrow(treeView, Priority.ALWAYS);
        getChildren().add(treeView);

        // Selection callback
        treeView.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, selected) -> {
                if (selected != null && selected.getValue() != null
                        && onFieldSelected != null) {
                    onFieldSelected.accept(resolvePath(selected));
                }
            });
    }

    /**
     * Populate the tree from a schema root. Expands all nodes.
     */
    public void setRoot(SchemaNode root) {
        if (root == null) {
            treeView.setRoot(null);
            return;
        }
        TreeItem<SchemaNode> rootItem = buildTree(root, new SchemaPath(root.getField()));
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);
    }

    /** Returns the backing TreeView for testing and layout purposes. */
    public TreeView<SchemaNode> getTreeView() {
        return treeView;
    }

    /** Set a callback that fires when a field is selected in the tree. */
    public void setOnFieldSelected(Consumer<SchemaPath> callback) {
        this.onFieldSelected = callback;
    }

    /** Force tree cells to re-render (picks up state changes). */
    public void refresh() {
        treeView.refresh();
    }

    private TreeItem<SchemaNode> buildTree(SchemaNode node, SchemaPath path) {
        TreeItem<SchemaNode> item = new TreeItem<>(node);
        item.setExpanded(true);
        if (node instanceof Relation r) {
            for (SchemaNode child : r.getChildren()) {
                SchemaPath childPath = path.child(child.getField());
                item.getChildren().add(buildTree(child, childPath));
            }
        }
        return item;
    }

    /**
     * Resolve the SchemaPath for a TreeItem by walking up the tree.
     */
    SchemaPath resolvePath(TreeItem<SchemaNode> item) {
        if (item.getParent() == null) {
            return new SchemaPath(item.getValue().getField());
        }
        return resolvePath(item.getParent()).child(item.getValue().getField());
    }

    /**
     * Custom tree cell with visibility checkbox, state badges, type labels,
     * and optional hide-if-empty checkbox for Relation nodes.
     */
    private class FieldTreeCell extends TreeCell<SchemaNode> {

        @Override
        protected void updateItem(SchemaNode node, boolean empty) {
            super.updateItem(node, empty);
            if (empty || node == null) {
                setText(null);
                setGraphic(null);
                getStyleClass().remove("field-hidden");
                return;
            }

            SchemaPath path = resolvePath(getTreeItem());
            FieldState fs = queryState.getFieldState(path);
            boolean visible = queryState.getVisibleOrDefault(path);

            var fieldLabel = new Label(node.getField());
            var visCheck = new CheckBox();
            visCheck.setSelected(visible);
            visCheck.setOnAction(e ->
                handler.apply(new LayoutInteraction.ToggleVisible(path)));

            var box = new HBox(4, visCheck, fieldLabel);

            // State badges
            addSortBadge(box, fs, path);
            addFilterBadge(box, fs);
            addTypeBadge(box, node);

            if (node instanceof Relation) {
                Boolean hideIfEmpty = fs.hideIfEmpty();
                var hideCheck = new CheckBox("hide if empty");
                hideCheck.setSelected(Boolean.TRUE.equals(hideIfEmpty));
                hideCheck.setOnAction(e ->
                    handler.apply(new LayoutInteraction.SetHideIfEmpty(path,
                        hideCheck.isSelected())));
                box.getChildren().add(hideCheck);
            }

            // Apply field-hidden CSS class for dimmed appearance
            if (!visible) {
                if (!getStyleClass().contains("field-hidden")) {
                    getStyleClass().add("field-hidden");
                }
            } else {
                getStyleClass().remove("field-hidden");
            }

            setText(null);
            setGraphic(box);
        }

        private void addSortBadge(HBox box, FieldState fs, SchemaPath path) {
            String sortFields = fs.sortFields();
            if (sortFields == null || sortFields.isEmpty()) return;

            String fieldName = path.leaf();
            Label badge = new Label();
            badge.getStyleClass().add("sort-badge");
            if (sortFields.equals(fieldName)) {
                badge.setText("\u25B2"); // ▲
            } else if (sortFields.equals("-" + fieldName)) {
                badge.setText("\u25BC"); // ▼
            } else {
                return; // sorted by a different field
            }
            box.getChildren().add(badge);
        }

        private void addFilterBadge(HBox box, FieldState fs) {
            if (fs.filterExpression() == null) return;
            Label badge = new Label("\u25CF"); // ●
            badge.getStyleClass().add("filter-badge");
            box.getChildren().add(badge);
        }

        private void addTypeBadge(HBox box, SchemaNode node) {
            Label badge = new Label();
            badge.getStyleClass().add("type-badge");
            if (node instanceof Relation r) {
                int childCount = r.getChildren().size();
                badge.setText("{" + childCount + "}");
            } else if (node instanceof Primitive) {
                badge.setText("a");
            } else {
                return;
            }
            // Push to right side
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            box.getChildren().addAll(spacer, badge);
        }
    }
}
