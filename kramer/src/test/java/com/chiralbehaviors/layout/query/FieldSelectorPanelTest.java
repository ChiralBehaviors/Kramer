// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.query;

import static org.junit.jupiter.api.Assertions.*;

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
import com.chiralbehaviors.layout.style.Style;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Tests for FieldSelectorPanel (Kramer-6u5t T3.1).
 */
@ExtendWith(ApplicationExtension.class)
class FieldSelectorPanelTest {

    private Style style;
    private LayoutQueryState queryState;
    private InteractionHandler handler;

    @Start
    void start(Stage stage) {
        stage.setScene(new Scene(new Pane(), 400, 300));
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

    @Test
    void setRootPopulatesTree() {
        var ref = new AtomicReference<Integer>();
        Platform.runLater(() -> {
            var panel = new FieldSelectorPanel(handler, queryState);
            panel.setRoot(buildSchema());
            // Root (records) has 3 children: id, name, details
            ref.set(panel.getTreeView().getRoot().getChildren().size());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(3, ref.get(), "Root should have 3 children");
    }

    @Test
    void nestedRelationPopulatesRecursively() {
        var ref = new AtomicReference<Integer>();
        Platform.runLater(() -> {
            var panel = new FieldSelectorPanel(handler, queryState);
            panel.setRoot(buildSchema());
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
            assertTrue(queryState.getVisibleOrDefault(path), "default visible");

            handler.apply(new LayoutInteraction.ToggleVisible(path));
            assertFalse(queryState.getVisibleOrDefault(path), "should be hidden after toggle");

            handler.apply(new LayoutInteraction.ToggleVisible(path));
            assertTrue(queryState.getVisibleOrDefault(path), "should be visible after second toggle");
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
            assertNull(queryState.getFieldState(path).hideIfEmpty(),
                       "Setting hideIfEmpty to false should clear it (null)");
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void setRootWithNullClearsTree() {
        Platform.runLater(() -> {
            var panel = new FieldSelectorPanel(handler, queryState);
            panel.setRoot(buildSchema());
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
            var panel = new FieldSelectorPanel(handler, queryState);
            ref.set(panel.getStyleClass().contains("field-selector-panel"));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get(), "Panel should have field-selector-panel style class");
    }
}
