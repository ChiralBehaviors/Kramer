// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.query;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.chiralbehaviors.layout.ConfiguredMeasurementStrategy;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.Style;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Tests for FieldSelectorPanel: visibility checkboxes, state badges,
 * type labels, and selection callback.
 */
@ExtendWith(ApplicationExtension.class)
class FieldSelectorPanelTest {

    private Style style;
    private LayoutQueryState queryState;
    private InteractionHandler handler;
    private Stage testStage;

    @Start
    void start(Stage stage) {
        this.testStage = stage;
        stage.setScene(new Scene(new Pane(), 400, 400));
        stage.show();
    }

    @BeforeEach
    void setUp() {
        style = new Style(new ConfiguredMeasurementStrategy());
        queryState = new LayoutQueryState(style);
        handler = new InteractionHandler(queryState);
    }

    private Relation buildSchema() {
        Relation root = new Relation("records");
        root.addChild(new Primitive("id"));
        root.addChild(new Primitive("name"));
        Relation nested = new Relation("details");
        nested.addChild(new Primitive("value"));
        root.addChild(nested);
        return root;
    }

    /**
     * Attach panel to scene and force layout so TreeView materializes cells.
     */
    private FieldSelectorPanel createAndAttach(Relation schema) {
        var panel = new FieldSelectorPanel(handler, queryState);
        panel.setRoot(schema);
        panel.setMinSize(300, 300);
        panel.setPrefSize(300, 300);
        Pane root = (Pane) testStage.getScene().getRoot();
        root.getChildren().setAll(panel);
        root.applyCss();
        root.layout();
        return panel;
    }

    /**
     * Collect all Labels with a given CSS class from the panel's scene graph.
     */
    private List<Label> findLabelsByClass(Node root, String cssClass) {
        List<Label> result = new ArrayList<>();
        findLabelsByClassRec(root, cssClass, result);
        return result;
    }

