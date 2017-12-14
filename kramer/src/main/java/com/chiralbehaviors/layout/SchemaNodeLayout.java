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

import com.chiralbehaviors.layout.StyleProvider.StyledInsets;
import com.chiralbehaviors.layout.cell.FocusTraversal;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.outline.OutlineElement;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.table.ColumnHeader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.control.Control;
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
            public double indent(Indent child, StyledInsets insets,
                                 double indentation) {
                switch (child) {
                    case LEFT:
                        return indentation + insets.getCellLeftInset();
                    case SINGULAR:
                        return indentation + insets.getCellLeftInset()
                               + insets.getCellRightInset();
                    case RIGHT:
                        return insets.getCellRightInset();
                    default:
                        return 0;
                }
            }
        },
        NONE,
        RIGHT {
            @Override
            public double indent(Indent child, StyledInsets insets,
                                 double indentation) {
                switch (child) {
                    case LEFT:
                        return insets.getCellLeftInset();
                    case RIGHT:
                        return indentation + insets.getCellRightInset();
                    case SINGULAR:
                        return indentation + insets.getCellLeftInset()
                               + insets.getCellRightInset();
                    default:
                        return 0;
                }
            }
        },
        SINGULAR {
            @Override
            public double indent(Indent child, StyledInsets insets,
                                 double indentation) {
                switch (child) {
                    case LEFT:
                        return indentation + insets.getCellLeftInset();
                    case RIGHT:
                        return indentation + insets.getCellRightInset();
                    case SINGULAR:
                        return indentation + insets.getCellHorizontalInset();
                    default:
                        return 0;
                }
            }
        },
        TOP {
            @Override
            public double indent(Indent child, StyledInsets insets,
                                 double indentation) {
                switch (child) {
                    case LEFT:
                        return insets.getCellLeftInset();
                    case RIGHT:
                        return insets.getCellRightInset();
                    case SINGULAR:
                        return insets.getCellHorizontalInset();
                    default:
                        return 0;
                }
            }
        };

        public double indent(Indent child, StyledInsets insets,
                             double indentation) {
            switch (child) {
                case LEFT:
                    return insets.getCellLeftInset();
                case RIGHT:
                    return insets.getCellRightInset();
                case SINGULAR:
                    return insets.getCellHorizontalInset();
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
    protected final SchemaNode     node;

    public SchemaNodeLayout(LayoutProvider layout, SchemaNode node) {
        this.layout = layout;
        this.node = node;
    }

    public void adjustHeight(double delta) {
        this.height = LayoutProvider.snap(height + delta);
    }

    public LayoutCell<? extends Region> autoLayout(double width,
                                                   FocusTraversal parentTraversal) {
        double justified = LayoutProvider.snap(width);
        layout(justified);
        compress(justified);
        calculateRootHeight();
        return buildControl(parentTraversal);
    }

    abstract public LayoutCell<? extends Region> buildColumn(double rendered,
                                                             FocusTraversal parentTraversal);

    abstract public LayoutCell<? extends Region> buildControl(FocusTraversal parentTraversal);

    abstract public void calculateCellHeight();

    public double calculateLabelWidth() {
        return labelWidth;
    }

    abstract public double calculateTableColumnWidth();

    abstract public double cellHeight(int cardinality, double available);

    abstract public Function<Double, ColumnHeader> columnHeader();

    public double columnHeaderHeight() {
        return layout.getTextLineHeight() + layout.getTextVerticalInset();
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

    abstract public double getJustifiedColumnWidth();

    public double getJustifiedWidth() {
        return justifiedWidth;
    }

    public String getLabel() {
        return node.getLabel();
    }

    public double getLabelWidth() {
        return labelWidth;
    }

    abstract public double justify(double justified);

    public Control label(double labelWidth) {
        return label(labelWidth, node.getLabel());
    }

    public Control label(double width, double half) {
        return layout.label(width, getLabel(), half);
    }

    public double labelWidth(String label) {
        return snap(layout.labelWidth(label));
    }

    abstract public double layout(double width);

    abstract public double layoutWidth();

    public SchemaNodeLayout measure(JsonNode datum) {
        Fold fold = fold(JsonNodeFactory.instance.objectNode()
                                                 .set(getField(), datum),
                         n -> n);
        fold.getLayout()
            .measure(fold.datum, n -> n);
        return fold.getLayout();
    }

    abstract public double measure(JsonNode data,
                                   Function<JsonNode, JsonNode> extractor);

    abstract public double nestTableColumn(Indent indent, double indentation);

    abstract public void normalizeRowHeight(double normalized);

    abstract public OutlineElement outlineElement(String parent,
                                                  int cardinality,
                                                  double labelWidth,
                                                  double justified,
                                                  FocusTraversal parentTraversal);

    abstract public double rowHeight(int averageCardinality,
                                     double justifiedWidth);

    abstract public double tableColumnWidth();

    abstract protected void calculateRootHeight();

    protected void clear() {
        height = -1.0;
        justifiedWidth = -1.0;
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

    protected Fold fold(JsonNode datum,
                        Function<JsonNode, JsonNode> extractor) {
        return fold(datum);
    }

    abstract protected SchemaNode getNode();

    protected Control label(double labelWidth, String label) {
        return layout.label(labelWidth, label, height);
    }
}
