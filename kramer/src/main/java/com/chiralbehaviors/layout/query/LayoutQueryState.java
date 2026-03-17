// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chiralbehaviors.layout.DefaultLayoutStylesheet;
import com.chiralbehaviors.layout.LayoutPropertyKeys;
import com.chiralbehaviors.layout.LayoutStylesheet;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Layer 2 Query Model — typed abstraction over {@link LayoutStylesheet} that
 * represents user intent per {@link SchemaPath}.
 * <p>
 * Implements {@code LayoutStylesheet} by delegating to a wrapped
 * {@link DefaultLayoutStylesheet}. Typed setters update the inner stylesheet
 * and fire change listeners. This eliminates dual representation —
 * {@code LayoutQueryState} IS the stylesheet that {@code AutoLayout} receives.
 * <p>
 * See RDR-026 for full design rationale.
 *
 * @author hhildebrand
 */
public class LayoutQueryState implements LayoutStylesheet {

    private static final String PIVOT_FIELD = LayoutPropertyKeys.PIVOT_FIELD;

    private final DefaultLayoutStylesheet inner;
    private final Map<SchemaPath, FieldState> fieldStates = new HashMap<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private boolean suppressing = false;
    private boolean mutatedDuringSuppression = false;

    public LayoutQueryState(Style style) {
        this.inner = new DefaultLayoutStylesheet(style);
    }

    // --- LayoutStylesheet delegation ---

    @Override
    public long getVersion() {
        return inner.getVersion();
    }

    @Override
    public double getDouble(SchemaPath path, String property, double defaultValue) {
        return inner.getDouble(path, property, defaultValue);
    }

    @Override
    public int getInt(SchemaPath path, String property, int defaultValue) {
        return inner.getInt(path, property, defaultValue);
    }

    @Override
    public String getString(SchemaPath path, String property, String defaultValue) {
        return inner.getString(path, property, defaultValue);
    }

    @Override
    public boolean getBoolean(SchemaPath path, String property, boolean defaultValue) {
        return inner.getBoolean(path, property, defaultValue);
    }

    @Override
    public PrimitiveStyle primitiveStyle(SchemaPath path) {
        return inner.primitiveStyle(path);
    }

    @Override
    public RelationStyle relationStyle(SchemaPath path) {
        return inner.relationStyle(path);
    }

    // --- FieldState access ---

    /**
     * Returns the current {@link FieldState} for the given path. Each field is
     * {@code null} if no override has been set. Never returns {@code null}.
     */
    public FieldState getFieldState(SchemaPath path) {
        return fieldStates.getOrDefault(path, FieldState.EMPTY);
    }

    /**
     * Convenience: returns the effective visible value for the given path.
     * Returns {@code true} (the default) when no override is set.
     */
    public boolean getVisibleOrDefault(SchemaPath path) {
        Boolean v = getFieldState(path).visible();
        return v != null ? v : true;
    }

    // --- Typed setters ---

    public void setVisible(SchemaPath path, Boolean value) {
        setProperty(path, LayoutPropertyKeys.VISIBLE, value,
            (fs, v) -> new FieldState(asBool(v), fs.renderMode(), fs.hideIfEmpty(),
                fs.sortFields(), fs.filterExpression(), fs.formulaExpression(),
                fs.aggregateExpression(), fs.sortExpression(), fs.pivotField()));
    }

    public void setRenderMode(SchemaPath path, String value) {
        setProperty(path, LayoutPropertyKeys.RENDER_MODE, value,
            (fs, v) -> new FieldState(fs.visible(), asStr(v), fs.hideIfEmpty(),
                fs.sortFields(), fs.filterExpression(), fs.formulaExpression(),
                fs.aggregateExpression(), fs.sortExpression(), fs.pivotField()));
    }

    public void setHideIfEmpty(SchemaPath path, Boolean value) {
        setProperty(path, LayoutPropertyKeys.HIDE_IF_EMPTY, value,
            (fs, v) -> new FieldState(fs.visible(), fs.renderMode(), asBool(v),
                fs.sortFields(), fs.filterExpression(), fs.formulaExpression(),
                fs.aggregateExpression(), fs.sortExpression(), fs.pivotField()));
    }

    public void setSortFields(SchemaPath path, String value) {
        setProperty(path, LayoutPropertyKeys.SORT_FIELDS, value,
            (fs, v) -> new FieldState(fs.visible(), fs.renderMode(), fs.hideIfEmpty(),
                asStr(v), fs.filterExpression(), fs.formulaExpression(),
                fs.aggregateExpression(), fs.sortExpression(), fs.pivotField()));
    }

