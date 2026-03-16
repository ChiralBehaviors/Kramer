// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.HashMap;
import java.util.Map;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;

/**
 * Default implementation that delegates to the existing {@link Style} for
 * CSS-derived styles and supports per-path property overrides.
 */
public class DefaultLayoutStylesheet implements LayoutStylesheet {

    private final Style style;
    private final Map<SchemaPath, Map<String, Object>> overrides = new HashMap<>();
    private long version = 0;

    public DefaultLayoutStylesheet(Style style) {
        this.style = style;
    }

    @Override
    public long getVersion() {
        return version;
    }

    public void setOverride(SchemaPath path, String property, Object value) {
        overrides.computeIfAbsent(path, k -> new HashMap<>()).put(property, value);
        version++;
    }

    public void clearOverrides() {
        overrides.clear();
        version++;
    }

    @Override
    public double getDouble(SchemaPath path, String property,
                            double defaultValue) {
        var pathOverrides = overrides.get(path);
        if (pathOverrides != null && pathOverrides.containsKey(property)) {
            return ((Number) pathOverrides.get(property)).doubleValue();
        }
        return defaultValue;
    }

    @Override
    public int getInt(SchemaPath path, String property, int defaultValue) {
        var pathOverrides = overrides.get(path);
        if (pathOverrides != null && pathOverrides.containsKey(property)) {
            return ((Number) pathOverrides.get(property)).intValue();
        }
        return defaultValue;
    }

    @Override
    public String getString(SchemaPath path, String property,
                            String defaultValue) {
        var pathOverrides = overrides.get(path);
        if (pathOverrides != null && pathOverrides.containsKey(property)) {
            return pathOverrides.get(property).toString();
        }
        return defaultValue;
    }

    @Override
    public boolean getBoolean(SchemaPath path, String property,
                              boolean defaultValue) {
        var pathOverrides = overrides.get(path);
        if (pathOverrides != null && pathOverrides.containsKey(property)) {
            return (Boolean) pathOverrides.get(property);
        }
        return defaultValue;
    }

    @Override
    public PrimitiveStyle primitiveStyle(SchemaPath path) {
        return style.style(new Primitive(path.leaf()));
    }

    @Override
    public RelationStyle relationStyle(SchemaPath path) {
        return style.style(new Relation(path.leaf()));
    }
}
