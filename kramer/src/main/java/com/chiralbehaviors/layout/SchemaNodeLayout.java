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

import com.chiralbehaviors.layout.schema.Relation;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.Control;

/**
 * @author halhildebrand
 *
 */
abstract public class SchemaNodeLayout {
    protected double height         = -1.0;
    protected double justifiedWidth = -1.0;

    public void adjustHeight(double delta) {
        this.height = Layout.snap(height + delta);
    }

    abstract public double baseOutlineWidth(double available);

    abstract public double baseTableColumnWidth(double available);

    public double getHeight() {
        return height;
    }

    public double getJustifiedWidth() {
        return justifiedWidth;
    }

    abstract public Control label(double labelWidth, String label);

    abstract public double labelWidth(String label);

    abstract public double layout(int cardinality, double width);

    abstract public double measure(Relation parent, JsonNode data,
                                   boolean isSingular);

    abstract public double tableColumnWidth(double columnWidth);

    protected void clear() {
        height = -1.0;
        justifiedWidth = -1.0;
    }

}
