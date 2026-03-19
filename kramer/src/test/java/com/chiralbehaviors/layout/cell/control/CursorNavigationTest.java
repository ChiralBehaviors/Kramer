// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.cell.control;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversalNode.Bias;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Pane;

/**
 * Tests for Phase 2: Hybrid cursor model.
 * Verifies physical navigation, off-screen fallback, cursor recovery,
 * and edge cases.
 */
class CursorNavigationTest {

    private FocusController<LayoutCell<?>> controller;
    private Pane controllerNode;

    @BeforeEach
    void setUp() {
        controllerNode = new Pane();
        controller = new FocusController<>(controllerNode);
    }

    // --- CursorState record tests ---

    @Test
    void testCursorStateRecordFields() {
        Object identity = "testNode";
        String fieldPath = "items.name";
        Point2D pos = new Point2D(100, 200);
        Bounds bounds = new BoundingBox(0, 0, 80, 30);

        CursorState cs = new CursorState(identity, fieldPath, 5, pos, bounds);

        assertSame(identity, cs.dataIdentity());
        assertEquals(fieldPath, cs.fieldPath());
        assertEquals(5, cs.cellIndex());
        assertEquals(pos, cs.scenePosition());
        assertEquals(bounds, cs.cellBounds());
    }

    @Test
    void testCursorStateNullFields() {
        CursorState cs = new CursorState(null, null, 0, null, null);
        assertNull(cs.dataIdentity());
        assertNull(cs.fieldPath());
        assertNull(cs.scenePosition());
        assertNull(cs.cellBounds());
    }

    // --- cursorState starts null ---

    @Test
    void testCursorStateNullBeforeFirstNavigation() {
        assertNull(controller.getCursorState(),
                   "cursorState should be null before any navigation");
    }

    // --- Physical navigation with cursorState ---

    @Test
    void testDownWithCursorState_usesPhysicalNavigation() {
        // When cursorState is set, down() should NOT delegate to bias-based navigation.
        // Instead it should attempt physical movement. Without a real VirtualFlow,
        // this verifies the dispatch path (movePhysical returns without action
        // when container is not a VirtualFlow).
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);

        Bounds cellBounds = new BoundingBox(0, 0, 80, 30);
        CursorState cs = new CursorState("data", null, 0,
                                          new Point2D(100, 100), cellBounds);
        controller.setCursorState(cs);

