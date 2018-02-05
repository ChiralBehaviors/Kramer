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

import com.chiralbehaviors.layout.flowless.Cell;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.beans.InvalidationListener;
import javafx.css.PseudoClass;
import javafx.css.StyleableProperty;
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

        node.focusedProperty()
            .addListener((InvalidationListener) property -> {
                System.out.println(String.format("Setting focus: %s on %s",
                                                 node.isFocused(), node));
                node.pseudoClassStateChanged(PSEUDO_CLASS_FOCUSED,
                                             node.isFocused());

                if (!node.isFocused() && isEditing()) {
                    cancelEdit();
                }
            });

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

    default void unselect() {
    }

    @Override
    default void updateItem(JsonNode item) {
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_EMPTY, item == null);
    }

    @Override
    default void updateSelection(boolean selected) {
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_SELECTED, selected);
    }
}
