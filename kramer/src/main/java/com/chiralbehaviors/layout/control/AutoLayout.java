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

package com.chiralbehaviors.layout.control;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.chiralbehaviors.layout.Layout;
import com.chiralbehaviors.layout.Layout.LayoutModel;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Skin;

/**
 * @author hhildebrand
 *
 */
public class AutoLayout extends JsonControl {
    private static final java.util.logging.Logger log         = Logger.getLogger(AutoLayout.class.getCanonicalName());

    private JsonControl                           control;
    private SimpleObjectProperty<JsonNode>        data        = new SimpleObjectProperty<>();
    private double                                layoutWidth = 0.0;
    private LayoutModel                           model;
    private final SimpleObjectProperty<Relation>  root        = new SimpleObjectProperty<>();
    private Layout                                style;

    public AutoLayout() {
        this(null);
    }

    public AutoLayout(Relation root) {
        this(root, new LayoutModel() {
        });
    }

    public AutoLayout(Relation root, LayoutModel model) {
        this.model = model;
        style = new Layout(this.model);
        this.root.set(root);
        widthProperty().addListener((o, p, c) -> resize(c.doubleValue()));
        data.addListener((o, p, c) -> setContent());
        getStylesheets().addListener((ListChangeListener<String>) c -> style = new Layout(getStylesheets(),
                                                                                          AutoLayout.this.model));
    }

    public void autoLayout() {
        layoutWidth = 0.0;
        resize(getWidth());
    }

    public Property<JsonNode> dataProperty() {
        return data;
    }

    public JsonNode getData() {
        return data.get();
    }

    public SchemaNode getRoot() {
        return root.get();
    }

    public void measure(JsonNode data) {
        Relation top = root.get();
        if (top == null) {
            return;
        }
        try {
            top.measure(data, style);
        } catch (Throwable e) {
            log.log(Level.SEVERE, "cannot measure data", e);
        }
    }

    public SimpleObjectProperty<Relation> root() {
        return root;
    }

    public Property<Relation> rootProperty() {
        return root;
    }

    @Override
    public void setItem(JsonNode node) {
        data.set(node);
    }

    public void setRoot(Relation rootRelationship) {
        root.set(rootRelationship);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AutoLayoutSkin(this);
    }

    private void autoLayout(JsonNode zeeData, Relation relation, double width) {
        relation.autoLayout(zeeData.size(), width);
        control = relation.buildControl(zeeData.size(), width);
        relation.setItem(control, zeeData);
        getChildren().add(control);
    }

    private void resize(double width) {
        if (layoutWidth == width) {
            return;
        }

        layoutWidth = width;
        getChildren().clear();

        Relation relation = root.get();
        if (relation == null) {
            return;
        }

        JsonNode zeeData = data.get();
        if (zeeData == null) {
            return;
        }

        try {
            autoLayout(zeeData, relation, width);
        } catch (Throwable e) {
            log.log(Level.SEVERE,
                    String.format("Unable to resize to %s", width), e);
        }
    }

    private void setContent() {
        try {
            if (control != null) {
                Relation relation = root.get();
                if (relation != null) {
                    relation.setItem(control, data.get());
                }
            }
        } catch (Throwable e) {
            log.log(Level.SEVERE, "cannot set content", e);
        }
    }
}
