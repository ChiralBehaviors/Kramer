// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class LayoutDecisionNodeTest {

    private static MeasureResult measureResult() {
        return new MeasureResult(10.0, 100.0, 90.0, 120.0, 1, false, 0, 5,
                                 node -> node, List.of(), null, null, null);
    }

    private static LayoutResult layoutResult() {
        return new LayoutResult(RelationRenderMode.OUTLINE, PrimitiveRenderMode.TEXT,
                                false, 0.0, 0.0, 100.0, List.of());
    }

    private static CompressResult compressResult() {
        return new CompressResult(100.0, List.of(), 20.0, List.of());
    }

    private static HeightResult heightResult() {
        return new HeightResult(200.0, 20.0, 5, 0.0, List.of());
    }

    private static ColumnSetSnapshot columnSetSnapshot() {
        return new ColumnSetSnapshot(List.of(new ColumnSnapshot(100.0, List.of("f1"))), 20.0);
    }

    @Test
    void constructionWithAllFieldsPopulated() {
        var path = new SchemaPath("root", "child");
        var measure = measureResult();
        var layout = layoutResult();
        var compress = compressResult();
        var height = heightResult();
        var snapshots = List.of(columnSetSnapshot());
        var child = new LayoutDecisionNode(new SchemaPath("root", "child", "leaf"),
                                           "leaf", measure, layout, compress, height,
                                           List.of(), List.of());
        var children = List.of(child);

        var node = new LayoutDecisionNode(path, "child", measure, layout, compress, height,
                                          snapshots, children);

        assertEquals(path, node.path());
        assertEquals("child", node.fieldName());
        assertSame(measure, node.measureResult());
        assertSame(layout, node.layoutResult());
        assertSame(compress, node.compressResult());
        assertSame(height, node.heightResult());
        assertEquals(1, node.columnSetSnapshots().size());
        assertEquals(1, node.childNodes().size());
    }

    @Test
    void fieldNameFromSchemaPathLeaf() {
        var path = new SchemaPath("root", "child", "myField");
        var node = new LayoutDecisionNode(path, path.leaf(), null, null, null, null,
                                          null, null);
        assertEquals("myField", node.fieldName());
        assertEquals("myField", path.leaf());
    }

    @Test
    void childNodesIsDefensivelyCopied() {
        var child = new LayoutDecisionNode(new SchemaPath("a"), "a",
                                           null, null, null, null, null, null);
        var mutable = new ArrayList<>(List.of(child));
        var node = new LayoutDecisionNode(new SchemaPath("root"), "root",
                                          null, null, null, null, null, mutable);

        mutable.add(child);  // mutate original list after construction

        assertEquals(1, node.childNodes().size(), "childNodes must be defensively copied");
        assertThrows(UnsupportedOperationException.class,
                     () -> node.childNodes().add(child));
    }

    @Test
    void nullChildNodesNormalizedToEmptyList() {
        var node = new LayoutDecisionNode(new SchemaPath("root"), "root",
                                          null, null, null, null, null, null);

        assertNotNull(node.childNodes());
        assertTrue(node.childNodes().isEmpty());
    }

    @Test
    void columnSetSnapshotsIsDefensivelyCopied() {
        var snap = columnSetSnapshot();
        var mutable = new ArrayList<>(List.of(snap));
        var node = new LayoutDecisionNode(new SchemaPath("root"), "root",
                                          null, null, null, null, mutable, null);

        mutable.add(snap);  // mutate after construction

        assertEquals(1, node.columnSetSnapshots().size(),
                     "columnSetSnapshots must be defensively copied");
        assertThrows(UnsupportedOperationException.class,
                     () -> node.columnSetSnapshots().add(snap));
    }

    @Test
    void nullColumnSetSnapshotsNormalizedToEmptyList() {
        var node = new LayoutDecisionNode(new SchemaPath("root"), "root",
                                          null, null, null, null, null, null);

        assertNotNull(node.columnSetSnapshots());
        assertTrue(node.columnSetSnapshots().isEmpty());
    }
}
