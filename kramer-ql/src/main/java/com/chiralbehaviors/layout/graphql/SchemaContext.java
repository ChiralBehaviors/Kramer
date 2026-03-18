// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.schema.Relation;

import graphql.language.Argument;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.StringValue;

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

    /**
     * Returns a new SchemaContext with the {@code after} argument on the Field at
     * {@code connectionPath} set to the given cursor value. Used for Relay pagination.
     *
     * <p>Both the {@code fieldIndex} and the {@code Document} AST are updated so
     * that {@link QueryBuilder#reconstruct} emits the cursor argument correctly.
     *
     * @param connectionPath path to the connection field (e.g. {@code new SchemaPath("users")})
     * @param cursor         the opaque cursor value from {@code pageInfo.endCursor}
     * @return a new immutable SchemaContext with the updated field argument
     * @throws IllegalArgumentException if no field exists at {@code connectionPath}
     */
    public SchemaContext withCursor(SchemaPath connectionPath, String cursor) {
        Field original = fieldIndex.get(connectionPath);
        if (original == null) {
            throw new IllegalArgumentException("No field at path: " + connectionPath);
        }

        // Build modified argument list
        List<Argument> newArgs = new ArrayList<>(original.getArguments());
        boolean replaced = false;
        for (int i = 0; i < newArgs.size(); i++) {
            if ("after".equals(newArgs.get(i).getName())) {
                newArgs.set(i, newArgs.get(i).transform(b -> b.value(StringValue.of(cursor))));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            newArgs.add(Argument.newArgument().name("after").value(StringValue.of(cursor)).build());
        }

        Field modified = original.transform(b -> b.arguments(newArgs));

        // Update the Document AST so QueryBuilder.reconstruct() picks up the change
        Document newDocument = patchDocument(document, original, modified);

        // Update field index
        Map<SchemaPath, Field> newIndex = new HashMap<>(fieldIndex);
        newIndex.put(connectionPath, modified);

        return new SchemaContext(newDocument, schema, newIndex, aliasIndex);
    }

    /**
     * Walks the Document definitions replacing {@code oldField} identity with
     * {@code newField} at every level of the selection set tree.
     */
    @SuppressWarnings("rawtypes")
    private static Document patchDocument(Document doc, Field oldField, Field newField) {
        List<Definition> newDefs = new ArrayList<>();
        for (Definition<?> def : doc.getDefinitions()) {
            if (def instanceof OperationDefinition op) {
                SelectionSet patched = patchSelectionSet(op.getSelectionSet(), oldField, newField);
                newDefs.add(op.transform(b -> b.selectionSet(patched)));
            } else if (def instanceof graphql.language.FragmentDefinition frag) {
                SelectionSet patched = patchSelectionSet(frag.getSelectionSet(), oldField, newField);
                newDefs.add(frag.transform(b -> b.selectionSet(patched)));
            } else {
                newDefs.add(def);
            }
        }
        return doc.transform(b -> b.definitions(newDefs));
    }

    private static SelectionSet patchSelectionSet(SelectionSet ss, Field oldField, Field newField) {
        if (ss == null) {
            return null;
        }
        List<Selection> patched = new ArrayList<>();
        for (Selection<?> sel : ss.getSelections()) {
            if (sel == oldField) {
                patched.add(newField);
            } else if (sel instanceof Field f) {
                SelectionSet childSS = patchSelectionSet(f.getSelectionSet(), oldField, newField);
                if (childSS != f.getSelectionSet()) {
                    patched.add(f.transform(b -> b.selectionSet(childSS)));
                } else {
                    patched.add(f);
                }
            } else {
                patched.add(sel);
            }
        }
        return ss.transform(b -> b.selections(patched));
    }
}
