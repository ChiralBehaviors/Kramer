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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

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

    public static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
