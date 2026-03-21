// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for TypeIntrospector — parses GraphQL __schema introspection
 * results into a browseable type tree.
 */
class TypeIntrospectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Minimal __schema response with two user types + system types.
     */
    private static final String SAMPLE_INTROSPECTION = """
        {
          "data": {
            "__schema": {
              "types": [
                {
                  "name": "Employee",
                  "kind": "OBJECT",
                  "fields": [
                    { "name": "id", "description": "Primary key",
                      "type": { "name": "ID", "kind": "SCALAR", "ofType": null } },
                    { "name": "name", "description": null,
                      "type": { "name": "String", "kind": "SCALAR", "ofType": null } },
                    { "name": "projects", "description": "Active projects",
                      "type": { "name": null, "kind": "LIST",
                        "ofType": { "name": "Project", "kind": "OBJECT", "ofType": null } } }
                  ]
                },
                {
                  "name": "Project",
                  "kind": "OBJECT",
                  "fields": [
                    { "name": "title", "description": null,
                      "type": { "name": "String", "kind": "SCALAR", "ofType": null } },
                    { "name": "status", "description": null,
                      "type": { "name": "String", "kind": "SCALAR", "ofType": null } },
                    { "name": "hours", "description": null,
                      "type": { "name": "Int", "kind": "SCALAR", "ofType": null } }
                  ]
                },
                {
                  "name": "__Schema",
                  "kind": "OBJECT",
                  "fields": [
                    { "name": "types", "description": null,
                      "type": { "name": null, "kind": "LIST",
                        "ofType": { "name": "__Type", "kind": "OBJECT", "ofType": null } } }
                  ]
                },
                {
                  "name": "__Type",
                  "kind": "OBJECT",
                  "fields": []
                },
                {
                  "name": "String",
                  "kind": "SCALAR",
                  "fields": null
                },
                {
                  "name": "Query",
                  "kind": "OBJECT",
                  "fields": [
                    { "name": "employees", "description": null,
                      "type": { "name": null, "kind": "LIST",
                        "ofType": { "name": "Employee", "kind": "OBJECT", "ofType": null } } }
                  ]
                }
              ]
            }
          }
        }
        """;

    private TypeIntrospector parse() throws Exception {
        JsonNode json = MAPPER.readTree(SAMPLE_INTROSPECTION);
        return TypeIntrospector.parse(json);
    }

    @Test
    void parsesUserTypes() throws Exception {
        TypeIntrospector introspector = parse();
        Map<String, TypeIntrospector.IntrospectedType> index = introspector.typeIndex();

        assertTrue(index.containsKey("Employee"), "Should contain Employee");
        assertTrue(index.containsKey("Project"), "Should contain Project");
        assertTrue(index.containsKey("Query"), "Should contain Query");
    }

    @Test
    void filtersSystemTypes() throws Exception {
        TypeIntrospector introspector = parse();
        Map<String, TypeIntrospector.IntrospectedType> index = introspector.typeIndex();

        assertFalse(index.containsKey("__Schema"), "__Schema should be filtered");
        assertFalse(index.containsKey("__Type"), "__Type should be filtered");
    }

    @Test
    void filtersScalarTypes() throws Exception {
        TypeIntrospector introspector = parse();
        Map<String, TypeIntrospector.IntrospectedType> index = introspector.typeIndex();

        assertFalse(index.containsKey("String"), "Scalar types should be filtered");
    }

    @Test
    void typeHasCorrectFields() throws Exception {
        TypeIntrospector introspector = parse();
        var employee = introspector.typeIndex().get("Employee");

        assertNotNull(employee);
        assertEquals(3, employee.fields().size());
        assertEquals("id", employee.fields().get(0).name());
        assertEquals("name", employee.fields().get(1).name());
        assertEquals("projects", employee.fields().get(2).name());
    }

    @Test
    void fieldHasDescription() throws Exception {
        TypeIntrospector introspector = parse();
        var employee = introspector.typeIndex().get("Employee");
        var idField = employee.fields().get(0);

        assertEquals("Primary key", idField.description());
    }

    @Test
    void fieldTypeRefScalar() throws Exception {
        TypeIntrospector introspector = parse();
        var employee = introspector.typeIndex().get("Employee");
        var nameField = employee.fields().get(1);

        assertEquals("String", nameField.type().name());
        assertEquals("SCALAR", nameField.type().kind());
        assertNull(nameField.type().ofType());
    }

    @Test
    void fieldTypeRefList() throws Exception {
        TypeIntrospector introspector = parse();
        var employee = introspector.typeIndex().get("Employee");
        var projectsField = employee.fields().get(2);

        assertEquals("LIST", projectsField.type().kind());
        assertNotNull(projectsField.type().ofType());
        assertEquals("Project", projectsField.type().ofType().name());
    }

    @Test
    void introspectionQueryIsValidGraphQL() {
        String query = TypeIntrospector.introspectionQuery();
        assertNotNull(query);
        assertTrue(query.contains("__schema"), "Query should contain __schema");
        assertTrue(query.contains("fields"), "Query should request fields");
        assertTrue(query.contains("ofType"), "Query should request ofType for wrapping");
    }

    @Test
    void userTypesListExcludesSystemAndScalar() throws Exception {
        TypeIntrospector introspector = parse();
        List<TypeIntrospector.IntrospectedType> types = introspector.userTypes();

        assertEquals(3, types.size(), "Should have Employee, Project, Query");
        assertTrue(types.stream().noneMatch(t -> t.name().startsWith("__")));
        assertTrue(types.stream().noneMatch(t -> "SCALAR".equals(t.kind())));
    }
}
