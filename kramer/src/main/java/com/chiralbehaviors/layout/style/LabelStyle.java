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

import com.chiralbehaviors.layout.LayoutLabel;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

/**
 * @author halhildebrand
 *
 */
public class LabelStyle {
    public static final String LAYOUT_LABEL = "layout-label";

    private static double getLineHeight(Font font, TextBoundsType boundsType) {
        Text text = new Text("WgTy\n a");
        text.setFont(font);
        Bounds tb = text.getBoundsInLocal();
        return Shape.intersect(text,
                               new Rectangle(tb.getMinX(), tb.getMinY(),
                                             tb.getWidth(), tb.getHeight()))
                    .getBoundsInLocal()
                    .getHeight();
    }

    private final Font   font;
    private final Insets insets;
    private final double lineHeight;

    public LabelStyle(Label label) {
        Insets lInsets = label.getInsets();
        insets = lInsets;
        lineHeight = getLineHeight(label.getFont(),
                                   TextBoundsType.LOGICAL_VERTICAL_CENTER);
        font = label.getFont();
    }

    public double getHeight() {
        return lineHeight + insets.getTop() + insets.getBottom();
    }

    public double getHeight(double rows) {
        return (lineHeight * rows) + insets.getTop() + insets.getBottom();
    }

    public double getHorizontalInset() {
        return insets.getLeft() + insets.getRight();
    }

    public Label label(double width, String text, double height) {
        LayoutLabel label = new LayoutLabel(text);
        label.setMinSize(width, height);
        label.setPrefSize(width, height);
        label.setMaxSize(width, height);
        return label;
    }

    public double width(String text) {
        return Style.textWidth(text, font) + insets.getLeft()
               + insets.getRight();
    }
}
