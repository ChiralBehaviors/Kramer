/**
 * Copyright (c) 2016 Chiral Behaviors, LLC, all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chiralbehaviors.layout.graphql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.chiralbehaviors.layout.SchemaPath;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.Parser;

/**
 *
 * @author halhildebrand
 *
 */
public final class GraphQlUtil {
    private static final Logger log = LoggerFactory.getLogger(GraphQlUtil.class);

    private static final Set<String> DEFAULT_EXCLUDED = Set.of("id");

    private GraphQlUtil() {}

    public static class QueryException extends Exception {
        private static final long serialVersionUID = 1L;
        private final ArrayNode   errors;

        public QueryException(ArrayNode errors) {
            super(errors.toString());
            this.errors = errors;
        }

        public ArrayNode getErrors() {
            return errors;
        }
    }

    public static record QueryRequest(String operationName, String query, Map<String, Object> variables) {
        public QueryRequest(String query, Map<String, Object> variables) {
            this(null, query, variables);
        }

        public QueryRequest() {
            this(null, null, Collections.emptyMap());
        }
    }

    public static Relation buildSchema(Field parentField) {
        return buildSchema(parentField, DEFAULT_EXCLUDED);
    }

    public static Relation buildSchema(Field parentField,
                                        Set<String> excludedFields) {
        Relation parent = new Relation(parentField.getName());
        for (Selection<?> selection : parentField.getSelectionSet()
                                                .getSelections()) {
            if (selection instanceof Field field) {
                if (field.getSelectionSet() == null) {
                    if (!excludedFields.contains(field.getName())) {
                        parent.addChild(new Primitive(field.getName()));
                    }
                } else {
                    parent.addChild(buildSchema(field, excludedFields));
                }
            } else if (selection instanceof InlineFragment inlineFragment) {
                buildSchema(parent, inlineFragment, excludedFields);
            } else if (selection instanceof FragmentSpread) {
                throw new UnsupportedOperationException("Named fragment spreads are not supported; use inline fragments");
            }
        }
        return parent;
    }

    public static void buildSchema(Relation parent, InlineFragment fragment) {
        buildSchema(parent, fragment, DEFAULT_EXCLUDED);
    }

    public static void buildSchema(Relation parent, InlineFragment fragment,
                                    Set<String> excludedFields) {
        for (Selection<?> selection : fragment.getSelectionSet()
                                             .getSelections()) {
            if (selection instanceof Field field) {
                if (field.getSelectionSet() == null) {
                    if (!excludedFields.contains(field.getName())) {
                        parent.addChild(new Primitive(field.getName()));
                    }
                } else {
                    parent.addChild(buildSchema(field, excludedFields));
                }
            } else if (selection instanceof InlineFragment inlineFragment) {
                buildSchema(parent, inlineFragment, excludedFields);
            } else if (selection instanceof FragmentSpread) {
                throw new UnsupportedOperationException("Named fragment spreads are not supported; use inline fragments");
            }
        }
    }

    public static Relation buildSchema(String query) {
        return buildSchema(query, DEFAULT_EXCLUDED);
    }

    public static Relation buildSchema(String query,
                                        Set<String> excludedFields) {
        List<Relation> children = new ArrayList<>();
        AtomicReference<String> operationName = new AtomicReference<>();
        Parser.parse(query)
              .getDefinitions()
              .stream()
              .filter(OperationDefinition.class::isInstance)
              .map(OperationDefinition.class::cast)
                    .filter(d -> d.getOperation()
                                  .equals(Operation.QUERY))
                    .findFirst()
                    .ifPresent(operation -> {
                        operationName.set(operation.getName());
                        for (Selection<?> selection : operation.getSelectionSet()
                                                              .getSelections()) {
                            if (selection instanceof Field field) {
                                children.add(buildSchema(field, excludedFields));
                            }
                        }
                    });
        if (children.isEmpty()) {
            throw new IllegalStateException(String.format("Invalid query: %s",
                                                          query));
        }
        Relation parent;
        if (operationName.get() != null) {
            parent = new Relation(operationName.get());
        } else {
            if (children.size() > 1) {
                parent = new QueryRoot("query");
            } else {
                return children.get(0);
            }
        }
        children.forEach(c -> parent.addChild(c));
        return parent;
    }

    public static Relation buildSchema(String query, String source) {
        return buildSchema(query, source, DEFAULT_EXCLUDED);
    }

