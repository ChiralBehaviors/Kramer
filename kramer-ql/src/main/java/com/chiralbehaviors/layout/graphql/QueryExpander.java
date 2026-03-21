// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import java.util.ArrayList;
import java.util.List;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.graphql.TypeIntrospector.IntrospectedField;
import com.chiralbehaviors.layout.graphql.TypeIntrospector.IntrospectedType;

import graphql.language.AstPrinter;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;

/**
 * Expands or contracts a GraphQL Document AST by adding or removing fields.
 * Uses graphql-java's immutable {@code .transform()} pattern — all operations
 * return new Document instances.
 *
 * <p>Field type information from {@link TypeIntrospector} determines whether
 * a newly added field is scalar (bare node) or a relation (node with a minimal
 * sub-selection).
 */
public class QueryExpander {

    private static final String QUERY_ROOT_TYPE = "Query";

    private final TypeIntrospector introspector;

    public QueryExpander(TypeIntrospector introspector) {
        this.introspector = introspector;
    }

    /**
     * Add a field to the SelectionSet of the node addressed by {@code parentPath}.
     * Idempotent — returns the document unchanged if the field already exists.
     * For relation types, creates a minimal sub-selection (id + first name-like field).
     */
    public Document addField(Document doc, SchemaPath parentPath, String fieldName) {
        List<String> segments = parentPath.segments();
        return transformDocument(doc, ss ->
            addFieldToSelectionSet(ss, segments, 0, fieldName));
    }

    /**
     * Remove the field identified by {@code fieldPath} from its parent's SelectionSet.
     * The last segment of fieldPath is the field to remove; preceding segments
     * identify the parent. Returns the document unchanged if the field doesn't exist.
     */
    public Document removeField(Document doc, SchemaPath fieldPath) {
        List<String> segments = fieldPath.segments();
        if (segments.isEmpty()) {
            return doc;
        }
        String fieldName = segments.getLast();
        List<String> parentSegments = segments.subList(0, segments.size() - 1);
        return transformDocument(doc, ss ->
            removeFieldFromSelectionSet(ss, parentSegments, 0, fieldName));
    }

    /** Serialize a Document AST back to a GraphQL query string. */
    public static String serialize(Document doc) {
        return AstPrinter.printAst(doc);
    }

    // -----------------------------------------------------------------------
    // Document-level traversal (same pattern as QueryRewriter.patchDocument)
    // -----------------------------------------------------------------------

    @SuppressWarnings("rawtypes")
    private static Document transformDocument(
            Document doc, java.util.function.Function<SelectionSet, SelectionSet> modifier) {
        List<Definition> newDefs = new ArrayList<>();
        for (Definition<?> def : doc.getDefinitions()) {
            if (def instanceof OperationDefinition op) {
                SelectionSet patched = modifier.apply(op.getSelectionSet());
                newDefs.add(op.transform(b -> b.selectionSet(patched)));
            } else if (def instanceof FragmentDefinition frag) {
                SelectionSet patched = modifier.apply(frag.getSelectionSet());
                newDefs.add(frag.transform(b -> b.selectionSet(patched)));
            } else {
                newDefs.add(def);
            }
        }
        return doc.transform(b -> b.definitions(newDefs));
    }

    // -----------------------------------------------------------------------
    // Add field — recursive navigation + insertion
    // -----------------------------------------------------------------------

    private SelectionSet addFieldToSelectionSet(
            SelectionSet ss, List<String> pathSegments, int depth, String fieldName) {
        if (ss == null) {
            return null;
        }

        if (depth == pathSegments.size()) {
            // We've reached the target — add field here if not already present
            boolean exists = ss.getSelections().stream()
                .filter(Field.class::isInstance)
                .map(s -> ((Field) s).getName())
                .anyMatch(fieldName::equals);
            if (exists) {
                return ss;
            }

            Field newField = buildField(pathSegments, fieldName);
            var selections = new ArrayList<>(ss.getSelections());
            selections.add(newField);
            return ss.transform(b -> b.selections(selections));
        }

        // Navigate deeper — find matching field at current depth
        String segment = pathSegments.get(depth);
        List<Selection<?>> patched = new ArrayList<>();
        for (Selection<?> sel : ss.getSelections()) {
            if (sel instanceof Field f && segment.equals(f.getName())) {
                SelectionSet childSS = addFieldToSelectionSet(
                    f.getSelectionSet(), pathSegments, depth + 1, fieldName);
                patched.add(f.transform(b -> b.selectionSet(childSS)));
            } else {
                patched.add(sel);
            }
        }
        return ss.transform(b -> b.selections(patched));
    }

    // -----------------------------------------------------------------------
    // Remove field — recursive navigation + removal
    // -----------------------------------------------------------------------

