// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Tests for list diffing / incremental patching in updateItem().
 *
 * The patching strategy under test:
 *   if (list.size() == items.size()) {
 *       for (int i = 0; i < list.size(); i++) {
 *           if (!list.get(i).equals(items.get(i))) {
 *               items.set(i, list.get(i));
 *           }
 *       }
 *   } else {
 *       items.setAll(list);
 *   }
 */
@ExtendWith(ApplicationExtension.class)
class ListDiffUpdateTest {

    private static final double CELL_HEIGHT = 30.0;
    private static final double CELL_WIDTH  = 300.0;
    private static final double VF_HEIGHT   = 100.0;

    private VirtualFlow<TestCell> vf;

    @Start
    void start(Stage stage) {
        ObservableList<JsonNode> items = FXCollections.observableArrayList();
        vf = new VirtualFlow<>("default.css", CELL_WIDTH, CELL_HEIGHT,
                               items, (item, focus) -> new TestCell(CELL_WIDTH, CELL_HEIGHT),
                               null, Collections.emptyList());
        StackPane root = new StackPane(vf);
        stage.setScene(new Scene(root, CELL_WIDTH + 20, VF_HEIGHT));
        stage.show();
    }

    /**
     * Same-size update with no changed entries: list.set() must never be called.
     */
    @Test
    void sameSizeNoChanges_triggersZeroItemSets() {
        List<JsonNode> initial = buildList("a", "b", "c");

        runOnFxAndWait(() -> applyPatchingUpdate(vf, initial));
        WaitForAsyncUtils.waitForFxEvents();

        AtomicInteger setCount = new AtomicInteger(0);
        runOnFxAndWait(() -> {
            vf.getItems().addListener((ListChangeListener<JsonNode>) change -> {
                while (change.next()) {
                    if (change.wasReplaced() || change.wasAdded() || change.wasRemoved()) {
                        setCount.incrementAndGet();
                    }
                }
            });
        });

        // Re-apply identical data — nothing should change
        runOnFxAndWait(() -> applyPatchingUpdate(vf, buildList("a", "b", "c")));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(0, setCount.get(),
                     "No item changes expected when same-size list has identical content");
    }

    /**
     * Same-size update with exactly one different entry: exactly one item.set() call.
     */
    @Test
    void sameSizeOneChange_triggersOneItemSet() {
        List<JsonNode> initial = buildList("a", "b", "c");

        runOnFxAndWait(() -> applyPatchingUpdate(vf, initial));
        WaitForAsyncUtils.waitForFxEvents();

        AtomicInteger setCount = new AtomicInteger(0);
        runOnFxAndWait(() -> {
            vf.getItems().addListener((ListChangeListener<JsonNode>) change -> {
                while (change.next()) {
                    if (change.wasReplaced()) {
                        setCount.addAndGet(change.getRemovedSize());
                    }
                }
            });
        });

        // Only item at index 1 changes
        runOnFxAndWait(() -> applyPatchingUpdate(vf, buildList("a", "X", "c")));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, setCount.get(),
                     "Exactly one item set expected when exactly one entry differs");
        runOnFxAndWait(() -> {
            assertEquals("\"X\"", vf.getItems().get(1).toString(),
                         "Changed entry should be updated to X");
        });
    }

    /**
     * Size mismatch falls back to setAll(): the whole list is replaced at once.
     */
    @Test
    void sizeMismatch_fallsBackToSetAll() {
        List<JsonNode> initial = buildList("a", "b", "c");

        runOnFxAndWait(() -> applyPatchingUpdate(vf, initial));
        WaitForAsyncUtils.waitForFxEvents();

        AtomicInteger setAllCount = new AtomicInteger(0);
        runOnFxAndWait(() -> {
            vf.getItems().addListener((ListChangeListener<JsonNode>) change -> {
                while (change.next()) {
                    // setAll produces a single permutation/replace event covering all items
                    if (change.wasReplaced() && change.getList().size() != initial.size()) {
                        setAllCount.incrementAndGet();
                    }
                }
            });
        });

        // Different size: 5 items vs 3
        runOnFxAndWait(() -> applyPatchingUpdate(vf, buildList("a", "b", "c", "d", "e")));
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            assertEquals(5, vf.getItems().size(), "Items should be replaced with 5-element list");
        });
    }

    /**
     * Empty incoming list: falls back to setAll(), items become empty. No exception.
     */
    @Test
    void emptyIncomingList_handledCorrectly() {
        List<JsonNode> initial = buildList("a", "b", "c");

        runOnFxAndWait(() -> applyPatchingUpdate(vf, initial));
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> applyPatchingUpdate(vf, Collections.emptyList()));
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            assertEquals(0, vf.getItems().size(), "Items should be empty after empty update");
        });
    }

    // ---- Helpers ----

    /**
     * The incremental patching strategy extracted from Outline/NestedRow.updateItem().
     */
    private static void applyPatchingUpdate(VirtualFlow<?> flow, List<JsonNode> list) {
        @SuppressWarnings("unchecked")
        ObservableList<JsonNode> items = (ObservableList<JsonNode>) flow.getItems();
        if (list.size() == items.size()) {
            for (int i = 0; i < list.size(); i++) {
                if (!list.get(i).equals(items.get(i))) {
                    items.set(i, list.get(i));
                }
            }
        } else {
            items.setAll(list);
        }
    }

    private static List<JsonNode> buildList(String... values) {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> list = new ArrayList<>();
        for (String v : values) {
            list.add(mapper.getNodeFactory().textNode(v));
        }
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