    private void findLabelsByClassRec(Node node, String cssClass, List<Label> out) {
        if (node instanceof Label label && label.getStyleClass().contains(cssClass)
                && label.isVisible()) {
            out.add(label);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                findLabelsByClassRec(child, cssClass, out);
            }
        }
    }

    // -------------------------------------------------------------------
    // Existing tests (tree structure, visibility, hide-if-empty)
    // -------------------------------------------------------------------

    @Test
    void setRootPopulatesTree() {
        var ref = new AtomicReference<Integer>();
        Platform.runLater(() -> {
            var panel = createAndAttach(buildSchema());
            ref.set(panel.getTreeView().getRoot().getChildren().size());
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(3, ref.get(), "Root should have 3 children");
    }

    @Test
    void nestedRelationPopulatesRecursively() {
        var ref = new AtomicReference<Integer>();
        Platform.runLater(() -> {
            var panel = createAndAttach(buildSchema());
            TreeItem<?> details = panel.getTreeView().getRoot().getChildren().get(2);
            ref.set(details.getChildren().size());
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, ref.get(), "'details' Relation should have 1 child (value)");
    }

    @Test
    void toggleVisibleDispatchesEvent() {
        Platform.runLater(() -> {
            var path = new SchemaPath("records", "name");
            assertTrue(queryState.getVisibleOrDefault(path));
            handler.apply(new LayoutInteraction.ToggleVisible(path));
            assertFalse(queryState.getVisibleOrDefault(path));
            handler.apply(new LayoutInteraction.ToggleVisible(path));
            assertTrue(queryState.getVisibleOrDefault(path));
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void setHideIfEmptyDispatchesEvent() {
        Platform.runLater(() -> {
            var path = new SchemaPath("records", "details");
            handler.apply(new LayoutInteraction.SetHideIfEmpty(path, true));
            assertEquals(Boolean.TRUE, queryState.getFieldState(path).hideIfEmpty());
            handler.apply(new LayoutInteraction.SetHideIfEmpty(path, false));
            assertNull(queryState.getFieldState(path).hideIfEmpty());
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void setRootWithNullClearsTree() {
        Platform.runLater(() -> {
            var panel = createAndAttach(buildSchema());
            assertNotNull(panel.getTreeView().getRoot());
            panel.setRoot(null);
            assertNull(panel.getTreeView().getRoot());
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void panelHasStyleClass() {
        var ref = new AtomicReference<Boolean>();
        Platform.runLater(() -> {
            var panel = createAndAttach(buildSchema());
            ref.set(panel.getStyleClass().contains("field-selector-panel"));
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(ref.get());
    }

    // -------------------------------------------------------------------
    // T1A: State badges
    // -------------------------------------------------------------------

    @Test
    void sortBadgeAppearsOnSortedField() {
        var ref = new AtomicReference<List<Label>>();
        Platform.runLater(() -> {
            var path = new SchemaPath("records", "name");
            handler.apply(new LayoutInteraction.SortBy(path, false));

            var panel = createAndAttach(buildSchema());
            ref.set(findLabelsByClass(panel, "sort-badge"));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(ref.get().isEmpty(), "Sort badge should appear on sorted field");
        assertTrue(ref.get().stream().anyMatch(l -> "▲".equals(l.getText())),
            "Ascending sort badge should show ▲");
    }

    @Test
    void filterBadgeAppearsOnFilteredField() {
        var ref = new AtomicReference<List<Label>>();
        Platform.runLater(() -> {
            var path = new SchemaPath("records", "name");
            handler.apply(new LayoutInteraction.SetFilter(path, "$name > 'A'"));

            var panel = createAndAttach(buildSchema());
            ref.set(findLabelsByClass(panel, "filter-badge"));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(ref.get().isEmpty(), "Filter badge should appear on filtered field");
    }

    @Test
    void typeBadgeAppearsOnRelationNodes() {
        var ref = new AtomicReference<List<Label>>();
        Platform.runLater(() -> {
            var panel = createAndAttach(buildSchema());
            ref.set(findLabelsByClass(panel, "type-badge"));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(ref.get().isEmpty(), "Type badges should appear");
        assertTrue(ref.get().stream().anyMatch(l -> l.getText().startsWith("{")),
            "Relation type badge should show {N} format");
        assertTrue(ref.get().stream().anyMatch(l -> "a".equals(l.getText())),
            "Primitive type badge should show 'a'");
    }

    @Test
    void noBadgesWhenNoStateOverrides() {
        var sortRef = new AtomicReference<List<Label>>();
        var filterRef = new AtomicReference<List<Label>>();
        Platform.runLater(() -> {
            var panel = createAndAttach(buildSchema());
            sortRef.set(findLabelsByClass(panel, "sort-badge"));
            filterRef.set(findLabelsByClass(panel, "filter-badge"));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(sortRef.get().isEmpty(), "No sort badges when no fields are sorted");
        assertTrue(filterRef.get().isEmpty(), "No filter badges when no fields are filtered");
    }

    // -------------------------------------------------------------------
    // T1B: Selection callback
    // -------------------------------------------------------------------

    @Test
    void selectionCallbackFiresOnTreeSelection() {
        var ref = new AtomicReference<SchemaPath>();
        Platform.runLater(() -> {
            var panel = createAndAttach(buildSchema());
            panel.setOnFieldSelected(ref::set);
            // Select "name" via tree model (not fragile index)
            TreeItem<SchemaNode> nameItem = panel.getTreeView().getRoot().getChildren().get(1);
            panel.getTreeView().getSelectionModel().select(nameItem);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(ref.get(), "Selection callback should fire");
        assertEquals("name", ref.get().leaf(), "Should select 'name' field");
    }

    @Test
    void selectionCallbackReceivesCorrectPath() {
        var ref = new AtomicReference<SchemaPath>();
        Platform.runLater(() -> {
            var panel = createAndAttach(buildSchema());
            panel.setOnFieldSelected(ref::set);
            // Select "value" inside "details" via tree model
            TreeItem<SchemaNode> details = panel.getTreeView().getRoot().getChildren().get(2);
            TreeItem<SchemaNode> value = details.getChildren().get(0);
            panel.getTreeView().getSelectionModel().select(value);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(ref.get(), "Selection callback should fire for nested field");
        assertEquals("records/details/value", ref.get().toString(),
            "Path should be fully qualified");
    }
}
