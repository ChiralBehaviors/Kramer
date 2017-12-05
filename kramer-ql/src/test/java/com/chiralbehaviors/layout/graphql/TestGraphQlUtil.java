/**
 * Copyright (c) 2017 Chiral Behaviors, LLC, all rights reserved.
 * 
 
 *  This file is part of Ultrastructure.
 *
 *  Ultrastructure is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ULtrastructure is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Ultrastructure.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.chiralbehaviors.layout.graphql;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

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
        assertEquals("query", relation.getField());
        assertEquals(1, relation.getChildren()
                                .size());
        relation = (Relation) relation.getChildren()
                                      .get(0);
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
                                             | c.getField()
                                                .equals("namespace"))
                                .count());
    }

    static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, Charset.defaultCharset());
    }
}