    public static Relation buildSchema(String query, String source,
                                        Set<String> excludedFields) {
        for (Definition<?> definition : Parser.parse(query)
                                                   .getDefinitions()) {
            if (definition instanceof OperationDefinition operation) {
                if (operation.getOperation()
                             .equals(Operation.QUERY)) {
                    for (Selection<?> selection : operation.getSelectionSet()
                                                          .getSelections()) {
                        if (selection instanceof Field field) {
                            if (source.equals(field.getName())) {
                                return buildSchema(field, excludedFields);
                            }
                        }
                    }
                }
            }
        }
        throw new IllegalStateException(String.format("Invalid query, cannot find source: %s",
                                                      source));
    }

    // --- buildContext: retains Document + builds SchemaPath↔Field index ---

    public static SchemaContext buildContext(String query) {
        return buildContext(query, DEFAULT_EXCLUDED);
    }

    public static SchemaContext buildContext(String query,
                                              Set<String> excludedFields) {
        Document document = Parser.parse(query);
        Map<SchemaPath, Field> fieldIndex = new LinkedHashMap<>();
        Map<SchemaPath, String> aliasIndex = new HashMap<>();
        List<Relation> children = new ArrayList<>();
        AtomicReference<String> operationName = new AtomicReference<>();

        document.getDefinitions()
                .stream()
                .filter(OperationDefinition.class::isInstance)
                .map(OperationDefinition.class::cast)
                .filter(d -> d.getOperation().equals(Operation.QUERY))
                .findFirst()
                .ifPresent(operation -> {
                    operationName.set(operation.getName());
                    for (Selection<?> selection : operation.getSelectionSet()
                                                          .getSelections()) {
                        if (selection instanceof Field field) {
                            String key = field.getAlias() != null ? field.getAlias() : field.getName();
                            SchemaPath path = new SchemaPath(key);
                            fieldIndex.put(path, field);
                            if (field.getAlias() != null) {
                                aliasIndex.put(path, field.getAlias());
                            }
                            children.add(buildContextRelation(field, path,
                                excludedFields, fieldIndex, aliasIndex, document));
                        }
                    }
                });

        if (children.isEmpty()) {
            throw new IllegalStateException(
                String.format("Invalid query: %s", query));
        }

        Relation schema;
        if (operationName.get() != null) {
            schema = new Relation(operationName.get());
            children.forEach(schema::addChild);
        } else if (children.size() > 1) {
            schema = new QueryRoot("query");
            children.forEach(schema::addChild);
        } else {
            schema = children.get(0);
        }

        return new SchemaContext(document, schema, fieldIndex, aliasIndex);
    }

    public static SchemaContext buildContext(String query, String source) {
        return buildContext(query, source, DEFAULT_EXCLUDED);
    }

