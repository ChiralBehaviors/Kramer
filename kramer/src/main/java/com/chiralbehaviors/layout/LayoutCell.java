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

import com.chiralbehaviors.layout.flowless.Cell;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.beans.InvalidationListener;
import javafx.css.PseudoClass;
import javafx.css.StyleableProperty;
import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * @author halhildebrand
 *
 */
public interface LayoutCell<T extends Region> extends Cell<JsonNode, T> {
    PseudoClass EXTERNAL_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("external-focus");
    PseudoClass INTERNAL_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("internal-focus");
    PseudoClass PSEUDO_CLASS_EMPTY         = PseudoClass.getPseudoClass("empty");
    PseudoClass PSEUDO_CLASS_FILLED        = PseudoClass.getPseudoClass("filled");
    PseudoClass PSEUDO_CLASS_FOCUSED       = PseudoClass.getPseudoClass("focused");
    PseudoClass PSEUDO_CLASS_SELECTED      = PseudoClass.getPseudoClass("selected");

    default void cancelEdit() {
    }

    @SuppressWarnings("unchecked")
    @Override
    default T getNode() {
        return (T) this;
    }

    default boolean isEditing() {
        return false;
    }

    @Override
    default boolean isReusable() {
        return true;
    }

    default void initialize(String defaultStyle) {
        T node = getNode();
        // focusTraversable is styleable through css. Calling setFocusTraversable
        // makes it look to css like the user set the value and css will not
        // override. Initializing focusTraversable by calling set on the
        // CssMetaData ensures that css will be able to override the value.
        @SuppressWarnings("unchecked")
        StyleableProperty<Boolean> styleableProperty = (StyleableProperty<Boolean>) node.focusTraversableProperty();
        styleableProperty.applyStyle(null, Boolean.TRUE);
        node.getStyleClass()
            .addAll(defaultStyle);
        /**
         * Indicates whether or not this cell has focus. For example, a ListView
         * defines zero or one cell as being the "focused" cell. This cell would
         * have focused set to true.
         */
        node.focusedProperty()
            .addListener((InvalidationListener) property -> {
                node.pseudoClassStateChanged(PSEUDO_CLASS_FOCUSED,
                                             node.isFocused()); // TODO is this necessary??

                // The user has shifted focus, so we should cancel the editing on this cell
                if (!node.isFocused() && isEditing()) {
                    cancelEdit();
                }
            });

        node.focusedProperty()
            .addListener((observable, oldVal, newVal) -> {
                if (newVal) {
                    setExternalFocus(false);
                } else {
                    setExternalFocus(true);
                }
            });

        // initialize default pseudo-class state
        node.pseudoClassStateChanged(PSEUDO_CLASS_EMPTY, true);
    }

    default void setExternalFocus(boolean externalFocus) {
        T node = getNode();
        node.pseudoClassStateChanged(INTERNAL_PSEUDOCLASS_STATE,
                                     !externalFocus);
        node.pseudoClassStateChanged(EXTERNAL_PSEUDOCLASS_STATE, externalFocus);
    }

    default void updateSelection(Node node, boolean selected) {
        node.pseudoClassStateChanged(PSEUDO_CLASS_SELECTED, selected);
    }
}
