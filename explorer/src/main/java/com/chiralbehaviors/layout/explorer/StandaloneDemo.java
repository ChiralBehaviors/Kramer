// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.explorer;

import java.util.ArrayList;

import com.chiralbehaviors.layout.AutoLayout;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.query.ColumnSortHandler;
import com.chiralbehaviors.layout.query.FieldInspectorPanel;
import com.chiralbehaviors.layout.query.FieldSelectorPanel;
import com.chiralbehaviors.layout.query.InteractionHandler;
import com.chiralbehaviors.layout.query.InteractionMenuFactory;
import com.chiralbehaviors.layout.query.LayoutInteraction;
import com.chiralbehaviors.layout.query.LayoutQueryState;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Polished standalone demo of the Kramer autolayout engine.
 * No external services required — uses inline course catalog data.
 *
 * <p>Showcases the full interactive pipeline: sort, filter, aggregate,
 * formula, visibility, context menus, field selector, field inspector,
 * undo/redo, keyboard shortcuts, column resize, and adaptive
 * outline/table layout on window resize.
 *
 * <p>Run: {@code cd explorer && mvn package && java -jar target/explorer-0.0.1-SNAPSHOT-phat.jar}
 * <br>Or via IDE: {@code StandaloneDemo.Main}
 */
public class StandaloneDemo extends Application {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double GRAB_ZONE = 4.0;

    private AutoLayout layout;
    private LayoutQueryState queryState;
    private InteractionHandler interactionHandler;
    private InteractionMenuFactory menuFactory;
    private FieldSelectorPanel fieldSelectorPanel;
    private FieldInspectorPanel fieldInspectorPanel;
    private SchemaPath lastClickedPath;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Relation schema = buildSchema();
        ArrayNode data = buildData();

        // Wire the full interactive pipeline
        Style style = new Style();
        queryState = new LayoutQueryState(style);
        style.setStylesheet(queryState);
        layout = new AutoLayout(null, style);

        // Re-layout on query state changes (sort, filter, visibility, etc.)
        queryState.addChangeListener(layout::autoLayout);

        interactionHandler = new InteractionHandler(queryState);
        menuFactory = new InteractionMenuFactory(interactionHandler, queryState);

        // Field selector panel (left sidebar)
        fieldSelectorPanel = new FieldSelectorPanel(interactionHandler, queryState);
        fieldSelectorPanel.setPrefWidth(200);
        fieldSelectorPanel.setMinWidth(0);

        // Field inspector panel (right sidebar)
        fieldInspectorPanel = new FieldInspectorPanel(interactionHandler, queryState);
        fieldInspectorPanel.setPrefWidth(250);
        fieldInspectorPanel.setMinWidth(0);

        // Tree selection → inspector
        fieldSelectorPanel.setOnFieldSelected(fieldInspectorPanel::inspect);

        // Sort handler + resize handler installation after each layout pass
        var sortHandler = new ColumnSortHandler(interactionHandler, queryState);
        layout.setPostLayoutCallback(() -> {
            installSortHandlers(layout, sortHandler);
            installResizeHandlers(layout);
        });

        // Context menu on right-click
        layout.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            SchemaPath hitPath = layout.hitSchemaPath(event.getX(), event.getY());
            if (hitPath != null) {
                String cellText = layout.hitCellText(event.getX(), event.getY());
                var contextMenu = isRelationPath(hitPath)
                    ? menuFactory.buildRelationMenu(hitPath)
                    : menuFactory.buildPrimitiveMenu(hitPath, cellText);

                // Undo/redo items
                contextMenu.getItems().add(new SeparatorMenuItem());
                var undoItem = new MenuItem("Undo");
                undoItem.setOnAction(e -> interactionHandler.undo());
                var redoItem = new MenuItem("Redo");
                redoItem.setOnAction(e -> interactionHandler.redo());
                contextMenu.getItems().addAll(undoItem, redoItem);
                contextMenu.setOnShowing(e -> {
                    undoItem.setDisable(!interactionHandler.canUndo());
                    redoItem.setDisable(!interactionHandler.canRedo());
                });
                contextMenu.show(layout, event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });

