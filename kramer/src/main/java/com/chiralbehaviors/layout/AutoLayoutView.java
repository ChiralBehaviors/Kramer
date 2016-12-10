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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.chiralbehaviors.layout.Layout.LayoutModel;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.layout.AnchorPane;

/**
 * @author hhildebrand
 *
 */
public class AutoLayoutView extends Control {
    private static final java.util.logging.Logger log         = Logger.getLogger(AutoLayoutView.class.getCanonicalName());

    private SimpleObjectProperty<JsonNode>        data        = new SimpleObjectProperty<>();
    private Control                               layout;
    private double                                layoutWidth = 0.0;
    private LayoutModel                           model;
    private final SimpleObjectProperty<Relation>  root        = new SimpleObjectProperty<>();
    private Layout                                style;

    public AutoLayoutView() {
        this(null);
    }

    public AutoLayoutView(Relation root) {
        this(root, new LayoutModel() {
        });
    }

    public AutoLayoutView(Relation root, LayoutModel model) {
        this.model = model;
        style = new Layout(this.model);
        this.root.set(root);
        widthProperty().addListener((o, p, c) -> resize(c.doubleValue()));
        data.addListener((o, p, c) -> setContent());
        getStylesheets().addListener(new ListChangeListener<String>() {
            @Override
            public void onChanged(javafx.collections.ListChangeListener.Change<? extends String> c) {
                style = new Layout(getStylesheets(), AutoLayoutView.this.model);
            }
        });
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

    public void setData(JsonNode node) {
        data.set(node);
    }

    public void setRoot(Relation rootRelationship) {
        root.set(rootRelationship);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AutoLayoutSkin(this);
    }

    private void resize(double width) {
        try {
            if (layoutWidth == width) {
                return;
            }
            layoutWidth = width;
            getChildren().clear();
            Relation relation = root.get();
            if (relation == null) {
                return;
            }
            relation.autoLayout(width, style);
            layout = relation.buildControl(style);
            relation.setItems(layout, data.get());
            AnchorPane.setTopAnchor(layout, 0.0);
            AnchorPane.setLeftAnchor(layout, 0.0);
            AnchorPane.setRightAnchor(layout, 0.0);
            AnchorPane.setBottomAnchor(layout, 0.0);
            getChildren().add(layout);
        } catch (Throwable e) {
            log.log(Level.SEVERE,
                    String.format("Unable to resize to {}", width), e);
        }
    }

    private void setContent() {
        try {
            if (layout != null) {
                SchemaNode relation = root.get();
                if (relation != null) {
                    relation.setItems(layout, data.get());
                }
            }
        } catch (Throwable e) {
            log.log(Level.SEVERE, "cannot set content", e);
        }
    }
}
