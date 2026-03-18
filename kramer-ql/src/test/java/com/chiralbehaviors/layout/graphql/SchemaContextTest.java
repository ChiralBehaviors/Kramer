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

package com.chiralbehaviors.layout.graphql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;

import graphql.language.Field;

/**
 * Tests for SchemaContext and GraphQlUtil.buildContext() (RDR-020 Phase A).
 *
 * @author hhildebrand
 */
class SchemaContextTest {

    // --- buildContext basic ---

    @Test
    void buildContextRetainsDocument() {
        String query = "{ users { name email } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        assertNotNull(ctx.document(), "Document should be retained");
        assertNotNull(ctx.schema(), "Schema should be built");
        assertEquals("users", ctx.schema().getField());
    }

    @Test
    void buildContextSchemaMatchesBuildSchema() {
        String query = "{ users { name email age } }";
        Relation fromBuildSchema = GraphQlUtil.buildSchema(query);
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        assertEquals(fromBuildSchema.getField(), ctx.schema().getField());
        assertEquals(fromBuildSchema.getChildren().size(), ctx.schema().getChildren().size());
    }

    // --- fieldIndex ---

    @Test
    void fieldIndexContainsPrimitives() {
        String query = "{ users { name email } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        SchemaPath namePath = new SchemaPath("users", "name");
        SchemaPath emailPath = new SchemaPath("users", "email");

        assertTrue(ctx.fieldAt(namePath).isPresent(), "name should be indexed");
        assertTrue(ctx.fieldAt(emailPath).isPresent(), "email should be indexed");
        assertEquals("name", ctx.fieldAt(namePath).get().getName());
        assertEquals("email", ctx.fieldAt(emailPath).get().getName());
    }

    @Test
    void fieldIndexContainsRelations() {
        String query = "{ users { name orders { total } } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        SchemaPath usersPath = new SchemaPath("users");
        SchemaPath ordersPath = new SchemaPath("users", "orders");
        SchemaPath totalPath = new SchemaPath("users", "orders", "total");

        assertTrue(ctx.fieldAt(usersPath).isPresent(), "users should be indexed");
        assertTrue(ctx.fieldAt(ordersPath).isPresent(), "orders should be indexed");
        assertTrue(ctx.fieldAt(totalPath).isPresent(), "total should be indexed");
    }

    @Test
    void fieldIndexExcludesDefaultExcluded() {
        String query = "{ users { id name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        SchemaPath idPath = new SchemaPath("users", "id");
        // id is excluded from schema but should still be in fieldIndex
        // (the Field AST node exists; it's just not in the Relation tree)
        // Actually per RDR: excluded fields should NOT be in fieldIndex
        // because they're unreachable via SchemaPath
        assertFalse(ctx.fieldAt(idPath).isPresent(),
            "Excluded fields should not be in fieldIndex");
    }

    @Test
    void fieldIndexEmptyExclusion() {
        String query = "{ users { id name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query, Set.of());

        SchemaPath idPath = new SchemaPath("users", "id");
        assertTrue(ctx.fieldAt(idPath).isPresent(),
            "id should be in fieldIndex when not excluded");
    }

    // --- Arguments preserved via fieldIndex ---

    @Test
    void fieldRetainsArguments() {
        String query = "{ users(limit: 10) { name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        SchemaPath usersPath = new SchemaPath("users");
        Field usersField = ctx.fieldAt(usersPath).orElseThrow();
        assertFalse(usersField.getArguments().isEmpty(),
            "Field arguments should be preserved");
        assertEquals("limit", usersField.getArguments().get(0).getName());
    }

    // --- Alias handling ---

    @Test
    void aliasUsedAsDataKey() {
        String query = "{ displayName: name { first last } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        // The schema node should use the alias as the field name (data key)
        assertEquals("displayName", ctx.schema().getField());

        // fieldIndex should be keyed by alias
        SchemaPath path = new SchemaPath("displayName");
        assertTrue(ctx.fieldAt(path).isPresent());
        assertEquals("name", ctx.fieldAt(path).get().getName()); // original name
    }

    @Test
    void displayNameReturnsAlias() {
        String query = "{ users { displayName: name email } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        SchemaPath aliasPath = new SchemaPath("users", "displayName");
        assertEquals("displayName", ctx.displayName(aliasPath));
    }

    @Test
    void displayNameFallsBackToLeaf() {
        String query = "{ users { name email } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        SchemaPath namePath = new SchemaPath("users", "name");
        assertEquals("name", ctx.displayName(namePath));
    }

    // --- Source-matching with alias ---

    @Test
    void buildContextWithSourceMatchesAlias() {
        String query = "{ myUsers: users { name } posts { title } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query, "myUsers");

        assertEquals("myUsers", ctx.schema().getField());
        assertTrue(ctx.fieldAt(new SchemaPath("myUsers", "name")).isPresent());
    }

