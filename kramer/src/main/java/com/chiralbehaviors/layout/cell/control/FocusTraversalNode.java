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

package com.chiralbehaviors.layout.cell.control;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.beans.InvalidationListener;
import javafx.scene.Node;

/**
 * @author halhildebrand
 * @param <C>
 *
 */
abstract public class FocusTraversalNode<C extends LayoutCell<?>>
        implements FocusTraversal<C> {

    public static enum Bias {
        HORIZONTAL,
        VERTICAL;
    }

    protected final Bias                         bias;
    protected final FocusTraversal<?>            parent;
    protected MultipleCellSelection<JsonNode, C> selectionModel;

    public FocusTraversalNode(FocusTraversal<?> parent,
                              MultipleCellSelection<JsonNode, C> selectionModel,
                              Bias bias) {
        this.bias = bias;
        this.parent = parent;
        this.selectionModel = selectionModel;
        Node node = getNode();
        node.focusedProperty()
            .addListener((InvalidationListener) property -> {
                if (node.isFocused()) {
                    setCurrent();
                }
            });
    }

    @Override
    public void activate() {
        int focusedIndex = selectionModel.getFocusedIndex();
        selectionModel.select(focusedIndex);
        if (focusedIndex >= 0) {
            edit();
        }
    }

    @Override
    public void edit() {
        // default nothing
    }

    @Override
    public void selectNext() {
        if (selectionModel.getFocusedIndex() == -1) {
            selectionModel.focus(0);
        } else if (selectionModel.getFocusedIndex() != selectionModel.getItemCount()
                                                       - 1) {
            selectionModel.focus(selectionModel.getFocusedIndex() + 1);
        } else {
            traverseNext();
        }
    }

    @Override
    public void selectPrevious() {
        if (selectionModel.getFocusedIndex() == -1) {
            selectionModel.focus(0);
        } else if (selectionModel.getFocusedIndex() > 0) {
            selectionModel.focus(selectionModel.getFocusedIndex() - 1);
        } else {
            traversePrevious();
        }
    }

    public void setCurrent() {
        if (parent != null) {
            parent.setCurrent(this);
        }
    }

    @Override
    public void setCurrent(FocusTraversalNode<?> focused) {
        if (parent != null) {
            parent.setCurrent(focused);
        }
    }

    @Override
    public final void traverseNext() {
        if (parent == null) {
            return;
        }
        parent.selectNext();
    }

    @Override
    public final void traversePrevious() {
        if (parent == null) {
            return;
        }
        parent.selectPrevious();
    }

    abstract protected Node getNode();
}