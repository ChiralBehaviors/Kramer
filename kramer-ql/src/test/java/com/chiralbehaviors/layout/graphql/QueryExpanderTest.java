// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.graphql.TypeIntrospector.IntrospectedField;
import com.chiralbehaviors.layout.graphql.TypeIntrospector.IntrospectedType;
import com.chiralbehaviors.layout.graphql.TypeIntrospector.TypeRef;
import com.fasterxml.jackson.databind.ObjectMapper;

import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;

import java.util.List;
import java.util.Map;

/**
 * TDD tests for QueryExpander — AST field addition/removal (RDR-030 Phase 4B).
 */
class QueryExpanderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Introspection response matching the query shapes used in these tests.
     * Query → employees(LIST<Employee>)
     * Employee: id(ID), name(String), age(Int), email(String),
     *           projects(LIST<Project>), department(Department)
     * Project: id(ID), title(String), status(String)
     * Department: id(ID), name(String), budget(Int)
     */
    private static final String INTROSPECTION = """
        {
          "data": {
            "__schema": {
              "types": [
                {
                  "name": "Employee",
                  "kind": "OBJECT",
                  "fields": [
                    { "name": "id", "description": null,
                      "type": { "name": "ID", "kind": "SCALAR", "ofType": null } },
                    { "name": "name", "description": null,
                      "type": { "name": "String", "kind": "SCALAR", "ofType": null } },
                    { "name": "age", "description": null,
                      "type": { "name": "Int", "kind": "SCALAR", "ofType": null } },
                    { "name": "email", "description": null,
                      "type": { "name": "String", "kind": "SCALAR", "ofType": null } },
                    { "name": "projects", "description": null,
                      "type": { "name": null, "kind": "LIST",
                        "ofType": { "name": "Project", "kind": "OBJECT", "ofType": null } } },
                    { "name": "department", "description": null,
                      "type": { "name": "Department", "kind": "OBJECT", "ofType": null } }
                  ]
                },
                {
                  "name": "Project",
                  "kind": "OBJECT",
                  "fields": [
                    { "name": "id", "description": null,
                      "type": { "name": "ID", "kind": "SCALAR", "ofType": null } },
                    { "name": "title", "description": null,
                      "type": { "name": "String", "kind": "SCALAR", "ofType": null } },
                    { "name": "status", "description": null,
                      "type": { "name": "String", "kind": "SCALAR", "ofType": null } }
                  ]
                },
                {
                  "name": "Department",
                  "kind": "OBJECT",
                  "fields": [
                    { "name": "id", "description": null,
                      "type": { "name": "ID", "kind": "SCALAR", "ofType": null } },
                    { "name": "name", "description": null,
                      "type": { "name": "String", "kind": "SCALAR", "ofType": null } },
                    { "name": "budget", "description": null,
                      "type": { "name": "Int", "kind": "SCALAR", "ofType": null } }
                  ]
                },
                {
                  "name": "Query",
                  "kind": "OBJECT",
                  "fields": [
                    { "name": "employees", "description": null,
                      "type": { "name": null, "kind": "LIST",
                        "ofType": { "name": "Employee", "kind": "OBJECT", "ofType": null } } }
                  ]
                },
                {
                  "name": "String",
                  "kind": "SCALAR",
                  "fields": null
                }
              ]
            }
          }
        }
        """;

    private TypeIntrospector introspector() throws Exception {
        return TypeIntrospector.parse(MAPPER.readTree(INTROSPECTION));
    }

    private QueryExpander expander() throws Exception {
        return new QueryExpander(introspector());
    }

    // -----------------------------------------------------------------------
    // Spike: round-trip validation
    // -----------------------------------------------------------------------

    @Test
    void spikeRoundTripFieldCreation() throws Exception {
        // Create a Field from scratch, add to a document, serialize, re-parse
        Document doc = graphql.parser.Parser.parse("{ employees { name } }");
        Field newField = Field.newField("age").build();

        // Manually add to first operation's first field's selection set
        var op = (OperationDefinition) doc.getDefinitions().get(0);
        var employeesField = (Field) op.getSelectionSet().getSelections().get(0);

        var selections = new java.util.ArrayList<>(
            employeesField.getSelectionSet().getSelections());
        selections.add(newField);
        var newSS = employeesField.getSelectionSet()
            .transform(b -> b.selections(selections));
        var newEmployees = employeesField.transform(b -> b.selectionSet(newSS));
        var newOpSS = op.getSelectionSet().transform(
            b -> b.selections(List.of(newEmployees)));
        var newOp = op.transform(b -> b.selectionSet(newOpSS));
        var newDoc = doc.transform(b -> b.definitions(List.of(newOp)));

        String serialized = QueryExpander.serialize(newDoc);
        assertTrue(serialized.contains("age"), "Serialized should contain 'age': " + serialized);
        assertTrue(serialized.contains("name"), "Serialized should contain 'name': " + serialized);

        // Round-trip: re-parse
        Document reparsed = graphql.parser.Parser.parse(serialized);
        var reparsedOp = (OperationDefinition) reparsed.getDefinitions().get(0);
        var reparsedField = (Field) reparsedOp.getSelectionSet().getSelections().get(0);
        var fieldNames = reparsedField.getSelectionSet().getSelections().stream()
            .filter(Field.class::isInstance)
            .map(s -> ((Field) s).getName())
            .toList();
        assertTrue(fieldNames.contains("name"), "Re-parsed should contain 'name'");
        assertTrue(fieldNames.contains("age"), "Re-parsed should contain 'age'");
    }

    // -----------------------------------------------------------------------
    // addField — scalar
    // -----------------------------------------------------------------------

    @Test
    void testAddScalarFieldToSelection() throws Exception {
        QueryExpander exp = expander();
        Document doc = graphql.parser.Parser.parse("{ employees { name } }");

        Document result = exp.addField(doc, new SchemaPath("employees"), "age");
        String serialized = QueryExpander.serialize(result);

        assertTrue(serialized.contains("age"),
            "Added scalar field should appear: " + serialized);
        assertTrue(serialized.contains("name"),
            "Existing field should be preserved: " + serialized);
        assertDoesNotThrow(() -> graphql.parser.Parser.parse(serialized),
            "Result must be valid GraphQL: " + serialized);
    }

    // -----------------------------------------------------------------------
    // addField — relation with sub-selection
    // -----------------------------------------------------------------------

    @Test
    void testAddRelationFieldWithSubSelection() throws Exception {
        QueryExpander exp = expander();
        Document doc = graphql.parser.Parser.parse("{ employees { name } }");

        Document result = exp.addField(doc, new SchemaPath("employees"), "projects");
        String serialized = QueryExpander.serialize(result);

        assertTrue(serialized.contains("projects"),
            "Added relation field should appear: " + serialized);
        // Relation should have sub-selection with at least one field
        assertTrue(serialized.contains("id") || serialized.contains("title"),
            "Relation field should have sub-fields: " + serialized);
        assertTrue(serialized.contains("name"),
            "Existing field should be preserved: " + serialized);
        assertDoesNotThrow(() -> graphql.parser.Parser.parse(serialized),
            "Result must be valid GraphQL: " + serialized);
    }

    // -----------------------------------------------------------------------
    // removeField
    // -----------------------------------------------------------------------

    @Test
    void testRemoveField() throws Exception {
        QueryExpander exp = expander();
        Document doc = graphql.parser.Parser.parse("{ employees { name age email } }");

        Document result = exp.removeField(doc, new SchemaPath("employees", "age"));
        String serialized = QueryExpander.serialize(result);

        assertFalse(serialized.contains("age"),
            "Removed field should not appear: " + serialized);
        assertTrue(serialized.contains("name"),
            "Other fields should be preserved: " + serialized);
        assertTrue(serialized.contains("email"),
            "Other fields should be preserved: " + serialized);
        assertDoesNotThrow(() -> graphql.parser.Parser.parse(serialized),
            "Result must be valid GraphQL: " + serialized);
    }

    // -----------------------------------------------------------------------
    // Round-trip: modify → serialize → parse → compare
    // -----------------------------------------------------------------------

    @Test
    void testRoundTripPreservesStructure() throws Exception {
        QueryExpander exp = expander();
        Document doc = graphql.parser.Parser.parse("{ employees { name } }");

        // Add a field
        Document modified = exp.addField(doc, new SchemaPath("employees"), "email");
        String serialized = QueryExpander.serialize(modified);

        // Re-parse
        Document reparsed = graphql.parser.Parser.parse(serialized);
        var op = (OperationDefinition) reparsed.getDefinitions().get(0);
        var employees = (Field) op.getSelectionSet().getSelections().get(0);
        var fieldNames = employees.getSelectionSet().getSelections().stream()
            .filter(Field.class::isInstance)
            .map(s -> ((Field) s).getName())
            .toList();

        assertTrue(fieldNames.contains("name"), "Original field preserved after round-trip");
        assertTrue(fieldNames.contains("email"), "Added field present after round-trip");
        assertEquals(2, fieldNames.size(), "Exactly 2 fields after adding one");
    }

    // -----------------------------------------------------------------------
    // addField — idempotent
    // -----------------------------------------------------------------------

    @Test
    void testAddFieldIdempotent() throws Exception {
        QueryExpander exp = expander();
        Document doc = graphql.parser.Parser.parse("{ employees { name age } }");

        // Adding 'name' which already exists should be no-op
        Document result = exp.addField(doc, new SchemaPath("employees"), "name");
        String serialized = QueryExpander.serialize(result);

        // Count occurrences of "name" as a field (not substring of other things)
        var op = (OperationDefinition) result.getDefinitions().get(0);
        var employees = (Field) op.getSelectionSet().getSelections().get(0);
        long nameCount = employees.getSelectionSet().getSelections().stream()
            .filter(Field.class::isInstance)
            .map(s -> ((Field) s).getName())
            .filter("name"::equals)
            .count();
        assertEquals(1, nameCount, "Idempotent: 'name' should appear exactly once");
    }

    // -----------------------------------------------------------------------
    // addField — relation uses type info from introspector
    // -----------------------------------------------------------------------

    @Test
    void testAddFieldWithTypeInfo() throws Exception {
        QueryExpander exp = expander();
        Document doc = graphql.parser.Parser.parse("{ employees { name } }");

        // Add 'department' — OBJECT type (not LIST), should create sub-selection
        Document result = exp.addField(doc, new SchemaPath("employees"), "department");
        String serialized = QueryExpander.serialize(result);

        assertTrue(serialized.contains("department"),
            "department field added: " + serialized);
        // Department has id, name, budget — minimal sub-selection should include id and/or name
        assertTrue(serialized.contains("id") || serialized.contains("name"),
            "Relation sub-selection should include representative fields: " + serialized);
        assertDoesNotThrow(() -> graphql.parser.Parser.parse(serialized));
    }

    // -----------------------------------------------------------------------
    // Nested path: add field to nested relation
    // -----------------------------------------------------------------------

    @Test
    void testAddFieldToNestedPath() throws Exception {
        QueryExpander exp = expander();
        Document doc = graphql.parser.Parser.parse(
            "{ employees { name projects { title } } }");

        // Add 'status' to employees/projects
        Document result = exp.addField(
            doc, new SchemaPath("employees", "projects"), "status");
        String serialized = QueryExpander.serialize(result);

        assertTrue(serialized.contains("status"),
            "Added field at nested path: " + serialized);
        assertTrue(serialized.contains("title"),
            "Existing nested field preserved: " + serialized);
        assertDoesNotThrow(() -> graphql.parser.Parser.parse(serialized));
    }

    // -----------------------------------------------------------------------
    // Remove non-existent field — no-op
    // -----------------------------------------------------------------------

    @Test
    void testRemoveNonExistentFieldIsNoOp() throws Exception {
        QueryExpander exp = expander();
        Document doc = graphql.parser.Parser.parse("{ employees { name } }");

        Document result = exp.removeField(doc, new SchemaPath("employees", "nonexistent"));
        String original = QueryExpander.serialize(doc);
        String modified = QueryExpander.serialize(result);

        assertEquals(original, modified, "Removing non-existent field should be no-op");
    }

    // -----------------------------------------------------------------------
    // serialize
    // -----------------------------------------------------------------------

    @Test
    void testSerialize() throws Exception {
        Document doc = graphql.parser.Parser.parse("{ employees { name age } }");
        String serialized = QueryExpander.serialize(doc);

        assertNotNull(serialized);
        assertTrue(serialized.contains("employees"));
        assertTrue(serialized.contains("name"));
        assertTrue(serialized.contains("age"));
    }
}
