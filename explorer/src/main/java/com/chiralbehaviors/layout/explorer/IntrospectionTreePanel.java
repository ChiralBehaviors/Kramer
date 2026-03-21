// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.explorer;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.graphql.QueryExpander;
import com.chiralbehaviors.layout.graphql.TypeIntrospector;
import com.chiralbehaviors.layout.graphql.TypeIntrospector.IntrospectedField;
import com.chiralbehaviors.layout.graphql.TypeIntrospector.IntrospectedType;

import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Panel showing available types and fields from a GraphQL introspection result.
 * Each field has an Add or Remove button depending on whether it's in the
 * current query. Add/remove actions use {@link QueryExpander} and trigger
 * re-execution via the provided callback.
 */
public final class IntrospectionTreePanel extends VBox {

    /** Wrapper for tree items — either a type header or a field entry. */
    sealed interface TreeEntry {
        record TypeHeader(IntrospectedType type) implements TreeEntry {}
        record FieldEntry(IntrospectedField field, SchemaPath parentPath,
                          String parentTypeName) implements TreeEntry {}
    }

    private final TreeView<TreeEntry> treeView;
    private final QueryExpander expander;
    private final Supplier<Document> documentSupplier;
    private final Consumer<String> reExecute;

    /**
     * @param introspector    parsed introspection result
     * @param expander        AST modifier for add/remove
     * @param documentSupplier supplies the current Document AST
     * @param reExecute       callback receiving the serialized new query string
     */
    public IntrospectionTreePanel(TypeIntrospector introspector,
                                   QueryExpander expander,
                                   Supplier<Document> documentSupplier,
                                   Consumer<String> reExecute) {
        this.expander = expander;
        this.documentSupplier = documentSupplier;
        this.reExecute = reExecute;
        this.treeView = new TreeView<>();

        getStyleClass().add("introspection-tree-panel");
        treeView.setCellFactory(tv -> new EntryTreeCell());
        treeView.setShowRoot(false);
        VBox.setVgrow(treeView, Priority.ALWAYS);
        getChildren().add(treeView);

        buildTree(introspector);
    }

    /** Returns the backing TreeView for testing. */
    TreeView<TreeEntry> getTreeView() {
        return treeView;
    }

    /**
     * Create a placeholder panel when introspection is unavailable.
     */
    public static IntrospectionTreePanel placeholder() {
        var panel = new IntrospectionTreePanel();
        panel.getChildren().add(new Label("Introspection not available"));
        return panel;
    }

    /** Private constructor for placeholder. */
    private IntrospectionTreePanel() {
        this.treeView = new TreeView<>();
        this.expander = null;
        this.documentSupplier = null;
        this.reExecute = null;
        getStyleClass().add("introspection-tree-panel");
    }

    private void buildTree(TypeIntrospector introspector) {
        TreeItem<TreeEntry> root = new TreeItem<>();

        for (IntrospectedType type : introspector.userTypes()) {
            if (type.fields() == null || type.fields().isEmpty()) {
                continue;
            }
            var typeItem = new TreeItem<TreeEntry>(new TreeEntry.TypeHeader(type));
            for (IntrospectedField field : type.fields()) {
                // parentPath for Query root fields: SchemaPath of the field itself in Query type
                // For non-Query types, we need the path context — but since
                // the tree is organized by type, we pass the type name for resolution
                typeItem.getChildren().add(
                    new TreeItem<>(new TreeEntry.FieldEntry(
                        field, null, type.name())));
            }
            typeItem.setExpanded(false);
            root.getChildren().add(typeItem);
        }

        treeView.setRoot(root);
    }

    /**
     * Collect field names currently in the document at any level.
     */
    private Set<String> activeFieldNames() {
        Document doc = documentSupplier != null ? documentSupplier.get() : null;
        if (doc == null) {
            return Set.of();
        }
        return collectFieldNames(doc);
    }

    private static Set<String> collectFieldNames(Document doc) {
        return doc.getDefinitions().stream()
            .filter(OperationDefinition.class::isInstance)
            .map(OperationDefinition.class::cast)
            .flatMap(op -> collectFromSelectionSet(op.getSelectionSet()).stream())
            .collect(Collectors.toSet());
    }

    private static Set<String> collectFromSelectionSet(SelectionSet ss) {
        if (ss == null) {
            return Set.of();
        }
        var names = new java.util.HashSet<String>();
        for (Selection<?> sel : ss.getSelections()) {
            if (sel instanceof Field f) {
                names.add(f.getName());
                names.addAll(collectFromSelectionSet(f.getSelectionSet()));
            }
        }
        return names;
    }

