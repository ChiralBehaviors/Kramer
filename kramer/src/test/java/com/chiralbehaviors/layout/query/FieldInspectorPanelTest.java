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
import com.chiralbehaviors.layout.style.Style;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Tests for FieldInspectorPanel (Kramer-tuiw T2A).
 */
@ExtendWith(ApplicationExtension.class)
class FieldInspectorPanelTest {

    private LayoutQueryState queryState;
    private InteractionHandler handler;

    @Start
    void start(Stage stage) {
        stage.setScene(new Scene(new Pane(), 400, 400));
        stage.show();
    }

    @BeforeEach
    void setUp() {
        var style = new Style(new ConfiguredMeasurementStrategy());
        queryState = new LayoutQueryState(style);
        handler = new InteractionHandler(queryState);
    }

    @Test
    void showsPathForSelectedField() {
        var ref = new AtomicReference<String>();
        Platform.runLater(() -> {
            var panel = new FieldInspectorPanel(handler, queryState);
            var path = new SchemaPath("employees", "name");
            panel.inspect(path);
            ref.set(panel.getDisplayedPath());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("employees/name", ref.get());
    }

    @Test
    void showsSortStateForSortedField() {
        var ref = new AtomicReference<String>();
        Platform.runLater(() -> {
            var path = new SchemaPath("employees", "name");
            handler.apply(new LayoutInteraction.SortBy(path, false));

            var panel = new FieldInspectorPanel(handler, queryState);
            panel.inspect(path);
            ref.set(panel.getDisplayedSort());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("ascending", ref.get());
    }

    @Test
    void showsFilterExpressionForFilteredField() {
        var ref = new AtomicReference<String>();
        Platform.runLater(() -> {
            var path = new SchemaPath("employees", "name");
            handler.apply(new LayoutInteraction.SetFilter(path, "$name > 'A'"));

            var panel = new FieldInspectorPanel(handler, queryState);
            panel.inspect(path);
            ref.set(panel.getDisplayedFilter());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("$name > 'A'", ref.get());
    }

    @Test
    void showsNoneForUnsortedField() {
        var ref = new AtomicReference<String>();
        Platform.runLater(() -> {
            var panel = new FieldInspectorPanel(handler, queryState);
            panel.inspect(new SchemaPath("employees", "name"));
            ref.set(panel.getDisplayedSort());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("none", ref.get());
    }

    @Test
    void clearSortViaInspectorUpdatesState() {
        Platform.runLater(() -> {
            var path = new SchemaPath("employees", "name");
            handler.apply(new LayoutInteraction.SortBy(path, false));
            assertEquals("name", queryState.getFieldState(path).sortFields());

            var panel = new FieldInspectorPanel(handler, queryState);
            panel.inspect(path);
            panel.clearSort();
            assertNull(queryState.getFieldState(path).sortFields());
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void clearFilterViaInspectorUpdatesState() {
        Platform.runLater(() -> {
            var path = new SchemaPath("employees", "name");
            handler.apply(new LayoutInteraction.SetFilter(path, "$x > 1"));

            var panel = new FieldInspectorPanel(handler, queryState);
            panel.inspect(path);
            panel.clearFilter();
            assertNull(queryState.getFieldState(path).filterExpression());
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void inspectNullClearsDisplay() {
        var ref = new AtomicReference<String>();
        Platform.runLater(() -> {
            var panel = new FieldInspectorPanel(handler, queryState);
            panel.inspect(new SchemaPath("employees", "name"));
            panel.inspect(null);
            ref.set(panel.getDisplayedPath());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("", ref.get());
    }

    @Test
    void hasStyleClass() {
        var ref = new AtomicReference<Boolean>();
        Platform.runLater(() -> {
            var panel = new FieldInspectorPanel(handler, queryState);
            ref.set(panel.getStyleClass().contains("field-inspector-panel"));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get());
    }
}
