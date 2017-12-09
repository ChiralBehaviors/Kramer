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

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.VerticalCell;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * @author hhildebrand
 *
 */
public class AutoLayout extends VerticalCell<AutoLayout> {
    private static final java.util.logging.Logger  log         = Logger.getLogger(AutoLayout.class.getCanonicalName());
    private static final String                    STYLE_SHEET = "auto-layout.css";

    private LayoutCell<? extends Region>           control;
    private SimpleObjectProperty<JsonNode>         data        = new SimpleObjectProperty<>();
    private SchemaNodeLayout                       layout;
    private double                                 layoutWidth = 0.0;
    private StyleProvider.LayoutModel              model;
    private final SimpleObjectProperty<SchemaNode> root        = new SimpleObjectProperty<>();
    private StyleProvider                          style;

    public AutoLayout() {
        this(null);
    }

    public AutoLayout(Relation root) {
        this(root, new StyleProvider.LayoutModel() {
        });
    }

    public AutoLayout(Relation root, StyleProvider.LayoutModel model) {
        super(STYLE_SHEET);
        this.model = model;
        style = new LayoutProvider(this.model);
        this.root.set(root);
        widthProperty().addListener((o, p, c) -> resize(c.doubleValue()));
        data.addListener((o, p, c) -> setContent());
        getStylesheets().addListener((ListChangeListener<String>) c -> style = new LayoutProvider(getStylesheets(),
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

    @Override
    public AutoLayout getNode() {
        return this;
    }

    public SchemaNode getRoot() {
        return root.get();
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    public void measure(JsonNode data) {
        SchemaNode top = root.get();
        if (top == null || data == null || data.isNull() || data.size() == 0) {
            return;
        }
        try {
            layout = style.layout(top)
                          .measure(data);
        } catch (Throwable e) {
            log.log(Level.SEVERE, "cannot measure data", e);
        }
    }

    public SchemaNode root() {
        return root.get();
    }

    public Property<SchemaNode> rootProperty() {
        return root;
    }

    public void setRoot(SchemaNode rootNode) {
        root.set(rootNode);
    }

    @Override
    public void updateItem(JsonNode item) {
        data.set(item);
    }

    private void autoLayout(JsonNode zeeData, double width) {
        if (width < 10.0) {
            return;
        }
        if (layout == null) {
            measure(zeeData);
        }
        LayoutCell<?> old = control;
        control = layout.autoLayout(width);
        Region node = control.getNode();
        VBox.setVgrow(node, Priority.ALWAYS);
        getChildren().setAll(node);
        if (old != null) {
            old.dispose();
        }
        node.setMinWidth(width);
        node.setPrefWidth(width);
        node.setMaxWidth(width);

        setMinSize(width, layout.getHeight());
        setPrefSize(width, layout.getHeight());
        control.updateItem(zeeData);
    }

    private void resize(double width) {
        if (layoutWidth == width || width < 10.0) {
            return;
        }

        layoutWidth = width;

        SchemaNode node = root.get();
        if (node == null) {
            return;
        }

        JsonNode zeeData = data.get();
        if (zeeData == null) {
            return;
        }

        try {
            autoLayout(zeeData, width);
        } catch (Throwable e) {
            log.log(Level.SEVERE,
                    String.format("Unable to resize to %s", width), e);
        }
    }

    private void setContent() {
        JsonNode datum = data.get();
        try {
            if (control == null) {
                Platform.runLater(() -> autoLayout(datum, getWidth()));
            } else {
                control.updateItem(datum);
            }
            layout();
        } catch (Throwable e) {
            log.log(Level.SEVERE, "cannot set content", e);
        }
    }
}
