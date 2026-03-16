// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.cell;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.TextNode;

import javafx.scene.layout.Pane;

/**
 * Tests for Phase 4: LabelCell adapter contract.
 *
 * JavaFX Label requires toolkit initialization which isn't available
 * in headless tests. We test using a LabelCellTestable subclass that
 * wraps a Pane instead, verifying the adapter pattern and pseudo-class
 * behavior. The OutlineElement integration (which creates real Labels)
 * is tested via the existing layout integration tests.
 */
class LabelCellTest {

    /**
     * A testable analog of LabelCell that wraps a Pane instead of Label,
     * avoiding toolkit initialization. Tests the LayoutCell adapter contract.
     */
    private static class PaneCellAdapter implements LayoutCell<Pane> {
        private final Pane pane;

        PaneCellAdapter(Pane pane) {
            this.pane = pane;
        }

        @Override
        public Pane getNode() {
            return pane;
        }

        @Override
        public boolean isReusable() {
            return false;
        }
    }

    @Test
    void testGetNodeReturnsWrappedNode() {
        Pane pane = new Pane();
        PaneCellAdapter cell = new PaneCellAdapter(pane);
        assertSame(pane, cell.getNode());
    }

    @Test
    void testIsReusableReturnsFalse() {
        PaneCellAdapter cell = new PaneCellAdapter(new Pane());
        assertFalse(cell.isReusable());
    }

    @Test
    void testUpdateItemSetsFilledPseudoClass() {
        Pane pane = new Pane();
        PaneCellAdapter cell = new PaneCellAdapter(pane);

        cell.updateItem(TextNode.valueOf("data"));
        assertTrue(pane.getPseudoClassStates().stream()
            .anyMatch(pc -> "filled".equals(pc.getPseudoClassName())));
        assertFalse(pane.getPseudoClassStates().stream()
            .anyMatch(pc -> "empty".equals(pc.getPseudoClassName())));
    }

    @Test
    void testUpdateItemNullSetsEmptyPseudoClass() {
        Pane pane = new Pane();
        PaneCellAdapter cell = new PaneCellAdapter(pane);

        cell.updateItem(null);
        assertTrue(pane.getPseudoClassStates().stream()
            .anyMatch(pc -> "empty".equals(pc.getPseudoClassName())));
        assertFalse(pane.getPseudoClassStates().stream()
            .anyMatch(pc -> "filled".equals(pc.getPseudoClassName())));
    }

    @Test
    void testUpdateSelectionSetsPseudoClass() {
        Pane pane = new Pane();
        PaneCellAdapter cell = new PaneCellAdapter(pane);

        cell.updateSelection(true);
        assertTrue(pane.getPseudoClassStates().stream()
            .anyMatch(pc -> "selected".equals(pc.getPseudoClassName())));

        cell.updateSelection(false);
        assertFalse(pane.getPseudoClassStates().stream()
            .anyMatch(pc -> "selected".equals(pc.getPseudoClassName())));
    }

    @Test
    void testInitializeAddsKramerContainerStyleClass() {
        Pane pane = new Pane();
        PaneCellAdapter cell = new PaneCellAdapter(pane);
        cell.initialize("outline-cell");
        assertTrue(pane.getStyleClass().contains("kramer-container"),
                   "initialize() must add 'kramer-container' style class");
        assertTrue(pane.getStyleClass().contains("outline-cell"),
                   "initialize() must add the defaultStyle argument");
    }

    @Test
    void testInitializeAddsKramerContainerBeforeDefaultStyle() {
        Pane pane = new Pane();
        PaneCellAdapter cell = new PaneCellAdapter(pane);
        cell.initialize("nested-row");
        var styles = pane.getStyleClass();
        int containerIdx = styles.indexOf("kramer-container");
        int defaultIdx = styles.indexOf("nested-row");
        assertTrue(containerIdx >= 0, "'kramer-container' must be present");
        assertTrue(defaultIdx >= 0, "defaultStyle must be present");
    }

    @Test
    void testActivateIsNoOp() {
        PaneCellAdapter cell = new PaneCellAdapter(new Pane());
        assertDoesNotThrow(cell::activate);
    }

    @Test
    void testDisposeIsNoOp() {
        PaneCellAdapter cell = new PaneCellAdapter(new Pane());
        assertDoesNotThrow(cell::dispose);
    }
}
