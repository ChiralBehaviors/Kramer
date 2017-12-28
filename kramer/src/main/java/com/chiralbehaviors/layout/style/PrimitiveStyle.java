/**
 * Copyright (c) 2017 Chiral Behaviors, LLC, all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chiralbehaviors.layout.style;

import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Insets;
import javafx.scene.control.Label;

/**
 * @author halhildebrand
 *
 */
abstract public class PrimitiveStyle extends NodeStyle {
    private final Insets listInsets;

    public static class PrimitiveLabelStyle extends PrimitiveStyle {

        public PrimitiveLabelStyle(LabelStyle labelStyle, Insets listInsets) {
            super(labelStyle, listInsets);
        }

        public double width(JsonNode row) {
            return labelStyle.width(LayoutModel.toString(row))
                   + labelStyle.getHorizontalInset();
        }

        public double getHeight(double maxWidth, double justified) {
            double rows = Math.ceil((maxWidth / justified) + 0.5);
            return (labelStyle.getLineHeight() * rows)
                   + labelStyle.getVerticalInset();
        }

        public LayoutCell<?> build(FocusTraversal<?> pt, PrimitiveLayout p) {
            String DEFAULT_STYLE = "primitive";
            Label label = new Label();
            label.getStyleClass()
                 .add(p.getField());
            label.setMinSize(p.getJustifiedWidth(), p.getCellHeight());
            label.setPrefSize(p.getJustifiedWidth(), p.getCellHeight());
            label.setMaxSize(p.getJustifiedWidth(), p.getCellHeight());
            return new LayoutCell<Label>() {
                {
                    initialize(p.getField());
                    label.setWrapText(true);
                    initialize(DEFAULT_STYLE);
                }

                @Override
                public Label getNode() {
                    return label;
                }

                @Override
                public boolean isReusable() {
                    return true;
                }

                @Override
                public void updateItem(JsonNode item) {
                    label.setText(SchemaNode.asText(item));
                    getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED,
                                                      item != null);
                }
            };

        }

    }

    public PrimitiveStyle(LabelStyle labelStyle, Insets listInsets) {
        super(labelStyle);
        this.listInsets = listInsets;
    }

    public double getListVerticalInset() {
        return listInsets.getTop() + listInsets.getBottom();
    }

    public double width(JsonNode row) {
        return labelStyle.width(LayoutModel.toString(row))
               + labelStyle.getHorizontalInset();
    }

    abstract public double getHeight(double maxWidth, double justified);

    abstract public LayoutCell<?> build(FocusTraversal<?> pt,
                                        PrimitiveLayout p);
}
