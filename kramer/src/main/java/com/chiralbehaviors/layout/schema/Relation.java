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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author hhildebrand
 *
 */
public non-sealed class Relation extends SchemaNode {
    private boolean                autoFold     = true;
    private boolean                autoSort     = false;
    private final List<SchemaNode> children     = new ArrayList<>();
    private Relation               fold;
    private Boolean                hideIfEmpty  = null;
    private List<String>           sortFields   = List.of();

    public Relation(String label) {
        super(label);
    }

    public void addChild(SchemaNode child) {
        children.add(child);
    }

    public Relation getAutoFoldable() {
        if (fold != null) {
            return fold;
        }
        return autoFold && children.size() == 1
               && children.get(children.size() - 1) instanceof Relation r
                                                                          ? r
                                                                          : null;
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

    @JsonProperty
    public boolean isFold() {
        return fold != null;
    }

    @Override
    public boolean isRelation() {
        return true;
    }

    public void setFold(boolean fold) {
        this.fold = (fold && children.size() == 1
                     && children.get(0) instanceof Relation r) ? r
                                                               : null;
    }

    public Boolean getHideIfEmpty() {
        return hideIfEmpty;
    }

    public void setHideIfEmpty(Boolean hide) {
        this.hideIfEmpty = hide;
    }

    public List<String> getSortFields() {
        return sortFields;
    }

    public boolean isAutoSort() {
        return autoSort;
    }

    public void setAutoSort(boolean autoSort) {
        this.autoSort = autoSort;
    }

    public void setSortFields(List<String> sortFields) {
        this.sortFields = sortFields == null ? List.of() : List.copyOf(sortFields);
    }

    @Override
    public String toString() {
        return toString(0);
    }

    @Override
    public String toString(int indent) {
        var buf = new StringBuilder();
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
}
