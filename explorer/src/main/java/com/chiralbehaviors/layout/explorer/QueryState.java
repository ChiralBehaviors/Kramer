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
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        QueryState other = (QueryState) obj;
        if (data == null) {
            if (other.data != null)
                return false;
        } else if (!data.equals(other.data))
            return false;
        if (operationName == null) {
            if (other.operationName != null)
                return false;
        } else if (!operationName.equals(other.operationName))
            return false;
        if (query == null) {
            if (other.query != null)
                return false;
        } else if (!query.equals(other.query))
            return false;
        if (selection == null) {
            if (other.selection != null)
                return false;
        } else if (!selection.equals(other.selection))
            return false;
        if (targetURL == null) {
            if (other.targetURL != null)
                return false;
        } else if (!targetURL.equals(other.targetURL))
            return false;
        if (variables == null) {
            if (other.variables != null)
                return false;
        } else if (!variables.equals(other.variables))
            return false;
        return true;
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
        final int prime = 31;
        int result = 1;
        result = prime * result + ((data == null) ? 0 : data.hashCode());
        result = prime * result
                 + ((operationName == null) ? 0 : operationName.hashCode());
        result = prime * result + ((query == null) ? 0 : query.hashCode());
        result = prime * result + ((selection == null) ? 0 : selection.hashCode());
        result = prime * result
                 + ((targetURL == null) ? 0 : targetURL.hashCode());
        result = prime * result
                 + ((variables == null) ? 0 : variables.hashCode());
        return result;
    }

    public void initializeFrom(QueryState state) {
        targetURL = state.getTargetURL();
        query = state.getQuery();
        variables = state.getVariables();
        operationName = state.getOperationName();
        selection = state.getSelection();
        data = state.getData();
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