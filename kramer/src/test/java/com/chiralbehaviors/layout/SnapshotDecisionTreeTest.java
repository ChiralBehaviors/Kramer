// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * TDD tests for snapshotDecisionTree() recursive composition (Kramer-4s4).
 */
class SnapshotDecisionTreeTest {

    // -----------------------------------------------------------------------
    // 1. snapshotLayoutResult() on PrimitiveLayout — no side effects
    // -----------------------------------------------------------------------

    @Test
    void primitiveSnapshotLayoutResultReturnsCurrentFieldsWithoutSideEffects() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("name"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("Alice");
        data.add("Bob");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);
        layout.layout(500);
        layout.compress(200);

        // Capture state before snapshot
        double justifiedBefore = layout.getJustifiedWidth();
        boolean uvhBefore = layout.isUseVerticalHeader();

        LayoutResult result = layout.snapshotLayoutResult();

        // Fields after snapshot must be unchanged (no clear() was called)
        assertEquals(justifiedBefore, layout.getJustifiedWidth(),
                     "snapshotLayoutResult must not alter justifiedWidth");
        assertEquals(uvhBefore, layout.isUseVerticalHeader(),
                     "snapshotLayoutResult must not alter useVerticalHeader");

        // Result must encode actual field values
        assertNotNull(result);
        assertFalse(result.useTable(), "primitives never use table mode");
        assertEquals(justifiedBefore, result.constrainedColumnWidth(),
                     "constrainedColumnWidth must match justifiedWidth");
        assertEquals(uvhBefore, result.useVerticalHeader(),
                     "useVerticalHeader must match field value");
        assertTrue(result.childResults().isEmpty(), "primitives have no child results");
    }

    @Test
    void primitiveSnapshotLayoutResultPreservesVerticalHeaderFlag() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        // narrow threshold so nestTableColumn sets useVerticalHeader=true
        when(style.getVerticalHeaderThreshold()).thenReturn(0.1);

        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("label"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("A");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);
        layout.layout(500);
        layout.compress(300);

        // Manually set useVerticalHeader (as nestTableColumn would)
        layout.setUseVerticalHeader(true);
        assertTrue(layout.isUseVerticalHeader());

        LayoutResult result = layout.snapshotLayoutResult();

        assertTrue(result.useVerticalHeader(),
                   "snapshotLayoutResult must capture useVerticalHeader=true");
        // Verify no side-effect reset
        assertTrue(layout.isUseVerticalHeader(),
                   "useVerticalHeader must survive snapshotLayoutResult call");
    }

    // -----------------------------------------------------------------------
    // 2. snapshotDecisionTree on PrimitiveLayout — leaf node
    // -----------------------------------------------------------------------

    @Test
    void primitiveSnapshotDecisionTreeProducesLeafNode() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("score"), primStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("42");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);
        layout.layout(300);
        layout.compress(300);

        SchemaPath path = new SchemaPath("root", "score");
        layout.buildPaths(path, model);

        LayoutDecisionNode node = layout.snapshotDecisionTree();

        assertNotNull(node);
        assertEquals(path, node.path());
        assertEquals("score", node.fieldName());
        assertTrue(node.childNodes().isEmpty(), "leaf node has no children");
        assertTrue(node.columnSetSnapshots().isEmpty(), "primitives have no column sets");
        assertNotNull(node.layoutResult());
        assertFalse(node.layoutResult().useTable());
    }

    @Test
    void primitiveSnapshotDecisionTreeFieldNameMatchesPathLeaf() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("myField"), primStyle);

        Style model = mock(Style.class);
        layout.measure(JsonNodeFactory.instance.arrayNode(), n -> n, model);

        SchemaPath path = new SchemaPath("root", "child", "myField");
        layout.buildPaths(path, model);

        LayoutDecisionNode node = layout.snapshotDecisionTree();

        assertEquals("myField", node.fieldName());
        assertEquals(path.leaf(), node.fieldName());
    }

    // -----------------------------------------------------------------------
    // 3. snapshotDecisionTree on RelationLayout — recursive tree
    // -----------------------------------------------------------------------

    @Test
    void relationSnapshotDecisionTreeProducesTreeWithChildNodes() {
        PrimitiveLayout p1 = TestLayouts.makePrimitive("age", 50);
        PrimitiveLayout p2 = TestLayouts.makePrimitive("name", 80);

        RelationLayout relation = TestLayouts.makeRelation("person", 10.0, 1, p1, p2);
        relation.columnWidth = 200;
        relation.justifiedWidth = 200;

        SchemaPath rootPath = new SchemaPath("person");
        // Set paths manually (bypassing model.layout since children are pre-built)
        relation.setSchemaPath(rootPath);
        p1.buildPaths(rootPath.child("age"), null);
        p2.buildPaths(rootPath.child("name"), null);

        LayoutDecisionNode node = relation.snapshotDecisionTree();

        assertNotNull(node);
        assertEquals(rootPath, node.path());
        assertEquals("person", node.fieldName());
        assertEquals(2, node.childNodes().size(), "must have one child per primitive child");
    }

    @Test
    void relationSnapshotDecisionTreeChildrenMatchSchema() {
        PrimitiveLayout p1 = TestLayouts.makePrimitive("firstName", 60);
        PrimitiveLayout p2 = TestLayouts.makePrimitive("lastName", 70);

        RelationLayout relation = TestLayouts.makeRelation("name", 0.0, 1, p1, p2);
        relation.columnWidth = 200;
        relation.justifiedWidth = 200;

        SchemaPath root = new SchemaPath("name");
        relation.setSchemaPath(root);
        p1.buildPaths(root.child("firstName"), null);
        p2.buildPaths(root.child("lastName"), null);

        LayoutDecisionNode node = relation.snapshotDecisionTree();

        List<LayoutDecisionNode> children = node.childNodes();
        assertEquals(2, children.size());

        var childFields = children.stream().map(LayoutDecisionNode::fieldName).toList();
        assertTrue(childFields.contains("firstName"), "child 'firstName' must be present");
        assertTrue(childFields.contains("lastName"), "child 'lastName' must be present");
    }

    @Test
    void relationSnapshotDecisionTreeColumnSetSnapshotsFromColumnSets() {
        PrimitiveLayout p1 = TestLayouts.makePrimitive("x", 50);
        PrimitiveLayout p2 = TestLayouts.makePrimitive("y", 50);

        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        Relation schema = new Relation("point");
        schema.addChild(new Primitive("x"));
        schema.addChild(new Primitive("y"));

        RelationLayout relation = new RelationLayout(schema, relStyle);
        relation.children.clear();
        relation.children.add(p1);
        relation.children.add(p2);
        relation.columnWidth = 200;
        relation.labelWidth = 0;
        relation.averageChildCardinality = 1;
        relation.maxCardinality = 1;

        // Run compress to populate columnSets
        relation.compress(200);

        SchemaPath root = new SchemaPath("point");
        relation.setSchemaPath(root);
        p1.buildPaths(root.child("x"), null);
        p2.buildPaths(root.child("y"), null);

        LayoutDecisionNode node = relation.snapshotDecisionTree();

        assertNotNull(node);
        assertNotNull(node.columnSetSnapshots());
        // When columnSets is non-empty, snapshots must be non-empty
        if (!relation.columnSets.isEmpty()) {
            assertFalse(node.columnSetSnapshots().isEmpty(),
                        "columnSetSnapshots must reflect columnSets after compress");
        }
    }

    // -----------------------------------------------------------------------
    // 4. snapshotDecisionTree does not cause side effects (no clear())
    // -----------------------------------------------------------------------

    @Test
    void snapshotDecisionTreeDoesNotAlterPrimitiveState() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("val"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("test");
        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);
        layout.layout(400);
        layout.compress(400);
        layout.setUseVerticalHeader(true);

        double jwBefore = layout.getJustifiedWidth();
        boolean uvhBefore = layout.isUseVerticalHeader();

        SchemaPath path = new SchemaPath("val");
        layout.buildPaths(path, model);
        layout.snapshotDecisionTree();

        assertEquals(jwBefore, layout.getJustifiedWidth(),
                     "snapshotDecisionTree must not alter justifiedWidth");
        assertEquals(uvhBefore, layout.isUseVerticalHeader(),
                     "snapshotDecisionTree must not alter useVerticalHeader");
    }
}
