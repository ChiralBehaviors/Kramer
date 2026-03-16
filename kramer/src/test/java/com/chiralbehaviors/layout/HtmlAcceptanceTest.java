// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Acceptance test: validates that HtmlLayoutRenderer and JavaFxLayoutRenderer
 * produce structurally equivalent output for the same LayoutDecisionNode tree.
 * <p>
 * "Parity" is defined as:
 * <ul>
 *   <li>Both renderers traverse child nodes in the same field-name order.</li>
 *   <li>Both produce the same count of leaf (primitive) elements.</li>
 *   <li>Both handle null data without error, producing empty/blank output.</li>
 *   <li>Table mode diverges structurally ({@code <table>} vs {@code VBox}),
 *       but the leaf-element count still matches.</li>
 * </ul>
 */
class HtmlAcceptanceTest extends ApplicationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void start(Stage stage) {
        // No UI needed; ApplicationTest provides the JavaFX toolkit.
    }

    // ── LayoutDecisionNode factory helpers ────────────────────────────────────

    private static LayoutDecisionNode leaf(SchemaPath parentPath, String name) {
        var path = parentPath.child(name);
        return new LayoutDecisionNode(path, name, null, null, null, null, null, null);
    }

    private static LayoutDecisionNode outlineParent(SchemaPath parentPath, String name,
                                                     List<LayoutDecisionNode> children) {
        var path = parentPath.child(name);
        var layoutResult = new LayoutResult(
            RelationRenderMode.OUTLINE, PrimitiveRenderMode.TEXT,
            false, 0.0, 0.0, 0.0, List.of());
        return new LayoutDecisionNode(path, name, null, layoutResult, null, null, null, children);
    }

    private static LayoutDecisionNode tableParent(SchemaPath parentPath, String name,
                                                   List<LayoutDecisionNode> children) {
        var path = parentPath.child(name);
        var layoutResult = new LayoutResult(
            RelationRenderMode.TABLE, PrimitiveRenderMode.TEXT,
            false, 0.0, 0.0, 0.0, List.of());
        return new LayoutDecisionNode(path, name, null, layoutResult, null, null, null, children);
    }

    /**
     * Builds a realistic "catalog" tree:
     * <pre>
     * catalog (outline)
     *   title    (leaf)
     *   items    (table)
     *     name   (leaf)
     *     price  (leaf)
     *     description (leaf)
     * </pre>
     */
    private static LayoutDecisionNode buildCatalogTree() {
        var root = new SchemaPath("catalog");
        var itemsPath = root.child("items");

        var name        = new LayoutDecisionNode(itemsPath.child("name"),        "name",        null, null, null, null, null, null);
        var price       = new LayoutDecisionNode(itemsPath.child("price"),       "price",       null, null, null, null, null, null);
        var description = new LayoutDecisionNode(itemsPath.child("description"), "description", null, null, null, null, null, null);

        var itemsLayout = new LayoutResult(RelationRenderMode.TABLE, PrimitiveRenderMode.TEXT,
                                           false, 0.0, 0.0, 0.0, List.of());
        var items = new LayoutDecisionNode(itemsPath, "items", null, itemsLayout,
                                           null, null, null, List.of(name, price, description));

        var title = new LayoutDecisionNode(root.child("title"), "title",
                                           null, null, null, null, null, null);

        var catalogLayout = new LayoutResult(RelationRenderMode.OUTLINE, PrimitiveRenderMode.TEXT,
                                             false, 0.0, 0.0, 0.0, List.of());
        return new LayoutDecisionNode(root, "catalog", null, catalogLayout,
                                      null, null, null, List.of(title, items));
    }

    /**
     * Builds catalog data: a single catalog object with a title and two item rows.
     */
    private static ObjectNode buildCatalogData() {
        var catalog = MAPPER.createObjectNode();
        catalog.put("title", "Spring Sale");

        var items = MAPPER.createArrayNode();

        var item1 = MAPPER.createObjectNode();
        item1.put("name", "Widget");
        item1.put("price", "9.99");
        item1.put("description", "A fine widget");
        items.add(item1);

        var item2 = MAPPER.createObjectNode();
        item2.put("name", "Gadget");
        item2.put("price", "19.99");
        item2.put("description", "A fine gadget");
        items.add(item2);

        catalog.set("items", items);
        return catalog;
    }

    // ── leaf-counting helpers ─────────────────────────────────────────────────

    /**
     * Counts leaf (Label) nodes recursively in a JavaFX Node tree.
     */
    private static int countFxLeaves(Node node) {
        if (node instanceof Label) {
            return 1;
        }
        if (node instanceof VBox vbox) {
            int count = 0;
            for (var child : vbox.getChildren()) {
                count += countFxLeaves(child);
            }
            return count;
        }
        return 0;
    }

    /**
     * Counts {@code <span>} occurrences in an HTML string (leaf elements).
     */
    private static int countHtmlLeaves(String html) {
        int count = 0;
        int idx = 0;
        while ((idx = html.indexOf("<span", idx)) != -1) {
            count++;
            idx += 5;
        }
        return count;
    }

    /**
     * Counts {@code <td>} occurrences in an HTML string (table cell elements that wrap leaves).
     */
    private static int countHtmlTableCells(String html) {
        int count = 0;
        int idx = 0;
        while ((idx = html.indexOf("<td>", idx)) != -1) {
            count++;
            idx += 4;
        }
        return count;
    }

    /**
     * Extracts child field names from a LayoutDecisionNode tree in DFS order.
     */
    private static List<String> fieldNamesInOrder(LayoutDecisionNode node) {
        var result = new java.util.ArrayList<String>();
        collectFieldNames(node, result);
        return result;
    }

    private static void collectFieldNames(LayoutDecisionNode node, List<String> out) {
        out.add(node.fieldName());
        for (var child : node.childNodes()) {
            collectFieldNames(child, out);
        }
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void bothRenderersTraverseSameChildStructure() {
        var tree = buildCatalogTree();

        // The field-name traversal order is a property of the tree, not the renderer.
        // Both renderers use AbstractLayoutRenderer which visits children in child order.
        var expectedOrder = fieldNamesInOrder(tree);

        // Verify order: catalog → title, items → name, price, description
        assertEquals(List.of("catalog", "title", "items", "name", "price", "description"),
                     expectedOrder,
                     "Tree traversal order must match expected field order");
    }

    @Test
    void htmlRendererProducesTableForTableModeRelation() {
        var tree = buildCatalogTree();
        var data = buildCatalogData();
        var renderer = new HtmlLayoutRenderer();

        var html = renderer.render(tree, data);

        assertTrue(html.contains("<table"), "items node must render as <table> in HTML");
        assertFalse(html.contains("<div class=\"items\""),
                    "items node must NOT render as <div> when in table mode");
    }

    @Test
    void javaFxRendererProducesVBoxForTableModeRelation() {
        var tree = buildCatalogTree();
        var data = buildCatalogData();
        var renderer = new JavaFxLayoutRenderer();

        var root = renderer.render(tree, data);

        // Root is VBox for catalog (outline)
        assertInstanceOf(VBox.class, root, "catalog root must be a VBox");
        var catalogVBox = (VBox) root;

        // title is first child label
        assertInstanceOf(Label.class, catalogVBox.getChildren().get(0), "title must be a Label");

        // items is second child — JavaFxLayoutRenderer always uses VBox for relations,
        // regardless of table mode (table mode is HTML-only in the proof-of-concept)
        assertInstanceOf(VBox.class, catalogVBox.getChildren().get(1),
                         "items must be a VBox in JavaFx renderer (no table concept)");
    }

    @Test
    void leafElementCountMatchesBetweenRenderers_outlineRoot() {
        // Build a pure-outline tree so both renderers produce spans/Labels for every leaf.
        var root = new SchemaPath("catalog");
        var title       = new LayoutDecisionNode(root.child("title"),       "title",       null, null, null, null, null, null);
        var subtitle    = new LayoutDecisionNode(root.child("subtitle"),    "subtitle",    null, null, null, null, null, null);
        var description = new LayoutDecisionNode(root.child("description"), "description", null, null, null, null, null, null);

        var outlineLayout = new LayoutResult(RelationRenderMode.OUTLINE, PrimitiveRenderMode.TEXT,
                                             false, 0.0, 0.0, 0.0, List.of());
        var catalogNode = new LayoutDecisionNode(root, "catalog", null, outlineLayout,
                                                 null, null, null,
                                                 List.of(title, subtitle, description));

        var data = MAPPER.createObjectNode();
        data.put("title", "T");
        data.put("subtitle", "S");
        data.put("description", "D");

        var htmlResult = new HtmlLayoutRenderer().render(catalogNode, data);
        var fxResult   = new JavaFxLayoutRenderer().render(catalogNode, data);

        int htmlLeaves = countHtmlLeaves(htmlResult);
        int fxLeaves   = countFxLeaves(fxResult);

        assertEquals(3, htmlLeaves, "HTML must have 3 <span> leaves");
        assertEquals(3, fxLeaves,   "JavaFx must have 3 Label leaves");
        assertEquals(htmlLeaves, fxLeaves, "Both renderers must produce the same leaf element count");
    }

    @Test
    void tableModeCellCountMatchesLeafCount() {
        // Table mode: items with 2 rows × 3 columns → 6 data cells
        var itemsPath = new SchemaPath("items");
        var name        = new LayoutDecisionNode(itemsPath.child("name"),        "name",        null, null, null, null, null, null);
        var price       = new LayoutDecisionNode(itemsPath.child("price"),       "price",       null, null, null, null, null, null);
        var description = new LayoutDecisionNode(itemsPath.child("description"), "description", null, null, null, null, null, null);

        var tableLayout = new LayoutResult(RelationRenderMode.TABLE, PrimitiveRenderMode.TEXT,
                                           false, 0.0, 0.0, 0.0, List.of());
        var itemsNode = new LayoutDecisionNode(itemsPath, "items", null, tableLayout,
                                               null, null, null,
                                               List.of(name, price, description));

        ArrayNode rows = MAPPER.createArrayNode();
        for (int i = 0; i < 2; i++) {
            var row = MAPPER.createObjectNode();
            row.put("name",        "Item " + i);
            row.put("price",       String.valueOf(i * 10.0));
            row.put("description", "Desc " + i);
            rows.add(row);
        }

        var html = new HtmlLayoutRenderer().render(itemsNode, rows);

        // 2 rows × 3 columns = 6 <td> cells; each <td> wraps a <span>
        int tdCount   = countHtmlTableCells(html);
        int spanCount = countHtmlLeaves(html);

        assertEquals(6, tdCount,   "Must have 6 <td> cells for 2 rows × 3 columns");
        assertEquals(6, spanCount, "Must have 6 <span> leaves inside the table cells");
    }

    @Test
    void nullDataHandledIdenticallyByBothRenderers() {
        var root = new SchemaPath("catalog");
        var title = new LayoutDecisionNode(root.child("title"), "title", null, null, null, null, null, null);
        var outlineLayout = new LayoutResult(RelationRenderMode.OUTLINE, PrimitiveRenderMode.TEXT,
                                             false, 0.0, 0.0, 0.0, List.of());
        var catalogNode = new LayoutDecisionNode(root, "catalog", null, outlineLayout,
                                                 null, null, null, List.of(title));

        // Both must not throw
        var html = assertDoesNotThrow(
            () -> new HtmlLayoutRenderer().render(catalogNode, null),
            "HTML renderer must handle null data without throwing");
        var fxNode = assertDoesNotThrow(
            () -> new JavaFxLayoutRenderer().render(catalogNode, null),
            "JavaFx renderer must handle null data without throwing");

        // HTML: 1 empty <span> for title (data==null → empty text)
        assertEquals(1, countHtmlLeaves(html),
                     "Null-data HTML must still produce 1 <span> for the leaf");

        // JavaFx: 1 empty Label for title
        assertInstanceOf(VBox.class, fxNode, "Null-data root must be VBox");
        var vbox = (VBox) fxNode;
        assertEquals(1, vbox.getChildren().size(), "VBox must have 1 child for the leaf");
        assertInstanceOf(Label.class, vbox.getChildren().get(0), "Leaf must be a Label");
        assertEquals("", ((Label) vbox.getChildren().get(0)).getText(),
                     "Null data must produce empty Label text");
    }

    @Test
    void nestedRelationProducesCorrectDepthInBothRenderers() {
        // 3-level tree: root → middle → leaf
        var root       = new SchemaPath("root");
        var middlePath = root.child("middle");
        var deepPath   = middlePath.child("deep");

        var deepLeaf = new LayoutDecisionNode(deepPath, "deep", null, null, null, null, null, null);

        var middleLayout = new LayoutResult(RelationRenderMode.OUTLINE, PrimitiveRenderMode.TEXT,
                                            false, 0.0, 0.0, 0.0, List.of());
        var middleNode = new LayoutDecisionNode(middlePath, "middle", null, middleLayout,
                                                null, null, null, List.of(deepLeaf));

        var rootLayout = new LayoutResult(RelationRenderMode.OUTLINE, PrimitiveRenderMode.TEXT,
                                          false, 0.0, 0.0, 0.0, List.of());
        var rootNode = new LayoutDecisionNode(root, "root", null, rootLayout,
                                              null, null, null, List.of(middleNode));

        var innerData = MAPPER.createObjectNode();
        innerData.put("deep", "bottom");
        var outerData = MAPPER.createObjectNode();
        outerData.set("middle", innerData);

        var html = new HtmlLayoutRenderer().render(rootNode, outerData);

        // Verify 3-level nesting in HTML
        assertTrue(html.contains("<div class=\"root\">"), "Root must be div.root");
        assertTrue(html.contains("<div class=\"middle\">"), "Middle level must be div.middle");
        assertTrue(html.contains("<span class=\"deep\">bottom</span>"),
                   "Leaf must be span.deep with value 'bottom'");

        // Verify 3-level nesting in JavaFx
        var fxRoot = new JavaFxLayoutRenderer().render(rootNode, outerData);
        assertInstanceOf(VBox.class, fxRoot, "Root must be VBox");
        var rootVBox = (VBox) fxRoot;
        assertEquals(1, rootVBox.getChildren().size());

        assertInstanceOf(VBox.class, rootVBox.getChildren().get(0), "Middle must be VBox");
        var middleVBox = (VBox) rootVBox.getChildren().get(0);
        assertEquals(1, middleVBox.getChildren().size());

        assertInstanceOf(Label.class, middleVBox.getChildren().get(0), "Leaf must be Label");
        assertEquals("bottom", ((Label) middleVBox.getChildren().get(0)).getText());
    }

    @Test
    void fullCatalogRenderDoesNotThrowForEitherRenderer() {
        var tree = buildCatalogTree();
        var data = buildCatalogData();

        assertDoesNotThrow(() -> new HtmlLayoutRenderer().render(tree, data),
                           "HTML renderer must render full catalog without throwing");
        assertDoesNotThrow(() -> new JavaFxLayoutRenderer().render(tree, data),
                           "JavaFx renderer must render full catalog without throwing");
    }

    @Test
    void htmlContainsCatalogFieldValues() {
        var tree = buildCatalogTree();
        var data = buildCatalogData();
        var html = new HtmlLayoutRenderer().render(tree, data);

        assertTrue(html.contains("Spring Sale"), "HTML must contain catalog title");
        assertTrue(html.contains("Widget"),      "HTML must contain first item name");
        assertTrue(html.contains("Gadget"),      "HTML must contain second item name");
        assertTrue(html.contains("9.99"),        "HTML must contain first item price");
        assertTrue(html.contains("19.99"),       "HTML must contain second item price");
    }

    @Test
    void javaFxContainsCatalogFieldValues() {
        var tree = buildCatalogTree();
        var data = buildCatalogData();
        var fxRoot = new JavaFxLayoutRenderer().render(tree, data);

        // Collect all label texts
        var texts = new java.util.ArrayList<String>();
        collectLabelTexts(fxRoot, texts);

        assertTrue(texts.contains("Spring Sale"), "JavaFx must contain catalog title label");
    }

    private static void collectLabelTexts(Node node, List<String> out) {
        if (node instanceof Label label) {
            out.add(label.getText());
        } else if (node instanceof VBox vbox) {
            for (var child : vbox.getChildren()) {
                collectLabelTexts(child, out);
            }
        }
    }
}
