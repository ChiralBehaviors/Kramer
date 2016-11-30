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

package com.chiralbehaviors.layout.toy;

import java.util.Collections;
import java.util.Map;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import com.chiralbehaviors.layout.graphql.GraphQlUtil;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.toy.Page.Route;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 
 * @author halhildebrand
 *
 */
public class PageContext {
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

    public static class QueryRequest {
        public String              query;
        public Map<String, Object> variables = Collections.emptyMap();

        public QueryRequest(String query, Map<String, Object> variables) {
            this.query = query;
            this.variables = variables;
        }
    }

    private final Page          page;

    private final Relation      root;

    private Map<String, Object> variables;

    public PageContext(Page page) {
        this(page, Collections.emptyMap());
    }

    public PageContext(Page page, Map<String, Object> variables) {
        this.page = page;
        this.variables = variables;
        this.root = GraphQlUtil.buildSchema(page.getQuery());
    }

    public ObjectNode evaluate(WebTarget endpoint) throws QueryException {
        Builder invocationBuilder = endpoint.request(MediaType.APPLICATION_JSON_TYPE);

        ObjectNode result = invocationBuilder.post(Entity.entity(new QueryRequest(page.getQuery(),
                                                                                  variables),
                                                                 MediaType.APPLICATION_JSON_TYPE),
                                                   ObjectNode.class);
        ArrayNode errors = result.withArray("errors");
        if (errors.size() > 0) {
            throw new QueryException(errors);
        }
        return (ObjectNode) result.get("data")
                                  .get(root.getField());
    }

    public Page getPage() {
        return page;
    }

    public Relation getRoot() {
        return root;
    }

    public Route getRoute(Relation relation) {
        return page.getRoute(relation);
    }
}
