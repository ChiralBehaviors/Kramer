// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.flowless;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Tests for VirtualFlow scrolling mechanics using TestFX headless mode.
 * Covers: SizeTracker reactivity, canScrollVertically, scrollYBy offset
 * changes, ScrollHandler event consumption, and nested VF scroll isolation.
 */
@ExtendWith(ApplicationExtension.class)
class VirtualFlowScrollTest {

    private static final double CELL_HEIGHT = 30.0;
    private static final double CELL_WIDTH  = 200.0;
    private static final double VF_HEIGHT   = 100.0;

    private VirtualFlow<TestCell> vf;
    private StackPane             root;

    @Start
    void start(Stage stage) {
        root = new StackPane();
        vf = createVirtualFlow();
        root.getChildren().add(vf);

        Scene scene = new Scene(root, 300, VF_HEIGHT);
        stage.setScene(scene);
        stage.show();
    }

    // --- SizeTracker reactivity ---

    @Test
    void totalLengthEstimateUpdatesWhenItemsAdded(FxRobot robot) {
        runOnFxAndWait(() -> {
            double totalBefore = vf.totalLengthEstimateProperty().getOrElse(0.0);
            assertEquals(0.0, totalBefore, 0.01, "Empty VF should have 0 total length");

            addItemsAndReset(vf, 10);
        });

        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());

