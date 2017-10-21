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

package com.chiralbehaviors.layout;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.Control;

/**
 * @author halhildebrand
 *
 */
abstract public class SchemaNodeLayout {
    public enum INDENT {
        LEFT,
        NONE,
        RIGHT,
        SINGULAR,
        SINGULAR_RIGHT;
    }

    protected double               height         = -1.0;
    protected INDENT               indent;
    protected double               justifiedWidth = -1.0;
    protected final LayoutProvider layout;

    public SchemaNodeLayout(LayoutProvider layout) {
        this.layout = layout;
    }

    public void adjustHeight(double delta) {
        this.height = LayoutProvider.snap(height + delta);
    }

    abstract public double baseTableColumnWidth(double available);

    public double columnHeaderHeight() {
        return layout.getTextLineHeight() + layout.getTextVerticalInset();
    }

    abstract public JsonNode extractFrom(JsonNode node);

    public double getHeight() {
        return height;
    }

    public double getJustifiedWidth() {
        return justifiedWidth;
    }

    abstract public double labelWidth(String label);

    abstract public double layout(int cardinality, double width);

    abstract public double measure(JsonNode data, boolean isSingular,
                                   INDENT indent);

    abstract public double tableColumnWidth(double columnWidth);

    abstract protected double baseOutlineWidth(double available);

    protected void clear() {
        height = -1.0;
        justifiedWidth = -1.0;
    }

    protected double indentation(INDENT indent) {
        switch (indent) {
            case LEFT:
                return layout.getNestedLeftInset();
            case RIGHT:
                return layout.getNestedRightInset();
            case SINGULAR:
                return layout.getNestedInset();
            case SINGULAR_RIGHT:
                return layout.getNestedInset() + layout.getNestedRightInset();
            default:
                return 0;
        }
    }

    abstract protected Control label(double labelWidth, String label);

}
