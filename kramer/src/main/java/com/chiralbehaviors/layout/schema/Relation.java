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

import com.chiralbehaviors.layout.LayoutCell;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.layout.Region;

/**
 * @author hhildebrand
 *
 */
public class Relation extends SchemaNode {
    private boolean                autoFold = true;
    private final List<SchemaNode> children = new ArrayList<>();
    private Relation               fold;

    public Relation(String label) {
        super(label);
    }

    public void addChild(SchemaNode child) {
        children.add(child);
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
        return children;
    }

    public Relation getFold() {
        return fold;
    }

    public Relation getAutoFoldable() {
        return fold == null && autoFold && children.size() == 1
               && children.get(children.size() - 1) instanceof Relation
                                                                        ? (Relation) children.get(0)
                                                                        : null;
    }

    @JsonProperty
    public boolean isFold() {
        return fold != null;
    }

    @Override
    public boolean isRelation() {
        return true;
    }

    public void setFold(boolean fold) {
        this.fold = (fold && children.size() == 1 && children.get(0)
                                                             .isRelation()) ? (Relation) children.get(0)
                                                                            : null;
    }

    public void setItem(LayoutCell<? extends Region> control, JsonNode data) {
        if (data == null) {
            data = JsonNodeFactory.instance.arrayNode();
        }
        if (isFold()) {
            fold.setItem(control, flatten(data));
        } else {
            control.updateItem(data);
        }
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
            if (!c.equals(children.get(children.size() - 1))) {
                buf.append('\n');
            }
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
}
