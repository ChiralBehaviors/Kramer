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

import java.util.function.Function;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.Style;
import com.chiralbehaviors.layout.table.ColumnHeader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/**
 * @author halhildebrand
 *
 */
abstract public class SchemaNodeLayout {

    public class Fold {
        public final int      averageCardinality;
        public final JsonNode datum;

        Fold(JsonNode datum, int averageCardinality) {
            assert averageCardinality > 0;
            this.datum = datum;
            this.averageCardinality = averageCardinality;
        }

        public SchemaNodeLayout getLayout() {
            return SchemaNodeLayout.this;
        }
    }

    public enum Indent {
        LEFT {
            @Override
            public Insets indent(Insets indentation, Indent child,
                                 Insets inset) {
                switch (child) {
                    case LEFT:
                        return new Insets(0, indentation.getRight(), 0,
                                          indentation.getLeft() + inset.getLeft());
                    case SINGULAR:
                        return new Insets(0, inset.getRight(), 0,
                                          indentation.getLeft() + inset.getLeft());
                    case RIGHT:
                        return new Insets(0, inset.getRight(), 0,
                                          inset.getLeft());
                    default:
                        return new Insets(0);
                }
            }
        },
        NONE {
            @Override
            public Insets indent(Insets indentation, Indent child,
                                 Insets inset) {
                switch (child) {
                    case LEFT:
                        return new Insets(0, 0, 0, inset.getLeft());
                    case RIGHT:
                        return new Insets(0, inset.getRight(), 0, 0);
                    case SINGULAR:
                        return inset;
                    default:
                        return new Insets(0);
                }
            }
        },
        RIGHT {
            @Override
            public Insets indent(Insets indentation, Indent child,
                                 Insets inset) {
                switch (child) {
                    case LEFT:
                        return new Insets(0, indentation.getRight(), 0,
                                          inset.getLeft());
                    case RIGHT:
                        return new Insets(0,
                                          indentation.getRight()
                                             + inset.getRight(),
                                          0, inset.getLeft());
                    case SINGULAR:
                        return new Insets(0,
                                          indentation.getRight()
                                             + inset.getRight(),
                                          0, inset.getLeft());
                    default:
                        return new Insets(0);
                }
            }
        },
        SINGULAR {
            @Override
            public Insets indent(Insets indentation, Indent child,
                                 Insets inset) {
                switch (child) {
                    case LEFT:
                        return new Insets(0, indentation.getRight(), 0,
                                          indentation.getLeft() + inset.getLeft());
                    case RIGHT:
                        return new Insets(0,
                                          indentation.getRight()
                                             + inset.getRight(),
                                          0, indentation.getLeft());
                    case SINGULAR:
                        return new Insets(0,
                                          indentation.getRight()
                                             + inset.getRight(),
                                          0, indentation.getLeft()
                                             + inset.getLeft());
                    default:
                        return new Insets(0);
                }
            }
        },
        TOP {
            @Override
            public Insets indent(Insets indentation, Indent child,
                                 Insets inset) {
                switch (child) {
                    case LEFT:
                        return new Insets(0, 0, 0, inset.getLeft());
                    case RIGHT:
                        return new Insets(0, inset.getRight(), 0, 0);
                    case SINGULAR:
                        return inset;
                    default:
                        return new Insets(0);
                }
            }
        };

        abstract public Insets indent(Insets indentation, Indent child,
                                      Insets inset);
    }

    protected double           columnHeaderIndentation = 0.0;
    protected double           columnWidth;
    protected double           height                  = -1.0;
    protected double           justifiedWidth          = -1.0;
    protected final LabelStyle labelStyle;
    protected double           labelWidth;

    protected final SchemaNode node;

    public SchemaNodeLayout(SchemaNode node, LabelStyle labelStyle) {
        this.node = node;
        this.labelStyle = labelStyle;
    }

    public void adjustHeight(double delta) {
        this.height = Style.snap(height + delta);
    }

    public LayoutCell<? extends Region> autoLayout(double width,
                                                   FocusTraversal<?> parentTraversal,
                                                   Style model) {
        double justified = Style.snap(width);
        layout(justified);
        compress(justified);
        calculateRootHeight();
        return buildControl(parentTraversal, model);
    }

    abstract public LayoutCell<? extends Region> buildColumn(double rendered,
                                                             FocusTraversal<?> focus,
                                                             Style model);

    abstract public LayoutCell<? extends Region> buildControl(FocusTraversal<?> parentTraversal,
                                                              Style model);

    public double calculateLabelWidth() {
        return labelWidth;
    }

    abstract public double calculateTableColumnWidth();

    abstract public double cellHeight(int cardinality, double available);

    abstract public Function<Double, ColumnHeader> columnHeader();

    public double columnHeaderHeight() {
        return Style.snap(labelStyle.getHeight());
    }

    abstract public double columnWidth();

    abstract public void compress(double justified);

    abstract public JsonNode extractFrom(JsonNode node);

    public String getField() {
        return node.getField();
    }

    public double getHeight() {
        return height;
    }

    public double getJustifiedWidth() {
        return Style.snap(justifiedWidth);
    }

    public String getLabel() {
        return node.getLabel();
    }

    public double getLabelWidth() {
        return labelWidth;
    }

    public abstract SchemaNode getNode();

    abstract public double justify(double justified);

    public Label label(double width, double height) {
        return labelStyle.label(width, getLabel(), height);
    }

    public double labelWidth(String label) {
        return Style.snap(labelStyle.width(label));
    }

    abstract public double layout(double width);

    abstract public double layoutWidth();

    abstract public double measure(JsonNode data,
                                   Function<JsonNode, JsonNode> extractor,
                                   Style model);

    public SchemaNodeLayout measure(JsonNode datum, Style model) {
        Fold fold = fold(JsonNodeFactory.instance.objectNode()
                                                 .set(getField(), datum),
                         n -> n, model);
        fold.getLayout()
            .measure(fold.datum, n -> n, model);
        return fold.getLayout();
    }

    abstract public double nestTableColumn(Indent inset, Insets indentation);

    abstract public void normalizeRowHeight(double normalized);

    abstract public double rowHeight(int averageCardinality,
                                     double justifiedWidth);

    abstract public double tableColumnWidth();

    abstract protected void calculateRootHeight();

    protected void clear() {
        height = -1.0;
        justifiedWidth = -1.0;
        columnHeaderIndentation = 0.0;
    }

    protected Fold fold(JsonNode datum) {
        ArrayNode aggregate = JsonNodeFactory.instance.arrayNode();
        int cardSum = 0;
        JsonNode data = datum.isArray() ? datum
                                        : JsonNodeFactory.instance.arrayNode()
                                                                  .add(datum);
        for (JsonNode node : data) {
            JsonNode sub = node.get(getField());
            if (sub instanceof ArrayNode) {
                aggregate.addAll((ArrayNode) sub);
                cardSum += sub.size();
            } else {
                cardSum += 1;
                aggregate.add(sub);
            }
        }
        return new Fold(aggregate,
                        (cardSum == 0
                         || data.size() == 0) ? 1
                                              : Math.round(cardSum
                                                           / data.size()));
    }

    protected Fold fold(JsonNode datum, Function<JsonNode, JsonNode> extractor,
                        Style model) {
        return fold(datum);
    }
}
