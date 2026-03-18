// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import java.util.Map;

import com.chiralbehaviors.layout.LayoutStylesheet;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;

/**
 * A {@link LayoutStylesheet} decorator that checks GraphQL directives first,
 * then delegates to an inner stylesheet for any property not covered by a
 * directive override.
 *
 * <p>Directive overrides are computed on demand via {@link DirectiveReader}
 * and {@link SchemaContext}. Numeric properties ({@code getDouble},
 * {@code getInt}) are not yet overridable via directives and always delegate
 * to the inner stylesheet.
 */
public final class DirectiveAwareStylesheet implements LayoutStylesheet {

    private final LayoutStylesheet inner;
    private final DirectiveReader  reader;
    private final SchemaContext    ctx;

    /**
     * @param inner  the wrapped stylesheet (fallback for all lookups)
     * @param reader extracts directive overrides from Field AST nodes
     * @param ctx    the schema context produced by {@link GraphQlUtil#buildContext}
     */
    public DirectiveAwareStylesheet(LayoutStylesheet inner,
                                    DirectiveReader reader,
                                    SchemaContext ctx) {
        this.inner  = inner;
        this.reader = reader;
        this.ctx    = ctx;
    }

    @Override
    public long getVersion() {
        return inner.getVersion();
    }

    @Override
    public boolean getBoolean(SchemaPath path, String property, boolean defaultValue) {
        Map<String, Object> overrides = reader.readDirectives(ctx, path);
        if (overrides.containsKey(property)) {
            return (Boolean) overrides.get(property);
        }
        return inner.getBoolean(path, property, defaultValue);
    }

    @Override
    public String getString(SchemaPath path, String property, String defaultValue) {
        Map<String, Object> overrides = reader.readDirectives(ctx, path);
        if (overrides.containsKey(property)) {
            return overrides.get(property).toString();
        }
        return inner.getString(path, property, defaultValue);
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
    public PrimitiveStyle primitiveStyle(SchemaPath path) {
        return inner.primitiveStyle(path);
    }

    @Override
    public RelationStyle relationStyle(SchemaPath path) {
        return inner.relationStyle(path);
    }
}
