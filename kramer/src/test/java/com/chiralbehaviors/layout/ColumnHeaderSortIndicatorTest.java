// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.chiralbehaviors.layout.query.ColumnSortHandler;
import com.chiralbehaviors.layout.query.InteractionHandler;
import com.chiralbehaviors.layout.query.LayoutQueryState;
import com.chiralbehaviors.layout.style.Style;
import com.chiralbehaviors.layout.table.ColumnHeader;
import com.chiralbehaviors.layout.table.TableHeader;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Tests for Kramer-a32l (sort arrow rendering) and Kramer-e856 (idempotent
 * handler guard).
 */
@ExtendWith(ApplicationExtension.class)
class ColumnHeaderSortIndicatorTest {

    @Start
    void start(Stage stage) {
        stage.setScene(new Scene(new Pane(), 400, 300));
        stage.show();
    }

    // -------------------------------------------------------------------
    // T1.1: Sort arrow rendering on ColumnHeader
    // -------------------------------------------------------------------

    @Test
    void ascendingSortShowsUpArrowAndCssClass() {
        var result = new AtomicReference<ColumnHeader>();
        Platform.runLater(() -> {
            var ch = new ColumnHeader();
            ch.updateSortIndicator("name", "name");
            result.set(ch);
        });
        WaitForAsyncUtils.waitForFxEvents();

        ColumnHeader ch = result.get();
        assertNotNull(ch);
        assertEquals("▲", findSortArrowText(ch));
        assertTrue(ch.getStyleClass().contains("sorted-asc"),
                   "ascending sort must add sorted-asc CSS class");
        assertFalse(ch.getStyleClass().contains("sorted-desc"),
                    "ascending sort must not have sorted-desc CSS class");
    }

    @Test
    void descendingSortShowsDownArrowAndCssClass() {
        var result = new AtomicReference<ColumnHeader>();
        Platform.runLater(() -> {
            var ch = new ColumnHeader();
            ch.updateSortIndicator("-name", "name");
            result.set(ch);
        });
        WaitForAsyncUtils.waitForFxEvents();

        ColumnHeader ch = result.get();
        assertEquals("▼", findSortArrowText(ch));
        assertTrue(ch.getStyleClass().contains("sorted-desc"));
        assertFalse(ch.getStyleClass().contains("sorted-asc"));
    }

    @Test
    void nullSortClearsArrowAndCssClasses() {
        var result = new AtomicReference<ColumnHeader>();
        Platform.runLater(() -> {
            var ch = new ColumnHeader();
            // First set ascending, then clear
            ch.updateSortIndicator("name", "name");
            ch.updateSortIndicator(null, "name");
            result.set(ch);
        });
        WaitForAsyncUtils.waitForFxEvents();

        ColumnHeader ch = result.get();
        assertEquals("", findSortArrowText(ch));
        assertFalse(ch.getStyleClass().contains("sorted-asc"));
        assertFalse(ch.getStyleClass().contains("sorted-desc"));
    }

    @Test
    void sortCycleAscDescClear() {
        var result = new AtomicReference<ColumnHeader>();
        Platform.runLater(() -> {
            var ch = new ColumnHeader();

            // Click 1: ascending
            ch.updateSortIndicator("id", "id");
            assertEquals("▲", findSortArrowText(ch));
            assertTrue(ch.getStyleClass().contains("sorted-asc"));

            // Click 2: descending
            ch.updateSortIndicator("-id", "id");
            assertEquals("▼", findSortArrowText(ch));
            assertTrue(ch.getStyleClass().contains("sorted-desc"));
            assertFalse(ch.getStyleClass().contains("sorted-asc"));

            // Click 3: cleared
            ch.updateSortIndicator(null, "id");
            assertEquals("", findSortArrowText(ch));
            assertFalse(ch.getStyleClass().contains("sorted-asc"));
            assertFalse(ch.getStyleClass().contains("sorted-desc"));

            result.set(ch);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(result.get());
    }

    @Test
    void unrelatedSortFieldDoesNotShowArrow() {
        var result = new AtomicReference<ColumnHeader>();
        Platform.runLater(() -> {
            var ch = new ColumnHeader();
            // sortFields is "other" but fieldName is "name" — no match
            ch.updateSortIndicator("other", "name");
            result.set(ch);
        });
        WaitForAsyncUtils.waitForFxEvents();

        ColumnHeader ch = result.get();
        assertEquals("", findSortArrowText(ch));
        assertFalse(ch.getStyleClass().contains("sorted-asc"));
        assertFalse(ch.getStyleClass().contains("sorted-desc"));
    }

    // -------------------------------------------------------------------
    // T1.2: Idempotent handler guard on ColumnSortHandler
    // -------------------------------------------------------------------

    @Test
    void installIsIdempotent() {
        var result = new AtomicReference<Integer>();
        Platform.runLater(() -> {
            Style style = new Style();
            var queryState = new LayoutQueryState(style);
            var handler = new InteractionHandler(queryState);
            var sortHandler = new ColumnSortHandler(handler, queryState);

            HBox header = new HBox();
            // Add some child nodes to simulate column headers
            var ch1 = new ColumnHeader();
            var ch2 = new ColumnHeader();
            header.getChildren().addAll(ch1, ch2);

            var paths = List.of(
                new SchemaPath("root", "col1"),
                new SchemaPath("root", "col2")
            );

            // Install three times — should only add handler once
            sortHandler.install(header, paths);
            sortHandler.install(header, paths);
            sortHandler.install(header, paths);

            // Count MOUSE_CLICKED handlers — reflection-free check via
            // the property marker
            result.set(countMouseClickedHandlers(header));
        });
        WaitForAsyncUtils.waitForFxEvents();

        // If idempotent, only 1 handler should be installed
        assertEquals(1, result.get(),
                     "install() called 3 times should only install 1 handler");
    }

    @Test
    void updateIndicatorsSetsCorrectState() {
        Platform.runLater(() -> {
            Style style = new Style();
            var queryState = new LayoutQueryState(style);
            var handler = new InteractionHandler(queryState);
            var sortHandler = new ColumnSortHandler(handler, queryState);

            var path1 = new SchemaPath("root", "name");
            var path2 = new SchemaPath("root", "value");

            // Set ascending sort on "name"
            queryState.setSortFields(path1, "name");

            HBox header = new HBox();
            var ch1 = new ColumnHeader();
            var ch2 = new ColumnHeader();
            header.getChildren().addAll(ch1, ch2);

            sortHandler.updateIndicators(header, List.of(path1, path2));

            assertEquals("▲", findSortArrowText(ch1),
                         "sorted column should show ▲");
            assertTrue(ch1.getStyleClass().contains("sorted-asc"));
            assertEquals("", findSortArrowText(ch2),
                         "unsorted column should be empty");
            assertFalse(ch2.getStyleClass().contains("sorted-asc"));
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private static String findSortArrowText(ColumnHeader ch) {
        for (Node child : ch.getChildren()) {
            if (child instanceof Label label
                && label.getStyleClass().contains("sort-arrow")) {
                return label.getText();
            }
        }
        return "";
    }

    /**
     * Count how many times install was called by checking the idempotent
     * marker. Returns 1 if the guard is working, or the actual count if
     * we tracked it (we just check via a call-counter property).
     */
    private static int countMouseClickedHandlers(HBox header) {
        Object count = header.getProperties().get("kramer.sortHandlerInstallCount");
        return count instanceof Integer i ? i : 0;
    }
}
