// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.cell;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.Label;

/**
 * Adapter wrapping a plain JavaFX Label as a LayoutCell so it can
 * participate in hit-testing and selection. Labels are leaf nodes —
 * they display text but don't contain child cells.
 */
public class LabelCell implements LayoutCell<Label> {

    private final Label label;

    public LabelCell(Label label) {
        this.label = label;
    }

    @Override
    public Label getNode() {
        return label;
    }

    @Override
    public void updateItem(JsonNode item) {
        // Labels display static text (field name, bullet); no data binding needed.
        label.pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
        label.pseudoClassStateChanged(PSEUDO_CLASS_EMPTY, item == null);
    }

    @Override
    public boolean isReusable() {
        return false;
    }
}
