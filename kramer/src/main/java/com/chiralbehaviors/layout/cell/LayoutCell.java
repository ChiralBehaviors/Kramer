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

package com.chiralbehaviors.layout.cell;

import java.util.List;

import com.chiralbehaviors.layout.flowless.Cell;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.beans.InvalidationListener;
import javafx.css.PseudoClass;
import javafx.css.StyleableProperty;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Region;

/**
 * @author halhildebrand
 *
 */
public interface LayoutCell<T extends Region> extends Cell<JsonNode, T> {
    PseudoClass EXTERNAL_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("external-focus");
    PseudoClass INTERNAL_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("internal-focus");
    PseudoClass PSEUDO_CLASS_EMPTY         = PseudoClass.getPseudoClass("empty");
    PseudoClass PSEUDO_CLASS_EVEN          = PseudoClass.getPseudoClass("even");
    PseudoClass PSEUDO_CLASS_FILLED        = PseudoClass.getPseudoClass("filled");
    PseudoClass PSEUDO_CLASS_FOCUSED       = PseudoClass.getPseudoClass("focused");
    PseudoClass PSEUDO_CLASS_ODD           = PseudoClass.getPseudoClass("odd");
    PseudoClass PSEUDO_CLASS_SELECTED      = PseudoClass.getPseudoClass("selected");

    static Node pick(Node node, double sceneX, double sceneY) {
        Point2D p = node.sceneToLocal(sceneX, sceneY, true /* rootScene */);

        // check if the given node has the point inside it, or else we drop out
        if (!node.contains(p)) {
            return null;
        }

        // at this point we know that _at least_ the given node is a valid
        // answer to the given point, so we will return that if we don't find
        // a better child option
        if (node instanceof Parent) {
            // we iterate through all children in reverse order, and stop when we find a match.
            // We do this as we know the elements at the end of the list have a higher
            // z-order, and are therefore the better match, compared to children that
            // might also intersect (but that would be underneath the element).
            Node bestMatchingChild = null;
            List<Node> children = ((Parent) node).getChildrenUnmodifiable();
            for (int i = children.size() - 1; i >= 0; i--) {
                Node child = children.get(i);
                p = child.sceneToLocal(sceneX, sceneY, true /* rootScene */);
                if (child.isVisible() && !child.isMouseTransparent()
                    && child.contains(p)) {
                    bestMatchingChild = child;
                    break;
                }
            }

            if (bestMatchingChild != null) {
                return pick(bestMatchingChild, sceneX, sceneY);
            }
        }

        return node;
    }

    default void activate() {
    }

    default void cancelEdit() {
    }

    @SuppressWarnings("unchecked")
    @Override
    default T getNode() {
        return (T) this;
    }

    default void initialize(String defaultStyle) {
        T node = getNode();
        node.getStyleClass()
            .add(defaultStyle);

        // focusTraversable is styleable through css. Calling setFocusTraversable
        // makes it look to css like the user set the value and css will not
        // override. Initializing focusTraversable by calling set on the
        // CssMetaData ensures that css will be able to override the value.
        @SuppressWarnings("unchecked")
        StyleableProperty<Boolean> styleableProperty = (StyleableProperty<Boolean>) node.focusTraversableProperty();
        styleableProperty.applyStyle(null, Boolean.TRUE);

        /**
         * Indicates whether or not this cell has focus. For example, a ListView
         * defines zero or one cell as being the "focused" cell. This cell would
         * have focused set to true.
         */
        node.focusedProperty()
            .addListener((InvalidationListener) property -> {
                System.out.println(String.format("Setting focus: %s on %s",
                                                 node.isFocused(),
                                                 node.getClass()
                                                     .getSimpleName()));
                node.pseudoClassStateChanged(PSEUDO_CLASS_FOCUSED,
                                             node.isFocused()); // TODO is this necessary??

                // The user has shifted focus, so we should cancel the editing on this cell
                if (!node.isFocused() && isEditing()) {
                    cancelEdit();
                }
            });

        // initialize default pseudo-class state
        node.pseudoClassStateChanged(PSEUDO_CLASS_EMPTY, true);
    }

    default boolean isEditing() {
        return false;
    }

    @Override
    default boolean isReusable() {
        return true;
    }

    default void setExternalFocus(boolean externalFocus) {
        T node = getNode();
        node.pseudoClassStateChanged(INTERNAL_PSEUDOCLASS_STATE,
                                     !externalFocus);
        node.pseudoClassStateChanged(EXTERNAL_PSEUDOCLASS_STATE, externalFocus);
    }

    @Override
    default void updateItem(JsonNode item) {
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
    }

    @Override
    default void updateSelection(boolean selected) {
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_SELECTED, selected);
    }
}
