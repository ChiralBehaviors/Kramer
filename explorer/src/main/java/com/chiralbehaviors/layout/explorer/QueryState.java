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

package com.chiralbehaviors.layout.explorer;

import java.util.Objects;

public class QueryState {
    private String data;
    private String operationName;
    private String query;
    private String selection;
    private String targetURL;
    private String variables;

    public QueryState() {
    }

    public QueryState(QueryState state) {
        targetURL = state.getTargetURL();
        query = state.getQuery();
        variables = state.getVariables();
        operationName = state.getOperationName();
        selection = state.getSelection();
        data = state.getData();
    }

    public QueryState(String targetURL, String query, String variables,
                      String operationName, String source, String data) {
        this.targetURL = targetURL;
        this.query = query;
        this.variables = variables;
        this.operationName = operationName;
        this.selection = source;
        this.data = data;
    }

    public void clear() {
        targetURL = null;
        query = null;
        variables = null;
        operationName = null;
        selection = null;
        data = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof QueryState other)) return false;
        return Objects.equals(data, other.data)
            && Objects.equals(operationName, other.operationName)
            && Objects.equals(query, other.query)
            && Objects.equals(selection, other.selection)
            && Objects.equals(targetURL, other.targetURL)
            && Objects.equals(variables, other.variables);
    }

    public String getData() {
        return data;
    }

    public String getOperationName() {
        return operationName;
    }

    public String getQuery() {
        return query;
    }

    public String getSelection() {
        return selection;
    }

    public String getTargetURL() {
        return targetURL;
    }

    public String getVariables() {
        return variables;
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, operationName, query, selection, targetURL, variables);
    }

    public void initializeFrom(QueryState state) {
        setTargetURL(state.getTargetURL());
        setQuery(state.getQuery());
        setVariables(state.getVariables());
        setOperationName(state.getOperationName());
        setSelection(state.getSelection());
        this.data = state.data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public void setSelection(String source) {
        this.selection = source;
    }

    public void setTargetURL(String targetURL) {
        this.targetURL = targetURL;
    }

    public void setVariables(String variables) {
        this.variables = variables;
    }

    @Override
    public String toString() {
        return String.format("QueryState [data=%s, operationName=%s, query=%s, selection=%s, targetURL=%s, variables=%s]",
                             data, operationName, query, selection, targetURL,
                             variables);
    }
}