// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import java.util.Map;
import java.util.Optional;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.schema.Relation;

import graphql.language.Document;
import graphql.language.Field;

/**
 * Immutable context produced by {@link GraphQlUtil#buildContext(String)}.
 * Retains the graphql-java {@link Document} AST alongside the schema tree,
 * with bidirectional indexes for field lookup and alias resolution.
 * <p>
 * See RDR-020 for full design rationale.
 *
 * @param document   the parsed GraphQL Document AST
 * @param schema     the Relation tree (same as buildSchema() would produce)
 * @param fieldIndex maps each SchemaPath to its graphql-java Field AST node
 * @param aliasIndex maps SchemaPath to its alias (only when an alias is present)
 *
 * @author hhildebrand
 */
public record SchemaContext(
    Document document,
    Relation schema,
    Map<SchemaPath, Field> fieldIndex,
    Map<SchemaPath, String> aliasIndex
) {
    public SchemaContext {
        fieldIndex = Map.copyOf(fieldIndex);
        aliasIndex = Map.copyOf(aliasIndex);
    }

    /** Look up the Field AST node for a given schema path. */
    public Optional<Field> fieldAt(SchemaPath path) {
        return Optional.ofNullable(fieldIndex.get(path));
    }

    /**
     * Returns the alias for a path if one is set, or the leaf segment
     * of the path (the field name) if no alias was present.
     */
    public String displayName(SchemaPath path) {
        return aliasIndex.getOrDefault(path, path.leaf());
    }
}
