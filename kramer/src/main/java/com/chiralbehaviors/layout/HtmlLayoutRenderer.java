// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Renders a {@link LayoutDecisionNode} tree to an HTML string.
 * <p>
 * Leaf nodes become {@code <span>} elements; interior (relation) nodes become
 * {@code <div>} elements; array leaf nodes become {@code <ul>/<li>} lists.
 * All text content is HTML-escaped to prevent XSS.
 * CSS class names are derived from the field name via
 * {@link SchemaPath#sanitize(String)}.
 */
public class HtmlLayoutRenderer extends AbstractLayoutRenderer<String> {

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
