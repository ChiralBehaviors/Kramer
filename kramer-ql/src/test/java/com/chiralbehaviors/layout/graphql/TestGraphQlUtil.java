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

package com.chiralbehaviors.layout.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Relation;

/**
 * @author halhildebrand
 *
 */
public class TestGraphQlUtil {

    @Test
    public void testFragments() throws Exception {

        Relation relation = GraphQlUtil.buildSchema(readFile("target/test-classes/fragment.query"));
        assertNotNull(relation);
        assertEquals("workspaces", relation.getField());
        assertEquals(3, relation.getChildren()
                                .size());
        relation = (Relation) relation.getChildren()
                                      .stream()
                                      .filter(r -> r.getField()
                                                    .equals("imports"))
                                      .findAny()
                                      .get();
        assertNotNull(relation);
        assertEquals(1, relation.getChildren()
                                .size());
        relation = (Relation) relation.getChildren()
                                      .get(0);
        assertEquals("_edge", relation.getField());
        assertEquals(2, relation.getChildren()
                                .size());
        assertEquals(2, relation.getChildren()
                                .stream()
                                .filter(c -> c.getField()
                                              .equals("lookupOrder")
                                             || c.getField()
                                                .equals("namespace"))
                                .count());
    }

    /**
     * Default exclusion: "id" fields are excluded from the schema.
     */
    @Test
    public void testDefaultIdExclusion() {
        String query = "{ users { id name email } }";
        Relation schema = GraphQlUtil.buildSchema(query);

        assertEquals("users", schema.getField());
        assertEquals(2, schema.getChildren().size());
        assertTrue(schema.getChildren().stream()
                         .noneMatch(c -> c.getField().equals("id")),
                   "Default exclusion should remove 'id' field");
        assertTrue(schema.getChildren().stream()
                         .anyMatch(c -> c.getField().equals("name")));
        assertTrue(schema.getChildren().stream()
                         .anyMatch(c -> c.getField().equals("email")));
    }

    /**
     * Empty exclusion set: all fields including "id" are included.
     */
    @Test
    public void testEmptyExclusionIncludesId() {
        String query = "{ users { id name email } }";
        Relation schema = GraphQlUtil.buildSchema(query, Set.of());

        assertEquals("users", schema.getField());
        assertEquals(3, schema.getChildren().size());
        assertTrue(schema.getChildren().stream()
                         .anyMatch(c -> c.getField().equals("id")),
                   "Empty exclusion set should include 'id' field");
    }

    /**
     * Custom exclusion set: specified fields are excluded.
     */
    @Test
    public void testCustomExclusionSet() {
        String query = "{ users { id name email version } }";
        Relation schema = GraphQlUtil.buildSchema(query, Set.of("id", "version"));

        assertEquals("users", schema.getField());
        assertEquals(2, schema.getChildren().size());
        assertTrue(schema.getChildren().stream()
                         .noneMatch(c -> c.getField().equals("id")),
                   "'id' should be excluded");
        assertTrue(schema.getChildren().stream()
                         .noneMatch(c -> c.getField().equals("version")),
                   "'version' should be excluded");
    }

    /**
     * Exclusion applies consistently to inline fragments.
     */
    @Test
    public void testExclusionInInlineFragment() throws Exception {
        // fragment.query has "id" field in the workspaces selection
        Relation schema = GraphQlUtil.buildSchema(
            readFile("target/test-classes/fragment.query"));
        // "id" should be excluded by default
        assertTrue(schema.getChildren().stream()
                         .noneMatch(c -> c.getField().equals("id")),
                   "'id' in fragment.query should be excluded by default");

        // With empty exclusion, "id" should appear
        Relation schemaWithId = GraphQlUtil.buildSchema(
            readFile("target/test-classes/fragment.query"), Set.of());
        assertTrue(schemaWithId.getChildren().stream()
                               .anyMatch(c -> c.getField().equals("id")),
                   "'id' should appear with empty exclusion set");
        assertEquals(schema.getChildren().size() + 1,
                     schemaWithId.getChildren().size());
    }

    /**
     * buildSchema(query, source) entry point respects exclusion.
     */
    @Test
    public void testBuildSchemaWithSourceAndExclusion() {
        String query = "query MyQuery { users { id name } posts { id title } }";
        Relation schema = GraphQlUtil.buildSchema(query, "users", Set.of());

        assertEquals("users", schema.getField());
        assertEquals(2, schema.getChildren().size());
        assertTrue(schema.getChildren().stream()
                         .anyMatch(c -> c.getField().equals("id")),
                   "'id' should be included with empty exclusion");
    }

    public static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
