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
