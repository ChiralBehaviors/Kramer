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

        // Focus the layout so keyboard shortcuts work immediately
        layout.setFocusTraversable(true);

        // Field selector starts closed — toggle via ⇧⌘F.
        // Opening it steals ~200px from the layout, which can push
        // deeply nested schemas past their readableTableWidth threshold.

        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("Kramer \u2014 Course Catalog Demo");
        stage.setScene(scene);

        // Load data after window is fully shown and sized.
        // WINDOW_SHOWN fires after the first layout pulse completes,
        // so getWidth() returns the real rendered width.
        stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWN, e -> {
            layout.setRoot(schema);
            fieldSelectorPanel.setRoot(schema);
            layout.measure(data);
            layout.updateItem(data);
            layout.requestFocus();
        });
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
        sections.addChild(new Primitive("id"));
        sections.addChild(new Primitive("enrolled"));

        Relation courses = new Relation("courses");
        courses.addChild(new Primitive("number"));
        courses.addChild(new Primitive("title"));
        courses.addChild(new Primitive("credits"));
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
            course("CS101", "Intro to Programming", 3,
                sec("A", 45), sec("B", 42), sec("C", 38)),
            course("CS201", "Data Structures", 4,
                sec("A", 35), sec("B", 30)),
            course("CS301", "Algorithms", 4,
                sec("A", 28)),
            course("CS410", "Machine Learning", 3,
                sec("A", 40), sec("B", 38))));

        depts.add(dept("Mathematics", "Hilbert Hall",
            course("MATH101", "Calculus I", 4,
                sec("A", 50), sec("B", 48), sec("C", 45)),
            course("MATH201", "Linear Algebra", 3,
                sec("A", 35)),
            course("MATH301", "Real Analysis", 3,
                sec("A", 22))));

        depts.add(dept("Physics", "Newton Building",
            course("PHYS101", "Mechanics", 4,
                sec("A", 55), sec("B", 50)),
            course("PHYS201", "E&M", 4,
                sec("A", 40)),
            course("PHYS310", "Quantum Mechanics", 3,
                sec("A", 25))));

        depts.add(dept("English", "Austen Hall",
            course("ENG101", "Composition", 3,
                sec("A", 25), sec("B", 25), sec("C", 24), sec("D", 22)),
            course("ENG201", "American Lit", 3,
                sec("A", 30)),
            course("ENG350", "Shakespeare", 3,
                sec("A", 28))));

        depts.add(dept("History", "Herodotus Hall",
            course("HIST101", "World History I", 3,
                sec("A", 60), sec("B", 55)),
            course("HIST201", "Modern Europe", 3,
                sec("A", 35)),
            course("HIST310", "Ancient Rome", 3,
                sec("A", 30))));

        depts.add(dept("Chemistry", "Mendeleev Hall",
            course("CHEM101", "General Chem", 4,
                sec("A", 48), sec("B", 45)),
            course("CHEM201", "Organic Chem", 4,
                sec("A", 32), sec("B", 30))));

        depts.add(dept("Economics", "Smith Hall",
            course("ECON101", "Microeconomics", 3,
                sec("A", 55), sec("B", 50)),
            course("ECON201", "Macroeconomics", 3,
                sec("A", 45)),
            course("ECON350", "Econometrics", 3,
                sec("A", 25))));

        depts.add(dept("Philosophy", "Plato Hall",
            course("PHIL101", "Intro to Phil", 3,
                sec("A", 40), sec("B", 35)),
            course("PHIL201", "Ethics", 3,
                sec("A", 30)),
            course("PHIL310", "Logic", 3,
                sec("A", 20))));

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
                               ObjectNode... sections) {
        ObjectNode c = MAPPER.createObjectNode();
        c.put("number", number);
        c.put("title", title);
        c.put("credits", credits);
        ArrayNode arr = MAPPER.createArrayNode();
        for (ObjectNode s : sections) arr.add(s);
        c.set("sections", arr);
        return c;
    }

    private ObjectNode sec(String id, int enrolled) {
        ObjectNode s = MAPPER.createObjectNode();
        s.put("id", id);
        s.put("enrolled", enrolled);
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
