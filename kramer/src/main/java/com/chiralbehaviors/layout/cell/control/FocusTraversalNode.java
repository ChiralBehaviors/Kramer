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

import static com.chiralbehaviors.layout.cell.control.SelectionEvent.SINGLE_SELECT;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.consumeWhen;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.sequence;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.unless;

import org.fxmisc.wellbehaved.event.template.InputMapTemplate;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.LayoutContainer;
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

    protected final Bias                                                         bias;
    protected final FocusTraversal<?>                                            parent;
    protected MultipleCellSelection<JsonNode, C>                                 selectionModel;

    private static final InputMapTemplate<FocusTraversalNode<?>, SelectionEvent> SELECTION_HANDLER_TEMPLATE;
    static {
        SELECTION_HANDLER_TEMPLATE = unless(h -> h.getContainer()
                                                  .getNode()
                                                  .isDisabled(),
                                            sequence(consumeWhen(SINGLE_SELECT,
                                                                 n -> true,
                                                                 (n, e) -> {
                                                                     if (n.parent != null) {
                                                                         n.parent.select(n.getContainer());
                                                                     }
                                                                 })));
    }

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
                    if (parent != null) {
                        parent.setCurrent();
                    }
                }
            });
        node.setOnMouseEntered(e -> node.pseudoClassStateChanged(LayoutCell.PSEUDO_CLASS_FOCUSED,
                                                                 true));
        node.setOnMouseExited(e -> node.pseudoClassStateChanged(LayoutCell.PSEUDO_CLASS_FOCUSED,
                                                                false));
        InputMapTemplate.installFallback(SELECTION_HANDLER_TEMPLATE, this,
                                         n -> n.getContainer()
                                               .getNode());
    }

    @Override
    public void activate() {
        System.out.println(String.format("Activate: %s",
                                         getContainer().getNode()
                                                       .getClass()
                                                       .getSimpleName()));
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
        return parent == null ? false : parent.isCurrent(this);
    }

    @Override
    public boolean isCurrent(FocusTraversalNode<?> node) {
        return parent == null ? false : parent.isCurrent(node);
    }

    @Override
    public void select(LayoutContainer<?, ?, ?> child) {
        if (parent != null) {
            parent.selectNoFocus(getContainer());
        }
        selectionModel.select(child.getIndex());
    }

    @Override
    public void selectNoFocus(LayoutContainer<?, ?, ?> child) {
        if (parent != null) {
            parent.selectNoFocus(getContainer());
        }
        selectionModel.select(child.getIndex(), false);
    }

    @Override
    public void selectNext() {
        System.out.println(String.format("Selecting next: %s",
                                         getContainer().getNode()
                                                       .getClass()
                                                       .getSimpleName()));
        if (selectionModel.getItemCount() > 0) {
            int focusedIndex = selectionModel.getFocusedIndex();
            if (focusedIndex == -1) {
                selectionModel.focus(0);
            } else if (focusedIndex == selectionModel.getItemCount() - 1) {
                //                selectionModel.focus(0);
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
        System.out.println(String.format("Selecting previous: %s",
                                         getContainer().getClass()
                                                       .getSimpleName()));
        if (selectionModel.getItemCount() > 0) {
            int focusedIndex = selectionModel.getFocusedIndex();
            if (focusedIndex > 0) {
                selectionModel.focus(focusedIndex - 1);
            } else {
                traversePrevious();
                //                selectionModel.focus(selectionModel.getItemCount() - 1);
            }
        } else {
            getContainer().getNode()
                          .requestFocus();
        }
    }

    @Override
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
        System.out.println(String.format("Traverse next: %s",
                                         getContainer().getClass()
                                                       .getSimpleName()));
        if (parent == null) {
            return;
        }
        parent.selectNext();
    }

    @Override
    public final void traversePrevious() {
        System.out.println(String.format("Traverse previous: %s",
                                         getContainer().getClass()
                                                       .getSimpleName()));
        if (parent == null) {
            return;
        }
        parent.selectPrevious();
    }

    public void unbind() {
        @SuppressWarnings("unchecked")
        FocusTraversalNode<LayoutCell<?>> n = (FocusTraversalNode<LayoutCell<?>>) this;
        InputMapTemplate.uninstall(SELECTION_HANDLER_TEMPLATE, n,
                                   c -> getContainer().getNode());
    }

    abstract protected LayoutContainer<?, ?, ?> getContainer();
}