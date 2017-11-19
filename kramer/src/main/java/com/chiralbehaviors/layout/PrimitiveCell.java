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

import com.chiralbehaviors.layout.flowless.Cell;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;

/**
 * @author halhildebrand
 *
 */
public class PrimitiveCell implements Cell<JsonNode, Region> {

    private AnchorPane  anchor;
    private final Label label;

    public PrimitiveCell() {
        super();
        label = new Label();
        label.setWrapText(true);
        label.setStyle("-fx-background-color: " + "         rgba(0,0,0,0.08),"
                       + "        linear-gradient(#9a9a9a, #909090),"
                       + "        white 0%;"
                       + "    -fx-background-insets: 0 0 -1 0,0,1;"
                       + "    -fx-background-radius: 5,5,4;"
                       + "    -fx-padding: 3 30 3 30;"
                       + "    -fx-text-fill: #242d35;"
                       + "    -fx-font-size: 14px;");
        AnchorPane.setLeftAnchor(label, 0d);
        AnchorPane.setRightAnchor(label, 0d);
        AnchorPane.setTopAnchor(label, 0d);
        AnchorPane.setBottomAnchor(label, 0d);
        anchor = new AnchorPane(label);
    }

    @Override
    public Region getNode() {
        return anchor;
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    @Override
    public void updateItem(JsonNode item) {
        label.setText(SchemaNode.asText(item));
    }

}
