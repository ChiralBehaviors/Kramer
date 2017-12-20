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

import static com.chiralbehaviors.layout.cell.control.SelectionEvent.DOUBLE_SELECT;
import static com.chiralbehaviors.layout.cell.control.SelectionEvent.SINGLE_SELECT;
import static com.chiralbehaviors.layout.cell.control.SelectionEvent.TRIPLE_SELECT;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

import com.chiralbehaviors.layout.cell.control.MouseHandler;
import com.chiralbehaviors.layout.cell.control.MultipleCellSelection;
import com.chiralbehaviors.layout.cell.control.SelectionEvent;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Point2D;
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

    default MouseHandler bind(MultipleCellSelection<JsonNode, C> selectionModel) {
        MouseHandler mouseHandler = new MouseHandler(new Duration(300)) {

            @Override
            public void doubleClick(MouseEvent mouseEvent) {
                Hit<C> hit = hit(mouseEvent.getX(), mouseEvent.getY());
                if (hit != null) {
                    selectionModel.select(hit.getCellIndex());
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
                Hit<C> hit = hit(mouseEvent.getX(), mouseEvent.getY());
                if (hit != null) {
                    selectionModel.select(hit.getCellIndex());
                    getNode().fireEvent(new SelectionEvent(hit.getCell(),
                                                           SINGLE_SELECT));
                }
            }

            @Override
            public void tripleClick(MouseEvent mouseEvent) {
                Hit<C> hit = hit(mouseEvent.getX(), mouseEvent.getY());
                if (hit != null) {
                    selectionModel.select(hit.getCellIndex());
                    getNode().fireEvent(new SelectionEvent(hit.getCell(),
                                                           TRIPLE_SELECT));
                }
            }
        };
        mouseHandler.bind();
        return mouseHandler;
    }

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

    Hit<C> hit(double x, double y);

    default Hit<C> hit(double x, double y, Collection<C> cells) {
        int i = 0;
        for (C cell : cells) {
            Region node = cell.getNode();
            Point2D p = new Point2D(x, y);
            if (node.contains(p)) {
                return new Hit<C>() {
                    @Override
                    public C getCell() {
                        return cell;
                    }

                    @Override
                    public int getCellIndex() {
                        return i;
                    }

                    @Override
                    public Point2D getCellOffset() {
                        return new Point2D(0, 0);
                    }

                    @Override
                    public Point2D getOffsetAfterCells() {
                        return new Point2D(0, 0);
                    }

                    @Override
                    public Point2D getOffsetBeforeCells() {
                        return new Point2D(0, 0);
                    }

                    @Override
                    public boolean isAfterCells() {
                        return false;
                    }

                    @Override
                    public boolean isBeforeCells() {
                        return false;
                    }

                    @Override
                    public boolean isCellHit() {
                        return true;
                    }
                };
            }
        }

        return null;
    }
}
