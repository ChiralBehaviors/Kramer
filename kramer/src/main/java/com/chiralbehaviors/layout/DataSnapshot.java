// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Captures a snapshot of primitive field values from a JSON data tree against
 * the schema layout tree, and computes which paths changed between snapshots.
 *
 * <p>Snapshot entries map each {@link SchemaPath} (of a primitive leaf) to the
 * ordered list of text values seen at that path across all data rows. A path is
 * considered changed when:
 * <ul>
 *   <li>it is absent from the prior snapshot (cold start), or</li>
 *   <li>its value list differs (different length or any element differs).</li>
 * </ul>
 *
 * <p>Row-count changes (i.e. the top-level array size changed) are a special
 * case: the method returns ALL known paths so that every {@link PrimitiveLayout}
 * is invalidated for a full rebuild.
 *
 * <p><b>Traversal semantics</b>: the root layout's rows ARE the data rows —
 * the root relation's field name is not a key in the data.  Nested relation
 * fields ARE keys in their parent rows and are extracted normally.
 */
final class DataSnapshot {

    /** Sentinel empty snapshot used on cold start. */
    static final Map<SchemaPath, List<String>> EMPTY = Map.of();

    private DataSnapshot() {}

    /**
     * Detect which primitive paths have changed values between {@code prior} and
     * the new {@code data}, walking the schema tree rooted at {@code rootLayout}.
     *
     * <p>If {@code prior} is empty (cold start) all paths are returned.
     * If the top-level row count changed, all paths are returned.
     *
     * @param rootLayout the measured layout tree root (RelationLayout or PrimitiveLayout)
     * @param data       the new JSON data (array of rows or single object)
     * @param prior      the previously captured snapshot, or {@link #EMPTY}
     * @return set of changed paths; never null, may be empty if nothing changed
     */
    static Set<SchemaPath> detectChangedPaths(SchemaNodeLayout rootLayout,
                                              JsonNode data,
                                              Map<SchemaPath, List<String>> prior) {
        if (prior.isEmpty()) {
            // Cold start: everything is "changed"
            Set<SchemaPath> allPaths = new HashSet<>();
            collectPaths(rootLayout, allPaths);
            return allPaths;
        }

        // Row-count change: full rebuild
        int newRowCount = (data != null && data.isArray()) ? data.size() : 1;
        int priorRowCount = priorRowCount(prior);
        if (priorRowCount >= 0 && newRowCount != priorRowCount) {
            Set<SchemaPath> allPaths = new HashSet<>();
            collectPaths(rootLayout, allPaths);
            return allPaths;
        }

        // Element-level comparison
        Map<SchemaPath, List<String>> current = buildSnapshot(rootLayout, data);
        Set<SchemaPath> changed = new HashSet<>();
        for (Map.Entry<SchemaPath, List<String>> entry : current.entrySet()) {
            SchemaPath path = entry.getKey();
            List<String> newValues = entry.getValue();
            List<String> oldValues = prior.get(path);
            if (oldValues == null || !oldValues.equals(newValues)) {
                changed.add(path);
            }
        }
        // Also mark paths that were in prior but are no longer present
        for (SchemaPath path : prior.keySet()) {
            if (!current.containsKey(path)) {
                changed.add(path);
            }
        }
        return changed;
    }

