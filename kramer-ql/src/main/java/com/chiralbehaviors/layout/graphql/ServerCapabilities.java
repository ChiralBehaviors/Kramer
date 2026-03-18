// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import java.util.Map;
import java.util.Set;

import com.chiralbehaviors.layout.SchemaPath;

/**
 * Describes which server-side operations are available for each field.
 * Built by inspecting the GraphQL schema's field arguments.
 * <p>
 * See RDR-028 for full design rationale.
 */
public record ServerCapabilities(
    Map<SchemaPath, Set<String>> pushableArguments
) {
    public static final ServerCapabilities NONE = new ServerCapabilities(Map.of());

    public ServerCapabilities {
        // Defensive copy — values are already immutable Sets from SchemaIntrospector
        pushableArguments = Map.copyOf(pushableArguments);
    }

    public boolean canPushFilter(SchemaPath path) {
        var args = pushableArguments.getOrDefault(path, Set.of());
        return args.contains("filter") || args.contains("where");
    }

    public boolean canPushSort(SchemaPath path) {
        var args = pushableArguments.getOrDefault(path, Set.of());
        return args.contains("orderBy") || args.contains("sort") || args.contains("order_by");
    }

    public boolean canPushPagination(SchemaPath path) {
        var args = pushableArguments.getOrDefault(path, Set.of());
        return args.contains("first") || args.contains("limit");
    }
}