    @Test
    void buildContextWithSourceMatchesFieldName() {
        String query = "{ users { name } posts { title } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query, "users");

        assertEquals("users", ctx.schema().getField());
    }

    // --- Multi-root (QueryRoot) ---

    @Test
    void multiRootQueryProducesQueryRoot() {
        String query = "{ users { name } posts { title } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        assertInstanceOf(QueryRoot.class, ctx.schema());
        assertEquals(2, ctx.schema().getChildren().size());
    }

    @Test
    void multiRootFieldIndexCoversAllRoots() {
        String query = "{ users { name } posts { title } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        assertTrue(ctx.fieldAt(new SchemaPath("users")).isPresent());
        assertTrue(ctx.fieldAt(new SchemaPath("users", "name")).isPresent());
        assertTrue(ctx.fieldAt(new SchemaPath("posts")).isPresent());
        assertTrue(ctx.fieldAt(new SchemaPath("posts", "title")).isPresent());
    }

    // --- Named operation ---

    @Test
    void namedOperationWrapsInRelation() {
        String query = "query MyQuery { users { name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        assertEquals("MyQuery", ctx.schema().getField());
        assertInstanceOf(Relation.class, ctx.schema());
        assertFalse(ctx.schema() instanceof QueryRoot);
    }

    // --- Inline fragment ---

    @Test
    void inlineFragmentFieldsIndexed() throws Exception {
        String query = TestGraphQlUtil.readFile("target/test-classes/fragment.query");
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        assertNotNull(ctx.schema());
        // The fragment.query has fields under workspaces including imports
        SchemaPath importsPath = new SchemaPath("workspaces", "imports");
        assertTrue(ctx.fieldAt(importsPath).isPresent(),
            "Inline fragment fields should be indexed");
    }

    // --- Named fragment spread resolution (RDR-020 Phase D1) ---

    @Test
    void buildContextResolvesNamedFragment() {
        String query = """
                query Q {
                  users {
                    ...UserFields
                  }
                }
                fragment UserFields on User {
                  name
                  email
                }
                """;
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        assertNotNull(ctx.schema());
        // Named operation wraps in Relation "Q"
        assertEquals("Q", ctx.schema().getField());

        // Fragment fields must appear in schema tree
        Relation usersRelation = (Relation) ctx.schema().getChildren().get(0);
        assertEquals("users", usersRelation.getField());
        assertEquals(2, usersRelation.getChildren().size(),
            "Fragment fields name and email should be inlined");

        // Fragment fields must appear in fieldIndex (paths rooted at query fields, not operation name)
        SchemaPath namePath = new SchemaPath("users", "name");
        SchemaPath emailPath = new SchemaPath("users", "email");
        assertTrue(ctx.fieldAt(namePath).isPresent(), "name from fragment should be indexed");
        assertTrue(ctx.fieldAt(emailPath).isPresent(), "email from fragment should be indexed");
    }

    @Test
    void buildContextResolvesNestedFragment() {
        String query = """
                query Q {
                  users {
                    ...UserFields
                  }
                }
                fragment UserFields on User {
                  name
                  orders {
                    total
                    amount
                  }
                }
                """;
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        // name (Primitive) + orders (Relation) from fragment
        Relation usersRelation = (Relation) ctx.schema().getChildren().get(0);
        assertEquals(2, usersRelation.getChildren().size(),
            "Fragment should contribute name and orders");

        SchemaPath ordersPath = new SchemaPath("users", "orders");
        SchemaPath totalPath = new SchemaPath("users", "orders", "total");
        assertTrue(ctx.fieldAt(ordersPath).isPresent(), "orders relation should be indexed");
        assertTrue(ctx.fieldAt(totalPath).isPresent(), "nested total should be indexed");
    }

    @Test
    void buildContextUnknownFragmentThrows() {
        String query = """
                {
                  users {
                    ...MissingFrag
                  }
                }
                """;
        assertThrows(IllegalStateException.class, () -> GraphQlUtil.buildContext(query),
            "Unknown fragment reference should throw IllegalStateException");
    }

    @Test
    void buildSchemaStillThrowsOnFragmentSpread() {
        // Legacy buildSchema() does not have a Document; fragment resolution not supported.
        String query = """
                {
                  users {
                    ...UserFields
                  }
                }
                fragment UserFields on User {
                  name
                  email
                }
                """;
        // Parse the Field manually to call buildSchema(Field, Set)
        var document = graphql.parser.Parser.parse(query);
        var operation = (graphql.language.OperationDefinition) document.getDefinitions().get(0);
        var usersField = (graphql.language.Field) operation.getSelectionSet().getSelections().get(0);

        assertThrows(UnsupportedOperationException.class,
            () -> GraphQlUtil.buildSchema(usersField),
            "Legacy buildSchema should still throw on FragmentSpread");
    }
}
