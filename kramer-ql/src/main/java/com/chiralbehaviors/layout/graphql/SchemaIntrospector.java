// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.chiralbehaviors.layout.SchemaPath;

import graphql.language.Field;

/**
 * Discovers server capabilities by inspecting field arguments in a SchemaContext.
 * Uses structural inspection of the parsed query's Field arguments, not
 * GraphQL introspection queries.
 * <p>
 * Recognized argument names:
 * <ul>
 *   <li>Filter: {@code filter}, {@code where}</li>
 *   <li>Sort:   {@code orderBy}, {@code sort}, {@code order_by}</li>
 *   <li>Page:   {@code first}, {@code limit}, {@code after}, {@code before}, {@code last}</li>
 * </ul>
 * <p>
 * See RDR-028 Phase 1.
 */
public final class SchemaIntrospector {

    private static final Set<String> RECOGNIZED = Set.of(
        "filter", "where",
        "orderBy", "sort", "order_by",
        "first", "limit", "after", "before", "last"
    );

    private SchemaIntrospector() {}

    /**
     * Discover server capabilities from a SchemaContext by inspecting which
     * fields have recognized argument names.
     *
     * @param ctx the schema context to inspect
     * @return a {@link ServerCapabilities} describing pushable operations per path
     */
    public static ServerCapabilities discover(SchemaContext ctx) {
        Map<SchemaPath, Set<String>> result = new HashMap<>();

        for (Map.Entry<SchemaPath, Field> entry : ctx.fieldIndex().entrySet()) {
            Field field = entry.getValue();
            if (field.getArguments().isEmpty()) {
                continue;
            }
            Set<String> recognized = field.getArguments()
                                          .stream()
                                          .map(graphql.language.Argument::getName)
                                          .filter(RECOGNIZED::contains)
                                          .collect(Collectors.toUnmodifiableSet());
            if (!recognized.isEmpty()) {
                result.put(entry.getKey(), recognized);
            }
        }

        return new ServerCapabilities(result);
    }
}