    private SelectionSet removeFieldFromSelectionSet(
            SelectionSet ss, List<String> parentSegments, int depth, String fieldName) {
        if (ss == null) {
            return null;
        }

        if (depth == parentSegments.size()) {
            // We've reached the parent — remove the named field
            var selections = new ArrayList<Selection<?>>();
            for (Selection<?> sel : ss.getSelections()) {
                if (sel instanceof Field f && fieldName.equals(f.getName())) {
                    continue; // skip = remove
                }
                selections.add(sel);
            }
            if (selections.size() == ss.getSelections().size()) {
                return ss; // nothing removed
            }
            return ss.transform(b -> b.selections(selections));
        }

        // Navigate deeper
        String segment = parentSegments.get(depth);
        List<Selection<?>> patched = new ArrayList<>();
        for (Selection<?> sel : ss.getSelections()) {
            if (sel instanceof Field f && segment.equals(f.getName())) {
                SelectionSet childSS = removeFieldFromSelectionSet(
                    f.getSelectionSet(), parentSegments, depth + 1, fieldName);
                patched.add(f.transform(b -> b.selectionSet(childSS)));
            } else {
                patched.add(sel);
            }
        }
        return ss.transform(b -> b.selections(patched));
    }

    // -----------------------------------------------------------------------
    // Field construction with type awareness
    // -----------------------------------------------------------------------

    /**
     * Build a Field node for the given field name. Uses the introspector to
     * determine if it's a scalar (bare field) or relation (field with
     * minimal sub-selection).
     *
     * @param parentPathSegments path segments to the parent — used to resolve
     *                           the parent type in the introspector
     * @param fieldName          the field to create
     */
    private Field buildField(List<String> parentPathSegments, String fieldName) {
        IntrospectedType parentType = resolveType(parentPathSegments);
        if (parentType == null) {
            // No type info — create bare scalar field
            return Field.newField(fieldName).build();
        }

        IntrospectedField fieldInfo = parentType.fields().stream()
            .filter(f -> fieldName.equals(f.name()))
            .findFirst()
            .orElse(null);

        if (fieldInfo == null || isScalarType(fieldInfo)) {
            return Field.newField(fieldName).build();
        }

        // Relation field — create with minimal sub-selection
        String targetTypeName = fieldInfo.type().baseName();
        IntrospectedType targetType = targetTypeName != null
            ? introspector.typeIndex().get(targetTypeName) : null;

        if (targetType == null || targetType.fields() == null || targetType.fields().isEmpty()) {
            return Field.newField(fieldName).build();
        }

        List<Field> subFields = minimalSubSelection(targetType);
        SelectionSet subSS = SelectionSet.newSelectionSet()
            .selections(subFields.stream().map(Selection.class::cast).toList())
            .build();
        return Field.newField(fieldName).selectionSet(subSS).build();
    }

    /**
     * Resolve the introspected type for a path by walking from the Query root.
     * e.g., ["employees"] → Query.employees → type Employee
     * e.g., ["employees", "projects"] → Query.employees → Employee.projects → type Project
     */
    private IntrospectedType resolveType(List<String> pathSegments) {
        IntrospectedType current = introspector.typeIndex().get(QUERY_ROOT_TYPE);
        if (current == null) {
            return null;
        }

        for (String segment : pathSegments) {
            IntrospectedField field = current.fields().stream()
                .filter(f -> segment.equals(f.name()))
                .findFirst()
                .orElse(null);
            if (field == null) {
                return null;
            }
            String typeName = field.type().baseName();
            current = typeName != null ? introspector.typeIndex().get(typeName) : null;
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static boolean isScalarType(IntrospectedField field) {
        String kind = field.type().kind();
        if ("SCALAR".equals(kind) || "ENUM".equals(kind)) {
            return true;
        }
        // For LIST/NON_NULL wrappers, check the base type
        String baseName = field.type().baseName();
        if (baseName == null) {
            return true; // unknown → treat as scalar
        }
        // Scalars: String, Int, Float, Boolean, ID
        return switch (baseName) {
            case "String", "Int", "Float", "Boolean", "ID" -> true;
            default -> false;
        };
    }

    /**
     * RF-6 heuristic: pick "id" if present, plus first name-like field (name, title, label).
     * If neither found, take the first field.
     */
    private static List<Field> minimalSubSelection(IntrospectedType type) {
        List<Field> result = new ArrayList<>();
        List<IntrospectedField> fields = type.fields();

        // Always include "id" if present
        fields.stream()
            .filter(f -> "id".equals(f.name()))
            .findFirst()
            .ifPresent(f -> result.add(Field.newField(f.name()).build()));

        // Find first name-like field
        var nameLike = List.of("name", "title", "label", "displayName");
        fields.stream()
            .filter(f -> nameLike.contains(f.name()))
            .findFirst()
            .ifPresent(f -> result.add(Field.newField(f.name()).build()));

        // Fallback: if we got nothing, take first field
        if (result.isEmpty() && !fields.isEmpty()) {
            result.add(Field.newField(fields.get(0).name()).build());
        }

        return result;
    }
}
