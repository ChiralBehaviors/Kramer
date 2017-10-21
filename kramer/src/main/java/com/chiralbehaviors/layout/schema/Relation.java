/**
 * Copyright (c) 2016 Chiral Behaviors, LLC, all rights reserved.
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

package com.chiralbehaviors.layout.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.LayoutProvider;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.SchemaNodeLayout.INDENT;
import com.chiralbehaviors.layout.control.JsonControl;
import com.chiralbehaviors.layout.control.NestedTable;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.util.Pair;

/**
 * @author hhildebrand
 *
 */
public class Relation extends SchemaNode {
    private boolean                autoFold = true;
    private final List<SchemaNode> children = new ArrayList<>();
    private Relation               fold;
    private RelationLayout         layout;
    private boolean                useTable = false;

    public Relation(String label) {
        super(label);
    }

    public void addChild(SchemaNode child) {
        children.add(child);
    }

    public void autoLayout(int cardinality, double width) {
        double justified = LayoutProvider.snap(width);
        layout(cardinality, justified);
        layout.compress(justified);
        cellHeight(cardinality, width);
    }

    @Override
    public Pair<Consumer<JsonNode>, Region> buildColumn(NestedTable table,
                                                        double rendered) {
        return table.buildRelation(rendered, layout);
    }

    @Override
    public Function<Double, Region> buildColumnHeader(INDENT indent) {
        if (isFold()) {
            return fold.buildColumnHeader(indent);
        }
        return layout.columnHeader(indent);
    }

    public JsonControl buildControl(int cardinality, double width) {
        if (isFold()) {
            return fold.buildControl(layout.getAverageCardinality()
                                     * cardinality, width);
        }
        return useTable ? buildNestedTable(n -> n, cardinality, width)
                        : buildOutline(n -> n, cardinality);
    }

    public JsonControl buildNestedTable(Function<JsonNode, JsonNode> extractor,
                                        int cardinality, double justified) {
        if (isFold()) {
            return fold.buildNestedTable(extract(extractor),
                                         layout.getAverageCardinality()
                                                             * cardinality,
                                         justified);
        }
        return layout.buildNestedTable(cardinality);
    }

    public JsonControl buildOutline(Function<JsonNode, JsonNode> extractor,
                                    int cardinality) {
        if (isFold()) {
            return fold.buildOutline(extract(extractor),
                                     layout.getAverageCardinality()
                                                         * cardinality);
        }
        return layout.buildOutline(extractor, cardinality);
    }

    @Override
    public double cellHeight(int card, double width) {
        if (isFold()) {
            return fold.cellHeight(layout.getAverageCardinality() * card,
                                   width);
        }
        return layout.cellHeight(card, width);
    }

    @Override
    public double columnHeaderHeight() {
        if (isFold()) {
            return fold.columnHeaderHeight();
        }
        return layout.getColumnHeaderHeight();
    }

    @Override
    public void compress(double justified) {
        if (isFold()) {
            fold.compress(justified);
            return;
        }
        layout.compress(justified);
    }

    @Override
    public JsonNode extractFrom(JsonNode jsonNode) {
        if (isFold()) {
            return fold.extractFrom(super.extractFrom(jsonNode));
        }
        return super.extractFrom(jsonNode);
    }

    public SchemaNode getChild(String field) {
        for (SchemaNode child : children) {
            if (child.getField()
                     .equals(field)) {
                return child;
            }
        }
        return null;
    }

    public List<SchemaNode> getChildren() {
        return isFold() ? fold.getChildren() : children;
    }

    public Relation getFold() {
        return fold;
    }

    @Override
    public double getLabelWidth() {
        if (isFold()) {
            return fold.getLabelWidth();
        }
        return layout.labelWidth(label);
    }

    @Override
    public RelationLayout getLayout() {
        return layout;
    }

    public double getTableColumnWidth() {
        return isFold() ? fold.getTableColumnWidth()
                        : layout.getTableColumnWidth();
    }

