// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.explorer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.graphql.QueryExpander;
import com.chiralbehaviors.layout.graphql.TypeIntrospector;
import com.chiralbehaviors.layout.explorer.IntrospectionTreePanel.TreeEntry;

import com.fasterxml.jackson.databind.ObjectMapper;

import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;

/**
 * Tests for IntrospectionTreePanel (RDR-030 Phase 4C).
 */
class IntrospectionTreePanelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
                        "ofType": { "name": "Project", "kind": "OBJECT", "ofType": null } } }
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

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // Already running
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX toolkit init");
    }

    private TypeIntrospector introspector() throws Exception {
        return TypeIntrospector.parse(MAPPER.readTree(INTROSPECTION));
    }

    private QueryExpander expander() throws Exception {
        return new QueryExpander(introspector());
    }

    /**
     * Run a task on the JavaFX Application Thread and wait for completion.
     */
    private void runOnFxAndWait(Runnable task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "FX task completion");
        if (error.get() != null) {
            throw new RuntimeException("FX task failed", error.get());
        }
    }

    // -----------------------------------------------------------------------
    // Tree displays types and fields
    // -----------------------------------------------------------------------

    @Test
    void testIntrospectionTreeDisplaysTypes() throws Exception {
        var doc = new AtomicReference<>(Parser.parse("{ employees { name } }"));
        var captured = new ArrayList<String>();

        runOnFxAndWait(() -> {
            try {
                var panel = new IntrospectionTreePanel(
                    introspector(), expander(), doc::get, captured::add);

                var tree = panel.getTreeView();
                assertNotNull(tree.getRoot());
                var children = tree.getRoot().getChildren();
                assertFalse(children.isEmpty(), "Should have type nodes");

                // Find Employee and Project types
                List<String> typeNames = children.stream()
                    .map(TreeItem::getValue)
                    .filter(TreeEntry.TypeHeader.class::isInstance)
                    .map(e -> ((TreeEntry.TypeHeader) e).type().name())
                    .toList();

                assertTrue(typeNames.contains("Employee"),
                    "Should contain Employee: " + typeNames);
                assertTrue(typeNames.contains("Project"),
                    "Should contain Project: " + typeNames);

                // Employee should have field children
                var employeeItem = children.stream()
                    .filter(i -> i.getValue() instanceof TreeEntry.TypeHeader th
                            && "Employee".equals(th.type().name()))
                    .findFirst().orElseThrow();
                assertFalse(employeeItem.getChildren().isEmpty(),
                    "Employee should have field children");

                List<String> fieldNames = employeeItem.getChildren().stream()
                    .map(TreeItem::getValue)
                    .filter(TreeEntry.FieldEntry.class::isInstance)
                    .map(e -> ((TreeEntry.FieldEntry) e).field().name())
                    .toList();
                assertTrue(fieldNames.contains("name"), "Employee has name field");
                assertTrue(fieldNames.contains("age"), "Employee has age field");
                assertTrue(fieldNames.contains("projects"), "Employee has projects field");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Add field modifies query via QueryExpander
    // -----------------------------------------------------------------------

    @Test
    void testAddFieldActionModifiesQuery() throws Exception {
        var doc = new AtomicReference<>(Parser.parse("{ employees { name } }"));
        var capturedQueries = new ArrayList<String>();

        runOnFxAndWait(() -> {
            try {
                var exp = expander();
                // Simulate what the add action does directly
                Document modified = exp.addField(doc.get(),
                    new com.chiralbehaviors.layout.SchemaPath("employees"), "age");
                String serialized = QueryExpander.serialize(modified);
                capturedQueries.add(serialized);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(1, capturedQueries.size());
        assertTrue(capturedQueries.get(0).contains("age"),
            "Added field should appear in query: " + capturedQueries.get(0));
        assertTrue(capturedQueries.get(0).contains("name"),
            "Existing field preserved: " + capturedQueries.get(0));
    }

    // -----------------------------------------------------------------------
    // Remove field modifies query
    // -----------------------------------------------------------------------

    @Test
    void testRemoveFieldActionModifiesQuery() throws Exception {
        var doc = new AtomicReference<>(
            Parser.parse("{ employees { name age email } }"));
        var capturedQueries = new ArrayList<String>();

        runOnFxAndWait(() -> {
            try {
                var exp = expander();
                Document modified = exp.removeField(doc.get(),
                    new com.chiralbehaviors.layout.SchemaPath("employees", "age"));
                String serialized = QueryExpander.serialize(modified);
                capturedQueries.add(serialized);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(1, capturedQueries.size());
        assertFalse(capturedQueries.get(0).contains("age"),
            "Removed field should not appear: " + capturedQueries.get(0));
        assertTrue(capturedQueries.get(0).contains("name"),
            "Other field preserved: " + capturedQueries.get(0));
    }

    // -----------------------------------------------------------------------
    // Re-execution pipeline: add field → callback receives serialized query
    // -----------------------------------------------------------------------

    @Test
    void testReExecutionPipeline() throws Exception {
        var doc = new AtomicReference<>(Parser.parse("{ employees { name } }"));
        var capturedQueries = new ArrayList<String>();

        runOnFxAndWait(() -> {
            try {
                var exp = expander();

                // Simulate the full pipeline that the panel would execute
                Document modified = exp.addField(doc.get(),
                    new com.chiralbehaviors.layout.SchemaPath("employees"), "email");
                String serialized = QueryExpander.serialize(modified);

                // This is what reExecute callback receives
                capturedQueries.add(serialized);

                // Verify the serialized query is valid GraphQL
                assertDoesNotThrow(() -> Parser.parse(serialized),
                    "Re-executed query must be valid: " + serialized);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(1, capturedQueries.size());
        assertTrue(capturedQueries.get(0).contains("email"),
            "Re-executed query contains new field");
        assertTrue(capturedQueries.get(0).contains("name"),
            "Re-executed query preserves existing field");

        // Verify round-trip: re-parse the captured query
        Document reparsed = Parser.parse(capturedQueries.get(0));
        var op = (OperationDefinition) reparsed.getDefinitions().get(0);
        var employees = (Field) op.getSelectionSet().getSelections().get(0);
        var fieldNames = employees.getSelectionSet().getSelections().stream()
            .filter(Field.class::isInstance)
            .map(s -> ((Field) s).getName())
            .toList();
        assertTrue(fieldNames.contains("name"));
        assertTrue(fieldNames.contains("email"));
    }

    // -----------------------------------------------------------------------
    // Graceful degradation: placeholder when introspection unavailable
    // -----------------------------------------------------------------------

    @Test
    void testGracefulDegradationWhenIntrospectionDisabled() throws Exception {
        runOnFxAndWait(() -> {
            var panel = IntrospectionTreePanel.placeholder();
            assertNotNull(panel);

            // Should contain a label with "not available" message
            boolean hasLabel = panel.getChildren().stream()
                .filter(Label.class::isInstance)
                .map(n -> ((Label) n).getText())
                .anyMatch(text -> text.contains("not available"));
            assertTrue(hasLabel,
                "Placeholder should show 'not available' message");
        });
    }
}
