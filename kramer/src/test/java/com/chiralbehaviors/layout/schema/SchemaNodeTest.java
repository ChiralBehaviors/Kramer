/**
 * Copyright (c) 2016 Chiral Behaviors, LLC, all rights reserved.
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

package com.chiralbehaviors.layout.schema;

import static com.chiralbehaviors.layout.schema.Util.build;
import static com.chiralbehaviors.layout.schema.Util.testData;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;

import com.chiralbehaviors.layout.Layout;
import com.chiralbehaviors.layout.Layout.LayoutModel;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * @author halhildebrand
 *
 */
public class SchemaNodeTest extends ApplicationTest {

    private Layout layout;
    private HBox   parent;

    @Override
    public void start(Stage stage) throws Exception {
        parent = new HBox();
        Scene scene = new Scene(parent, 1200, 800);
        stage.setScene(scene);
        stage.show();
        LayoutModel model = mock(LayoutModel.class);
        layout = new Layout(model);
    }

    @Test
    public void testColumns() throws Exception {
        JsonNode data = testData();
        Relation root = build();
        root.measure(data, layout);
        root.autoLayout(1, layout, 1200);
        System.out.println(root);
        List<ColumnSet> columnSets = root.getColumnSets();
        assertEquals(3, columnSets.size());
        assertFirst(columnSets.get(0));
        assertSecond(columnSets.get(1));
        assertThird(columnSets.get(2));
    }

    private void assertFirst(ColumnSet columnSet) {
        assertEquals(142.0, columnSet.getCellHeight(), 0d);
        assertEquals(2, columnSet.getColumns()
                                 .size());
        Column column = columnSet.getColumns()
                                 .get(0);
        assertEquals(591.0, column.getWidth(), 0d);
        List<SchemaNode> fields = column.getFields();
        assertEquals(3, fields.size());
        assertEquals("name", fields.get(0).field);
        assertEquals("notes", fields.get(1).field);
        assertEquals("classifier", fields.get(2).field);

        column = columnSet.getColumns()
                          .get(1);
        assertEquals(591.0, column.getWidth(), 0d);
        fields = column.getFields();
        assertEquals(1, fields.size());
        assertEquals("classification", fields.get(0).field);
    }

    private void assertSecond(ColumnSet columnSet) {
        assertEquals(530.0, columnSet.getCellHeight(), 0d);
        assertEquals(1, columnSet.getColumns()
                                 .size());
        Column column = columnSet.getColumns()
                                 .get(0);
        assertEquals(1182.0, column.getWidth(), 0d);
        List<SchemaNode> fields = column.getFields();
        assertEquals(1, fields.size());
        assertEquals("attributes", fields.get(0).field);
    }

    private void assertThird(ColumnSet columnSet) {
        assertEquals(302.0, columnSet.getCellHeight(), 0d);
        assertEquals(1, columnSet.getColumns()
                                 .size());
        Column column = columnSet.getColumns()
                                 .get(0);
        assertEquals(1182.0, column.getWidth(), 0d);
        assertEquals(1, column.getFields()
                              .size());

        Relation children = (Relation) column.getFields()
                                             .get(0);
        assertEquals("children", children.field);
        assertEquals(773.0, children.getChildren()
                                    .stream()
                                    .mapToDouble(f -> f.justifiedWidth)
                                    .reduce((a, b) -> a + b)
                                    .getAsDouble(),
                     0d);
        assertEquals(1057.0, children.justifiedWidth, 0d);

        assertEquals(120.0, children.getChildren()
                                    .get(0).justifiedWidth,
                     0d);
        assertEquals(79.0, children.getChildren()
                                   .get(1).justifiedWidth,
                     0d);
        assertEquals(109.0, children.getChildren()
                                    .get(2).justifiedWidth,
                     0d);

        Relation relationship = (Relation) children.getChildren()
                                                   .get(3);
        assertEquals("relationship", relationship.field);
        assertEquals(325.0, relationship.getChildren()
                                        .stream()
                                        .mapToDouble(f -> f.justifiedWidth)
                                        .reduce((a, b) -> a + b)
                                        .getAsDouble(),
                     0d);
        assertEquals(355.0, relationship.justifiedWidth, 0d);

        Relation child = (Relation) children.getChildren()
                                            .get(4);
        assertEquals("child", child.field);
        assertEquals(89.0, child.getChildren()
                                .stream()
                                .mapToDouble(f -> f.justifiedWidth)
                                .reduce((a, b) -> a + b)
                                .getAsDouble(),
                     0d);
        assertEquals(110.0, child.justifiedWidth, 0d);
    }
}
