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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.text.Font;

/**
 * @author halhildebrand
 *
 */
public class LabelStyle {
    public static final String LAYOUT_LABEL = "layout-label";

    private final Font         font;
    private final Insets       insets;
    private final double       lineHeight;

    public LabelStyle(Insets insets, double lineHeight, Font font) {
        this.insets = insets;
        this.lineHeight = lineHeight;
        this.font = font;
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
        label.setAlignment(Pos.CENTER);
        label.setMinSize(width, height);
        label.setMaxSize(width, height);
        return label;
    }

    public double width(String text) {
        return Layout.textWidth(text, font) + insets.getLeft()
               + insets.getRight();
    }
}
