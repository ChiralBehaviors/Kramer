// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import java.util.ArrayList;
import java.util.List;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.expression.Expr;
import com.chiralbehaviors.layout.expression.ParseException;
import com.chiralbehaviors.layout.expression.Parser;
import com.chiralbehaviors.layout.query.FieldState;
import com.chiralbehaviors.layout.query.LayoutQueryState;

import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.EnumValue;
import graphql.language.Field;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;

/**
 * Translates LayoutQueryState changes into modified GraphQL queries
 * via QueryBuilder.reconstruct(). Server-pushable operations (filter,
 * sort) are injected as field arguments; non-pushable operations
 * remain for client-side evaluation.
 * <p>
 * Filter expressions are translated via {@link ExprToGraphQL}; only
 * translatable expressions are pushed server-side. Non-translatable
 * expressions (ScalarCall, arithmetic) are silently left for client evaluation.
 * <p>
 * Sort fields are translated as Hasura-style {@code orderBy: [{field: asc}]}
 * argument values.
 * <p>
 * Visible-false field removal is already handled by {@link QueryBuilder#reconstruct}
 * — this class does not duplicate that logic.
 * <p>
 * See RDR-028 Phase 2 for design rationale.
 */
public final class QueryRewriter {

    private final ServerCapabilities capabilities;
    private final QueryBuilder queryBuilder;

    public QueryRewriter(ServerCapabilities capabilities) {
        this.capabilities = capabilities;
        this.queryBuilder = new QueryBuilder();
    }

    /**
     * Rewrite the query, pushing filter/sort operations server-side
     * where capabilities allow. Returns the reconstructed query string.
     * <p>
     * For each path in queryState that has filter or sort overrides:
     * <ol>
     *   <li>Check if {@link ServerCapabilities} supports push for this path.</li>
     *   <li>If pushable and filter is translatable via {@link ExprToGraphQL},
     *       inject the filter argument into the field's AST node.</li>
     *   <li>If pushable and sortFields is set, inject an orderBy argument.</li>
     *   <li>Otherwise leave for client-side.</li>
     * </ol>
     *
     * @param ctx        the schema context holding the parsed document
     * @param queryState the current layout query state with user overrides
     * @return the rewritten query string
     */
    public String rewrite(SchemaContext ctx, LayoutQueryState queryState) {
        SchemaContext modified = ctx;

        for (SchemaPath path : queryState.overriddenPaths()) {
            FieldState fs = queryState.getFieldState(path);

            // --- Filter push-down ---
            String filterExpr = fs.filterExpression();
            if (filterExpr != null && !filterExpr.isBlank()
                    && capabilities.canPushFilter(path)) {
                modified = pushFilter(modified, path, filterExpr);
            }

            // --- Sort push-down ---
            String sortFields = fs.sortFields();
            if (sortFields != null && !sortFields.isBlank()
                    && capabilities.canPushSort(path)) {
                modified = pushSort(modified, path, sortFields);
            }
        }

        return queryBuilder.reconstruct(modified, queryState);
    }

    // -----------------------------------------------------------------------

    /**
     * Inject a translated filter argument into the field at {@code path}.
     * Falls back silently if the expression is not translatable or unparseable.
     */
    private SchemaContext pushFilter(SchemaContext ctx, SchemaPath path, String filterExpr) {
        Expr parsed;
        try {
            parsed = Parser.parse(filterExpr);
        } catch (ParseException e) {
            // Unparseable expression — leave for client-side
            return ctx;
        }

        if (!ExprToGraphQL.isTranslatable(parsed)) {
            return ctx;
        }

        Value<?> filterValue = ExprToGraphQL.translate(parsed);

        // Determine which argument name this server uses
        Field original = ctx.fieldIndex().get(path);
        if (original == null) {
            return ctx;
        }
        String argName = resolveFilterArgName(original);
        if (argName == null) {
            return ctx;
        }

        Field modified = withArgument(original, argName, filterValue);
        return patchContext(ctx, path, original, modified);
    }

