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

package com.chiralbehaviors.layout.schema;

import java.io.IOException;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author halhildebrand
 *
 */
final public class Util {

    public static Relation build() {
        Relation root = new Relation("field");
        root.addChild(new Primitive("name"));
        root.addChild(new Primitive("notes"));

        Relation n = new Relation("classification");
        n.addChild(new Primitive("name"));
        n.addChild(new Primitive("description"));
        root.addChild(n);

        n = new Relation("classifier");
        n.addChild(new Primitive("name"));
        n.addChild(new Primitive("description"));
        root.addChild(n);

        n = new Relation("attributes");
        n.addChild(new Primitive("name"));
        n.addChild(new Primitive("description"));
        root.addChild(n);

        Relation n2 = new Relation("authorizedAttribute");
        n2.addChild(new Primitive("name"));
        n2.addChild(new Primitive("description"));
        n.addChild(n2);

        n = new Relation("children");
        n.addChild(new Primitive("name"));
        n.addChild(new Primitive("notes"));
        n.addChild(new Primitive("cardinality"));
        root.addChild(n);

        n2 = new Relation("relationship");
        n2.addChild(new Primitive("name"));
        n2.addChild(new Primitive("description"));
        n.addChild(n2);

        n2 = new Relation("child");
        n2.addChild(new Primitive("name"));
        n.addChild(n2);

        return root;
    }

    public static JsonNode testData() throws IOException {
        return new ObjectMapper().readTree(Util.class.getResourceAsStream("/columns.json"));
    }
}
