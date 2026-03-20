// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Snapshot of a rendered layout's scene graph, captured at a specific width.
 * All values are captured at construction time — no live references.
 *
 * @param width        the width at which the layout was rendered
 * @param tableMode    true if the root relation chose table mode
 * @param labels       all labeled text found in the scene graph with widths
 * @param fieldWidths  justified widths per child (field name → width)
 * @param fieldNames   the set of schema field names (for filtering headers)
 */
public record LayoutTestResult(
    double width,
    boolean tableMode,
    List<LabelEntry> labels,
    Map<String, Double> fieldWidths,
    Set<String> fieldNames
) {

    public record LabelEntry(String text, double width) {}

    /** All rendered label texts. */
    public List<String> getRenderedTexts() {
        return labels.stream().map(LabelEntry::text).toList();
    }

    /** Label texts that are NOT schema field names — i.e., actual data values. */
    public List<String> getDataTexts() {
        return labels.stream()
            .map(LabelEntry::text)
            .filter(t -> !fieldNames.contains(t))
            .toList();
    }

    /** Labels where rendered width is ≤ 0. */
    public List<LabelEntry> getZeroWidthLabels() {
        return labels.stream()
            .filter(l -> l.width() <= 0)
            .toList();
    }

    /** Labels containing "..." indicating truncation. */
    public List<LabelEntry> getTruncatedLabels() {
        return labels.stream()
            .filter(l -> l.text().contains("...") || l.text().contains("\u2026"))
            .toList();
    }

    /** Data labels (not field names) with width ≤ 0. */
    public List<LabelEntry> getZeroWidthDataLabels() {
        return labels.stream()
            .filter(l -> l.width() <= 0)
            .filter(l -> !fieldNames.contains(l.text()))
            .toList();
    }

    /** Check if a specific data value (or prefix) appears in any rendered label. */
    public boolean containsData(String valueOrPrefix) {
        return labels.stream().anyMatch(l -> l.text().contains(valueOrPrefix));
    }

    /** Formatted diagnostic dump for assertion messages. */
    public String dump() {
        var sb = new StringBuilder();
        sb.append(String.format("width=%.0f mode=%s labels=%d data=%d\n",
            width, tableMode ? "TABLE" : "OUTLINE",
            labels.size(), getDataTexts().size()));
        labels.stream().limit(30).forEach(l ->
            sb.append(String.format("  '%s' (w=%.0f)\n", l.text(), l.width())));
        if (labels.size() > 30) {
            sb.append(String.format("  ... (%d more)\n", labels.size() - 30));
        }
        return sb.toString();
    }
}