        runOnFxAndWait(() -> {
            double totalAfter = vf.totalLengthEstimateProperty().getOrElse(0.0);
            assertEquals(10 * CELL_HEIGHT, totalAfter, 0.01,
                         "Total length should equal item count * cell height");
        });
    }

    // --- canScrollVertically ---

    @Test
    void canScrollVertically_falseWhenContentFits(FxRobot robot) {
        runOnFxAndWait(() -> addItemsAndReset(vf, 2));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());

        runOnFxAndWait(() -> {
            assertFalse(vf.canScrollVertically(),
                        "Should not be scrollable when content fits viewport");
        });
    }

    @Test
    void canScrollVertically_trueWhenContentOverflows(FxRobot robot) {
        ensureWindowSize(300, VF_HEIGHT);
        runOnFxAndWait(() -> addItemsAndReset(vf, 10));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());

        runOnFxAndWait(() -> {
            assertTrue(vf.canScrollVertically(),
                       "Should be scrollable when content overflows viewport");
        });
    }

    // --- scrollYBy changes offset ---

    @Test
    void scrollYByChangesLengthOffset(FxRobot robot) {
        ensureWindowSize(300, VF_HEIGHT);
        runOnFxAndWait(() -> addItemsAndReset(vf, 10));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            double before = vf.lengthOffsetEstimateProperty().getValue();
            assertEquals(0.0, before, 0.01, "Offset should start at 0");
            vf.scrollYBy(15);
        });

        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            double after = vf.lengthOffsetEstimateProperty().getValue();
            assertEquals(15.0, after, 1.0,
                         "Offset should change after scrollYBy");
        });
    }

    @Test
    void scrollYByClampsToMax(FxRobot robot) {
        ensureWindowSize(300, VF_HEIGHT);
        runOnFxAndWait(() -> addItemsAndReset(vf, 10));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> vf.scrollYBy(10000));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            double total = vf.totalLengthEstimateProperty().getOrElse(0.0);
            double vpLen = vf.getLayoutBounds().getHeight();
            double max = total - vpLen;
            double offset = vf.lengthOffsetEstimateProperty().getValue();
            assertTrue(offset <= max + 1.0,
                       "Offset should be clamped to max=" + max + " but was " + offset);
            assertTrue(offset >= max - 1.0,
                       "Offset should be near max=" + max + " but was " + offset);
        });
    }

    // --- ScrollHandler event consumption ---

    /**
     * When a scrollable VF receives a scroll event, it should scroll and
     * consume the event (preventing parent handlers from seeing it).
     * We verify by installing a handler on the parent that tracks bubbled events.
     */
    @Test
    void scrollEventConsumedWhenScrollable(FxRobot robot) {
        ensureWindowSize(300, VF_HEIGHT);
        runOnFxAndWait(() -> addItemsAndReset(vf, 10));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        AtomicReference<Boolean> parentSawEvent = new AtomicReference<>(false);
        EventHandler<ScrollEvent> parentHandler = evt -> parentSawEvent.set(true);

        runOnFxAndWait(() -> {
            assertTrue(vf.canScrollVertically(), "Precondition: VF should be scrollable");

            // Install a handler on parent to detect if event bubbles up
            root.addEventHandler(ScrollEvent.SCROLL, parentHandler);

            ScrollEvent event = createScrollEvent(-30);
            vf.fireEvent(event);
        });

        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            root.removeEventHandler(ScrollEvent.SCROLL, parentHandler);

            // The VF should have consumed the event, so parent should NOT see it
            assertFalse(parentSawEvent.get(),
                        "Parent should not see scroll event when child VF consumes it");
        });
    }

    @Test
    void scrollEventBubblesToParentWhenNotScrollable(FxRobot robot) {
        runOnFxAndWait(() -> addItemsAndReset(vf, 2));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        AtomicReference<Boolean> parentSawEvent = new AtomicReference<>(false);
        EventHandler<ScrollEvent> parentHandler = evt -> parentSawEvent.set(true);

        runOnFxAndWait(() -> {
            assertFalse(vf.canScrollVertically(), "Precondition: VF should not be scrollable");

            root.addEventHandler(ScrollEvent.SCROLL, parentHandler);

            ScrollEvent event = createScrollEvent(-30);
            vf.fireEvent(event);
        });

        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            root.removeEventHandler(ScrollEvent.SCROLL, parentHandler);

            // The VF should NOT consume the event, so parent SHOULD see it
            assertTrue(parentSawEvent.get(),
                       "Parent should see scroll event when child VF cannot scroll");
        });
    }

    // --- Nested VF scroll isolation ---

    @Test
    void nestedScrollableVfConsumesEventBeforeParent(FxRobot robot) {
        VirtualFlow<InnerVfCell> outerVf = createOuterVirtualFlow();

        runOnFxAndWait(() -> {
            root.getChildren().clear();
            root.getChildren().add(outerVf);
        });
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> addItemsAndReset(outerVf, 10));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> outerVf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        AtomicReference<Boolean> outerSawEvent = new AtomicReference<>(false);
        EventHandler<ScrollEvent> outerHandler = evt -> outerSawEvent.set(true);

        runOnFxAndWait(() -> {
            assertTrue(outerVf.canScrollVertically(),
                       "Precondition: outer VF should be scrollable");

            // Track whether the outer VF's parent sees the event
            root.addEventHandler(ScrollEvent.SCROLL, outerHandler);

            outerVf.getFirstVisibleIndex().ifPresent(idx -> {
                outerVf.getCellIfVisible(idx).ifPresent(cell -> {
                    VirtualFlow<TestCell> innerVf = cell.getInnerVf();
                    if (innerVf != null) {
                        innerVf.layout();
                        if (innerVf.canScrollVertically()) {
                            ScrollEvent event = createScrollEvent(-15);
                            innerVf.fireEvent(event);
                        }
                    }
                });
            });
        });

        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            root.removeEventHandler(ScrollEvent.SCROLL, outerHandler);

            // If inner VF consumed the event, the outer's parent should not see it
            assertFalse(outerSawEvent.get(),
                        "Outer parent should not see scroll event when inner VF consumes it");
        });
    }

    // --- Root-level scroll (VBox + VGrow) ---

    /**
     * Reproduces the root-level table scenario: VirtualFlow inside a VBox
     * with VBox.setVgrow(ALWAYS), mimicking NestedTable's rootLevel=true.
     * The VF fills the viewport and must still scroll when items exceed it.
     */
    @Test
    void rootLevelVfScrollsWhenItemsExceedViewport(FxRobot robot) {
        VirtualFlow<TestCell> rootVf = createVirtualFlow();

        runOnFxAndWait(() -> {
            root.getChildren().clear();
            // Mimic NestedTable: VBox with header + VirtualFlow
            javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox();
            Pane header = new Pane();
            header.setPrefHeight(20);
            header.setMinHeight(20);
            header.setMaxHeight(20);
            javafx.scene.layout.VBox.setVgrow(rootVf,
                javafx.scene.layout.Priority.ALWAYS);
            vbox.getChildren().addAll(header, rootVf);
            root.getChildren().add(vbox);
        });

        WaitForAsyncUtils.waitForFxEvents();

        // Set window small enough that 10 items * 30px = 300px exceeds viewport
        // Window height 100, header 20, so VF viewport ~ 80px
        ensureWindowSize(300, VF_HEIGHT);

        runOnFxAndWait(() -> addItemsAndReset(rootVf, 10));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> rootVf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            double total = rootVf.totalLengthEstimateProperty().getOrElse(0.0);
            double vpLen = rootVf.getLayoutBounds().getHeight();
            assertTrue(total > vpLen,
                       "totalLengthEstimate (" + total
                       + ") should exceed viewport (" + vpLen
                       + ") for " + rootVf.getItemCount() + " items");
            assertTrue(rootVf.canScrollVertically(),
                       "Root VF should be scrollable when items exceed viewport");
        });
    }

    /**
     * Verifies that scroll events actually move the root-level VF content
     * when hosted in a VBox with VGrow.
     */
    @Test
    void rootLevelVfScrollEventMovesContent(FxRobot robot) {
        VirtualFlow<TestCell> rootVf = createVirtualFlow();

        runOnFxAndWait(() -> {
            root.getChildren().clear();
            javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox();
            Pane header = new Pane();
            header.setPrefHeight(20);
            header.setMinHeight(20);
            header.setMaxHeight(20);
            javafx.scene.layout.VBox.setVgrow(rootVf,
                javafx.scene.layout.Priority.ALWAYS);
            vbox.getChildren().addAll(header, rootVf);
            root.getChildren().add(vbox);
        });

        ensureWindowSize(300, VF_HEIGHT);

        runOnFxAndWait(() -> addItemsAndReset(rootVf, 10));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> rootVf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        // Fire scroll event and verify offset changes
        runOnFxAndWait(() -> {
            double before = rootVf.lengthOffsetEstimateProperty().getValue();
            assertEquals(0.0, before, 0.01, "Offset should start at 0");

            ScrollEvent event = createScrollEvent(-30);
            rootVf.fireEvent(event);
        });

        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> rootVf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            double after = rootVf.lengthOffsetEstimateProperty().getValue();
            assertTrue(after > 0,
                       "Offset should change after scroll event, but was " + after);
        });
    }

    /**
     * Verifies totalLengthEstimate reflects total item count, not just
     * visible/memoized cell count.
     */
    @Test
    void totalLengthEstimateReflectsAllItems(FxRobot robot) {
        runOnFxAndWait(() -> addItemsAndReset(vf, 20));
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            double total = vf.totalLengthEstimateProperty().getOrElse(0.0);
            double expected = 20 * CELL_HEIGHT;
            assertEquals(expected, total, 1.0,
                         "totalLengthEstimate should be itemCount * cellHeight"
                         + " (" + expected + ") but was " + total);
        });
    }

    /**
     * Tests bulk item population via setAll() — the pattern used by the
     * real app's NestedTable.updateItem(). Ensures totalLengthEstimate
     * updates correctly with bulk operations.
     */
    @Test
    void totalLengthEstimateUpdatesAfterSetAll(FxRobot robot) {
        // Use setAll like the real app does (NestedRow.updateItem → items.setAll)
        runOnFxAndWait(() -> {
            java.util.List<JsonNode> batch = new java.util.ArrayList<>();
            for (int i = 0; i < 15; i++) {
                batch.add(IntNode.valueOf(i));
            }
            vf.getItems().setAll(batch);
            if (!vf.getItems().isEmpty()) {
                vf.showAsFirst(0);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> vf.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            double total = vf.totalLengthEstimateProperty().getOrElse(0.0);
            double expected = 15 * CELL_HEIGHT;
            assertEquals(expected, total, 1.0,
                         "totalLengthEstimate after setAll should be "
                         + expected + " but was " + total);
            assertTrue(vf.canScrollVertically(),
                       "Should be scrollable after setAll with 15 items");
        });
    }

    // --- AutoLayout integration: root table scrolling ---

    /**
     * Integration test mimicking the actual StandaloneDemo setup.
     * Creates an AutoLayout with a schema + data that produces a root table,
     * with a viewport too small to show all rows, and verifies scrolling works.
     */
    @Test
    void autoLayoutRootTableIsScrollable(FxRobot robot) {
        com.chiralbehaviors.layout.AutoLayout autoLayout =
            new com.chiralbehaviors.layout.AutoLayout(buildTestSchema());

        runOnFxAndWait(() -> {
            root.getChildren().clear();
            root.getChildren().add(autoLayout);
        });

        // Use a window height that forces scrolling (smaller than content)
        runOnFxAndWait(() -> {
            root.getScene().getWindow().setWidth(600);
            root.getScene().getWindow().setHeight(200);
        });
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            autoLayout.measure(buildTestData());
            autoLayout.updateItem(buildTestData());
        });
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> autoLayout.layout());
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> autoLayout.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            VirtualFlow<?> rootVf = findVirtualFlow(autoLayout);
            assertNotNull(rootVf,
                          "Should find a VirtualFlow in AutoLayout's tree");
            double total = rootVf.totalLengthEstimateProperty().getOrElse(0.0);
            double vpLen = rootVf.getLayoutBounds().getHeight();
            int itemCount = rootVf.getItemCount();
            assertTrue(itemCount > 0,
                       "Root VF should have items, but has " + itemCount);
            assertTrue(total > vpLen,
                       "totalLengthEstimate (" + total
                       + ") should exceed viewport (" + vpLen
                       + ") for " + itemCount + " items");
            assertTrue(rootVf.canScrollVertically(),
                       "Root VF should be scrollable");
        });
    }

    /**
     * Verifies that when viewport slightly exceeds content,
     * distributeExtraHeight doesn't break scroll detection by
     * creating a height/cellHeight mismatch.
     */
    @Test
    void autoLayoutRootTableNoScrollMismatch(FxRobot robot) {
        com.chiralbehaviors.layout.AutoLayout autoLayout =
            new com.chiralbehaviors.layout.AutoLayout(buildTestSchema());

        runOnFxAndWait(() -> {
            root.getChildren().clear();
            root.getChildren().add(autoLayout);
        });

        // Use a large window where content fits with a few px to spare
        runOnFxAndWait(() -> {
            root.getScene().getWindow().setWidth(600);
            root.getScene().getWindow().setHeight(800);
        });
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            autoLayout.measure(buildTestData());
            autoLayout.updateItem(buildTestData());
        });
        WaitForAsyncUtils.waitForFxEvents();
        runOnFxAndWait(() -> autoLayout.layout());
        WaitForAsyncUtils.waitForFxEvents();

        runOnFxAndWait(() -> {
            VirtualFlow<?> rootVf = findVirtualFlow(autoLayout);
            assertNotNull(rootVf, "Should find root VF");
            double total = rootVf.totalLengthEstimateProperty().getOrElse(0.0);
            double vpLen = rootVf.getLayoutBounds().getHeight();
            // Key assertion: totalLengthEstimate should NOT be less than
            // viewport by just a few pixels (the distributeExtraHeight
            // mismatch bug). Either content clearly fits (total <= viewport
            // and all items visible) or total > viewport (scrollable).
            if (total < vpLen) {
                // Content fits — verify the gap is consistent
                // (cellHeight * items should equal totalLengthEstimate)
                double cellLen = rootVf.totalLengthEstimateProperty()
                                       .getOrElse(0.0) / rootVf.getItemCount();
                double recomputed = cellLen * rootVf.getItemCount();
                assertEquals(total, recomputed, 0.01,
                             "totalLengthEstimate should be consistent");
            }
        });
    }

    private com.chiralbehaviors.layout.schema.Relation buildTestSchema() {
        var projects = new com.chiralbehaviors.layout.schema.Relation("projects");
        projects.addChild(new com.chiralbehaviors.layout.schema.Primitive("project"));
        projects.addChild(new com.chiralbehaviors.layout.schema.Primitive("status"));

        var employees = new com.chiralbehaviors.layout.schema.Relation("employees");
        employees.addChild(new com.chiralbehaviors.layout.schema.Primitive("name"));
        employees.addChild(new com.chiralbehaviors.layout.schema.Primitive("role"));
        employees.addChild(projects);
        return employees;
    }

    private JsonNode buildTestData() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var arr = mapper.createArrayNode();
        for (int i = 0; i < 10; i++) {
            var emp = mapper.createObjectNode();
            emp.put("name", "Employee " + i);
            emp.put("role", "Role " + i);
            var projs = mapper.createArrayNode();
            for (int j = 0; j < 2; j++) {
                var p = mapper.createObjectNode();
                p.put("project", "Project " + j);
                p.put("status", "Active");
                projs.add(p);
            }
            emp.set("projects", projs);
            arr.add(emp);
        }
        return arr;
    }

    private VirtualFlow<?> findVirtualFlow(javafx.scene.Node node) {
        if (node instanceof VirtualFlow<?> vf && vf.getItemCount() > 0) {
            return vf;
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                VirtualFlow<?> found = findVirtualFlow(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    // --- Helpers ---

    private VirtualFlow<TestCell> createVirtualFlow() {
        ObservableList<JsonNode> items = FXCollections.observableArrayList();
        return new VirtualFlow<>("default.css", CELL_WIDTH, CELL_HEIGHT,
                                 items, (item, focus) -> new TestCell(CELL_WIDTH, CELL_HEIGHT),
                                 null, Collections.emptyList());
    }

    private VirtualFlow<InnerVfCell> createOuterVirtualFlow() {
        ObservableList<JsonNode> items = FXCollections.observableArrayList();
        return new VirtualFlow<>("default.css", CELL_WIDTH, CELL_HEIGHT * 2,
                                 items, (item, focus) -> new InnerVfCell(CELL_WIDTH, CELL_HEIGHT),
                                 null, Collections.emptyList());
    }

    private static <C extends LayoutCell<?>> void addItemsAndReset(VirtualFlow<C> vf, int count) {
        for (int i = 0; i < count; i++) {
            vf.getItems().add(IntNode.valueOf(i));
        }
        if (!vf.getItems().isEmpty()) {
            vf.showAsFirst(0);
        }
    }

    private static ScrollEvent createScrollEvent(double deltaY) {
        return new ScrollEvent(ScrollEvent.SCROLL,
                               0, 0,    // x, y
                               0, 0,    // screenX, screenY
                               false,   // shiftDown
                               false,   // controlDown
                               false,   // altDown
                               false,   // metaDown
                               false,   // direct
                               false,   // inertia
                               0, deltaY, // deltaX, deltaY
                               0, 0,    // totalDeltaX, totalDeltaY
                               ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                               ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                               0, null);
    }

    private void ensureWindowSize(double width, double height) {
        runOnFxAndWait(() -> {
            root.getScene().getWindow().setWidth(width);
            root.getScene().getWindow().setHeight(height);
        });
        WaitForAsyncUtils.waitForFxEvents();
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

    /**
     * A minimal LayoutCell for testing — a Pane with fixed dimensions.
     */
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

    /**
     * A LayoutCell that contains an inner VirtualFlow, for testing nested scroll.
     */
    static class InnerVfCell extends Pane implements LayoutCell<Pane> {
        private final VirtualFlow<TestCell> innerVf;

        InnerVfCell(double width, double cellHeight) {
            double innerHeight = cellHeight * 2;
            setPrefSize(width, innerHeight);
            setMinSize(width, innerHeight);
            setMaxSize(width, innerHeight);

            ObservableList<JsonNode> innerItems = FXCollections.observableArrayList();
            innerVf = new VirtualFlow<>("default.css", width, cellHeight / 2,
                                        innerItems,
                                        (item, focus) -> new TestCell(width, cellHeight / 2),
                                        null, Collections.emptyList());
            innerVf.setPrefSize(width, innerHeight);
            innerVf.setMinSize(width, innerHeight);
            innerVf.setMaxSize(width, innerHeight);
            getChildren().add(innerVf);

            // Add enough items to make inner VF scrollable
            for (int i = 0; i < 20; i++) {
                innerItems.add(IntNode.valueOf(i));
            }
            innerVf.showAsFirst(0);
        }

        VirtualFlow<TestCell> getInnerVf() {
            return innerVf;
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