    /**
     * Build a fresh snapshot by walking the layout tree and extracting all
     * primitive field values from {@code data}.
     *
     * <p>The root layout's rows are the top-level data rows (the root relation's
     * field is not a key in the data).  Nested relations extract their field
     * from parent rows.
     *
     * @param rootLayout the measured layout tree root
     * @param data       the JSON data (array of rows or single object)
     * @return map of SchemaPath to ordered list of text values; never null
     */
    static Map<SchemaPath, List<String>> buildSnapshot(SchemaNodeLayout rootLayout,
                                                       JsonNode data) {
        Map<SchemaPath, List<String>> snapshot = new HashMap<>();
        if (rootLayout == null || data == null) {
            return snapshot;
        }
        List<JsonNode> rows = SchemaNode.asList(data);
        if (rootLayout instanceof PrimitiveLayout pl) {
            // Single-primitive root: rows are the values directly
            collectPrimitiveValues(pl, rows, snapshot);
        } else if (rootLayout instanceof RelationLayout rl) {
            // Root relation: rows ARE the data rows; recurse children with same rows
            collectRelationChildren(rl, rows, snapshot);
        }
        return snapshot;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Collect all SchemaPath keys reachable from {@code snl} into {@code out}.
     */
    private static void collectPaths(SchemaNodeLayout snl, Set<SchemaPath> out) {
        if (snl instanceof PrimitiveLayout pl) {
            SchemaPath p = pl.getSchemaPath();
            if (p != null) out.add(p);
        } else if (snl instanceof RelationLayout rl) {
            for (SchemaNodeLayout child : rl.getChildren()) {
                collectPaths(child, out);
            }
        }
    }

    /**
     * Recursively collect values for {@code snl} given that {@code rows} are the
     * parent rows from which {@code snl}'s field should be extracted.
     *
     * <p>For a nested {@link RelationLayout}, the field is extracted from each
     * parent row (possibly returning an array) and flattened before recursing.
     * For a {@link PrimitiveLayout}, the field is extracted from each parent row
     * and the text value is stored.
     */
    private static void collectValues(SchemaNodeLayout snl,
                                      List<JsonNode> parentRows,
                                      Map<SchemaPath, List<String>> snapshot) {
        if (snl instanceof PrimitiveLayout pl) {
            collectPrimitiveValues(pl, parentRows, snapshot);
        } else if (snl instanceof RelationLayout rl) {
            // Extract this relation's field from each parent row, flatten, recurse
            List<JsonNode> childRows = extractField(parentRows, rl.getField());
            collectRelationChildren(rl, childRows, snapshot);
        }
    }

    /**
     * Collect values for {@code pl} by extracting its field from each parent row.
     */
    private static void collectPrimitiveValues(PrimitiveLayout pl,
                                               List<JsonNode> parentRows,
                                               Map<SchemaPath, List<String>> snapshot) {
        SchemaPath path = pl.getSchemaPath();
        if (path == null) return;
        List<String> values = new ArrayList<>(parentRows.size());
        for (JsonNode row : parentRows) {
            if (row == null) {
                values.add("");
            } else {
                JsonNode extracted = pl.extractFrom(row);
                values.add(SchemaNode.asText(extracted));
            }
        }
        snapshot.put(path, values);
    }

    /**
     * Recurse into the children of {@code rl}, passing {@code rows} as the
     * parent rows for each child.
     */
    private static void collectRelationChildren(RelationLayout rl,
                                                List<JsonNode> rows,
                                                Map<SchemaPath, List<String>> snapshot) {
        for (SchemaNodeLayout child : rl.getChildren()) {
            collectValues(child, rows, snapshot);
        }
    }

    /**
     * Extract {@code field} from each row and flatten into a single list.
     * Arrays are expanded; missing/null values are skipped.
     */
    private static List<JsonNode> extractField(List<JsonNode> rows, String field) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode row : rows) {
            if (row == null) continue;
            JsonNode extracted = row.get(field);
            if (extracted == null || extracted.isNull() || extracted.isMissingNode()) continue;
            if (extracted.isArray()) {
                for (JsonNode child : (ArrayNode) extracted) {
                    result.add(child);
                }
            } else {
                result.add(extracted);
            }
        }
        return result;
    }

    /**
     * Infer the prior row count from the snapshot. Returns the length of any
     * value list (all should be equal for top-level rows), or -1 if empty.
     */
    private static int priorRowCount(Map<SchemaPath, List<String>> prior) {
        for (List<String> values : prior.values()) {
            return values.size();
        }
        return -1;
    }

    /** Returns the number of top-level rows in data. */
    static int rowCount(JsonNode data) {
        if (data == null) return 0;
        return data.isArray() ? data.size() : 1;
    }
}
