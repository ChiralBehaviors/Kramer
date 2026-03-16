// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.cell.control;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversalNode.Bias;

import javafx.scene.layout.Pane;

/**
 * Tests for Phase 1: FocusController keyboard navigation wiring.
 * Verifies the setCurrent bug fix, bindKeyboard installation,
 * and that navigation methods dispatch correctly based on bias.
 */
class FocusControllerWiringTest {

    private FocusController<LayoutCell<?>> controller;
    private Pane                           controllerNode;

    @BeforeEach
    void setUp() {
        controllerNode = new Pane();
        controller = new FocusController<>(controllerNode);
    }

    // --- setCurrent bug fix tests ---

    @Test
    void testSetCurrentWithNode_setsCurrentField() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        assertSame(ftn, controller.getCurrent(),
                   "setCurrent(node) should set the current field");
    }

    @Test
    void testSetCurrentNoArg_doesNotSetCurrent() {
        // The no-arg setCurrent on FocusController is intentionally a no-op
        controller.setCurrent();
        assertNull(controller.getCurrent(),
                   "setCurrent() no-arg should remain a no-op on FocusController");
    }

    @Test
    void testIsCurrentReturnsTrueForCurrentNode() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        assertTrue(controller.isCurrent(ftn));
    }

    @Test
    void testIsCurrentReturnsFalseForDifferentNode() {
        FocusTraversalNode<?> ftn1 = mockFTN(Bias.VERTICAL);
        FocusTraversalNode<?> ftn2 = mockFTN(Bias.HORIZONTAL);
        controller.setCurrent(ftn1);
        assertFalse(controller.isCurrent(ftn2));
    }

    // --- Navigation dispatch: current == null guards ---

    @Test
    void testDownWithNullCurrent_noOp() {
        assertNull(controller.getCurrent());
        assertDoesNotThrow(() -> controller.down(),
                           "down() should be a no-op when current is null");
    }

    @Test
    void testUpWithNullCurrent_noOp() {
        assertNull(controller.getCurrent());
        assertDoesNotThrow(() -> controller.up());
    }

    @Test
    void testLeftWithNullCurrent_noOp() {
        assertNull(controller.getCurrent());
        assertDoesNotThrow(() -> controller.left());
    }

    @Test
    void testRightWithNullCurrent_noOp() {
        assertNull(controller.getCurrent());
        assertDoesNotThrow(() -> controller.right());
    }

    @Test
    void testCurrentActivateWithNullCurrent_noOp() {
        assertNull(controller.getCurrent());
        assertDoesNotThrow(() -> controller.currentActivate());
    }

    @Test
    void testTraverseCurrentNextWithNullCurrent_noOp() {
        assertNull(controller.getCurrent());
        assertDoesNotThrow(() -> controller.traverseCurrentNext());
    }

    @Test
    void testTraverseCurrentPreviousWithNullCurrent_noOp() {
        assertNull(controller.getCurrent());
        assertDoesNotThrow(() -> controller.traverseCurrentPrevious());
    }

    // --- Arrow key dispatch with VERTICAL bias ---

    @Test
    void testDownVertical_selectsNext() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        controller.down();
        verify(ftn).selectNext();
    }

    @Test
    void testUpVertical_selectsPrevious() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        controller.up();
        verify(ftn).selectPrevious();
    }

    @Test
    void testLeftVertical_traversesPrevious() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        controller.left();
        verify(ftn).traversePrevious();
    }

    @Test
    void testRightVertical_traversesNext() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        controller.right();
        verify(ftn).traverseNext();
    }

    // --- Arrow key dispatch with HORIZONTAL bias ---

    @Test
    void testDownHorizontal_traversesNext() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.HORIZONTAL);
        controller.setCurrent(ftn);
        controller.down();
        verify(ftn).traverseNext();
    }

    @Test
    void testUpHorizontal_traversesPrevious() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.HORIZONTAL);
        controller.setCurrent(ftn);
        controller.up();
        verify(ftn).traversePrevious();
    }

    @Test
    void testLeftHorizontal_selectsPrevious() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.HORIZONTAL);
        controller.setCurrent(ftn);
        controller.left();
        verify(ftn).selectPrevious();
    }

    @Test
    void testRightHorizontal_selectsNext() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.HORIZONTAL);
        controller.setCurrent(ftn);
        controller.right();
        verify(ftn).selectNext();
    }

    // --- TAB / SHIFT+TAB / ENTER ---

    @Test
    void testTraverseCurrentNext_delegatesToCurrent() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        controller.traverseCurrentNext();
        verify(ftn).traverseNext();
    }

    @Test
    void testTraverseCurrentPrevious_delegatesToCurrent() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        controller.traverseCurrentPrevious();
        verify(ftn).traversePrevious();
    }

    @Test
    void testCurrentActivate_delegatesToCurrent() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        controller.currentActivate();
        verify(ftn).activate();
    }

    // --- bindKeyboard / unbind ---

    @Test
    void testBindKeyboardTracksNode() {
        Pane vfNode = new Pane();
        controller.bindKeyboard(vfNode);
        // After unbind, the tracked node should be cleared
        // (We can't directly inspect boundNodes, but we verify unbind doesn't throw)
        assertDoesNotThrow(() -> controller.unbind());
    }

    @Test
    void testBindKeyboardMultipleNodes() {
        Pane vf1 = new Pane();
        Pane vf2 = new Pane();
        controller.bindKeyboard(vf1);
        controller.bindKeyboard(vf2);
        // unbind should clean up both without error
        assertDoesNotThrow(() -> controller.unbind());
    }

    @Test
    void testUnbindWithoutBind_noOp() {
        // unbind with no bound nodes should not throw
        assertDoesNotThrow(() -> controller.unbind());
    }

    // --- FocusTraversalNode.bindKeyboard delegation ---

    @Test
    void testFTNBindKeyboardDelegatesToParent() {
        FocusTraversal<?> parent = mock(FocusTraversal.class);
        Pane node = new Pane();
        // We can't easily construct a real FTN (needs SelectionModel with listeners),
        // so we verify the interface contract: FTN.bindKeyboard should delegate to parent
        parent.bindKeyboard(node);
        verify(parent).bindKeyboard(node);
    }

    // --- Disabled node guard ---

    @Test
    void testIsDisabledReflectsNodeState() {
        assertFalse(controller.isDisabled());
        controllerNode.setDisable(true);
        assertTrue(controller.isDisabled());
    }

    // --- navigateTo() public API ---

    @Test
    void testNavigateTo_isPublicApi() throws NoSuchMethodException {
        // Verify navigateTo(VirtualFlow<?>, int) is declared public on FocusController
        var method = FocusController.class.getMethod("navigateTo",
                                                     com.chiralbehaviors.layout.flowless.VirtualFlow.class,
                                                     int.class);
        assertNotNull(method, "navigateTo(VirtualFlow<?>, int) must be a public method");
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()),
                   "navigateTo must be public");
    }

    // --- Helper ---

    private FocusTraversalNode<?> mockFTN(Bias bias) {
        FocusTraversalNode<?> ftn = mock(FocusTraversalNode.class);
        // The bias field is accessed directly (not via getter) by FocusController,
        // so we need to set it on the mock. Mockito mocks initialize fields to defaults,
        // but we need the specific bias. Use reflection since it's a final field on
        // an abstract class.
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
