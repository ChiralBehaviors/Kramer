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
import com.chiralbehaviors.layout.cell.LayoutContainer;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;

/**
 * @author halhildebrand
 * @param <C>
 *
 */
abstract public class FocusTraversalNode<C extends LayoutCell<?>>
        implements FocusTraversal<C> {

    @Override
    public boolean propagate(SelectionEvent event) {
        parent.selectNoFocus(getContainer());
        selectionModel.select(event.getSelected()
                                   .getIndex());
        return parent.propagate(new SelectionEvent(getContainer(),
                                                   event.getEventType()));
    }

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
        Node node = getContainer().getNode();
        node.focusedProperty()
            .addListener((InvalidationListener) property -> {
                if (node.isFocused()) {
                    parent.setCurrent();
                }
            });
        node.setOnMouseEntered(e -> node.pseudoClassStateChanged(LayoutCell.PSEUDO_CLASS_FOCUSED,
                                                                 true));
        node.setOnMouseExited(e -> node.pseudoClassStateChanged(LayoutCell.PSEUDO_CLASS_FOCUSED,
                                                                false));
        selectionModel.getSelectedIndices()
                      .addListener(new ListChangeListener<Integer>() {

                          @Override
                          public void onChanged(Change<? extends Integer> c) {
                              c.next();
                              if (c.wasRemoved()) {
                                  c.getRemoved()
                                   .forEach(i -> selectionModel.getCell(i)
                                                               .unselect());
                              }
                          }
                      });
    }

    @Override
    public void activate() {
        int focusedIndex = selectionModel.getFocusedIndex();
        if (focusedIndex < 0) {
            return;
        }
        selectionModel.select(focusedIndex);
        selectionModel.getCell(focusedIndex)
                      .activate();
    }

    @Override
    public void edit() {
        setCurrent();
    }

    @Override
    public boolean isCurrent() {
        return parent.isCurrent(this);
    }

    @Override
    public boolean isCurrent(FocusTraversalNode<?> node) {
        return parent.isCurrent(node);
    }

    @Override
    public void select(LayoutContainer<?, ?, ?> child) {
        parent.selectNoFocus(getContainer());
        selectionModel.select(child.getIndex());
    }

    @Override
    public void selectNoFocus(LayoutContainer<?, ?, ?> child) {
        parent.selectNoFocus(getContainer());
        selectionModel.select(child.getIndex(), false);
    }

    @Override
    public void selectNext() {
        if (selectionModel.getItemCount() > 0) {
            int focusedIndex = selectionModel.getFocusedIndex();
            if (focusedIndex == -1) {
                selectionModel.focus(0);
            } else if (focusedIndex == selectionModel.getItemCount() - 1) {
                traverseNext();
            } else {
                selectionModel.focus(focusedIndex + 1);
            }
        } else {
            getContainer().getNode()
                          .requestFocus();
        }
    }

    @Override
    public void selectPrevious() {
        if (selectionModel.getItemCount() > 0) {
            int focusedIndex = selectionModel.getFocusedIndex();
            if (focusedIndex > 0) {
                selectionModel.focus(focusedIndex - 1);
            } else {
                traversePrevious();
            }
        } else {
            getContainer().getNode()
                          .requestFocus();
        }
    }

    @Override
    public void setCurrent() {
        parent.setCurrent(this);
    }

    @Override
    public void setCurrent(FocusTraversalNode<?> focused) {
        parent.setCurrent(focused);
    }

    @Override
    public final void traverseNext() {
        parent.selectNext();
    }

    @Override
    public final void traversePrevious() {
        parent.selectPrevious();
    }

    public void unbind() {
        // nothing to do
    }

    abstract protected LayoutContainer<?, ?, ?> getContainer();
}