// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;

/**
 * Abstraction for layout property lookup by schema path. Enables per-path
 * property overrides beyond what CSS alone provides.
 *
 * @see SchemaPath
 * @see DefaultLayoutStylesheet
 */
public interface LayoutStylesheet {

    double getDouble(SchemaPath path, String property, double defaultValue);

    int getInt(SchemaPath path, String property, int defaultValue);

    String getString(SchemaPath path, String property, String defaultValue);

    boolean getBoolean(SchemaPath path, String property, boolean defaultValue);

    PrimitiveStyle primitiveStyle(SchemaPath path);

    RelationStyle relationStyle(SchemaPath path);
}
