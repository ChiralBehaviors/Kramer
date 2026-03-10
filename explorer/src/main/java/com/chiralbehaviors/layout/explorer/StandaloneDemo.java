// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.explorer;

import com.chiralbehaviors.layout.AutoLayout;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Self-contained demo of the Kramer autolayout engine.
 * No external services required — generates sample data inline.
 *
 * Run from IDE: StandaloneDemo.Main
 * Run from CLI: mvn -pl explorer exec:java -Dexec.mainClass=com.chiralbehaviors.layout.explorer.StandaloneDemo
 */
public class StandaloneDemo extends Application {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Relation schema = buildSchema();
        JsonNode data = buildData();

        AutoLayout layout = new AutoLayout(schema);
        layout.measure(data);
        layout.updateItem(data);

        Scene scene = new Scene(layout, 1000, 700);
        stage.setTitle("Kramer AutoLayout Demo");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Build a schema that exercises both outline and table modes:
     * employees (relation)
     *   ├── name (primitive)
     *   ├── role (primitive)
     *   ├── department (primitive)
     *   ├── email (primitive)
     *   └── projects (relation)
     *         ├── project (primitive)
     *         ├── status (primitive)
     *         └── hours (primitive)
     */
    private Relation buildSchema() {
        Relation projects = new Relation("projects");
        projects.addChild(new Primitive("project"));
        projects.addChild(new Primitive("status"));
        projects.addChild(new Primitive("hours"));

        Relation employees = new Relation("employees");
        employees.addChild(new Primitive("name"));
        employees.addChild(new Primitive("role"));
        employees.addChild(new Primitive("department"));
        employees.addChild(new Primitive("email"));
        employees.addChild(projects);

        return employees;
    }

    /**
     * Generate sample employee/project data.
     */
    private JsonNode buildData() {
        ArrayNode employees = mapper.createArrayNode();

        employees.add(employee("Alice Chen", "Principal Engineer", "Platform",
                               "alice@example.com",
                               project("Kramer Layout", "Active", 320),
                               project("Schema Migration", "Complete", 80),
                               project("CI Pipeline", "Active", 45)));

        employees.add(employee("Bob Martinez", "Staff Designer", "Design",
                               "bob@example.com",
                               project("Design System v3", "Active", 200),
                               project("User Research", "Planning", 0)));

        employees.add(employee("Carol Williams", "Senior Engineer", "Platform",
                               "carol@example.com",
                               project("Kramer Layout", "Active", 160),
                               project("Performance Audit", "Complete", 90),
                               project("API Gateway", "Active", 110),
                               project("Load Testing", "Planning", 0)));

        employees.add(employee("David Park", "Engineering Manager", "Platform",
                               "david@example.com",
                               project("Team Planning", "Active", 400)));

        employees.add(employee("Eva Johansson", "Backend Engineer", "Services",
                               "eva@example.com",
                               project("Auth Service", "Active", 260),
                               project("Rate Limiter", "Complete", 55),
                               project("Monitoring", "Active", 85)));

        employees.add(employee("Frank Osei", "Frontend Engineer", "Web",
                               "frank@example.com",
                               project("Dashboard UI", "Active", 180),
                               project("Component Library", "Active", 95),
                               project("A11y Audit", "Planning", 0)));

        employees.add(employee("Grace Liu", "Data Engineer", "Analytics",
                               "grace@example.com",
                               project("ETL Pipeline", "Active", 300),
                               project("Data Warehouse", "Planning", 20)));

        employees.add(employee("Hiro Tanaka", "SRE", "Infrastructure",
                               "hiro@example.com",
                               project("K8s Migration", "Active", 400),
                               project("Incident Response", "Active", 120),
                               project("Chaos Testing", "Planning", 10)));

        employees.add(employee("Ines Garcia", "Product Manager", "Product",
                               "ines@example.com",
                               project("Q2 Roadmap", "Complete", 60),
                               project("Customer Interviews", "Active", 40)));

        employees.add(employee("James O'Brien", "QA Lead", "Quality",
                               "james@example.com",
                               project("Test Automation", "Active", 250),
                               project("Release Validation", "Active", 90),
                               project("Regression Suite", "Complete", 180)));

        return employees;
    }

    private ObjectNode employee(String name, String role, String dept,
                                String email, ObjectNode... projects) {
        ObjectNode emp = mapper.createObjectNode();
        emp.put("name", name);
        emp.put("role", role);
        emp.put("department", dept);
        emp.put("email", email);
        ArrayNode projArray = mapper.createArrayNode();
        for (ObjectNode p : projects) {
            projArray.add(p);
        }
        emp.set("projects", projArray);
        return emp;
    }

    private ObjectNode project(String name, String status, int hours) {
        ObjectNode proj = mapper.createObjectNode();
        proj.put("project", name);
        proj.put("status", status);
        proj.put("hours", hours);
        return proj;
    }

    /**
     * IDE entry point (avoids JavaFX module loading issues).
     */
    public static class Main {
        public static void main(String[] args) {
            StandaloneDemo.main(args);
        }
    }
}
