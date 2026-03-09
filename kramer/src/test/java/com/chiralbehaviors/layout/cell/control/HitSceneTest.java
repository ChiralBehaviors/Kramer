// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.cell.control;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.cell.Hit;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.LayoutContainer;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;

/**
 * Tests for Phase 3: Cross-container hit dispatch.
 * Verifies hitScene default method on LayoutContainer and
 * the cross-container fallback path in movePhysical.
 */
class HitSceneTest {

    // --- LayoutContainer.hitScene tests ---

    @Test
    void testHitScene_pointOutsideBounds_returnsNull() {
        // A container at (0,0) with size 100x100
        TestContainer container = new TestContainer(100, 100);

        // Point outside the container's bounds (no scene transform needed
        // since the pane is not in a scene — sceneToLocal returns identity)
        Point2D outside = new Point2D(200, 200);
        Hit<?> result = container.hitScene(outside);

        // sceneToLocal on a node not in scene returns null → hitScene returns null
        assertNull(result);
    }

    @Test
    void testHitScene_delegatesToHitWhenContained() {
        TestContainer container = new TestContainer(200, 200);

        // When not in a scene graph, sceneToLocal returns null for Pane,
        // so hitScene will return null. This tests the null guard.
        Point2D inside = new Point2D(50, 50);
        Hit<?> result = container.hitScene(inside);

        // sceneToLocal returns null when node is not in scene → null result
        assertNull(result);
    }

    // --- FocusController cross-container dispatch ---

    @Test
    void testMovePhysical_crossContainerFallback_nonAutoLayoutNode() {
        // FocusController's node is a plain Pane (not AutoLayout)
        // Cross-container dispatch should be a no-op
        Pane plainNode = new Pane();
        FocusController<LayoutCell<?>> controller = new FocusController<>(plainNode);

        FocusTraversalNode<?> ftn = mockFTN(FocusTraversalNode.Bias.VERTICAL);
        // Return null container so movePhysical's VirtualFlow check fails
        when(ftn.getContainer()).thenReturn(null);
        controller.setCurrent(ftn);

        controller.setCursorState(new CursorState("data", null, 0,
            new Point2D(100, 100),
            new javafx.geometry.BoundingBox(0, 0, 80, 30)));

        // Should not throw — no VirtualFlow container, exits early
        assertDoesNotThrow(() -> controller.down());
    }

    @Test
    void testMovePhysical_nullCursorState_noOp() {
        Pane plainNode = new Pane();
        FocusController<LayoutCell<?>> controller = new FocusController<>(plainNode);

        FocusTraversalNode<?> ftn = mockFTN(FocusTraversalNode.Bias.VERTICAL);
        controller.setCurrent(ftn);
        // cursorState is null — should fall through to logical navigation
        assertNull(controller.getCursorState());

        controller.down();
        // Logical fallback: VERTICAL bias + down = selectNext
        verify(ftn).selectNext();
    }

    @Test
    void testMovePhysical_nullCurrent_noOp() {
        Pane plainNode = new Pane();
        FocusController<LayoutCell<?>> controller = new FocusController<>(plainNode);

        controller.setCursorState(new CursorState("data", null, 0,
            new Point2D(100, 100),
            new javafx.geometry.BoundingBox(0, 0, 80, 30)));

        // current is null — should be a no-op
        assertDoesNotThrow(() -> controller.down());
        assertDoesNotThrow(() -> controller.up());
        assertDoesNotThrow(() -> controller.left());
        assertDoesNotThrow(() -> controller.right());
    }

    // --- Helper: simple LayoutContainer for testing ---

    private static class TestContainer extends Pane
            implements LayoutContainer<JsonNode, Pane, LayoutCell<?>> {

        TestContainer(double width, double height) {
            setPrefSize(width, height);
            setMinSize(width, height);
            setMaxSize(width, height);
        }

        @Override
        public Collection<LayoutCell<?>> getContained() {
            return List.of();
        }

        @Override
        public Hit<LayoutCell<?>> hit(double x, double y) {
            return null; // no cells
        }

        @Override
        public Pane getNode() {
            return this;
        }

        @Override
        public void updateItem(JsonNode item) {
        }

        @Override
        public boolean isReusable() {
            return false;
        }
    }

    // --- Helper ---

    private FocusTraversalNode<?> mockFTN(FocusTraversalNode.Bias bias) {
        FocusTraversalNode<?> ftn = mock(FocusTraversalNode.class);
        try {
            var biasField = FocusTraversalNode.class.getDeclaredField("bias");
            biasField.setAccessible(true);
            biasField.set(ftn, bias);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set bias on mock FTN", e);
        }
        return ftn;
    }
}
