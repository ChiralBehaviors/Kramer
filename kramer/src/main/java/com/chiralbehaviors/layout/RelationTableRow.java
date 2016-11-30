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

package com.chiralbehaviors.layout;

import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.TableRow;

/**
 * 
 * The tableRow which will holds the top level relation and primitives
 */
public class RelationTableRow extends TableRow<JsonNode> {
    public final Consumer<JsonNode> manager;

    public RelationTableRow(Consumer<JsonNode> manager, Control row) {
        this.manager = manager;
        getChildren().setAll(row);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RelationTableRowSkin(this);
    }

    @Override
    protected void updateItem(JsonNode item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            return;
        }
        manager.accept(item);
    }
}
