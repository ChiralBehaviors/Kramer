// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.chiralbehaviors.layout.LayoutPropertyKeys;
import com.chiralbehaviors.layout.LayoutStylesheet;
import com.chiralbehaviors.layout.SchemaPath;

import graphql.language.AstPrinter;
import graphql.language.Definition;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;

/**
 * Reconstructs a GraphQL query string from a {@link SchemaContext}, omitting
 * fields where the stylesheet marks them as not visible, and stripping
 * client-only directives.
 *
 * <p>See RDR-020 Phase C for design rationale.
 *
 * @author hhildebrand
 */
public class QueryBuilder {

    private static final Set<String> DEFAULT_CLIENT_DIRECTIVES =
        Set.of("hide", "render", "hideIfEmpty");

    private final Set<String> clientDirectiveNames;

    public QueryBuilder() {
        this(DEFAULT_CLIENT_DIRECTIVES);
    }

    public QueryBuilder(Set<String> clientDirectiveNames) {
        this.clientDirectiveNames = Set.copyOf(clientDirectiveNames);
    }

    /**
     * Reconstruct a query string from ctx, omitting fields where
     * {@code stylesheet.getBoolean(path, VISIBLE, true) == false}.
     * Strips client-only directives. Preserves all field arguments.
     */
    public String reconstruct(SchemaContext ctx, LayoutStylesheet stylesheet) {
        Document original = ctx.document();

        List<Definition> newDefs = new ArrayList<>();

        for (Definition<?> def : original.getDefinitions()) {
            if (def instanceof OperationDefinition op) {
                SelectionSet newSS = filterSelectionSet(
                    op.getSelectionSet(), null, stylesheet);
                newDefs.add(op.transform(b -> b.selectionSet(newSS)));
            } else if (def instanceof FragmentDefinition frag) {
                // preserve fragment definitions verbatim
                newDefs.add(frag);
            } else {
                newDefs.add(def);
            }
        }

        Document newDoc = original.transform(b -> b.definitions(newDefs));
        return AstPrinter.printAst(newDoc);
    }

    // -----------------------------------------------------------------------

    private SelectionSet filterSelectionSet(SelectionSet ss,
                                            SchemaPath parentPath,
                                            LayoutStylesheet stylesheet) {
        if (ss == null) {
            return null;
        }

        List<Selection> filtered = new ArrayList<>();
        for (Selection<?> sel : ss.getSelections()) {
            if (sel instanceof Field field) {
                String key = field.getAlias() != null
                             ? field.getAlias() : field.getName();
                SchemaPath childPath = parentPath == null
                                       ? new SchemaPath(key)
                                       : parentPath.child(key);

                if (!stylesheet.getBoolean(childPath, LayoutPropertyKeys.VISIBLE, true)) {
                    continue; // omit hidden field
                }

                List<Directive> keptDirectives = stripClientDirectives(field.getDirectives());
                SelectionSet childSS = filterSelectionSet(
                    field.getSelectionSet(), childPath, stylesheet);

                filtered.add(field.transform(b -> {
                    b.directives(keptDirectives);
                    if (childSS != null) {
                        b.selectionSet(childSS);
                    }
                }));
            } else {
                // InlineFragment / FragmentSpread — pass through unchanged
                filtered.add(sel);
            }
        }

        return ss.transform(b -> b.selections(filtered));
    }

    private List<Directive> stripClientDirectives(List<Directive> directives) {
        if (directives == null || directives.isEmpty()) {
            return directives;
        }
        return directives.stream()
                         .filter(d -> !clientDirectiveNames.contains(d.getName()))
                         .toList();
    }
}
