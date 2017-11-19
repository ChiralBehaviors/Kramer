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

import static com.chiralbehaviors.layout.LayoutProvider.snap;

import java.util.function.Function;

import com.chiralbehaviors.layout.flowless.Cell;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.Control;
import javafx.scene.layout.Region;

/**
 * @author halhildebrand
 *
 */
abstract public class SchemaNodeLayout {

    public enum Indent {
        LEFT {
            @Override
            public double indent(Indent child, LayoutProvider layout,
                                 double indentation, boolean isChildRelation) {
                switch (child) {
                    case LEFT:
                        return indentation + layout.getNestedLeftInset();
                    case SINGULAR:
                        return indentation + (2 * layout.getNestedLeftInset());
                    case RIGHT:
                        return layout.getNestedRightInset();
                    default:
                        return 0;
                }
            }
        },
        NONE,
        RIGHT {
            @Override
            public double indent(Indent child, LayoutProvider layout,
                                 double indentation, boolean isChildRelation) {
                switch (child) {
                    case LEFT:
                        return layout.getNestedLeftInset();
                    case RIGHT:
                        return indentation + layout.getNestedRightInset();
                    case SINGULAR:
                        return indentation + (2 * layout.getNestedRightInset());
                    default:
                        return 0;
                }
            }
        },
        SINGULAR {
            @Override
            public double indent(Indent child, LayoutProvider layout,
                                 double indentation, boolean isChildRelation) {
                switch (child) {
                    case LEFT:
                        return indentation + layout.getNestedLeftInset();
                    case RIGHT:
                        return indentation + layout.getNestedRightInset();
                    case SINGULAR:
                        return indentation + layout.getNestedInset();
                    default:
                        return 0;
                }
            }
        },
        TOP {
            @Override
            public double indent(Indent child, LayoutProvider layout,
                                 double indentation, boolean isChildRelation) {
                switch (child) {
                    case LEFT:
                        return layout.getNestedLeftInset();
                    case RIGHT:
                        return layout.getNestedRightInset();
                    case SINGULAR:
                        return layout.getNestedInset();
                    default:
                        return 0;
                }
            }
        };

        public double indent(Indent child, LayoutProvider layout,
                             double indentation, boolean isChildRelation) {
            switch (child) {
                case LEFT:
                    return layout.getNestedLeftInset();
                case RIGHT:
                    return layout.getNestedRightInset();
                case SINGULAR:
                    return layout.getNestedInset();
                default:
                    return 0;
            }
        };
    }

    protected double               columnHeaderIndentation = 0.0;
    protected double               columnWidth;
    protected double               height                  = -1.0;
    protected double               justifiedWidth          = -1.0;
    protected double               labelWidth;
    protected final LayoutProvider layout;

    public SchemaNodeLayout(LayoutProvider layout) {
        this.layout = layout;
    }

    public void adjustHeight(double delta) {
        this.height = LayoutProvider.snap(height + delta);
    } 

    abstract public Function<Double, Region> columnHeader();

    public double columnHeaderHeight() {
        return layout.getTextLineHeight() + layout.getTextVerticalInset();
    }

    abstract public double columnWidth();

    abstract public void compress(double justified);

    abstract public JsonNode extractFrom(JsonNode node);

    public double getHeight() {
        return height;
    }

    abstract public double getJustifiedColumnWidth();

    public double getJustifiedWidth() {
        return justifiedWidth;
    }

    public double getLabelWidth() {
        return labelWidth;
    }

    abstract public double justify(double justified);

    public double labelWidth(String label) {
        return snap(layout.labelWidth(label));
    }

    abstract public double layout(double width);

    abstract public double layoutWidth();

    abstract public double measure(JsonNode data, boolean isSingular);

    abstract public double nestTableColumn(Indent indent, double indentation);

    abstract public Cell<JsonNode, Region> outlineElement(int cardinality,
                                                          double labelWidth,
                                                          Function<JsonNode, JsonNode> extractor,
                                                          double justified);

    protected void clear() {
        height = -1.0;
        justifiedWidth = -1.0;
    }

    protected Control label(double labelWidth, String label) {
        return layout.label(labelWidth, label, height);
    }
}
