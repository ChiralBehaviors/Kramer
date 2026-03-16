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
public abstract sealed class SchemaNodeLayout permits PrimitiveLayout, RelationLayout {

    public record Fold(JsonNode datum, int averageCardinality, SchemaNodeLayout layout) {
        public Fold {
            assert averageCardinality > 0;
        }
    }

    public enum Indent {
        LEFT {
            @Override
            public Insets indent(Insets indentation, Indent child,
                                 Insets inset) {
                return switch (child) {
                    case LEFT -> new Insets(0, indentation.getRight(), 0,
                                            indentation.getLeft() + inset.getLeft());
                    case SINGULAR -> new Insets(0, inset.getRight(), 0,
                                                indentation.getLeft() + inset.getLeft());
                    case RIGHT -> new Insets(0, inset.getRight(), 0,
                                             inset.getLeft());
                    default -> new Insets(0);
                };
            }
        },
        NONE {
            @Override
            public Insets indent(Insets indentation, Indent child,
                                 Insets inset) {
                return switch (child) {
                    case LEFT -> new Insets(0, 0, 0, inset.getLeft());
                    case RIGHT -> new Insets(0, inset.getRight(), 0, 0);
                    case SINGULAR -> inset;
                    default -> new Insets(0);
                };
            }
        },
        RIGHT {
            @Override
            public Insets indent(Insets indentation, Indent child,
                                 Insets inset) {
                return switch (child) {
                    case LEFT -> new Insets(0, indentation.getRight(), 0,
                                            inset.getLeft());
                    case RIGHT -> new Insets(0,
                                             indentation.getRight()
                                                + inset.getRight(),
                                             0, inset.getLeft());
                    case SINGULAR -> new Insets(0,
                                                indentation.getRight()
                                                   + inset.getRight(),
                                                0, inset.getLeft());
                    default -> new Insets(0);
                };
            }
        },
        SINGULAR {
            @Override
            public Insets indent(Insets indentation, Indent child,
                                 Insets inset) {
                return switch (child) {
                    case LEFT -> new Insets(0, indentation.getRight(), 0,
                                            indentation.getLeft() + inset.getLeft());
                    case RIGHT -> new Insets(0,
                                             indentation.getRight()
                                                + inset.getRight(),
                                             0, indentation.getLeft());
                    case SINGULAR -> new Insets(0,
                                                indentation.getRight()
                                                   + inset.getRight(),
                                                0, indentation.getLeft()
                                                   + inset.getLeft());
                    default -> new Insets(0);
                };
            }
        },
        TOP {
            @Override
            public Insets indent(Insets indentation, Indent child,
                                 Insets inset) {
                return switch (child) {
                    case LEFT -> new Insets(0, 0, 0, indentation.getLeft()
                                                     + inset.getLeft());
                    case RIGHT -> new Insets(0, indentation.getRight()
                                                + inset.getRight(),
                                             0, 0);
                    case SINGULAR -> inset;
                    default -> new Insets(0);
                };
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
    protected boolean          rootLevel;
    private SchemaPath         schemaPath;

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
        return autoLayout(width, 0, parentTraversal, model);
    }

    public LayoutCell<? extends Region> autoLayout(double width,
                                                   double availableHeight,
                                                   FocusTraversal<?> parentTraversal,
                                                   Style model) {
        double justified = Style.snap(width);
        layout(justified);
        compress(justified);
        calculateRootHeight();
        distributeExtraHeight(availableHeight);
        rootLevel = true;
        LayoutCell<? extends Region> control = buildControl(parentTraversal,
                                                             model);
        rootLevel = false;
        return control;
    }

    /**
     * Distribute extra viewport height among rows. Applies a soft cap
     * so individual rows don't grow excessively for aberrant inputs.
     */
    protected void distributeExtraHeight(double availableHeight) {
        // default: no distribution for primitives or when height unavailable
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

    public String getCssClass() {
        return SchemaPath.sanitize(node.getField());
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

    public double getLabelHeight() {
        return labelStyle.getHeight();
    }

    public double getLabelWidth() {
        return labelWidth;
    }

    public abstract SchemaNode getNode();

    public abstract MeasureResult getMeasureResult();

    /** Returns true when this node and all descendants have converged (frozen result cached). */
    public abstract boolean isConverged();

    /**
     * Compose a {@link LayoutDecisionNode} tree from the current layout state.
     * Must be called after {@code autoLayout()} completes. Reads existing field
     * state without triggering any layout side effects (no {@code clear()} calls).
     *
     * @return the decision tree rooted at this node
     */
    public abstract LayoutDecisionNode snapshotDecisionTree();

    /**
     * Snapshot the layout result from the current in-memory state without
     * triggering any layout side effects (no {@code clear()} calls).
     * Must be called after {@code layout()} has run.
     *
     * @return a point-in-time immutable snapshot of the layout result
     */
    public abstract LayoutResult snapshotLayoutResult();

    public SchemaPath getSchemaPath() {
        return schemaPath;
    }

    public void setSchemaPath(SchemaPath schemaPath) {
        this.schemaPath = schemaPath;
    }

    abstract public double justify(double justified);

    public Label label(double width, double height) {
        return labelStyle.label(width, getLabel(), height);
    }

    public double labelWidth(String label) {
        return Style.snap(labelStyle.width(label));
    }

    /**
     * Derives and sets {@link SchemaPath} for this layout and all child layouts,
     * walking the schema tree topology. Must be called after the layout is
     * obtained from {@link com.chiralbehaviors.layout.style.Style#layout(com.chiralbehaviors.layout.schema.SchemaNode)}
     * and before {@link #measure(com.fasterxml.jackson.databind.JsonNode, com.chiralbehaviors.layout.style.Style)}.
     *
     * @param path  the path for this node
     * @param model factory used to obtain child layouts
     */
    public abstract void buildPaths(SchemaPath path, Style model);

    abstract public double layout(double width);

    abstract public double layoutWidth();

    abstract public double measure(JsonNode data,
                                   Function<JsonNode, JsonNode> extractor,
                                   Style model);

    public SchemaNodeLayout measure(JsonNode datum, Style model) {
        Fold fold = fold(JsonNodeFactory.instance.objectNode()
                                                 .set(getField(), datum),
                         n -> n, model);
        fold.layout()
            .measure(fold.datum(), n -> n, model);
        return fold.layout();
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
            if (sub instanceof ArrayNode subArray) {
                aggregate.addAll(subArray);
                cardSum += sub.size();
            } else {
                cardSum += 1;
                aggregate.add(sub);
            }
        }
        return new Fold(aggregate,
                        (cardSum == 0
                         || data.size() == 0) ? 1
                                              : (int) Math.round((double) cardSum
                                                                  / data.size()),
                        this);
    }

    protected Fold fold(JsonNode datum, Function<JsonNode, JsonNode> extractor,
                        Style model) {
        return fold(datum);
    }

    public double getColumnHeaderIndentation() {
        return columnHeaderIndentation;
    }
}
