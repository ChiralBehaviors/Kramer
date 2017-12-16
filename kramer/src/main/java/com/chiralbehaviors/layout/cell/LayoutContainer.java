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

import static com.chiralbehaviors.layout.cell.SelectionEvent.DOUBLE_SELECT;
import static com.chiralbehaviors.layout.cell.SelectionEvent.SINGLE_SELECT;
import static com.chiralbehaviors.layout.cell.SelectionEvent.TRIPLE_SELECT;

import java.util.function.Function;
import java.util.function.Supplier;

import com.chiralbehaviors.layout.flowless.Cell;

import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * @author halhildebrand
 *
 */
public interface LayoutContainer<T, R extends Region, C extends LayoutCell<?>>
        extends LayoutCell<R> {

    default MultipleCellSelection<T, C> buildSelectionModel(Function<Integer, T> itemProvider,
                                                            Supplier<Integer> itemSizeProvider,
                                                            Function<Integer, C> cellProvider) {

        return new MultipleCellSelection<T, C>() {

            @Override
            public C getCell(int index) {
                return cellProvider.apply(index);
            }

            @Override
            public int getItemCount() {
                return itemSizeProvider.get();
            }

            @Override
            public T getModelItem(int index) {
                return (T) itemProvider.apply(index);
            }
        };
    }

    default MouseHandler bind() {
        MouseHandler mouseHandler = new MouseHandler(new Duration(300)) {

            @Override
            public void doubleClick(MouseEvent mouseEvent) {
                Hit<Cell<?, ?>> hit = hit(mouseEvent.getX(), mouseEvent.getY());
                if (hit != null) {
                    getNode().fireEvent(new SelectionEvent(hit.getCell(),
                                                           DOUBLE_SELECT));
                }
            }

            @Override
            public Node getNode() {
                return LayoutContainer.this.getNode();
            }

            @Override
            public void singleClick(MouseEvent mouseEvent) {
                Hit<Cell<?, ?>> hit = hit(mouseEvent.getX(), mouseEvent.getY());
                if (hit != null) {
                    getNode().fireEvent(new SelectionEvent(hit.getCell(),
                                                           SINGLE_SELECT));
                }
            }

            @Override
            public void tripleClick(MouseEvent mouseEvent) {
                Hit<Cell<?, ?>> hit = hit(mouseEvent.getX(), mouseEvent.getY());
                if (hit != null) {
                    getNode().fireEvent(new SelectionEvent(hit.getCell(),
                                                           TRIPLE_SELECT));
                }
            }
        };
        mouseHandler.bind();
        return mouseHandler;
    }

    <H extends Cell<?, ?>> Hit<Cell<?, ?>> hit(double x, double y);

}