        // Click → track path + update inspector
        layout.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            SchemaPath hitPath = layout.hitSchemaPath(event.getX(), event.getY());
            if (hitPath != null) {
                lastClickedPath = hitPath;
                fieldInspectorPanel.inspect(hitPath);
            }
        });

        // Keyboard shortcuts: undo/redo, sort
        layout.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown()) {
                switch (event.getCode()) {
                    case Z -> {
                        if (event.isShiftDown()) {
                            interactionHandler.redo();
                        } else {
                            interactionHandler.undo();
                        }
                        event.consume();
                    }
                    case UP -> {
                        if (lastClickedPath != null) {
                            interactionHandler.apply(
                                new LayoutInteraction.SortBy(lastClickedPath, false));
                        }
                        event.consume();
                    }
                    case DOWN -> {
                        if (lastClickedPath != null) {
                            interactionHandler.apply(
                                new LayoutInteraction.SortBy(lastClickedPath, true));
                        }
                        event.consume();
                    }
                    default -> {}
                }
            }
        });

        // Assemble the scene — AutoLayout directly as BorderPane center
        // (AutoLayout extends AnchorPane; BorderPane calls resize() on center child)
        var root = new BorderPane();
        root.setCenter(layout);

        // Toggle buttons in bottom bar
        var fieldToggle = new ToggleButton("Fields (\u21E7\u2318F)");
        fieldToggle.selectedProperty().addListener((o, prev, sel) ->
            root.setLeft(sel ? fieldSelectorPanel : null));

        var inspectorToggle = new ToggleButton("Inspector (\u21E7\u2318I)");
        inspectorToggle.selectedProperty().addListener((o, prev, sel) ->
            root.setRight(sel ? fieldInspectorPanel : null));

        var buttonBar = new ButtonBar();
        buttonBar.getButtons().addAll(fieldToggle, inspectorToggle);
        root.setBottom(buttonBar);

        // Global keyboard shortcuts for panel toggles
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() && event.isShiftDown()) {
                switch (event.getCode()) {
                    case F -> {
                        fieldToggle.setSelected(!fieldToggle.isSelected());
                        event.consume();
                    }
                    case I -> {
                        inspectorToggle.setSelected(!inspectorToggle.isSelected());
                        event.consume();
                    }
                    default -> {}
                }
            }
        });

        // Load schema and data into the layout
        layout.setRoot(schema);
        fieldSelectorPanel.setRoot(schema);
        layout.measure(data);
        layout.updateItem(data);

        // Focus the layout so keyboard shortcuts work immediately
        layout.setFocusTraversable(true);
        layout.requestFocus();

        // Start with field selector open
        fieldToggle.setSelected(true);

        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("Kramer \u2014 Course Catalog Demo");
        stage.setScene(scene);
        stage.show();
    }

    // -----------------------------------------------------------------------
    // Schema: 3-level hierarchy exercising outline + table adaptation
    // -----------------------------------------------------------------------

    /**
     * departments → courses → sections
     */
    private Relation buildSchema() {
        Relation sections = new Relation("sections");
        sections.addChild(new Primitive("section"));
        sections.addChild(new Primitive("enrollment"));
        sections.addChild(new Primitive("room"));
        sections.addChild(new Primitive("schedule"));

        Relation courses = new Relation("courses");
        courses.addChild(new Primitive("number"));
        courses.addChild(new Primitive("title"));
        courses.addChild(new Primitive("credits"));
        courses.addChild(new Primitive("instructor"));
        courses.addChild(sections);

        Relation departments = new Relation("departments");
        departments.addChild(new Primitive("name"));
        departments.addChild(new Primitive("building"));
        departments.addChild(courses);

        return departments;
    }

    // -----------------------------------------------------------------------
    // Sample data: ~8 departments, ~30 courses, ~60 sections
    // -----------------------------------------------------------------------

    private ArrayNode buildData() {
        ArrayNode depts = MAPPER.createArrayNode();

        depts.add(dept("Computer Science", "Gates Hall",
            course("CS101", "Introduction to Programming", 3, "Prof. Turing",
                section("A", 45, "Room 101", "MWF 9:00-9:50"),
                section("B", 42, "Room 102", "MWF 10:00-10:50"),
                section("C", 38, "Room 201", "TTh 1:00-2:15")),
            course("CS201", "Data Structures", 4, "Prof. Knuth",
                section("A", 35, "Room 301", "MWF 11:00-11:50"),
                section("B", 30, "Room 302", "TTh 9:00-10:15")),
            course("CS301", "Algorithms", 4, "Prof. Dijkstra",
                section("A", 28, "Room 401", "MWF 1:00-1:50")),
            course("CS410", "Machine Learning", 3, "Prof. Ng",
                section("A", 40, "Auditorium", "TTh 3:00-4:15"),
                section("B", 38, "Room 501", "MWF 2:00-2:50"))));

        depts.add(dept("Mathematics", "Hilbert Hall",
            course("MATH101", "Calculus I", 4, "Prof. Euler",
                section("A", 50, "Room 110", "MWF 8:00-8:50"),
                section("B", 48, "Room 111", "MWF 9:00-9:50"),
                section("C", 45, "Room 112", "TTh 10:30-11:45")),
            course("MATH201", "Linear Algebra", 3, "Prof. Gauss",
                section("A", 35, "Room 210", "TTh 1:00-2:15")),
            course("MATH301", "Real Analysis", 3, "Prof. Cauchy",
                section("A", 22, "Room 310", "MWF 11:00-11:50"))));

        depts.add(dept("Physics", "Newton Building",
            course("PHYS101", "Mechanics", 4, "Prof. Feynman",
                section("A", 55, "Lecture Hall A", "MWF 10:00-10:50"),
                section("B", 50, "Lecture Hall B", "TTh 9:00-10:15")),
            course("PHYS201", "Electricity & Magnetism", 4, "Prof. Maxwell",
                section("A", 40, "Room 220", "MWF 1:00-1:50")),
            course("PHYS310", "Quantum Mechanics", 3, "Prof. Bohr",
                section("A", 25, "Room 320", "TTh 3:00-4:15"))));

        depts.add(dept("English", "Austen Hall",
            course("ENG101", "Composition", 3, "Prof. Strunk",
                section("A", 25, "Room 105", "MWF 9:00-9:50"),
                section("B", 25, "Room 106", "MWF 10:00-10:50"),
                section("C", 24, "Room 107", "TTh 1:00-2:15"),
                section("D", 22, "Room 108", "TTh 3:00-4:15")),
            course("ENG201", "American Literature", 3, "Prof. Twain",
                section("A", 30, "Room 205", "MWF 11:00-11:50")),
            course("ENG350", "Shakespeare", 3, "Prof. Bloom",
                section("A", 28, "Room 305", "TTh 10:30-11:45"))));

        depts.add(dept("History", "Herodotus Hall",
            course("HIST101", "World History I", 3, "Prof. Gibbon",
                section("A", 60, "Auditorium B", "MWF 10:00-10:50"),
                section("B", 55, "Auditorium C", "TTh 9:00-10:15")),
            course("HIST201", "Modern Europe", 3, "Prof. Hobsbawm",
                section("A", 35, "Room 215", "MWF 1:00-1:50")),
            course("HIST310", "Ancient Rome", 3, "Prof. Beard",
                section("A", 30, "Room 315", "TTh 1:00-2:15"))));

        depts.add(dept("Chemistry", "Mendeleev Hall",
            course("CHEM101", "General Chemistry", 4, "Prof. Pauling",
                section("A", 48, "Lab A", "MWF 9:00-9:50"),
                section("B", 45, "Lab B", "TTh 10:30-11:45")),
            course("CHEM201", "Organic Chemistry", 4, "Prof. Woodward",
                section("A", 32, "Lab C", "MWF 11:00-11:50"),
                section("B", 30, "Lab D", "TTh 1:00-2:15"))));

        depts.add(dept("Economics", "Smith Hall",
            course("ECON101", "Microeconomics", 3, "Prof. Samuelson",
                section("A", 55, "Room 120", "MWF 10:00-10:50"),
                section("B", 50, "Room 121", "TTh 9:00-10:15")),
            course("ECON201", "Macroeconomics", 3, "Prof. Keynes",
                section("A", 45, "Room 220", "MWF 1:00-1:50")),
            course("ECON350", "Econometrics", 3, "Prof. Heckman",
                section("A", 25, "Room 320", "TTh 3:00-4:15"))));

        depts.add(dept("Philosophy", "Plato Hall",
            course("PHIL101", "Introduction to Philosophy", 3, "Prof. Russell",
                section("A", 40, "Room 130", "MWF 9:00-9:50"),
                section("B", 35, "Room 131", "TTh 10:30-11:45")),
            course("PHIL201", "Ethics", 3, "Prof. Rawls",
                section("A", 30, "Room 230", "MWF 11:00-11:50")),
            course("PHIL310", "Logic", 3, "Prof. Gödel",
                section("A", 20, "Room 330", "TTh 1:00-2:15"))));

        return depts;
    }

    private ObjectNode dept(String name, String building, ObjectNode... courses) {
        ObjectNode d = MAPPER.createObjectNode();
        d.put("name", name);
        d.put("building", building);
        ArrayNode arr = MAPPER.createArrayNode();
        for (ObjectNode c : courses) arr.add(c);
        d.set("courses", arr);
        return d;
    }

    private ObjectNode course(String number, String title, int credits,
                               String instructor, ObjectNode... sections) {
        ObjectNode c = MAPPER.createObjectNode();
        c.put("number", number);
        c.put("title", title);
        c.put("credits", credits);
        c.put("instructor", instructor);
        ArrayNode arr = MAPPER.createArrayNode();
        for (ObjectNode s : sections) arr.add(s);
        c.set("sections", arr);
        return c;
    }

    private ObjectNode section(String name, int enrollment,
                                String room, String schedule) {
        ObjectNode s = MAPPER.createObjectNode();
        s.put("section", name);
        s.put("enrollment", enrollment);
        s.put("room", room);
        s.put("schedule", schedule);
        return s;
    }

    // -----------------------------------------------------------------------
    // Handler installation (same pattern as AutoLayoutController)
    // -----------------------------------------------------------------------

    private void installSortHandlers(javafx.scene.Parent root,
                                     ColumnSortHandler handler) {
        for (javafx.scene.Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof com.chiralbehaviors.layout.table.TableHeader th) {
                var paths = new ArrayList<SchemaPath>();
                for (javafx.scene.Node col : th.getChildren()) {
                    if (col.getUserData() instanceof SchemaPath sp) {
                        paths.add(sp);
                    }
                }
                if (!paths.isEmpty()) {
                    handler.install(th, paths);
                    handler.updateIndicators(th, paths);
                }
            }
            if (child instanceof javafx.scene.Parent p) {
                installSortHandlers(p, handler);
            }
        }
    }

    private void installResizeHandlers(javafx.scene.Parent root) {
        for (javafx.scene.Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof com.chiralbehaviors.layout.table.TableHeader th) {
                for (javafx.scene.Node col : th.getChildren()) {
                    if (col.getUserData() instanceof SchemaPath sp) {
                        installColumnResizeHandler(col, sp);
                    }
                }
            }
            if (child instanceof javafx.scene.Parent p) {
                installResizeHandlers(p);
            }
        }
    }

    private void installColumnResizeHandler(javafx.scene.Node columnNode,
                                             SchemaPath path) {
        final double[] dragState = {0, -1};

        columnNode.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            double rightEdge = columnNode.getBoundsInLocal().getMaxX();
            columnNode.setCursor(
                Math.abs(e.getX() - rightEdge) <= GRAB_ZONE
                    ? javafx.scene.Cursor.H_RESIZE
                    : javafx.scene.Cursor.DEFAULT);
        });

        columnNode.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            double rightEdge = columnNode.getBoundsInLocal().getMaxX();
            if (Math.abs(e.getX() - rightEdge) <= GRAB_ZONE) {
                dragState[0] = e.getScreenX();
                dragState[1] = columnNode.getBoundsInLocal().getWidth();
                e.consume();
            }
        });

        columnNode.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (dragState[1] >= 0) {
                double delta = e.getScreenX() - dragState[0];
                double newWidth = Math.max(20.0, dragState[1] + delta);
                columnNode.setStyle(
                    "-fx-min-width: " + newWidth
                    + "; -fx-pref-width: " + newWidth
                    + "; -fx-max-width: " + newWidth + ";");
                e.consume();
            }
        });

        columnNode.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (dragState[1] >= 0) {
                double delta = e.getScreenX() - dragState[0];
                columnNode.setStyle("");
                if (Math.abs(delta) > 1.0) {
                    double newWidth = Math.max(20.0, dragState[1] + delta);
                    queryState.setColumnWidth(path, newWidth);
                }
                dragState[1] = -1;
                e.consume();
            }
        });
    }

    private boolean isRelationPath(SchemaPath path) {
        SchemaNode root = layout.getRoot();
        if (root == null) return false;
        SchemaNode current = root;
        for (int i = 1; i < path.segments().size(); i++) {
            if (current instanceof Relation r) {
                SchemaNode child = r.getChild(path.segments().get(i));
                if (child == null) return false;
                current = child;
            } else {
                return false;
            }
        }
        return current instanceof Relation;
    }

    /** IDE entry point (avoids JavaFX module loading issues). */
    public static class Main {
        public static void main(String[] args) {
            StandaloneDemo.main(args);
        }
    }
}
