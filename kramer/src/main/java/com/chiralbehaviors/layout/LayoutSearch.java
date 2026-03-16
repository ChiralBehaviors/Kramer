// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusController;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Text search over the flat array of rows produced by a schema + data pair.
 *
 * <p>The search is row-major: for each row, primitive fields are visited in
 * schema depth-first order. Matches are enumerated lazily by cursor position.
 *
 * <p>Flags:
 * <ul>
 *   <li>{@code caseSensitive} — default {@code false} (case-insensitive)</li>
 *   <li>{@code wrapAround}    — default {@code true}  (wraps at end/start)</li>
 * </ul>
 *
 * <p>When constructed with a {@link VirtualFlow} and {@link FocusController},
 * {@link #findNext()}, {@link #findPrevious()}, and
 * {@link #navigateToResult(SearchResult)} will scroll the outermost flow to
 * the matched row using {@link VirtualFlow#show(int)} (MinDistanceTo semantics)
 * and update selection via {@link FocusController#navigateTo}.
 */
public class LayoutSearch {

    private final SchemaNode                  root;
    private final JsonNode                    data;
    private final VirtualFlow<LayoutCell<?>>  virtualFlow;
    private final FocusController<LayoutCell<?>> focusController;

    private String  query;
    private boolean caseSensitive = false;
    private boolean wrapAround    = true;

    /** Index into the full match list; -1 means "before first findNext". */
    private int cursor = -1;

    /** Cached list of all matches for the current query. Invalidated on query change. */
    private List<SearchResult> matchCache;

    /**
     * Creates a search without navigation capability.
     * {@link #navigateToResult(SearchResult)} is a no-op.
     */
    public LayoutSearch(SchemaNode root, JsonNode data) {
        this(root, data, null, null);
    }

    /**
     * Creates a search with navigation capability.
     *
     * @param root            schema root
     * @param data            array data to search
     * @param virtualFlow     outermost VirtualFlow for scrolling; may be {@code null}
     * @param focusController focus/selection controller; may be {@code null}
     */
    @SuppressWarnings("unchecked")
    public LayoutSearch(SchemaNode root, JsonNode data,
                        VirtualFlow<? extends LayoutCell<?>> virtualFlow,
                        FocusController<? extends LayoutCell<?>> focusController) {
        this.root = root;
        this.data = data;
        this.virtualFlow = (VirtualFlow<LayoutCell<?>>) virtualFlow;
        this.focusController = (FocusController<LayoutCell<?>>) focusController;
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
        invalidateCache();
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
        invalidateCache();
    }

    public boolean isWrapAround() {
        return wrapAround;
    }

    public void setWrapAround(boolean wrapAround) {
        this.wrapAround = wrapAround;
    }

    // -------------------------------------------------------------------------
    // Search operations
    // -------------------------------------------------------------------------

    /**
     * Advances to the next match, auto-navigates the VirtualFlow if one is
     * configured, and returns the result.
     *
     * @return the next {@link SearchResult}, or empty when no match is found
     */
    public Optional<SearchResult> findNext() {
        if (isBlankQuery()) {
            return Optional.empty();
        }
        List<SearchResult> matches = getMatches();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        int next = cursor + 1;
        if (next >= matches.size()) {
            if (!wrapAround) {
                return Optional.empty();
            }
            next = 0;
        }
        cursor = next;
        SearchResult result = matches.get(cursor);
        navigateToResult(result);
        return Optional.of(result);
    }

    /**
     * Moves to the previous match, auto-navigates the VirtualFlow if one is
     * configured, and returns the result.
     *
     * @return the previous {@link SearchResult}, or empty when no match is found
     */
    public Optional<SearchResult> findPrevious() {
        if (isBlankQuery()) {
            return Optional.empty();
        }
        List<SearchResult> matches = getMatches();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        int prev = cursor - 1;
        if (prev < 0) {
            if (!wrapAround) {
                return Optional.empty();
            }
            prev = matches.size() - 1;
        }
        cursor = prev;
        SearchResult result = matches.get(cursor);
        navigateToResult(result);
        return Optional.of(result);
    }

    /**
     * Returns the total number of matches across all rows and fields for the
     * current query.
     */
    public int countMatches() {
        if (isBlankQuery()) {
            return 0;
        }
        return getMatches().size();
    }

    /**
     * Scrolls the outermost VirtualFlow to the row identified by
     * {@code result.rowIndex()} using {@link VirtualFlow#show(int)}
     * (MinDistanceTo semantics — minimal scroll) and selects the row via the
     * FocusController.
     *
     * <p>This is a no-op when no VirtualFlow was supplied at construction time.
     *
     * @param result the search result to navigate to
     */
    public void navigateToResult(SearchResult result) {
        if (virtualFlow == null) {
            return;
        }
        int rowIndex = result.rowIndex();
        virtualFlow.show(rowIndex);
        if (focusController != null) {
            focusController.navigateTo(virtualFlow, rowIndex);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private boolean isBlankQuery() {
        return query == null || query.isBlank();
    }

    private void invalidateCache() {
        matchCache = null;
        cursor = -1;
    }

    private List<SearchResult> getMatches() {
        if (matchCache == null) {
            matchCache = buildMatches();
        }
        return matchCache;
    }

    /**
     * Builds the full ordered list of matches for the current query.
     * Guard: if data is not an array, returns an empty list.
     */
    private List<SearchResult> buildMatches() {
        if (data == null || !data.isArray()) {
            return List.of();
        }
        ArrayNode array = (ArrayNode) data;
        String needle = caseSensitive ? query : query.toLowerCase();

        List<SchemaPath> primitiveFields = new ArrayList<>();
        collectPrimitiveFields(root, new SchemaPath(root.getField()), primitiveFields);

        List<SearchResult> results = new ArrayList<>();
        for (int rowIdx = 0; rowIdx < array.size(); rowIdx++) {
            JsonNode row = array.get(rowIdx);
            for (SchemaPath fieldPath : primitiveFields) {
                // Navigate to the field value using the path segments relative to root
                // The path starts with the root field name, so skip segment 0 when extracting
                JsonNode value = extractValue(row, fieldPath);
                String text = SchemaNode.asText(value);
                if (text == null || text.isEmpty()) {
                    continue;
                }
                String haystack = caseSensitive ? text : text.toLowerCase();
                int idx = haystack.indexOf(needle);
                if (idx >= 0) {
                    results.add(new SearchResult(fieldPath, rowIdx, text, idx, needle.length()));
                }
            }
        }
        return results;
    }

    /**
     * Walks the schema tree depth-first and collects paths to all Primitive leaves.
     */
    private void collectPrimitiveFields(SchemaNode node, SchemaPath path,
                                        List<SchemaPath> out) {
        if (node instanceof Primitive) {
            out.add(path);
        } else if (node instanceof Relation relation) {
            for (SchemaNode child : relation.getChildren()) {
                collectPrimitiveFields(child, path.child(child.getField()), out);
            }
        }
    }

    /**
     * Extracts the value for a given field path from a single row object.
     * The path segments include the root segment at index 0, which corresponds to
     * the top-level array element, so we start navigation from segment 1.
     */
    private JsonNode extractValue(JsonNode row, SchemaPath fieldPath) {
        List<String> segments = fieldPath.segments();
        // segments[0] is the root field name (the array field); row is already one element
        // so we navigate from segments[1] onward
        JsonNode current = row;
        for (int i = 1; i < segments.size(); i++) {
            if (current == null || current.isNull()) {
                return null;
            }
            current = current.get(segments.get(i));
        }
        return current;
    }
}