    /**
     * Resolve the SchemaPath for a field within a given type, by finding
     * which top-level query field leads to this type.
     */
    private SchemaPath resolveParentPath(String parentTypeName, Document doc) {
        if (doc == null || "Query".equals(parentTypeName)) {
            return null; // top-level fields — parent is the operation root
        }
        // Walk the document looking for a field whose sub-selection would
        // lead to this type. For simplicity, search top-level field names
        // that match a path segment (heuristic: type name lowercased or
        // pluralized might match field names).
        // This is a best-effort resolution — for deeply nested types,
        // the user's click context provides better information.
        for (var def : doc.getDefinitions()) {
            if (def instanceof OperationDefinition op) {
                SchemaPath found = findPathToType(
                    op.getSelectionSet(), parentTypeName, new SchemaPath(List.of()));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Recursively search for a selection set that likely corresponds to the
     * target type. Uses the heuristic that field names often match type names
     * (lowercased/pluralized).
     */
    private SchemaPath findPathToType(SelectionSet ss, String typeName,
                                       SchemaPath currentPath) {
        if (ss == null) {
            return null;
        }
        for (Selection<?> sel : ss.getSelections()) {
            if (sel instanceof Field f && f.getSelectionSet() != null) {
                SchemaPath fieldPath = currentPath.segments().isEmpty()
                    ? new SchemaPath(f.getName())
                    : currentPath.child(f.getName());
                // Check if this field's sub-fields match the target type's fields
                // Simple heuristic: if the field name contains the type name (case-insensitive)
                if (f.getName().toLowerCase().contains(typeName.toLowerCase())
                        || typeName.toLowerCase().contains(f.getName().toLowerCase())) {
                    return fieldPath;
                }
                // Recurse
                SchemaPath deeper = findPathToType(
                    f.getSelectionSet(), typeName, fieldPath);
                if (deeper != null) {
                    return deeper;
                }
            }
        }
        return null;
    }

    private void addField(TreeEntry.FieldEntry entry) {
        if (expander == null || documentSupplier == null || reExecute == null) {
            return;
        }
        Document doc = documentSupplier.get();
        if (doc == null) {
            return;
        }

        SchemaPath parentPath = resolveParentPath(entry.parentTypeName(), doc);
        if (parentPath == null) {
            // Top-level or unresolvable — skip
            return;
        }

        Document modified = expander.addField(doc, parentPath, entry.field().name());
        reExecute.accept(QueryExpander.serialize(modified));
        treeView.refresh();
    }

    private void removeField(TreeEntry.FieldEntry entry) {
        if (expander == null || documentSupplier == null || reExecute == null) {
            return;
        }
        Document doc = documentSupplier.get();
        if (doc == null) {
            return;
        }

        SchemaPath parentPath = resolveParentPath(entry.parentTypeName(), doc);
        if (parentPath == null) {
            return;
        }
        SchemaPath fieldPath = parentPath.child(entry.field().name());
        Document modified = expander.removeField(doc, fieldPath);
        reExecute.accept(QueryExpander.serialize(modified));
        treeView.refresh();
    }

    /**
     * Custom tree cell showing field name + Add/Remove button.
     */
    private class EntryTreeCell extends TreeCell<TreeEntry> {
        @Override
        protected void updateItem(TreeEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            switch (item) {
                case TreeEntry.TypeHeader th -> {
                    setText(null);
                    var label = new Label(th.type().name());
                    label.setStyle("-fx-font-weight: bold;");
                    setGraphic(label);
                }
                case TreeEntry.FieldEntry fe -> {
                    setText(null);
                    var label = new Label(fe.field().name());
                    String typeDesc = fe.field().type().baseName();
                    if (typeDesc != null) {
                        label.setText(fe.field().name() + " : " + typeDesc);
                    }

                    var spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Set<String> active = activeFieldNames();
                    boolean inQuery = active.contains(fe.field().name());

                    Button actionBtn;
                    if (inQuery) {
                        actionBtn = new Button("−");
                        actionBtn.getStyleClass().add("remove-field-btn");
                        actionBtn.setOnAction(e -> removeField(fe));
                    } else {
                        actionBtn = new Button("+");
                        actionBtn.getStyleClass().add("add-field-btn");
                        actionBtn.setOnAction(e -> addField(fe));
                    }
                    actionBtn.setMinWidth(24);

                    var hbox = new HBox(4, label, spacer, actionBtn);
                    setGraphic(hbox);
                }
            }
        }
    }
}
