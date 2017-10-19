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

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.layout.AnchorPane;

/**
 * @author halhildebrand
 *
 */
public class PrimitiveControl extends JsonControl {

    private final Label label;

    public PrimitiveControl(Primitive primitive) {
        super();
        getStyleClass().add(primitive.getField());
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
        getChildren().add(new AnchorPane(label));
    }

    @Override
    public void setItem(JsonNode item) {
        label.setText(SchemaNode.asText(item));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new PrimitiveControlSkin(this);
    }

}
