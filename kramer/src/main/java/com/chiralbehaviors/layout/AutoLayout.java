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

import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusController;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;

/**
 * @author hhildebrand
 *
 */
public class AutoLayout extends AnchorPane implements LayoutCell<AutoLayout> {
    /**
     * 
     */
    private static final String                    AUTO_LAYOUT = "auto-layout";
    private static final String                    DEFAULT_CSS = "default.css";
    private static final java.util.logging.Logger  log         = Logger.getLogger(AutoLayout.class.getCanonicalName());
    private static final String                    STYLE_SHEET = "auto-layout.css";

    private LayoutCell<? extends Region>           control;
    private final FocusController<AutoLayout>      controller;
    private SimpleObjectProperty<JsonNode>         data        = new SimpleObjectProperty<>();
    private SchemaNodeLayout                       layout;
    private double                                 layoutWidth = 0.0;
    private Style                                 model;
    private final SimpleObjectProperty<SchemaNode> root        = new SimpleObjectProperty<>();
    private final String                           stylesheet;

    public AutoLayout() {
        this(null);
    }

    public AutoLayout(Relation root) {
        this(root, new Style() {
        });
    }

    public AutoLayout(Relation root, Style model) {
        URL url = getClass().getResource(STYLE_SHEET);
        stylesheet = url == null ? null : url.toExternalForm();
        getStyleClass().add(AUTO_LAYOUT);
        this.model = model;
        this.root.set(root);
        data.addListener((o, p, c) -> setContent());
        controller = new FocusController<>(this);
        getStylesheets().addListener((ListChangeListener<String>) c -> {
            model.setStyleSheets(getStylesheets());
            layout = null;
        });
        getStylesheets().add(getClass().getResource(DEFAULT_CSS)
                                       .toExternalForm());
    }

    public void autoLayout() {
        layoutWidth = 0.0;
        Platform.runLater(() -> autoLayout(getData(), getWidth()));
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
    public String getUserAgentStylesheet() {
        return stylesheet;
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
            layout = model.layout(top)
                          .measure(data, model);
        } catch (Throwable e) {
            log.log(Level.SEVERE, "cannot measure data", e);
        }
    }

    @Override
    public void resize(double width, double height) {
        super.resize(width, height);
        if (layoutWidth == width || width < 10.0 || height < 10.0) {
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
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
    }

    private void autoLayout(JsonNode zeeData, double width) {
        if (width < 10.0) {
            return;
        }
        if (layout == null) {
            measure(zeeData);
        }
        LayoutCell<?> old = control;
        control = layout.autoLayout(width, controller, model);
        Region node = control.getNode();

        setTopAnchor(node, 0d);
        setRightAnchor(node, 0d);
        setBottomAnchor(node, 0d);
        setLeftAnchor(node, 0d);

        getChildren().setAll(node);
        if (old != null) {
            old.dispose();
        }
        node.setMinWidth(width);
        node.setPrefWidth(width);
        node.setMaxWidth(width);
        control.updateItem(zeeData);
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
