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

package com.chiralbehaviors.layout.explorer;

import org.controlsfx.control.CheckTreeView;

import com.chiralbehaviors.layout.schema.Relation;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

public class SchemaView extends Control {
    private final ObjectProperty<Relation> root;

    public SchemaView() {
        this((Relation) null);
    }

    public SchemaView(Relation root) {
        this(new SimpleObjectProperty<Relation>(root));
    }

    public SchemaView(SimpleObjectProperty<Relation> root) {
        this.root = root;
        this.root.addListener((o, p, c) -> rebuild(c));
        rebuild(root.get());
    }

    public ObjectProperty<Relation> root() {
        return root;
    }

    public void setRoot(Relation root) {
        this.root.set(root);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SchemaViewSkin(this);
    }

    private CheckBoxTreeItem<String> buildItem(Relation node) {
        final CheckBoxTreeItem<String> item = new CheckBoxTreeItem<String>(node.getLabel());
        item.setExpanded(true);
        item.setIndependent(true);
        item.setSelected(!node.isFold());
        item.selectedProperty()
            .addListener((o, p, c) -> {
                node.setFold(!c);
            });
        node.getChildren()
            .stream()
            .filter(c -> c instanceof Relation)
            .map(c -> (Relation) c)
            .forEach(c -> {
                item.getChildren()
                    .add(buildItem(c));
            });
        return item;
    }

    private CheckTreeView<String> buildSchemaTree(Relation root) {
        CheckTreeView<String> view = new CheckTreeView<>();
        view.setRoot(buildItem(root));
        return view;
    }

    private Object rebuild(Relation relation) {
        if (relation == null) {
            getChildren().clear();
        } else {
            getChildren().add(buildSchemaTree(relation));
        }
        return null;
    }
}
