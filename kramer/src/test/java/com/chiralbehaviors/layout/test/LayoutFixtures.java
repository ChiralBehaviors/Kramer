// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.test;

import java.util.Set;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Test fixtures providing schema + data + field names for the layout E2E
 * test framework. Each fixture is a self-contained (schema, data, fieldNames)
 * triple that can be passed to {@link LayoutTestHarness}.
 */
public final class LayoutFixtures {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    private LayoutFixtures() {}

    /** Bundled fixture: schema + data + field names. */
    public record Fixture(Relation schema, ArrayNode data, Set<String> fieldNames,
                           String name) {}

    // -------------------------------------------------------------------
    // Flat: 3 primitives, 10 rows
    // -------------------------------------------------------------------

    public static Fixture flat() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("id"));
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("value"));

        ArrayNode data = NF.arrayNode();
        String[] names = {"Alpha", "Bravo", "Charlie", "Delta", "Echo",
                           "Foxtrot", "Golf", "Hotel", "India", "Juliet"};
        for (int i = 0; i < 10; i++) {
            ObjectNode row = NF.objectNode();
            row.put("id", i + 1);
            row.put("name", names[i]);
            row.put("value", (i + 1) * 42);
            data.add(row);
        }

        return new Fixture(schema, data,
            Set.of("items", "id", "name", "value"), "flat");
    }

    // -------------------------------------------------------------------
    // Nested: employees (4 prims) + projects (3 prims), 5 rows
    // Same structure as the demo app
    // -------------------------------------------------------------------

    public static Fixture nested() {
        Relation schema = new Relation("employees");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("role"));
        schema.addChild(new Primitive("department"));
        schema.addChild(new Primitive("email"));

        Relation projects = new Relation("projects");
        projects.addChild(new Primitive("project"));
        projects.addChild(new Primitive("status"));
        projects.addChild(new Primitive("hours"));
        schema.addChild(projects);

        ArrayNode data = NF.arrayNode();
        data.add(emp("Eva Johansson", "Backend Engineer", "Services",
            "eva@example.com",
            proj("Auth Service", "Active", 260),
            proj("Rate Limiter", "Complete", 55),
            proj("Monitoring", "Active", 85)));
        data.add(emp("Frank Osei", "Frontend Engineer", "Web",
            "frank@example.com",
            proj("Dashboard UI", "Active", 180),
            proj("Component Library", "Active", 95)));
        data.add(emp("Grace Liu", "Data Engineer", "Analytics",
            "grace@example.com",
            proj("ETL Pipeline", "Active", 300),
            proj("Data Warehouse", "Planning", 20)));
        data.add(emp("Hiro Tanaka", "SRE", "Infrastructure",
            "hiro@example.com",
            proj("K8s Migration", "Active", 400),
            proj("Incident Response", "Active", 120)));
        data.add(emp("Ines Garcia", "Product Manager", "Product",
            "ines@example.com",
            proj("Q2 Roadmap", "Complete", 60),
            proj("Customer Interviews", "Active", 40)));

        return new Fixture(schema, data,
            Set.of("employees", "name", "role", "department", "email",
                   "projects", "project", "status", "hours"), "nested");
    }

    // -------------------------------------------------------------------
    // Deep: org → teams → members (3 nesting levels)
    // -------------------------------------------------------------------

    public static Fixture deep() {
        Relation schema = new Relation("org");
        schema.addChild(new Primitive("name"));

        Relation teams = new Relation("teams");
        teams.addChild(new Primitive("team"));

        Relation members = new Relation("members");
        members.addChild(new Primitive("person"));
        members.addChild(new Primitive("role"));
        teams.addChild(members);

        schema.addChild(teams);

        ArrayNode data = NF.arrayNode();
        ObjectNode org = NF.objectNode();
        org.put("name", "Engineering");
        ArrayNode teamsArr = NF.arrayNode();
        for (String[] t : new String[][] {
            {"Backend", "Alice Chen", "Senior", "Bob Park", "Junior"},
            {"Frontend", "Carol Wu", "Lead", "Dave Kim", "Mid"},
            {"Platform", "Eve Rao", "Staff", "Frank Li", "Senior"}
        }) {
            ObjectNode team = NF.objectNode();
            team.put("team", t[0]);
            ArrayNode membersArr = NF.arrayNode();
            for (int i = 1; i < t.length; i += 2) {
                ObjectNode m = NF.objectNode();
                m.put("person", t[i]);
                m.put("role", t[i + 1]);
                membersArr.add(m);
            }
            team.set("members", membersArr);
            teamsArr.add(team);
        }
        org.set("teams", teamsArr);
        data.add(org);

        return new Fixture(schema, data,
            Set.of("org", "name", "teams", "team", "members", "person", "role"),
            "deep");
    }

    // -------------------------------------------------------------------
    // Wide: 12 primitives (stress test for column distribution)
    // -------------------------------------------------------------------

    public static Fixture wide() {
        Relation schema = new Relation("records");
        String[] fields = {"id", "first_name", "last_name", "email",
                            "phone", "department", "title", "salary",
                            "start_date", "office", "manager", "status"};
        for (String f : fields) {
            schema.addChild(new Primitive(f));
        }

        ArrayNode data = NF.arrayNode();
        String[][] rows = {
            {"1", "Alice", "Smith", "alice@co.com", "555-0101",
             "Engineering", "Senior Dev", "120000", "2020-01-15",
             "NYC", "Bob", "Active"},
            {"2", "Charlie", "Brown", "charlie@co.com", "555-0102",
             "Marketing", "Director", "150000", "2019-06-01",
             "SF", "Diana", "Active"},
            {"3", "Eve", "Johnson", "eve@co.com", "555-0103",
             "Sales", "Account Exec", "95000", "2021-03-20",
             "CHI", "Frank", "On Leave"},
        };
        for (String[] row : rows) {
            ObjectNode obj = NF.objectNode();
            for (int i = 0; i < fields.length; i++) {
                obj.put(fields[i], row[i]);
            }
            data.add(obj);
        }

        var allFields = new java.util.HashSet<>(Set.of(fields));
        allFields.add("records");
        return new Fixture(schema, data, Set.copyOf(allFields), "wide");
    }

    // -------------------------------------------------------------------
    // All fixtures for parameterized tests
    // -------------------------------------------------------------------

    public static Fixture[] all() {
        return new Fixture[] { flat(), nested(), deep(), wide() };
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private static ObjectNode emp(String name, String role, String dept,
                                   String email, ObjectNode... projects) {
        ObjectNode obj = NF.objectNode();
        obj.put("name", name);
        obj.put("role", role);
        obj.put("department", dept);
        obj.put("email", email);
        ArrayNode pa = NF.arrayNode();
        for (ObjectNode p : projects) pa.add(p);
        obj.set("projects", pa);
        return obj;
    }

    private static ObjectNode proj(String project, String status, int hours) {
        ObjectNode obj = NF.objectNode();
        obj.put("project", project);
        obj.put("status", status);
        obj.put("hours", hours);
        return obj;
    }
}
