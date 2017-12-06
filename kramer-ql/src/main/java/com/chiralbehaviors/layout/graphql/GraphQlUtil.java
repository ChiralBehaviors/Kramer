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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import graphql.language.Definition;
import graphql.language.Field;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import graphql.language.Selection;
import graphql.parser.Parser;

/**
 * 
 * @author halhildebrand
 *
 */
public interface GraphQlUtil {
    static class QueryException extends Exception {
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

    static class QueryRequest {
        public String              operationName;
        public String              query;
        public Map<String, Object> variables = Collections.emptyMap();

        public QueryRequest() {
        }

        public QueryRequest(String query, Map<String, Object> variables) {
            this.query = query;
            this.variables = variables;
        }

        @Override
        public String toString() {
            return "QueryRequest [query=" + query + ", variables=" + variables
                   + "]";
        }
    }

    static Relation buildSchema(Field parentField) {
        Relation parent = new Relation(parentField.getName());
        for (Selection selection : parentField.getSelectionSet()
                                              .getSelections()) {
            if (selection instanceof Field) {
                Field field = (Field) selection;
                if (field.getSelectionSet() == null) {
                    if (!field.getName()
                              .equals("id")) {
                        parent.addChild(new Primitive(field.getName()));
                    }
                } else {
                    parent.addChild(buildSchema(field));
                }
            } else if (selection instanceof InlineFragment) {
                buildSchema(parent, (InlineFragment) selection);
            } else if (selection instanceof FragmentSpread) {

            }
        }
        return parent;
    }

    static void buildSchema(Relation parent, InlineFragment fragment) {
        for (Selection selection : fragment.getSelectionSet()
                                           .getSelections()) {
            if (selection instanceof Field) {
                Field field = (Field) selection;
                if (field.getSelectionSet() == null) {
                    if (!field.getName()
                              .equals("id")) {
                        parent.addChild(new Primitive(field.getName()));
                    }
                } else {
                    parent.addChild(buildSchema(field));
                }
            } else if (selection instanceof InlineFragment) {
                buildSchema(parent, (InlineFragment) selection);
            } else if (selection instanceof FragmentSpread) {

            }
        }
    }

    static Relation buildSchema(String query) {
        List<Relation> children = new ArrayList<Relation>();
        AtomicReference<String> operationName = new AtomicReference<>();
        new Parser().parseDocument(query)
                    .getDefinitions()
                    .stream()
                    .filter(d -> d instanceof OperationDefinition)
                    .map(d -> (OperationDefinition) d)
                    .filter(d -> d.getOperation()
                                  .equals(Operation.QUERY))
                    .findFirst()
                    .ifPresent(operation -> {
                        operationName.set(operation.getName());
                        for (Selection selection : operation.getSelectionSet()
                                                            .getSelections()) {
                            if (selection instanceof Field) {
                                children.add(buildSchema((Field) selection));
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

    static Relation buildSchema(String query, String source) {
        for (Definition definition : new Parser().parseDocument(query)
                                                 .getDefinitions()) {
            if (definition instanceof OperationDefinition) {
                OperationDefinition operation = (OperationDefinition) definition;
                if (operation.getOperation()
                             .equals(Operation.QUERY)) {
                    for (Selection selection : operation.getSelectionSet()
                                                        .getSelections()) {
                        if (selection instanceof Field) {
                            Field field = (Field) selection;
                            if (source.equals(field.getName())) {
                                return buildSchema(field);
                            }
                        }
                    }
                }
            }
        }
        throw new IllegalStateException(String.format("Invalid query, cannot find source: %s",
                                                      source));
    }

    static ObjectNode evaluate(WebTarget endpoint,
                               QueryRequest request) throws QueryException {
        Builder invocationBuilder = endpoint.request(MediaType.APPLICATION_JSON_TYPE);

        ObjectNode result = invocationBuilder.post(Entity.entity(request,
                                                                 MediaType.APPLICATION_JSON_TYPE),
                                                   ObjectNode.class);
        ArrayNode errors = result.withArray("errors");
        if (errors.size() > 0) {
            throw new QueryException(errors);
        }
        return (ObjectNode) result.get("data");
    }

    static String evaluate(WebTarget endpoint,
                           String request) throws IOException {
        try {
            return endpoint.request(MediaType.APPLICATION_JSON_TYPE)
                           .post(Entity.entity(request,
                                               MediaType.APPLICATION_JSON_TYPE),
                                 String.class);
        } catch (BadRequestException e) {
            return "{}";
        }
    }
}
