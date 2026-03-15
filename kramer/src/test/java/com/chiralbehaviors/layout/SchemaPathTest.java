// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class SchemaPathTest {

    @Test
    void rootPath() {
        var path = new SchemaPath("root");
        assertEquals(List.of("root"), path.segments());
        assertEquals("root", path.leaf());
        assertEquals("root", path.cssClass());
        assertEquals("root", path.toString());
    }

    @Test
    void childPath() {
        var root = new SchemaPath("catalog");
        var child = root.child("name");
        assertEquals(List.of("catalog", "name"), child.segments());
        assertEquals("name", child.leaf());
        assertEquals("name", child.cssClass());
        assertEquals("catalog/name", child.toString());
    }

    @Test
    void deepNesting() {
        var path = new SchemaPath("root").child("items").child("name");
        assertEquals(List.of("root", "items", "name"), path.segments());
        assertEquals("name", path.leaf());
        assertEquals("root/items/name", path.toString());
    }

    @Test
    void sameFieldDifferentPathsNotEqual() {
        var path1 = new SchemaPath("root").child("name");
        var path2 = new SchemaPath("root").child("items").child("name");
        assertNotEquals(path1, path2,
                        "Same field at different nesting levels must be different paths");
        // But same CSS class
        assertEquals(path1.cssClass(), path2.cssClass());
    }

    @Test
    void equalPathsAreEqual() {
        var path1 = new SchemaPath("root").child("name");
        var path2 = new SchemaPath("root").child("name");
        assertEquals(path1, path2);
        assertEquals(path1.hashCode(), path2.hashCode());
    }

    @Test
    void immutableSegments() {
        var path = new SchemaPath("root");
        assertThrows(UnsupportedOperationException.class,
                     () -> path.segments().add("x"));
    }

    @Test
    void schemaPathWiredThroughMeasure() {
        var schema = new com.chiralbehaviors.layout.schema.Relation("catalog");
        schema.addChild(new com.chiralbehaviors.layout.schema.Primitive("name"));
        schema.addChild(new com.chiralbehaviors.layout.schema.Primitive("code"));

        var relStyle = TestLayouts.mockRelationStyle();
        var primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        var model = org.mockito.Mockito.mock(com.chiralbehaviors.layout.style.Style.class);
        for (var child : schema.getChildren()) {
            if (child instanceof com.chiralbehaviors.layout.schema.Primitive p) {
                var pl = new PrimitiveLayout(p, primStyle);
                org.mockito.Mockito.when(model.layout(p)).thenReturn(pl);
            }
        }
        org.mockito.Mockito.when(model.layout(
            org.mockito.ArgumentMatchers.any(com.chiralbehaviors.layout.schema.SchemaNode.class)))
            .thenAnswer(inv -> {
                var n = inv.getArgument(0);
                if (n instanceof com.chiralbehaviors.layout.schema.Primitive p)
                    return model.layout(p);
                return model.layout((com.chiralbehaviors.layout.schema.Relation) n);
            });

        var layout = new RelationLayout(schema, relStyle);
        layout.setSchemaPath(new SchemaPath("catalog"));

        var data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        var row = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        row.put("name", "Alice");
        row.put("code", "A1");
        data.add(row);

        layout.measure(data, n -> n, model);

        assertEquals(new SchemaPath("catalog"), layout.getSchemaPath());
        assertEquals(2, layout.children.size());
        assertEquals(new SchemaPath("catalog").child("name"),
                     layout.children.get(0).getSchemaPath());
        assertEquals(new SchemaPath("catalog").child("code"),
                     layout.children.get(1).getSchemaPath());
    }

    @Test
    void defaultStylesheetOverrides() {
        var stylesheet = new DefaultLayoutStylesheet(new com.chiralbehaviors.layout.style.Style());
        var path = new SchemaPath("root").child("name");

        assertEquals(42.0, stylesheet.getDouble(path, "width", 42.0),
                     "No override should return default");

        stylesheet.setOverride(path, "width", 100.0);
        assertEquals(100.0, stylesheet.getDouble(path, "width", 42.0),
                     "Override should take precedence");

        stylesheet.clearOverrides();
        assertEquals(42.0, stylesheet.getDouble(path, "width", 42.0),
                     "After clear, default should return");
    }
}
