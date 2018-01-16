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
    public static class PrimitiveTextStyle extends PrimitiveStyle {

        public static String     DEFAULT_STYLE        = "primitive";
        public static String     PRIMITIVE_TEXT_CLASS = "primitive-text";

        private final LabelStyle primitiveStyle;

        public PrimitiveTextStyle(LabelStyle labelStyle, Insets listInsets,
                                  LabelStyle primitiveStyle) {
            super(labelStyle, listInsets);
            this.primitiveStyle = primitiveStyle;
        }

        public LayoutCell<?> build(FocusTraversal<?> pt, PrimitiveLayout p) {
            Label label = new Label();
            label.getStyleClass()
                 .clear();
            label.setMinSize(p.getJustifiedWidth(), p.getCellHeight());
            label.setPrefSize(p.getJustifiedWidth(), p.getCellHeight());
            label.setMaxSize(p.getJustifiedWidth(), p.getCellHeight());
            return new LayoutCell<Label>() {
                {
                    initialize(p.getField());
                    label.setWrapText(true);
                    initialize(DEFAULT_STYLE);
                    label.getStyleClass()
                         .addAll(PRIMITIVE_TEXT_CLASS, p.getField());
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

        public double getHeight(double maxWidth, double justified) {
            return primitiveStyle.getHeight(Math.ceil((maxWidth / justified)));
        }

        public double width(JsonNode row) {
            return primitiveStyle.width(Style.toString(row))
                   + primitiveStyle.getHorizontalInset();
        }

    }

    private final Insets listInsets;

    public PrimitiveStyle(LabelStyle labelStyle, Insets listInsets) {
        super(labelStyle);
        this.listInsets = listInsets;
    }

    abstract public LayoutCell<?> build(FocusTraversal<?> pt,
                                        PrimitiveLayout p);

    abstract public double getHeight(double maxWidth, double justified);

    public double getListVerticalInset() {
        return listInsets.getTop() + listInsets.getBottom();
    }

    abstract public double width(JsonNode row);
}