    public void setFilterExpression(SchemaPath path, String value) {
        setProperty(path, LayoutPropertyKeys.FILTER_EXPRESSION, value,
            (fs, v) -> new FieldState(fs.visible(), fs.renderMode(), fs.hideIfEmpty(),
                fs.sortFields(), asStr(v), fs.formulaExpression(),
                fs.aggregateExpression(), fs.sortExpression(), fs.pivotField()));
    }

    public void setFormulaExpression(SchemaPath path, String value) {
        setProperty(path, LayoutPropertyKeys.FORMULA_EXPRESSION, value,
            (fs, v) -> new FieldState(fs.visible(), fs.renderMode(), fs.hideIfEmpty(),
                fs.sortFields(), fs.filterExpression(), asStr(v),
                fs.aggregateExpression(), fs.sortExpression(), fs.pivotField()));
    }

    public void setAggregateExpression(SchemaPath path, String value) {
        setProperty(path, LayoutPropertyKeys.AGGREGATE_EXPRESSION, value,
            (fs, v) -> new FieldState(fs.visible(), fs.renderMode(), fs.hideIfEmpty(),
                fs.sortFields(), fs.filterExpression(), fs.formulaExpression(),
                asStr(v), fs.sortExpression(), fs.pivotField()));
    }

    public void setSortExpression(SchemaPath path, String value) {
        setProperty(path, LayoutPropertyKeys.SORT_EXPRESSION, value,
            (fs, v) -> new FieldState(fs.visible(), fs.renderMode(), fs.hideIfEmpty(),
                fs.sortFields(), fs.filterExpression(), fs.formulaExpression(),
                fs.aggregateExpression(), asStr(v), fs.pivotField()));
    }

    public void setPivotField(SchemaPath path, String value) {
        setProperty(path, PIVOT_FIELD, value,
            (fs, v) -> new FieldState(fs.visible(), fs.renderMode(), fs.hideIfEmpty(),
                fs.sortFields(), fs.filterExpression(), fs.formulaExpression(),
                fs.aggregateExpression(), fs.sortExpression(), asStr(v)));
    }

    // --- Reset ---

    /** Clear all overrides and fire change listeners. */
    public void reset() {
        fieldStates.clear();
        inner.clearOverrides();
        fireChangeListeners();
    }

    // --- Change listener ---

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    /**
     * Execute a batch of mutations with notifications suppressed. Fires exactly
     * one change notification after the batch completes (if any mutations
     * occurred). No notifications fire during the batch.
     */
    public void suppressNotifications(Runnable batch) {
        suppressing = true;
        mutatedDuringSuppression = false;
        try {
            batch.run();
        } finally {
            suppressing = false;
            if (mutatedDuringSuppression) {
                fireChangeListeners();
            }
            mutatedDuringSuppression = false;
        }
    }

    // --- Serialization ---

