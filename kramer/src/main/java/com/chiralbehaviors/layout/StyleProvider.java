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

import com.chiralbehaviors.layout.LayoutProvider.LayoutModel;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;

import javafx.geometry.Insets;

/**
 * @author halhildebrand
 *
 */
public interface StyleProvider {

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

        public double getCellInset() {
            return cell.getLeft();
        }

        public double getCellVerticalInset() {
            return cell.getTop() + cell.getBottom();
        }

        public double getLeftHorizontalInset() {
            return container.getLeft();
        }

        public double getListHorizontalInset() {
            return container.getLeft() + container.getRight();
        }

        public double getNestedCellInset() {
            return getNestedRightInset() + getNestedLeftInset();
        }

        public double getNestedInset() {
            return getNestedLeftInset() + getNestedRightInset();
        }

        public double getNestedLeftInset() {
            return container.getLeft() + cell.getLeft();
        }

        public double getNestedListInset() {
            return getNestedLeftInset() + getNestedRightInset();
        }

        public double getNestedRightInset() {
            return container.getRight() + cell.getRight();
        }

        public double getRightCellInset() {
            return cell.getRight();
        }

        public double getRightHorizontalInset() {
            return container.getRight();
        }

        public double getVerticalInset() {
            return container.getTop() + container.getBottom();
        }
    }

    LayoutModel getModel();

    void initialize(List<String> styleSheets);

    PrimitiveLayout layout(Primitive primitive);

    RelationLayout layout(Relation relation);

    SchemaNodeLayout layout(SchemaNode node);

}