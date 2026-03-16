// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Renders a {@link LayoutDecisionNode} tree to an HTML string.
 * <p>
 * Leaf nodes become {@code <span>} elements; interior (relation) nodes in
 * outline mode become {@code <div>} elements; relation nodes in table mode
 * become {@code <table>/<thead>/<tbody>/<tr>/<th>/<td>} elements.
 * Array leaf nodes become {@code <ul>/<li>} lists.
 * All text content is HTML-escaped to prevent XSS.
 * CSS class names are derived from the field name via
 * {@link SchemaPath#sanitize(String)}.
 */
public class HtmlLayoutRenderer extends AbstractLayoutRenderer<String> {

    @Override
    public String render(LayoutDecisionNode node, JsonNode data) {
        if (!node.childNodes().isEmpty() && isTableMode(node)) {
            return renderTable(node, data);
        }
        return super.render(node, data);
    }

    @Override
    protected String renderPrimitive(LayoutDecisionNode node, JsonNode data) {
        String cssClass = SchemaPath.sanitize(node.fieldName());
        if (data != null && data.isArray()) {
            return renderArray(cssClass, data);
        }
        String text = data != null ? escapeHtml(data.asText()) : "";
        return "<span class=\"" + cssClass + "\">" + text + "</span>";
    }

    @Override
    protected String renderRelation(LayoutDecisionNode node, JsonNode data, List<String> children) {
        String cssClass = SchemaPath.sanitize(node.fieldName());
        return "<div class=\"" + cssClass + "\">" + String.join("", children) + "</div>";
    }

    private String renderTable(LayoutDecisionNode node, JsonNode data) {
        String cssClass = SchemaPath.sanitize(node.fieldName());
        var sb = new StringBuilder();
        sb.append("<table class=\"").append(cssClass).append("\">");

        // thead
        sb.append("<thead><tr>");
        for (var child : node.childNodes()) {
            sb.append("<th>").append(escapeHtml(child.fieldName())).append("</th>");
        }
        sb.append("</tr></thead>");

        // tbody — data is an ArrayNode of row objects
        sb.append("<tbody>");
        if (data != null && data.isArray()) {
            for (JsonNode row : data) {
                sb.append("<tr>");
                for (var child : node.childNodes()) {
                    JsonNode cellData = row.get(child.fieldName());
                    sb.append("<td>").append(render(child, cellData)).append("</td>");
                }
                sb.append("</tr>");
            }
        }
        sb.append("</tbody>");

        sb.append("</table>");
        return sb.toString();
    }

    private static boolean isTableMode(LayoutDecisionNode node) {
        var lr = node.layoutResult();
        return lr != null && lr.useTable();
    }

    private static String renderArray(String cssClass, JsonNode array) {
        var sb = new StringBuilder();
        sb.append("<ul class=\"").append(cssClass).append("\">");
        for (JsonNode item : array) {
            sb.append("<li>").append(escapeHtml(item.asText())).append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
