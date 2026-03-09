// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.cell.control;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversalNode.Bias;

import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;

/**
 * Tests for Phase 5: Home/End keys and PAGE_UP/PAGE_DOWN cursor movement.
 */
class AdditionalKeysTest {

    private FocusController<LayoutCell<?>> controller;

    @BeforeEach
    void setUp() {
        controller = new FocusController<>(new Pane());
    }

    // --- Home/End with null current ---

    @Test
    void testHomeWithNullCurrent_noOp() {
        assertDoesNotThrow(() -> controller.home());
    }

    @Test
    void testEndWithNullCurrent_noOp() {
        assertDoesNotThrow(() -> controller.end());
    }

    // --- Home/End with non-VirtualFlow container ---

    @Test
    void testHomeWithNonVirtualFlowContainer_noOp() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        when(ftn.getContainer()).thenReturn(null);
        controller.setCurrent(ftn);

        assertDoesNotThrow(() -> controller.home());
    }

    @Test
    void testEndWithNonVirtualFlowContainer_noOp() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        when(ftn.getContainer()).thenReturn(null);
        controller.setCurrent(ftn);

        assertDoesNotThrow(() -> controller.end());
    }

    // --- Home/End dispatch path ---

    @Test
    void testHomeWithCursorState_doesNotFallToLogical() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        when(ftn.getContainer()).thenReturn(null);
        controller.setCurrent(ftn);
        controller.setCursorState(new CursorState("data", null, 5,
            new Point2D(100, 100), new BoundingBox(0, 0, 80, 30)));

        controller.home();
        verify(ftn, never()).selectPrevious();
    }

    @Test
    void testEndWithCursorState_doesNotFallToLogical() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        when(ftn.getContainer()).thenReturn(null);
        controller.setCurrent(ftn);
        controller.setCursorState(new CursorState("data", null, 5,
            new Point2D(100, 100), new BoundingBox(0, 0, 80, 30)));

        controller.end();
        verify(ftn, never()).selectNext();
    }

    // --- TAB still uses logical traversal with Home/End available ---

    @Test
    void testTabUnaffectedByHomeEndAdditions() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);

        controller.traverseCurrentNext();
        verify(ftn).traverseNext();
    }

    @Test
    void testShiftTabUnaffectedByHomeEndAdditions() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);

        controller.traverseCurrentPrevious();
        verify(ftn).traversePrevious();
    }

    // --- Helper ---

    private FocusTraversalNode<?> mockFTN(Bias bias) {
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
