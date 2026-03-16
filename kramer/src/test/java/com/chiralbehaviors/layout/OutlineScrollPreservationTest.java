// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Tests that scroll position is preserved by the ifPresentOrElse pattern used
 * in Outline.updateItem() and NestedRow.updateItem().
 *
 * These tests operate directly on VirtualFlow (the base class) to verify the
 * scroll-preservation logic without requiring the full Outline/NestedRow
 * constructor infrastructure. The pattern under test is:
 *
 *   OptionalInt saved = getFirstVisibleIndex();
 *   items.setAll(newItems);
 *   if (!items.isEmpty()) {
 *       saved.ifPresentOrElse(
 *           idx -> showAsFirst(Math.min(idx, items.size() - 1)),
 *           () -> showAsFirst(0));
 *   }
 */
@ExtendWith(ApplicationExtension.class)
class OutlineScrollPreservationTest {

    private static final double CELL_HEIGHT = 30.0;
    private static final double CELL_WIDTH  = 300.0;
    private static final double VF_HEIGHT   = 100.0;

    private VirtualFlow<TestCell> vf;
    private StackPane             root;

    @Start
    void start(Stage stage) {
        root = new StackPane();
        ObservableList<JsonNode> items = FXCollections.observableArrayList();
        vf = new VirtualFlow<>("default.css", CELL_WIDTH, CELL_HEIGHT,
                               items, (item, focus) -> new TestCell(CELL_WIDTH, CELL_HEIGHT),
                               null, Collections.emptyList());
        root.getChildren().add(vf);
        stage.setScene(new Scene(root, CELL_WIDTH + 20, VF_HEIGHT));
        stage.show();
    }

    /**
     * First population: getFirstVisibleIndex() returns empty (nothing rendered),
     * so ifPresentOrElse must call showAsFirst(0).
     * After layout, first visible index should be 0.
     */
    @Test
    void firstPopulation_showsFromZero() {
        List<JsonNode> data = buildList(10);

        runOnFxAndWait(() -> applyScrollPreservingUpdate(vf, data));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            OptionalInt idx = vf.getFirstVisibleIndex();
            assertTrue(idx.isPresent(), "First visible index should be present after population");
            assertEquals(0, idx.getAsInt(), "First population should start at index 0");
        });
    }

    /**
     * After scrolling to index 3, a second updateItem with the same-size data
     * must preserve the scroll position at 3 (not reset to 0).
     */
    @Test
    void updateItem_preservesScrollAfterScroll() {
        List<JsonNode> data = buildList(10);

        // Initial population
        runOnFxAndWait(() -> applyScrollPreservingUpdate(vf, data));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        // Scroll to index 3
        runOnFxAndWait(() -> vf.showAsFirst(3));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        // Confirm scroll position
        runOnFxAndWait(() -> {
            OptionalInt idx = vf.getFirstVisibleIndex();
            assertTrue(idx.isPresent(), "Index should be present after scrolling");
            assertEquals(3, idx.getAsInt(), "Should be at index 3 before second updateItem");
        });

        // Re-populate with same-size data (the key scenario)
        runOnFxAndWait(() -> applyScrollPreservingUpdate(vf, buildList(10)));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        // Scroll position must be preserved
        runOnFxAndWait(() -> {
            OptionalInt idx = vf.getFirstVisibleIndex();
            assertTrue(idx.isPresent(), "Index should be present after second updateItem");
            assertEquals(3, idx.getAsInt(),
                         "Scroll position should be preserved after updateItem with same-size data");
        });
    }

    /**
     * When new data has fewer items than the saved scroll index, the index must
     * be clamped to items.size()-1 (no IndexOutOfBoundsException).
     */
    @Test
    void updateItem_clampsIndexWhenDataShrinks() {
        // Start with 10 items, scroll to index 7
        runOnFxAndWait(() -> applyScrollPreservingUpdate(vf, buildList(10)));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> vf.showAsFirst(7));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        // Update with only 3 items — saved index 7 is out of range
        runOnFxAndWait(() -> applyScrollPreservingUpdate(vf, buildList(3)));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            OptionalInt idx = vf.getFirstVisibleIndex();
            assertTrue(idx.isPresent(), "Index should be present after shrink update");
            assertTrue(idx.getAsInt() <= 2,
                       "Index should be clamped to max valid (2) but was " + idx.getAsInt());
        });
    }

    /**
     * Empty item list: updateItem with null/empty data should not call showAsFirst.
     * This verifies the !items.isEmpty() guard.
     */
    @Test
    void updateItem_emptyData_noShowAsFirst() {
        // Populate then clear
        runOnFxAndWait(() -> applyScrollPreservingUpdate(vf, buildList(5)));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        // Update with empty list — should not throw, items should be empty
        runOnFxAndWait(() -> applyScrollPreservingUpdate(vf, Collections.emptyList()));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            assertEquals(0, vf.getItemCount(), "Item count should be 0 after empty update");
        });
    }

    // ---- Helpers ----

    /**
     * Applies the scroll-preserving update pattern from Outline.updateItem()
     * and NestedRow.updateItem().
     */
    private static void applyScrollPreservingUpdate(VirtualFlow<?> flow, List<JsonNode> newItems) {
        OptionalInt savedIndex = flow.getFirstVisibleIndex();
        flow.getItems().setAll(newItems);
        if (!flow.getItems().isEmpty()) {
            savedIndex.ifPresentOrElse(
                idx -> flow.showAsFirst(Math.min(idx, flow.getItems().size() - 1)),
                () -> flow.showAsFirst(0)
            );
        }
    }

    private static List<JsonNode> buildList(int size) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arr = mapper.createArrayNode();
        for (int i = 0; i < size; i++) {
            arr.add("item-" + i);
        }
        List<JsonNode> list = new ArrayList<>();
        arr.forEach(list::add);
        return list;
    }

    private static void runOnFxAndWait(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    action.run();
                } finally {
                    latch.countDown();
                }
            });
            try {
                assertTrue(latch.await(5, TimeUnit.SECONDS),
                           "Timed out waiting for FX thread");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted waiting for FX thread");
            }
        }
    }

    static class TestCell extends Pane implements LayoutCell<Pane> {
        TestCell(double width, double height) {
            setPrefSize(width, height);
            setMinSize(width, height);
            setMaxSize(width, height);
        }

        @Override
        public Pane getNode() {
            return this;
        }

        @Override
        public boolean isReusable() {
            return true;
        }
    }
}
