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
        var lr = node.layoutResult();
        var mr = node.measureResult();
        if (lr != null && lr.primitiveMode() == PrimitiveRenderMode.SPARKLINE
                && mr != null && mr.sparklineStats() != null
                && data != null && data.isArray()) {
            return renderSparklineSvg(cssClass, data, mr.sparklineStats());
        }
        if (data != null && data.isArray()) {
            return renderArray(cssClass, data);
        }
        String text = data != null ? escapeHtml(data.asText()) : "";
        return "<span class=\"" + cssClass + "\">" + text + "</span>";
    }

    private static String renderSparklineSvg(String cssClass, JsonNode data, SparklineStats stats) {
        int n = data.size();
        if (n == 0) {
            return "<svg class=\"sparkline " + cssClass + "\" width=\"100\" height=\"20\" viewBox=\"0 0 100 20\"></svg>";
        }

        double vMin = stats.seriesMin();
        double vMax = stats.seriesMax();
        double range = vMax - vMin;
        // Avoid division by zero when all values are equal
        if (range == 0.0) {
            range = 1.0;
        }

        double viewW = 100.0;
        double viewH = 20.0;

        // Build polyline points
        var points = new StringBuilder();
        double lastX = 0.0, lastY = 0.0;
        for (int i = 0; i < n; i++) {
            double x = (n == 1) ? viewW / 2 : (i / (double) (n - 1)) * viewW;
            double y = viewH - ((data.get(i).asDouble() - vMin) / range) * viewH;
            if (i > 0) {
                points.append(' ');
            }
            points.append(round2(x)).append(',').append(round2(y));
            lastX = x;
            lastY = y;
        }

        // IQR band: y coords are inverted (higher value = lower y)
        double yQ3 = viewH - ((stats.q3() - vMin) / range) * viewH;
        double yQ1 = viewH - ((stats.q1() - vMin) / range) * viewH;
        // yQ1 > yQ3 because y increases downward; height = yQ1 - yQ3
        double bandY = Math.min(yQ1, yQ3);
        double bandH = Math.abs(yQ1 - yQ3);

        return "<svg class=\"sparkline " + cssClass + "\" width=\"100\" height=\"20\" viewBox=\"0 0 100 20\">"
             + "<rect class=\"sparkline-band\" x=\"0\" y=\"" + round2(bandY) + "\" width=\"100\" height=\"" + round2(bandH) + "\" opacity=\"0.15\"/>"
             + "<polyline class=\"sparkline-line\" points=\"" + points + "\" fill=\"none\" stroke=\"currentColor\"/>"
             + "<circle class=\"sparkline-end\" cx=\"" + round2(lastX) + "\" cy=\"" + round2(lastY) + "\" r=\"1.5\"/>"
             + "</svg>";
    }

    private static String round2(double v) {
        // Format to at most 2 decimal places, no trailing zeros
        long rounded = Math.round(v * 100);
        if (rounded % 100 == 0) {
            return String.valueOf(rounded / 100);
        } else if (rounded % 10 == 0) {
            return (rounded / 100) + "." + ((rounded % 100) / 10);
        } else {
            int intPart = (int) (rounded / 100);
            int fracPart = (int) Math.abs(rounded % 100);
            return intPart + "." + String.format("%02d", fracPart);
        }
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
