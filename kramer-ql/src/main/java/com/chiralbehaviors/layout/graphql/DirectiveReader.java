// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import java.util.HashMap;
import java.util.Map;

import com.chiralbehaviors.layout.LayoutPropertyKeys;
import com.chiralbehaviors.layout.SchemaPath;

import graphql.language.Directive;
import graphql.language.StringValue;

/**
 * Reads graphql-java {@link Directive} annotations from Field AST nodes via
 * {@link SchemaContext#fieldAt(SchemaPath)} and returns a map of property
 * overrides keyed by {@link LayoutPropertyKeys} constants.
 *
 * <p>Supported directives:
 * <ul>
 *   <li>{@code @hide} → {@link LayoutPropertyKeys#VISIBLE} = {@code false}</li>
 *   <li>{@code @render(mode: "...")} → {@link LayoutPropertyKeys#RENDER_MODE} = the mode argument</li>
 *   <li>{@code @hideIfEmpty} → {@link LayoutPropertyKeys#HIDE_IF_EMPTY} = {@code true}</li>
 * </ul>
 * Unknown directives are silently ignored.
 */
public final class DirectiveReader {

    /**
     * Reads all recognised directives on the field at {@code path} within
     * {@code ctx} and returns a map of property overrides.
     *
     * @param ctx  the schema context built from the GraphQL query
     * @param path the schema path addressing the field of interest
     * @return immutable map of property key → override value; empty if none
     */
    public Map<String, Object> readDirectives(SchemaContext ctx, SchemaPath path) {
        var field = ctx.fieldAt(path);
        if (field.isEmpty() || field.get().getDirectives().isEmpty()) {
            return Map.of();
        }

        Map<String, Object> result = new HashMap<>();
        for (Directive directive : field.get().getDirectives()) {
            switch (directive.getName()) {
                case "hide" ->
                    result.put(LayoutPropertyKeys.VISIBLE, Boolean.FALSE);
                case "render" -> {
                    var arg = directive.getArgument("mode");
                    if (arg != null && arg.getValue() instanceof StringValue sv) {
                        result.put(LayoutPropertyKeys.RENDER_MODE, sv.getValue());
                    }
                }
                case "hideIfEmpty" ->
                    result.put(LayoutPropertyKeys.HIDE_IF_EMPTY, Boolean.TRUE);
                default -> { /* ignore unknown directives */ }
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }
}
