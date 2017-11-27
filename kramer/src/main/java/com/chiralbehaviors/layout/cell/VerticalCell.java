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

import javafx.geometry.Point2D;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * @author halhildebrand
 *
 */
abstract public class VerticalCell<T extends Region> extends VBox
        implements LayoutCell<T> {
    public static final String STYLE_CLASS = "horizontal-cell";

    private final String       stylesheet;

    protected VerticalCell(String styleSheet) {
        URL url = getClass().getResource(styleSheet);
        stylesheet = url == null ? null : url.toExternalForm();
        getStyleClass().add(STYLE_CLASS);
    }

    public Point2D expand(double width, double height) {
        return new Point2D(width + snappedLeftInset() + snappedRightInset(),
                           height + snappedLeftInset() + snappedRightInset());
    }

    @Override
    public String getUserAgentStylesheet() {
        return stylesheet;
    }
}