    @JsonProperty
    public boolean isFold() {
        return fold != null;
    }

    @Override
    public boolean isRelation() {
        return true;
    }

    public boolean isUseTable() {
        if (isFold()) {
            return fold.isUseTable();
        }
        return useTable;
    }

    @Override
    public double justify(double width) {
        if (isFold()) {
            return fold.justify(width);
        }
        assert useTable : "Not a nested table";
        return layout.justify(width);
    }

    @Override
    public double layout(int cardinality, double width) {
        useTable = false;
        if (isFold()) {
            return fold.layout(cardinality * layout.getAverageCardinality(),
                               width);
        }
        return layout.layout(cardinality, width);
    }

    @Override
    public double layoutWidth() {
        return layout.getLayoutWidth();
    }

    public void measure(JsonNode jsonNode, LayoutProvider layout) {
        measure(jsonNode, !jsonNode.isArray(), layout);
    }

    @Override
    public double measure(JsonNode data, boolean isSingular, LayoutProvider provider) {
        layout = provider.layout(this);

        if (isAutoFoldable()) {
            fold = ((Relation) children.get(children.size() - 1));
        }
        if (data.isNull() || children.size() == 0) {
            return 0;
        }

        return layout.measure(data, isSingular);
    }

    public void nestTable() {
        useTable = true;
        children.forEach(child -> {
            if (child.isRelation()) {
                ((Relation) child).nestTable();
            }
        });
    }

    @Override
    public Pair<Consumer<JsonNode>, Parent> outlineElement(int cardinality,
                                                           double labelWidth,
                                                           Function<JsonNode, JsonNode> extractor,
                                                           double justified) {
        if (isFold()) {
            return fold.outlineElement(layout.getAverageCardinality()
                                       * cardinality, labelWidth,
                                       extract(extractor), justified);
        }

        return layout.outlineElement(field, cardinality, label, labelWidth,
                                     extractor, useTable, justified);
    }

    @Override
    public double rowHeight(int cardinality, double justified) {
        if (isFold()) {
            return fold.rowHeight(cardinality * layout.getAverageCardinality(),
                                  justified);
        }
        return layout.rowHeight(cardinality, justified);
    }

    public void setFold(boolean fold) {
        this.fold = (fold && children.size() == 1 && children.get(0)
                                                             .isRelation()) ? (Relation) children.get(0)
                                                                            : null;
    }

    public void setItem(JsonControl control, JsonNode data) {
        if (data == null) {
            data = JsonNodeFactory.instance.arrayNode();
        }
        if (isFold()) {
            fold.setItem(control, flatten(data));
        } else {
            control.setItem(data);
        }
    }

    public void setUseTable(boolean useTable) {
        this.useTable = useTable;
    }

    @Override
    public double tableColumnWidth() {
        if (isFold()) {
            return fold.tableColumnWidth();
        }
        return layout.tableColumnWidth();
    }

    @Override
    public String toString() {
        return toString(0);
    }

    @Override
    public String toString(int indent) {
        StringBuffer buf = new StringBuffer();
        buf.append(String.format("Relation [%s]", label));
        buf.append('\n');
        children.forEach(c -> {
            for (int i = 0; i < indent; i++) {
                buf.append("    ");
            }
            buf.append("  - ");
            buf.append(c.toString(indent + 1));
            buf.append('\n');
        });
        return buf.toString();
    }

    private ArrayNode flatten(JsonNode data) {
        ArrayNode flattened = JsonNodeFactory.instance.arrayNode();
        if (data != null) {
            if (data.isArray()) {
                data.forEach(item -> {
                    flattened.addAll(SchemaNode.asArray(item.get(fold.getField())));
                });
            } else {
                flattened.addAll(SchemaNode.asArray(data.get(fold.getField())));
            }
        }
        return flattened;
    }

    private boolean isAutoFoldable() {
        return fold == null && autoFold && children.size() == 1
               && children.get(children.size() - 1) instanceof Relation;
    }
}
