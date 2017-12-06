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

import java.util.List;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Insets;
import javafx.util.Pair;

/**
 * @author halhildebrand
 *
 */
public interface StyleProvider {

    interface LayoutModel {

        default <T extends LayoutCell<?>> void apply(T cell, Primitive p) {
        }

        default <T extends LayoutCell<?>> void apply(VirtualFlow<JsonNode, T> list,
                                                     Relation relation) {
        }
    }

    class StyledInsets {
        public final Insets cell;
        public final Insets container;

        public StyledInsets(Insets container, Insets cell) {
            this.container = container;
            this.cell = cell;
        }

        public double getCellHorizontalInset() {
            return cell.getLeft() + cell.getRight();
        }

        public double getCellLeftInset() {
            return cell.getLeft();
        }

        public double getCellRightInset() {
            return cell.getRight();
        }

        public double getCellVerticalInset() {
            return cell.getTop() + cell.getBottom();
        }

        public double getHorizontalInset() {
            return container.getLeft() + container.getRight();
        }

        public double getVerticalInset() {
            return container.getTop() + container.getBottom();
        }
    }

    StyleProvider.LayoutModel getModel();

    void initialize(List<String> styleSheets);

    Pair<StyledInsets, StyledInsets> insets(RelationLayout layout);

    RelationLayout layout(Relation relation);

    SchemaNodeLayout layout(SchemaNode top);

    Insets listInsets(PrimitiveLayout node);

}