        controller.down();
        // Physical path taken — should NOT fall through to logical bias dispatch
        verify(ftn, never()).selectNext();
        verify(ftn, never()).traverseNext();
    }

    @Test
    void testUpWithCursorState_usesPhysicalNavigation() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);

        Bounds cellBounds = new BoundingBox(0, 0, 80, 30);
        CursorState cs = new CursorState("data", null, 0,
                                          new Point2D(100, 100), cellBounds);
        controller.setCursorState(cs);

        controller.up();
        verify(ftn, never()).selectPrevious();
        verify(ftn, never()).traversePrevious();
    }

    @Test
    void testLeftWithCursorState_usesPhysicalNavigation() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.HORIZONTAL);
        controller.setCurrent(ftn);

        Bounds cellBounds = new BoundingBox(0, 0, 80, 30);
        CursorState cs = new CursorState("data", null, 0,
                                          new Point2D(100, 100), cellBounds);
        controller.setCursorState(cs);

        controller.left();
        verify(ftn, never()).selectPrevious();
        verify(ftn, never()).traversePrevious();
    }

    @Test
    void testRightWithCursorState_usesPhysicalNavigation() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.HORIZONTAL);
        controller.setCurrent(ftn);

        Bounds cellBounds = new BoundingBox(0, 0, 80, 30);
        CursorState cs = new CursorState("data", null, 0,
                                          new Point2D(100, 100), cellBounds);
        controller.setCursorState(cs);

        controller.right();
        verify(ftn, never()).selectNext();
        verify(ftn, never()).traverseNext();
    }

    // --- Logical fallback when cursorState is null ---

    @Test
    void testDownWithoutCursorState_usesLogicalNavigation() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        assertNull(controller.getCursorState());

        controller.down();
        verify(ftn).selectNext();
    }

    @Test
    void testUpWithoutCursorState_usesLogicalNavigation() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        assertNull(controller.getCursorState());

        controller.up();
        verify(ftn).selectPrevious();
    }

    // --- TAB/ENTER always use logical navigation regardless of cursorState ---

    @Test
    void testTabStillUsesLogicalWithCursorState() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        controller.setCursorState(new CursorState("data", null, 0,
            new Point2D(100, 100), new BoundingBox(0, 0, 80, 30)));

        controller.traverseCurrentNext();
        verify(ftn).traverseNext();
    }

    @Test
    void testShiftTabStillUsesLogicalWithCursorState() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        controller.setCursorState(new CursorState("data", null, 0,
            new Point2D(100, 100), new BoundingBox(0, 0, 80, 30)));

        controller.traverseCurrentPrevious();
        verify(ftn).traversePrevious();
    }

    @Test
    void testEnterStillUsesLogicalWithCursorState() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        controller.setCurrent(ftn);
        controller.setCursorState(new CursorState("data", null, 0,
            new Point2D(100, 100), new BoundingBox(0, 0, 80, 30)));

        controller.currentActivate();
        verify(ftn).activate();
    }

    // --- Unbind clears cursorState ---

    @Test
    void testUnbindClearsCursorState() {
        controller.setCursorState(new CursorState("data", null, 0,
            new Point2D(100, 100), new BoundingBox(0, 0, 80, 30)));
        assertNotNull(controller.getCursorState());

        controller.unbind();
        assertNull(controller.getCursorState());
    }

    // --- RecoverCursor with identity matching ---

    @Test
    void testRecoverCursorWithNullState_noOp() {
        assertNull(controller.getCursorState());
        VirtualFlow<?> vf = mock(VirtualFlow.class);
        assertDoesNotThrow(() -> controller.recoverCursor(null, vf));
    }

    @Test
    void testRecoverCursorWithNullFlow_noOp() {
        var saved = new CursorState("data", null, 0,
            new Point2D(100, 100), new BoundingBox(0, 0, 80, 30));
        assertDoesNotThrow(() -> controller.recoverCursor(saved, null));
    }

    @Test
    void testRecoverCursorClearsStateWhenIdentityNotFound() {
        JsonNode identity = TextNode.valueOf("missing");
        var saved = new CursorState(identity, null, 0,
            new Point2D(100, 100), new BoundingBox(0, 0, 80, 30));

        VirtualFlow<LayoutCell<?>> vf = mock(VirtualFlow.class);
        ObservableList<JsonNode> items = FXCollections.observableArrayList(
            TextNode.valueOf("a"), TextNode.valueOf("b"));
        when(vf.getItems()).thenReturn(items);
        when(vf.getItemCount()).thenReturn(2);

        controller.recoverCursor(saved, vf);
        assertNull(controller.getCursorState(),
                   "cursorState should be cleared when identity is not found");
    }

    // --- movePhysical with non-VirtualFlow container is no-op ---

    @Test
    void testPhysicalNavWithNonVirtualFlowContainer_noOp() {
        FocusTraversalNode<?> ftn = mockFTN(Bias.VERTICAL);
        // Mock getContainer to return a non-VirtualFlow
        when(ftn.getContainer()).thenReturn(null);
        controller.setCurrent(ftn);
        controller.setCursorState(new CursorState("data", null, 0,
            new Point2D(100, 100), new BoundingBox(0, 0, 80, 30)));

        // Should not throw, just be a no-op
        assertDoesNotThrow(() -> controller.down());
    }

    // --- fieldPath derived from VirtualFlow SchemaPath ---

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void testNavigateTo_fieldPathNonNullWhenFlowHasSchemaPath() {
        // Build a mock VirtualFlow with a SchemaPath set
        VirtualFlow vf = mock(VirtualFlow.class);
        SchemaPath path = new SchemaPath("catalog", "items");
        when(vf.getSchemaPath()).thenReturn(path);
        when(vf.getItemCount()).thenReturn(1);

        // Items list with one entry (identity object)
        JsonNode item = TextNode.valueOf("row0");
        ObservableList<JsonNode> items = FXCollections.observableArrayList(item);
        when(vf.getItems()).thenReturn(items);

        // Mock selection model + cell with a real Pane as node
        MultipleCellSelection selModel = mock(MultipleCellSelection.class);
        when(vf.getSelectionModel()).thenReturn(selModel);

        Pane cellPane = new Pane();
        LayoutCell cell = mock(LayoutCell.class);
        when(cell.getNode()).thenReturn(cellPane);
        when(selModel.getCell(0)).thenReturn(cell);

        controller.navigateTo(vf, 0);

        CursorState cs = controller.getCursorState();
        assertNotNull(cs, "CursorState must be set after navigateTo");
        assertNotNull(cs.fieldPath(),
                      "fieldPath must be non-null when VirtualFlow has a SchemaPath");
        assertEquals("catalog/items", cs.fieldPath(),
                     "fieldPath should be the SchemaPath string representation");
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void testNavigateTo_fieldPathNullWhenFlowHasNoSchemaPath() {
        // VirtualFlow with no SchemaPath set (returns null)
        VirtualFlow vf = mock(VirtualFlow.class);
        when(vf.getSchemaPath()).thenReturn(null);
        when(vf.getItemCount()).thenReturn(1);

        JsonNode item = TextNode.valueOf("row0");
        ObservableList<JsonNode> items = FXCollections.observableArrayList(item);
        when(vf.getItems()).thenReturn(items);

        MultipleCellSelection selModel = mock(MultipleCellSelection.class);
        when(vf.getSelectionModel()).thenReturn(selModel);

        Pane cellPane = new Pane();
        LayoutCell cell = mock(LayoutCell.class);
        when(cell.getNode()).thenReturn(cellPane);
        when(selModel.getCell(0)).thenReturn(cell);

        controller.navigateTo(vf, 0);

        CursorState cs = controller.getCursorState();
        assertNotNull(cs, "CursorState must be set after navigateTo");
        assertNull(cs.fieldPath(),
                   "fieldPath should be null when VirtualFlow has no SchemaPath");
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