    /**
     * Serialize all non-default FieldState entries to a Jackson ObjectNode.
     * Keys are {@code SchemaPath.toString()} (format: "segment1/segment2/...").
     * Only non-null properties are included per path.
     */
    public ObjectNode toJson() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        for (var entry : fieldStates.entrySet()) {
            ObjectNode pathNode = fieldStateToJson(entry.getValue());
            if (pathNode.size() > 0) {
                root.set(entry.getKey().toString(), pathNode);
            }
        }
        return root;
    }

    /**
     * Restore state from a previously serialized ObjectNode. Clears all
     * existing overrides first. Notifications are suppressed during
     * restoration — exactly one change notification fires on completion.
     */
    public void fromJson(ObjectNode node) {
        suppressNotifications(() -> {
            reset();
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String pathStr = entry.getKey();
                JsonNode pathNode = entry.getValue();
                if (!pathNode.isObject()) continue;

                String[] segments = pathStr.split("/");
                SchemaPath path = new SchemaPath(segments[0],
                    java.util.Arrays.copyOfRange(segments, 1, segments.length));

                ObjectNode obj = (ObjectNode) pathNode;
                if (obj.has("visible")) setVisible(path, obj.get("visible").asBoolean());
                if (obj.has("renderMode")) setRenderMode(path, obj.get("renderMode").asText());
                if (obj.has("hideIfEmpty")) setHideIfEmpty(path, obj.get("hideIfEmpty").asBoolean());
                if (obj.has("sortFields")) setSortFields(path, obj.get("sortFields").asText());
                if (obj.has("filterExpression")) setFilterExpression(path, obj.get("filterExpression").asText());
                if (obj.has("formulaExpression")) setFormulaExpression(path, obj.get("formulaExpression").asText());
                if (obj.has("aggregateExpression")) setAggregateExpression(path, obj.get("aggregateExpression").asText());
                if (obj.has("sortExpression")) setSortExpression(path, obj.get("sortExpression").asText());
                if (obj.has("pivotField")) setPivotField(path, obj.get("pivotField").asText());
            }
        });
    }

    private static ObjectNode fieldStateToJson(FieldState fs) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (fs.visible() != null) node.put("visible", fs.visible());
        if (fs.renderMode() != null) node.put("renderMode", fs.renderMode());
        if (fs.hideIfEmpty() != null) node.put("hideIfEmpty", fs.hideIfEmpty());
        if (fs.sortFields() != null) node.put("sortFields", fs.sortFields());
        if (fs.filterExpression() != null) node.put("filterExpression", fs.filterExpression());
        if (fs.formulaExpression() != null) node.put("formulaExpression", fs.formulaExpression());
        if (fs.aggregateExpression() != null) node.put("aggregateExpression", fs.aggregateExpression());
        if (fs.sortExpression() != null) node.put("sortExpression", fs.sortExpression());
        if (fs.pivotField() != null) node.put("pivotField", fs.pivotField());
        return node;
    }

    // --- Internal ---

    @FunctionalInterface
    private interface FieldStateUpdater {
        FieldState apply(FieldState current, Object value);
    }

    private void setProperty(SchemaPath path, String propertyKey, Object value,
                              FieldStateUpdater updater) {
        FieldState current = fieldStates.getOrDefault(path, FieldState.EMPTY);
        FieldState updated = updater.apply(current, value);

        if (updated.equals(FieldState.EMPTY)) {
            fieldStates.remove(path);
        } else {
            fieldStates.put(path, updated);
        }

        if (value != null) {
            inner.setOverride(path, propertyKey, value);
        } else {
            // Setting null clears the override for this specific key.
            // DefaultLayoutStylesheet doesn't have per-key clear, so we
            // rebuild all overrides for this path from the updated FieldState.
            rebuildOverrides(path, updated);
        }

        fireChangeListeners();
    }

    private void rebuildOverrides(SchemaPath path, FieldState fs) {
        // Clear all overrides for this path by clearing and re-adding
        // We must clear globally and rebuild all paths — but that's expensive.
        // Instead, set the specific key to its default to "undo" it.
        // DefaultLayoutStylesheet doesn't support per-key removal, so we
        // use clearOverrides and rebuild. For simplicity, since setting null
        // is infrequent, we do a full rebuild.
        inner.clearOverrides();
        for (var entry : fieldStates.entrySet()) {
            applyFieldState(entry.getKey(), entry.getValue());
        }
    }

    private void applyFieldState(SchemaPath p, FieldState fs) {
        if (fs.visible() != null) inner.setOverride(p, LayoutPropertyKeys.VISIBLE, fs.visible());
        if (fs.renderMode() != null) inner.setOverride(p, LayoutPropertyKeys.RENDER_MODE, fs.renderMode());
        if (fs.hideIfEmpty() != null) inner.setOverride(p, LayoutPropertyKeys.HIDE_IF_EMPTY, fs.hideIfEmpty());
        if (fs.sortFields() != null) inner.setOverride(p, LayoutPropertyKeys.SORT_FIELDS, fs.sortFields());
        if (fs.filterExpression() != null) inner.setOverride(p, LayoutPropertyKeys.FILTER_EXPRESSION, fs.filterExpression());
        if (fs.formulaExpression() != null) inner.setOverride(p, LayoutPropertyKeys.FORMULA_EXPRESSION, fs.formulaExpression());
        if (fs.aggregateExpression() != null) inner.setOverride(p, LayoutPropertyKeys.AGGREGATE_EXPRESSION, fs.aggregateExpression());
        if (fs.sortExpression() != null) inner.setOverride(p, LayoutPropertyKeys.SORT_EXPRESSION, fs.sortExpression());
        if (fs.pivotField() != null) inner.setOverride(p, PIVOT_FIELD, fs.pivotField());
    }

    private void fireChangeListeners() {
        if (suppressing) {
            mutatedDuringSuppression = true;
            return;
        }
        for (var listener : List.copyOf(changeListeners)) {
            listener.run();
        }
    }

    private static Boolean asBool(Object v) {
        return v instanceof Boolean b ? b : null;
    }

    private static String asStr(Object v) {
        return v instanceof String s ? s : null;
    }
}