    /**
     * Inject an orderBy argument from comma-separated sort field names.
     * Produces Hasura-style: {@code orderBy: [{name: asc}, {age: desc}]}.
     * A leading '-' prefix on a field name means descending.
     */
    private SchemaContext pushSort(SchemaContext ctx, SchemaPath path, String sortFields) {
        Field original = ctx.fieldIndex().get(path);
        if (original == null) {
            return ctx;
        }
        String argName = resolveSortArgName(original);
        if (argName == null) {
            return ctx;
        }

        String[] parts = sortFields.split(",");
        ArrayValue.Builder arrayBuilder = ArrayValue.newArrayValue();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isBlank()) continue;
            boolean descending = trimmed.startsWith("-");
            String fieldName = descending ? trimmed.substring(1) : trimmed;
            String direction = descending ? "desc" : "asc";
            ObjectValue fieldObj = ObjectValue.newObjectValue()
                .objectField(new ObjectField(fieldName, new EnumValue(direction)))
                .build();
            arrayBuilder.value(fieldObj);
        }
        ArrayValue sortValue = arrayBuilder.build();

        Field modified = withArgument(original, argName, sortValue);
        return patchContext(ctx, path, original, modified);
    }

    /**
     * Determine the filter argument name for a field by inspecting existing arguments.
     * Returns "where" or "filter" depending on which one the field already has,
     * or null if neither is present.
     */
    private static String resolveFilterArgName(Field field) {
        for (Argument arg : field.getArguments()) {
            String name = arg.getName();
            if ("where".equals(name) || "filter".equals(name)) {
                return name;
            }
        }
        return null;
    }

    /**
     * Determine the sort argument name for a field by inspecting existing arguments.
     * Returns the first recognized name, or null if none is present.
     */
    private static String resolveSortArgName(Field field) {
        for (Argument arg : field.getArguments()) {
            String name = arg.getName();
            if ("orderBy".equals(name) || "sort".equals(name) || "order_by".equals(name)) {
                return name;
            }
        }
        return null;
    }

    /**
     * Return a copy of {@code field} with the named argument replaced (or added)
     * with the given value.
     */
    private static Field withArgument(Field field, String argName, Value<?> value) {
        List<Argument> args = new ArrayList<>(field.getArguments());
        boolean replaced = false;
        for (int i = 0; i < args.size(); i++) {
            if (argName.equals(args.get(i).getName())) {
                Argument newArg = args.get(i).transform(b -> b.value(value));
                args.set(i, newArg);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            args.add(Argument.newArgument().name(argName).value(value).build());
        }
        List<Argument> finalArgs = List.copyOf(args);
        return field.transform(b -> b.arguments(finalArgs));
    }

    /**
     * Return a new SchemaContext with {@code oldField} replaced by {@code newField}
     * in both the field index and the document AST.
     */
    private static SchemaContext patchContext(
            SchemaContext ctx, SchemaPath path, Field oldField, Field newField) {

        var newIndex = new java.util.HashMap<>(ctx.fieldIndex());
        newIndex.put(path, newField);

        // Delegate document patching to SchemaContext's own withCursor mechanism is
        // cursor-specific; we need a general patch. SchemaContext has a private
        // patchDocument helper but it's internal. We replicate the same logic here
        // by walking the document and replacing by identity.
        graphql.language.Document newDoc = patchDocument(ctx.document(), oldField, newField);

        return new SchemaContext(newDoc, ctx.schema(), newIndex, ctx.aliasIndex());
    }

    /**
     * Walk all definitions in the document, replacing {@code oldField} (by identity)
     * with {@code newField}.
     */
    @SuppressWarnings("rawtypes")
    private static graphql.language.Document patchDocument(
            graphql.language.Document doc,
            Field oldField,
            Field newField) {

        List<graphql.language.Definition> newDefs = new ArrayList<>();
        for (graphql.language.Definition<?> def : doc.getDefinitions()) {
            if (def instanceof graphql.language.OperationDefinition op) {
                graphql.language.SelectionSet patched =
                    patchSelectionSet(op.getSelectionSet(), oldField, newField);
                newDefs.add(op.transform(b -> b.selectionSet(patched)));
            } else if (def instanceof graphql.language.FragmentDefinition frag) {
                graphql.language.SelectionSet patched =
                    patchSelectionSet(frag.getSelectionSet(), oldField, newField);
                newDefs.add(frag.transform(b -> b.selectionSet(patched)));
            } else {
                newDefs.add(def);
            }
        }
        return doc.transform(b -> b.definitions(newDefs));
    }

    private static graphql.language.SelectionSet patchSelectionSet(
            graphql.language.SelectionSet ss, Field oldField, Field newField) {

        if (ss == null) return null;

        List<graphql.language.Selection> patched = new ArrayList<>();
        for (graphql.language.Selection<?> sel : ss.getSelections()) {
            if (sel == oldField) {
                patched.add(newField);
            } else if (sel instanceof Field f) {
                graphql.language.SelectionSet childSS =
                    patchSelectionSet(f.getSelectionSet(), oldField, newField);
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