    public static SchemaContext buildContext(String query, String source,
                                              Set<String> excludedFields) {
        Document document = Parser.parse(query);
        Map<SchemaPath, Field> fieldIndex = new LinkedHashMap<>();
        Map<SchemaPath, String> aliasIndex = new HashMap<>();

        for (Definition<?> definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition operation) {
                if (operation.getOperation().equals(Operation.QUERY)) {
                    for (Selection<?> selection : operation.getSelectionSet()
                                                          .getSelections()) {
                        if (selection instanceof Field field) {
                            String key = field.getAlias() != null
                                ? field.getAlias() : field.getName();
                            if (source.equals(key)) {
                                SchemaPath path = new SchemaPath(key);
                                fieldIndex.put(path, field);
                                if (field.getAlias() != null) {
                                    aliasIndex.put(path, field.getAlias());
                                }
                                Relation schema = buildContextRelation(
                                    field, path, excludedFields,
                                    fieldIndex, aliasIndex, document);
                                return new SchemaContext(document, schema,
                                    fieldIndex, aliasIndex);
                            }
                        }
                    }
                }
            }
        }
        throw new IllegalStateException(
            String.format("Invalid query, cannot find source: %s", source));
    }

    private static Relation buildContextRelation(
            Field parentField, SchemaPath parentPath,
            Set<String> excludedFields,
            Map<SchemaPath, Field> fieldIndex,
            Map<SchemaPath, String> aliasIndex,
            Document document) {
        String parentKey = parentField.getAlias() != null
            ? parentField.getAlias() : parentField.getName();
        Relation parent = new Relation(parentKey);

        for (Selection<?> selection : parentField.getSelectionSet()
                                                 .getSelections()) {
            if (selection instanceof Field field) {
                String key = field.getAlias() != null
                    ? field.getAlias() : field.getName();
                if (excludedFields.contains(field.getName())) {
                    continue;
                }
                SchemaPath childPath = parentPath.child(key);
                fieldIndex.put(childPath, field);
                if (field.getAlias() != null) {
                    aliasIndex.put(childPath, field.getAlias());
                }
                if (field.getSelectionSet() == null) {
                    parent.addChild(new Primitive(key));
                } else {
                    parent.addChild(buildContextRelation(field, childPath,
                        excludedFields, fieldIndex, aliasIndex, document));
                }
            } else if (selection instanceof InlineFragment inlineFragment) {
                buildContextInlineFragment(parent, inlineFragment, parentPath,
                    excludedFields, fieldIndex, aliasIndex, document);
            } else if (selection instanceof FragmentSpread spread) {
                var fragmentDef = document.getDefinitionsOfType(FragmentDefinition.class)
                    .stream()
                    .filter(f -> f.getName().equals(spread.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                        "Fragment not found: " + spread.getName()));
                buildContextInlineFragment(parent, fragmentDef.getSelectionSet(),
                    parentPath, excludedFields, fieldIndex, aliasIndex, document);
            }
        }
        return parent;
    }

    private static void buildContextInlineFragment(
            Relation parent, InlineFragment fragment, SchemaPath parentPath,
            Set<String> excludedFields,
            Map<SchemaPath, Field> fieldIndex,
            Map<SchemaPath, String> aliasIndex,
            Document document) {
        buildContextInlineFragment(parent, fragment.getSelectionSet(),
            parentPath, excludedFields, fieldIndex, aliasIndex, document);
    }

    private static void buildContextInlineFragment(
            Relation parent, SelectionSet selectionSet,
            SchemaPath parentPath,
            Set<String> excludedFields,
            Map<SchemaPath, Field> fieldIndex,
            Map<SchemaPath, String> aliasIndex,
            Document document) {
        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                String key = field.getAlias() != null
                    ? field.getAlias() : field.getName();
                if (excludedFields.contains(field.getName())) {
                    continue;
                }
                SchemaPath childPath = parentPath.child(key);
                fieldIndex.put(childPath, field);
                if (field.getAlias() != null) {
                    aliasIndex.put(childPath, field.getAlias());
                }
                if (field.getSelectionSet() == null) {
                    parent.addChild(new Primitive(key));
                } else {
                    parent.addChild(buildContextRelation(field, childPath,
                        excludedFields, fieldIndex, aliasIndex, document));
                }
            } else if (selection instanceof InlineFragment inlineFragment) {
                buildContextInlineFragment(parent, inlineFragment, parentPath,
                    excludedFields, fieldIndex, aliasIndex, document);
            } else if (selection instanceof FragmentSpread spread) {
                var fragmentDef = document.getDefinitionsOfType(FragmentDefinition.class)
                    .stream()
                    .filter(f -> f.getName().equals(spread.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                        "Fragment not found: " + spread.getName()));
                buildContextInlineFragment(parent, fragmentDef.getSelectionSet(),
                    parentPath, excludedFields, fieldIndex, aliasIndex, document);
            }
        }
    }

    public static ObjectNode evaluate(WebTarget endpoint,
                                      QueryRequest request) throws QueryException {
        Builder invocationBuilder = endpoint.request(MediaType.APPLICATION_JSON_TYPE);

        ObjectNode result = invocationBuilder.post(Entity.entity(request,
                                                                 MediaType.APPLICATION_JSON_TYPE),
                                                   ObjectNode.class);
        ArrayNode errors = result.withArray("errors");
        if (errors.size() > 0) {
            throw new QueryException(errors);
        }
        JsonNode data = result.get("data");
        if (data == null || data.isNull()) {
            throw new QueryException(result.withArray("errors"));
        }
        return (ObjectNode) data;
    }

    public static String evaluate(WebTarget endpoint,
                                  String request) {
        try {
            return endpoint.request(MediaType.APPLICATION_JSON_TYPE)
                           .post(Entity.entity(request,
                                               MediaType.APPLICATION_JSON_TYPE),
                                 String.class);
        } catch (BadRequestException e) {
            log.warn("GraphQL request returned HTTP 400: {}", e.getMessage());
            return "{}";
        }
    }
}
