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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * @author halhildebrand
 *
 */
abstract public class VerticalCell<T extends Region> extends VBox
        implements LayoutCell<T> {
    public static final String                                     STYLE_CLASS = "vertical-cell";

    private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

    static {
        List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Region.getClassCssMetaData());
        STYLEABLES = Collections.unmodifiableList(styleables);
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return STYLEABLES;
    }

    private final String stylesheet;

    protected VerticalCell(String styleSheet) {
        URL url = getClass().getResource(styleSheet);
        stylesheet = url == null ? null : url.toExternalForm();
        getStyleClass().add(STYLE_CLASS);
    }

    @Override
    public String getUserAgentStylesheet() {
        return stylesheet;
    }

    @Override
    public void setFocus(boolean focus) {
        setFocused(focus);
    }
}
