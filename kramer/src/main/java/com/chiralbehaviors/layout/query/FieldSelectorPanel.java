// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.query;

import java.util.IdentityHashMap;
import java.util.Map;
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
 * Call {@link #dispose()} when the panel is removed from the scene to
 * unregister the query state change listener and prevent memory leaks.
 *
 * @author hhildebrand
 */
public final class FieldSelectorPanel extends VBox {

    private final TreeView<SchemaNode> treeView;
    private final InteractionHandler handler;
    private final LayoutQueryState queryState;
    private final Runnable changeListener;
    private final Map<TreeItem<SchemaNode>, SchemaPath> pathCache = new IdentityHashMap<>();
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

        // Refresh tree cells when query state changes (sort, filter, visibility)
        changeListener = treeView::refresh;
        queryState.addChangeListener(changeListener);

        treeView.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, selected) -> {
                if (selected != null && selected.getValue() != null
                        && onFieldSelected != null) {
                    onFieldSelected.accept(getCachedPath(selected));
                }
            });
    }

    public void setRoot(SchemaNode root) {
        pathCache.clear();
        if (root == null) {
            treeView.setRoot(null);
            return;
        }
        TreeItem<SchemaNode> rootItem = buildTree(root, new SchemaPath(root.getField()));
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);
    }

    public TreeView<SchemaNode> getTreeView() {
        return treeView;
    }

    public void setOnFieldSelected(Consumer<SchemaPath> callback) {
        this.onFieldSelected = callback;
    }

    public void refresh() {
        treeView.refresh();
    }

    /** Unregister the query state listener. Call when removing the panel. */
    public void dispose() {
        queryState.removeChangeListener(changeListener);
    }

    private TreeItem<SchemaNode> buildTree(SchemaNode node, SchemaPath path) {
        TreeItem<SchemaNode> item = new TreeItem<>(node);
        pathCache.put(item, path);
        item.setExpanded(true);
        if (node instanceof Relation r) {
            for (SchemaNode child : r.getChildren()) {
                SchemaPath childPath = path.child(child.getField());
                item.getChildren().add(buildTree(child, childPath));
            }
        }
        return item;
    }

    /** Get cached SchemaPath from a TreeItem, falling back to recomputation. */
    private SchemaPath getCachedPath(TreeItem<SchemaNode> item) {
        SchemaPath cached = pathCache.get(item);
        return cached != null ? cached : resolvePath(item);
    }

    SchemaPath resolvePath(TreeItem<SchemaNode> item) {
        if (item.getParent() == null) {
            return new SchemaPath(item.getValue().getField());
        }
        return resolvePath(item.getParent()).child(item.getValue().getField());
    }

    /**
     * Tree cell that allocates its node graph ONCE in the constructor.
     * {@code updateItem} only updates text, checkbox state, and badge
     * visibility — no new nodes are created on cell recycle.
     */
    private class FieldTreeCell extends TreeCell<SchemaNode> {

        private final HBox box = new HBox(4);
        private final CheckBox visCheck = new CheckBox();
        private final Label fieldLabel = new Label();
        private final Label sortBadge = new Label();
        private final Label filterBadge = new Label();
        private final Region spacer = new Region();
        private final Label typeBadge = new Label();
        private final CheckBox hideCheck = new CheckBox("hide if empty");

        FieldTreeCell() {
            sortBadge.getStyleClass().add("sort-badge");
            filterBadge.getStyleClass().add("filter-badge");
            typeBadge.getStyleClass().add("type-badge");
            HBox.setHgrow(spacer, Priority.ALWAYS);
            box.getChildren().setAll(visCheck, fieldLabel, sortBadge,
                                      filterBadge, spacer, typeBadge, hideCheck);

            visCheck.setOnAction(e -> {
                if (getTreeItem() != null) {
                    handler.apply(new LayoutInteraction.ToggleVisible(
                        getCachedPath(getTreeItem())));
                }
            });
            hideCheck.setOnAction(e -> {
                if (getTreeItem() != null) {
                    handler.apply(new LayoutInteraction.SetHideIfEmpty(
                        getCachedPath(getTreeItem()), hideCheck.isSelected()));
                }
            });
        }

        @Override
        protected void updateItem(SchemaNode node, boolean empty) {
            super.updateItem(node, empty);
            if (empty || node == null) {
                setText(null);
                setGraphic(null);
                getStyleClass().remove("field-hidden");
                return;
            }

            SchemaPath path = getCachedPath(getTreeItem());
            FieldState fs = queryState.getFieldState(path);
            boolean visible = queryState.getVisibleOrDefault(path);

            fieldLabel.setText(node.getField());
            visCheck.setSelected(visible);

            // Sort badge
            String sortFields = fs.sortFields();
            String fieldName = path.leaf();
            if (sortFields != null && sortFields.equals(fieldName)) {
                sortBadge.setText("\u25B2");
                sortBadge.setVisible(true);
                sortBadge.setManaged(true);
            } else if (sortFields != null && sortFields.equals("-" + fieldName)) {
                sortBadge.setText("\u25BC");
                sortBadge.setVisible(true);
                sortBadge.setManaged(true);
            } else {
                sortBadge.setVisible(false);
                sortBadge.setManaged(false);
            }

            // Filter badge
            boolean hasFilter = fs.filterExpression() != null;
            filterBadge.setText(hasFilter ? "\u25CF" : "");
            filterBadge.setVisible(hasFilter);
            filterBadge.setManaged(hasFilter);

            // Type badge
            if (node instanceof Relation r) {
                typeBadge.setText("{" + r.getChildren().size() + "}");
            } else {
                typeBadge.setText("a");
            }

            // Hide-if-empty checkbox (Relations only)
            boolean isRelation = node instanceof Relation;
            hideCheck.setVisible(isRelation);
            hideCheck.setManaged(isRelation);
            if (isRelation) {
                hideCheck.setSelected(Boolean.TRUE.equals(fs.hideIfEmpty()));
            }

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
    }
}
