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

package com.chiralbehaviors.layout.style;

import javafx.geometry.Insets;

/**
 * @author halhildebrand
 *
 */
public class RelationStyle extends NodeStyle {

    private final Insets column;
    private final Insets element;
    private final Insets nestedInsets;
    private final Insets outline;
    private final Insets outlineCell;
    private final Insets row;
    private final Insets rowCell;
    private final Insets span;
    private final Insets table;

    public RelationStyle(LabelStyle labelStyle, Insets table, Insets row,
                         Insets rowCell, Insets outline, Insets outlineCell,
                         Insets column, Insets span, Insets element) {
        super(labelStyle);
        this.table = table;
        this.row = row;
        this.rowCell = rowCell;
        this.outline = outline;
        this.outlineCell = outlineCell;
        this.column = column;
        this.span = span;
        this.element = element;
        nestedInsets = Layout.add(row, rowCell);
    }

    public double getColumnHorizontalInset() {
        return column.getLeft() + column.getRight();
    }

    public double getColumnVerticalInset() {
        return column.getTop() + column.getBottom();
    }

    public double getElementHorizontalInset() {
        return element.getLeft() + element.getRight();
    }

    public double getElementVerticalInset() {
        return element.getTop() + element.getBottom();
    }

    public double getNestedHorizontalInset() {
        return nestedInsets.getLeft() + nestedInsets.getRight();
    }

    public Insets getNestedInsets() {
        return nestedInsets;
    }

    public double getOutlineVerticalInset() {
        return outline.getTop() + outline.getBottom();
    }

    public double getOutlineCellHorizontalInset() {
        return outlineCell.getTop() + outlineCell.getBottom();
    }

    public double getOutlineCellVerticalInset() {
        return outlineCell.getTop() + outlineCell.getBottom();
    }

    public double getOutlineHorizontalInset() {
        return outline.getLeft() + outline.getRight();
    }

    public double getRowCellHorizontalInset() {
        return rowCell.getLeft() + rowCell.getRight();
    }

    public double getRowCellVerticalInset() {
        return rowCell.getTop() + rowCell.getBottom();
    }

    public double getRowHorizontalInset() {
        return row.getLeft() + row.getRight();
    }

    public Insets getRowInset() {
        return row;
    }

    public double getRowVerticalInset() {
        return row.getTop() + row.getBottom();
    }

    public double getSpanHorizontalInset() {
        return span.getLeft() + span.getRight();
    }

    public double getSpanVerticalInset() {
        return span.getTop() + span.getBottom();
    }

    public double getTableHorizontalInset() {
        return table.getLeft() + table.getRight();
    }

    public Insets getTableInset() {
        return table;
    }

    public double getTableVerticalInset() {
        return table.getTop() + table.getBottom();
    }

}
