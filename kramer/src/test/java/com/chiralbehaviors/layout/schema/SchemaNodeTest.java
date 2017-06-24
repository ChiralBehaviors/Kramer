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

import java.util.Collections;
import java.util.List;

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

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new Scene(new HBox(), 800, 600);
        stage.setScene(scene);
        stage.show();
    }

    @Test
    public void testColumns() throws Exception {
        JsonNode data = testData();
        Relation root = build();
        LayoutModel model = mock(LayoutModel.class);
        Layout layout = new Layout(Collections.emptyList(), model);
        root.measure(data, layout);
        root.autoLayout(1, layout, 800d);
        List<ColumnSet> columnSets = root.getColumnSets();
        assertEquals(3, columnSets.size());
        assertFirst(columnSets.get(0));
        assertSecond(columnSets.get(1));
        assertThird(columnSets.get(2));
    }

    private void assertFirst(ColumnSet columnSet) {
        assertEquals(3, columnSet.getColumns()
                                 .size());
        Column column = columnSet.getColumns()
                                 .get(0);
        List<SchemaNode> fields = column.getFields();
        assertEquals(2, fields.size());
        assertEquals("name", fields.get(0).field);
        assertEquals("notes", fields.get(1).field);

        column = columnSet.getColumns()
                          .get(1);
        fields = column.getFields();
        assertEquals(1, fields.size());
        assertEquals("classification", fields.get(0).field);

        column = columnSet.getColumns()
                          .get(2);
        fields = column.getFields();
        assertEquals(1, fields.size());
        assertEquals("classifier", fields.get(0).field);
    }

    private void assertSecond(ColumnSet columnSet) {
        assertEquals(1, columnSet.getColumns()
                                 .size());
        Column column = columnSet.getColumns()
                                 .get(0);
        List<SchemaNode> fields = column.getFields();
        assertEquals(1, fields.size());
        assertEquals("attributes", fields.get(0).field);
    }

    private void assertThird(ColumnSet columnSet) {
        assertEquals(1, columnSet.getColumns()
                                 .size());
        Column column = columnSet.getColumns()
                                 .get(0);
        List<SchemaNode> fields = column.getFields();
        assertEquals(1, fields.size());
        assertEquals("children", fields.get(0).field);
    }
}
