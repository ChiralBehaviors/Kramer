/**
 * Copyright (c) 2017 Chiral Behaviors, LLC, all rights reserved.
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

package com.chiralbehaviors.layout;

import static com.chiralbehaviors.layout.style.Style.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.expression.ExpressionEvaluator;
import com.chiralbehaviors.layout.expression.Expr;
import com.chiralbehaviors.layout.expression.ParseException;
import com.chiralbehaviors.layout.outline.Outline;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.Style;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.table.ColumnHeader;
import com.chiralbehaviors.layout.table.CrosstabHeader;
import com.chiralbehaviors.layout.table.NestedRow;
import com.chiralbehaviors.layout.table.NestedTable;
import com.chiralbehaviors.layout.table.TableHeader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.geometry.Insets;
import javafx.scene.layout.Region;

/**
 *
 * @author halhildebrand
 *
 */
public final class RelationLayout extends SchemaNodeLayout {

    public static ArrayNode flatten(Relation fold, JsonNode datum) {
        ArrayNode flattened = JsonNodeFactory.instance.arrayNode();
        if (datum != null) {
            if (datum.isArray()) {
                datum.forEach(item -> {
                    flattened.addAll(SchemaNode.asArray(item.get(fold.getField())));
                });
            } else {
                flattened.addAll(SchemaNode.asArray(datum.get(fold.getField())));
            }
        }
        return flattened;
    }

    /**
     * Build a stable comparator for a single sort field. Numeric JSON nodes are
     * compared numerically; all other values are compared lexicographically on
     * their text representation. Null/missing values sort last.
     */
    static Comparator<JsonNode> fieldComparator(String field) {
        boolean descending = field.startsWith("-");
        String key = descending ? field.substring(1) : field;
        int dir = descending ? -1 : 1;
        return (a, b) -> {
            JsonNode va = a == null ? null : a.get(key);
            JsonNode vb = b == null ? null : b.get(key);
            boolean missingA = va == null || va.isNull();
            boolean missingB = vb == null || vb.isNull();
            if (missingA && missingB) return 0;
            if (missingA)             return 1;   // nulls last (direction-independent)
            if (missingB)             return -1;
            if (va.isNumber() && vb.isNumber()) {
                return dir * Double.compare(va.asDouble(), vb.asDouble());
            }
            return dir * va.asText().compareTo(vb.asText());
        };
    }

    /**
     * Derive a comparator from the relation's sortFields / autoSort configuration.
     * Returns null when no sort is needed (autoSort=false, sortFields empty).
     *
     * Heuristic for autoSort with no sortFields: id > key > name > first primitive field.
     */
    static Comparator<JsonNode> buildSortComparator(Relation relation) {
        // Priority: explicit sortFields always take effect (regardless of autoSort flag).
        // autoSort only applies when sortFields is empty.
        List<String> fields = relation.getSortFields();
        if (!fields.isEmpty()) {
            Comparator<JsonNode> cmp = fieldComparator(fields.get(0));
            for (int i = 1; i < fields.size(); i++) {
                cmp = cmp.thenComparing(fieldComparator(fields.get(i)));
            }
            return cmp;
        }
        if (!relation.isAutoSort()) {
            return null;
        }
        // Heuristic: pick first child primitive matching id > key > name
        List<String> candidates = AUTO_SORT_CANDIDATES;
        for (String candidate : candidates) {
            if (relation.getChild(candidate) != null) {
                return fieldComparator(candidate);
            }
        }
        // Fallback: first primitive child
        return relation.getChildren().stream()
                       .filter(c -> c instanceof com.chiralbehaviors.layout.schema.Primitive)
                       .map(c -> fieldComparator(c.getField()))
                       .findFirst()
                       .orElse(null);
    }

    /**
     * Sort an ArrayNode in-place using a stable sort. Returns the same instance.
     */
    static ArrayNode sortArrayNode(ArrayNode array, Comparator<JsonNode> cmp) {
        if (cmp == null || array == null || array.size() <= 1) {
            return array;
        }
        List<JsonNode> list = new ArrayList<>(array.size());
        array.forEach(list::add);
        list.sort(cmp);   // List.sort is stable
        array.removeAll();
        list.forEach(array::add);
        return array;
    }

    /**
     * Compare two evaluation results for sort ordering. Null-safe; nulls sort last.
     */
    static int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;  // nulls last
        if (b == null) return -1;
        if (a instanceof Double da && b instanceof Double db) return Double.compare(da, db);
        if (a instanceof String sa && b instanceof String sb) return sa.compareTo(sb);
        if (a instanceof Boolean ba && b instanceof Boolean bb) return Boolean.compare(ba, bb);
        return a.toString().compareTo(b.toString());
    }

    /**
     * Evaluate an expression that mixes aggregate and scalar operations.
     * Creates a synthetic row where each AggregateCall is pre-evaluated and
     * injected as a literal, then evaluates the remaining expression per-row.
     */
    static Object evaluateWithAggregates(ExpressionEvaluator evaluator,
                                          Expr expr, List<JsonNode> rows) {
        // Replace AggregateCall nodes with their computed literal values
        Expr resolved = resolveAggregates(evaluator, expr, rows);
        // Evaluate the resolved (aggregate-free) expression with an empty row
        return evaluator.evaluate(resolved, JsonNodeFactory.instance.objectNode());
    }

    private static Expr resolveAggregates(ExpressionEvaluator evaluator,
                                           Expr expr, List<JsonNode> rows) {
        return switch (expr) {
            case Expr.AggregateCall agg -> {
                Object result = evaluator.evaluateAggregate(agg, rows);
                yield new Expr.Literal(result);
            }
            case Expr.BinaryOp(var op, var l, var r) ->
                new Expr.BinaryOp(op,
                    resolveAggregates(evaluator, l, rows),
                    resolveAggregates(evaluator, r, rows));
            case Expr.UnaryOp(var op, var operand) ->
                new Expr.UnaryOp(op, resolveAggregates(evaluator, operand, rows));
            case Expr.ScalarCall(var name, var args) ->
                new Expr.ScalarCall(name,
                    args.stream().map(a -> resolveAggregates(evaluator, a, rows)).toList());
            case Expr.Literal l -> l;
            case Expr.FieldRef f -> f;
        };
    }

    /**
     * Returns true if the item has at least one child Relation field that is a
     * non-empty array, or if the relation has no Relation children at all.
     */
    static boolean hasNonEmptyChildren(JsonNode item, Relation relation) {
        boolean hasRelationChild = false;
        for (SchemaNode child : relation.getChildren()) {
            if (child.isRelation()) {
                hasRelationChild = true;
                JsonNode childData = item.get(child.getField());
                if (childData != null && childData.isArray() && childData.size() > 0) {
                    return true;
                }
            }
        }
        // If no Relation children exist, the row is always kept
        return !hasRelationChild;
    }

    /**
     * Filter an ArrayNode, keeping only items that pass the given predicate.
     * Returns a new ArrayNode containing the survivors.
     */
    static ArrayNode filterArrayNode(JsonNode source, Predicate<JsonNode> pred) {
        if (source == null) return JsonNodeFactory.instance.arrayNode();
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        StreamSupport.stream(source.spliterator(), false)
                     .filter(pred)
                     .forEach(result::add);
        return result;
    }

    private static final List<String>  AUTO_SORT_CANDIDATES = List.of("id", "key", "name");
    private static final Logger        LOG                  = Logger.getLogger(RelationLayout.class.getName());

    protected int                          averageChildCardinality;
    protected double                       cellHeight       = -1;
    protected final List<SchemaNodeLayout> children         = new ArrayList<>();
    protected double                       columnHeaderHeight;
    protected final List<ColumnSet>        columnSets       = new ArrayList<>();
    protected Function<JsonNode, JsonNode>  extractor;
    protected int                          maxCardinality;
    protected int                          resolvedCardinality;
    protected final RelationStyle          style;
    protected double                       tableColumnWidth  = 0;
    protected boolean                      useTable          = false;
    /** True when layoutCrosstab() has been called with non-empty pivot values. */
    protected boolean                      useCrosstab       = false;
    /** Pivot column values set by layoutCrosstab(); empty when not in crosstab mode. */
    protected List<String>                 crosstabPivotValues = List.of();
    /** Width of each pivot data column in crosstab mode. */
    protected double                       crosstabColumnWidth = 0.0;
    private MeasureResult                  measureResult;
    /** Comparator derived from sortFields / autoSort; null when no sort is needed. */
    private Comparator<JsonNode>           sortComparator;
    /** Filter predicate for hide-if-empty; null when filtering is disabled. */
    private Predicate<JsonNode>            hideIfEmptyFilter;
    /** Aggregate expression results keyed by child field name. Populated during measure(). */
    private Map<String, Object>            aggregateResults;
    /** Compiled filter expression AST; null when no filter-expression is set. */
    private Expr                           filterExprAst;
    /** Ordered list of (field, ast) pairs for formula overlay; empty when no formulas. */
    private List<Map.Entry<String, Expr>>  formulaEntries = List.of();
    /** Compiled sort expression AST; null when no sort-expression is set. */
    private Expr                           sortExprAst;
    /** The ExpressionEvaluator from the last measure pass; held for extractFrom(). */
    private ExpressionEvaluator            cachedEvaluator;
    /**
     * Solver-driven render-mode assignments. When non-null, layout() consults
     * this map instead of the greedy width check. Set by AutoLayout before the
     * layout pass and cleared to null afterward so standalone calls fall back to
     * greedy behavior.
     */
    private Map<SchemaPath, RelationRenderMode> solverResults;

    public RelationLayout(Relation r, RelationStyle style) {
        super(r, style.getLabelStyle());
        assert r != null && style != null;
        this.style = style;
    }

    @Override
    public void buildPaths(SchemaPath path, Style model) {
        // Note: RelationLayout.measure() also sets child SchemaPath at line ~591.
        // buildPaths() runs at construction time; measure() re-sets as a safety net.
        // The two paths should produce identical results.
        setSchemaPath(path);
        for (SchemaNode child : getNode().getChildren()) {
            SchemaNodeLayout childLayout = model.layout(child);
            childLayout.buildPaths(path.child(child.getField()), model);
        }
    }

    @Override
    public void adjustHeight(double delta) {
        super.adjustHeight(delta);
        if (useCrosstab) {
            // CROSSTAB rows behave like table rows: distribute delta per row
            if (resolvedCardinality > 0) {
                double subDelta = delta / resolvedCardinality;
                if (subDelta >= 1.0) {
                    cellHeight = Style.snap(cellHeight + subDelta);
                }
            }
        } else if (useTable) {
            double subDelta = delta / resolvedCardinality;
            if (subDelta >= 1.0) {
                cellHeight = Style.snap(cellHeight + subDelta);
                children.forEach(f -> f.adjustHeight(subDelta));
            }
        } else {
            double subDelta = delta / columnSets.size();
            if (subDelta >= 1.0) {
                columnSets.forEach(c -> c.adjustHeight(subDelta));
            }
        }
    }

    public double baseRowCellHeight(double extended) {
        return Style.snap(extended - style.getRowCellVerticalInset());
    }

    private static final double HEIGHT_DISTRIBUTION_CAP = 0.5;

    @Override
    protected void distributeExtraHeight(double availableHeight) {
        boolean tableLike = useTable || useCrosstab;
        if (!tableLike || availableHeight <= 0 || height <= 0
            || availableHeight <= height || resolvedCardinality <= 0) {
            return;
        }
        double deficit = availableHeight - height;
        double perRow = deficit / resolvedCardinality;
        // Skip sub-pixel per-row adjustments: adjustHeight only updates
        // cellHeight when subDelta >= 1.0, so smaller distributions
        // create a mismatch where height > cellHeight * count,
        // which breaks VirtualFlow scroll detection.
        if (perRow < 1.0) {
            return;
        }
        // Soft cap: don't let any row grow more than 50% beyond its
        // computed height, preventing a single line swimming in space.
        double maxExtra = cellHeight * HEIGHT_DISTRIBUTION_CAP;
        if (perRow > maxExtra) {
            perRow = maxExtra;
            deficit = perRow * resolvedCardinality;
        }
        if (deficit >= 1.0) {
            adjustHeight(deficit);
        }
    }

    @Override
    public LayoutCell<? extends Region> buildColumn(double rendered,
                                                    FocusTraversal<?> parentTraversal,
                                                    Style model) {
        return new NestedRow(rendered, this, resolvedCardinality,
                             parentTraversal, model, style, false);
    }

    public TableHeader buildColumnHeader() {
        return new TableHeader(Style.snap(justifiedWidth), columnHeaderHeight,
                               children);
    }

    @Override
    public LayoutCell<?> buildControl(FocusTraversal<?> parentTraversal,
                                      Style model) {
        if (useCrosstab) {
            return buildCrosstab(parentTraversal, model);
        }
        return useTable ? buildNestedTable(parentTraversal, model)
                        : buildOutline(parentTraversal, model);
    }

    public LayoutCell<NestedTable> buildNestedTable(FocusTraversal<?> parentTraversal,
                                                    Style model) {
        return new NestedTable(resolvedCardinality, this, parentTraversal,
                               model, style, rootLevel);
    }

    public Outline buildOutline(FocusTraversal<?> parentTraversal,
                                Style model) {
        Outline outline = new Outline(getJustifiedWidth(), cellHeight,
                                      columnSets, resolvedCardinality, this,
                                      parentTraversal, model, style,
                                      labelWidth);
        return outline;
    }

    @Override
    public double calculateLabelWidth() {
        return labelWidth(getLabel());
    }

    @Override
    public double calculateTableColumnWidth() {
        return children.stream()
                       .mapToDouble(c -> c.calculateTableColumnWidth())
                       .sum()
               + style.getRowCellHorizontalInset()
               + style.getRowHorizontalInset();
    }

    @Override
    public double cellHeight(int cardinality, double width) {
        if (height > 0) {
            return height;
        }
        resolvedCardinality = resolveCardinality(cardinality);
        if (useCrosstab) {
            calculateCrosstabHeight();
        } else if (useTable) {
            calculateTableHeight();
        } else {
            calculateOutlineHeight();
        }
        return height;
    }

    @Override
    public Function<Double, ColumnHeader> columnHeader() {
        List<Function<Double, ColumnHeader>> nestedHeaders = children.stream()
                                                                     .map(c -> c.columnHeader())
                                                                     .collect(Collectors.toList());
        return rendered -> {
            double width = Style.snap(justifiedWidth + columnHeaderIndentation);
            return new ColumnHeader(width, rendered, this, nestedHeaders);
        };
    }

    @Override
    public double columnHeaderHeight() {
        if (columnHeaderHeight <= 0) {
            columnHeaderHeight = Style.snap((labelStyle.getHeight())
                                            + children.stream()
                                                      .mapToDouble(c -> c.columnHeaderHeight())
                                                      .max()
                                                      .orElse(0.0));
        }
        return columnHeaderHeight;
    }

    @Override
    public double columnWidth() {
        return Style.snap(columnWidth + style.getOutlineCellHorizontalInset());
    }

    @Override
    public void compress(double justified) {
        compress(justified, true);
    }

    // Package-private for evaluation testing (Kramer-avn F2+F3)
    void compress(double justified, boolean useHalfWidthGuard) {
        if (useTable) {
            justifyTable(justified);
            return;
        }
        columnSets.clear();
        justifiedWidth = Style.snap(justified
                                    - style.getOutlineHorizontalInset()
                                    - style.getOutlineCellHorizontalInset());
        ColumnSet current = null;
        double available = Style.snap(justifiedWidth
                                      - style.getSpanHorizontalInset());
        double halfWidth = Style.snap((available / 2.0)
                                      - style.getColumnHorizontalInset()
                                      - style.getElementHorizontalInset());
        double lw = measureResult != null ? measureResult.labelWidth() : labelWidth;
        int avgChildCard = measureResult != null ? measureResult.averageChildCardinality() : averageChildCardinality;
        for (SchemaNodeLayout child : children) {
            double childWidth = lw + child.layoutWidth();
            // Paper §3.4: outline-mode relations are excluded from column sets
            boolean excluded = (child instanceof RelationLayout rl) && !rl.isUseTable();
            boolean wideChild = useHalfWidthGuard && childWidth > halfWidth;
            if (excluded || wideChild || current == null) {
                current = new ColumnSet();
                columnSets.add(current);
                current.add(child);
                if (excluded || wideChild) {
                    current = null;
                }
            } else {
                current.add(child);
            }
        }
        cellHeight = Style.snap(columnSets.stream()
                                          .mapToDouble(cs -> Style.snap(cs.compress(avgChildCard,
                                                                                    available,
                                                                                    style,
                                                                                    lw)
                                                                        + style.getSpanVerticalInset()))
                                          .sum());
    }

    @Override
    public JsonNode extractFrom(JsonNode datum) {
        Function<JsonNode, JsonNode> ex = extractor != null ? extractor : n -> n;
        JsonNode extracted = ex.apply(datum);
        JsonNode result = node.extractFrom(extracted);
        // Apply base sort (sort-fields / Relation.sortFields) unless
        // sort-expression is present — in that case, sort happens after formulas.
        if (sortComparator != null && sortExprAst == null && result instanceof ArrayNode arr) {
            sortArrayNode(arr, sortComparator);
        }
        if (hideIfEmptyFilter != null && result instanceof ArrayNode arr) {
            result = filterArrayNode(arr, hideIfEmptyFilter);
        }
        // Expression pipeline replay (filter → formula → sort-expression)
        if (cachedEvaluator != null) {
            if (filterExprAst != null && result instanceof ArrayNode arr) {
                final ExpressionEvaluator ev = cachedEvaluator;
                final Expr fAst = filterExprAst;
                result = filterArrayNode(arr, row -> ev.toBoolean(ev.evaluate(fAst, row)));
            }
            if (!formulaEntries.isEmpty() && result instanceof ArrayNode arr) {
                result = applyFormulas(arr, cachedEvaluator);
            }
            // Sort after formulas only when sort-expression is active (may reference formula fields)
            if (sortComparator != null && sortExprAst != null && result instanceof ArrayNode arr) {
                sortArrayNode(arr, sortComparator);
            }
        } else if (sortComparator != null && sortExprAst != null
                   && result instanceof ArrayNode arr) {
            // Expression evaluator gone but sort comparator persists
            sortArrayNode(arr, sortComparator);
        }
        return result;
    }

    /**
     * Apply formula overlays to each row in the array.
     * Returns a new ArrayNode with formula results injected.
     */
    private ArrayNode applyFormulas(ArrayNode data, ExpressionEvaluator evaluator) {
        ArrayNode overlaid = JsonNodeFactory.instance.arrayNode();
        for (JsonNode row : data) {
            ObjectNode newRow = JsonNodeFactory.instance.objectNode();
            if (row.isObject()) {
                newRow.setAll((ObjectNode) row);
            }
            for (var entry : formulaEntries) {
                Object result = evaluator.evaluate(entry.getValue(), newRow);
                newRow.set(entry.getKey(), ExpressionEvaluator.toJsonNode(result));
            }
            overlaid.add(newRow);
        }
        return overlaid;
    }

    public void forEach(Consumer<? super SchemaNodeLayout> action) {
        children.forEach(action);
    }

    public int getAverageCardinality() {
        return averageChildCardinality;
    }

    public double getCellHeight() {
        return cellHeight;
    }

    @Override
    public Relation getNode() {
        return (Relation) node;
    }

    public String getStyleClass() {
        return node.getField();
    }

    /** Returns the {@link RelationStyle} governing this layout's insets and metrics. */
    public RelationStyle getStyle() {
        return style;
    }

    public boolean isUseTable() {
        return useTable;
    }

    /**
     * Returns the direct child layout list. Exposed so tests can build
     * trees without going through Style.layout().
     */
    public List<SchemaNodeLayout> getChildren() {
        return children;
    }

    public MeasureResult getMeasureResult() {
        return measureResult;
    }

    /** Aggregate expression results from the last measure() pass, keyed by child field name. */
    public Map<String, Object> getAggregateResults() {
        return aggregateResults;
    }

    /** Returns true when every descendant PrimitiveLayout has converged.
     *  allMatch on empty stream is vacuously true — a relation with no children is trivially stable. */
    @Override
    public boolean isConverged() {
        return children.stream().allMatch(SchemaNodeLayout::isConverged);
    }

    public LayoutResult computeLayout(double width) {
        layout(width);
        // primitiveMode is not applicable to relation nodes; TEXT is a sentinel value
        return new LayoutResult(
            useCrosstab ? RelationRenderMode.CROSSTAB
                        : useTable ? RelationRenderMode.TABLE
                        : RelationRenderMode.OUTLINE,
            PrimitiveRenderMode.TEXT,
            false,
            tableColumnWidth,
            columnHeaderIndentation,
            columnWidth,
            children.stream()
                .map(c -> {
                    if (c instanceof PrimitiveLayout pl) return pl.computeLayout(width);
                    if (c instanceof RelationLayout rl) return rl.computeLayout(width);
                    return null;
                })
                .toList()
        );
    }

    /**
     * Snapshot the layout decisions already computed by a prior {@code autoLayout()} run.
     * Unlike {@link #computeLayout(double)}, this does NOT call {@code layout()} again —
     * it reads the current in-memory state (useTable, justifiedWidth, etc.).
     * Safe to call immediately after {@code autoLayout()} without side effects.
     */
    public LayoutResult snapshotLayoutResult() {
        return new LayoutResult(
            useTable ? RelationRenderMode.TABLE : RelationRenderMode.OUTLINE,
            PrimitiveRenderMode.TEXT,  // primitiveMode is not applicable to relations; TEXT is a sentinel
            false,                     // useVerticalHeader: not applicable at relation level
            tableColumnWidth,
            columnHeaderIndentation,
            columnWidth,
            children.stream()
                .map(SchemaNodeLayout::snapshotLayoutResult)
                .toList()
        );
    }

    /**
     * Compose a {@link LayoutDecisionNode} tree from the current layout state.
     * Recursively snapshots all children. No layout side effects.
     */
    @Override
    public LayoutDecisionNode snapshotDecisionTree() {
        SchemaPath path = getSchemaPath();
        String field = path != null ? path.leaf() : getField();

        List<ColumnSetSnapshot> colSetSnaps = columnSets.stream()
            .map(cs -> cs.toSnapshot(cellHeight))
            .toList();

        CompressResult compressSnap = new CompressResult(
            justifiedWidth, colSetSnaps, cellHeight, List.of());

        HeightResult heightSnap = new HeightResult(
            height, cellHeight, resolvedCardinality, columnHeaderHeight, List.of());

        List<LayoutDecisionNode> childNodes = children.stream()
            .map(SchemaNodeLayout::snapshotDecisionTree)
            .toList();

        return new LayoutDecisionNode(
            path,
            field,
            measureResult,
            snapshotLayoutResult(),
            compressSnap,
            heightSnap,
            colSetSnaps,
            childNodes
        );
    }

    public CompressResult computeCompress(double justified) {
        compress(justified);
        List<ColumnSetSnapshot> snaps = columnSets.stream()
            .map(cs -> cs.toSnapshot(cellHeight))
            .toList();
        return new CompressResult(
            justifiedWidth,
            snaps,
            cellHeight,
            children.stream()
                .map(c -> {
                    if (c instanceof PrimitiveLayout pl) return pl.computeCompress(justified);
                    if (c instanceof RelationLayout rl) return rl.computeCompress(justified);
                    return null;
                })
                .toList()
        );
    }

    public HeightResult computeCellHeight(int cardinality, double justified) {
        double h = cellHeight(cardinality, justified);
        return new HeightResult(
            h,
            cellHeight,
            resolvedCardinality,
            columnHeaderHeight,
            List.of()
        );
    }

    @Override
    public double justify(double justifed) {
        justifyColumn(Style.snap(justifed - columnHeaderIndentation));
        return justifed;
    }

    public double justifyTable(double justifed) {
        justifyColumn(Style.snap(justifed - columnHeaderIndentation));
        return justifed;
    }

    @Override
    public double layout(double width) {
        clear();
        double lw = measureResult != null ? measureResult.labelWidth() : labelWidth;
        double available = (width - lw)
                           - style.getOutlineCellHorizontalInset();
        assert available > 0;
        columnWidth = children.stream()
                              .mapToDouble(c -> c.layout(available))
                              .max()
                              .orElse(0.0)
                      + lw;
        double tableWidth = calculateTableColumnWidth();

        // Solver-driven decision: if a pre-computed render-mode map is present,
        // use it; otherwise fall back to the greedy width check (Paper §3.3).
        SchemaPath myPath = getSchemaPath();
        if (solverResults != null && myPath != null) {
            RelationRenderMode mode = solverResults.get(myPath);
            if (mode == null) {
                // Path mismatch: solver map has no entry for this node's path.
                // This indicates a stale or mismatched constraint tree; fall through to greedy.
                LOG.warning(() -> "Solver results present but no entry for path: " + myPath
                        + "; falling back to greedy layout decision");
            } else if (mode == RelationRenderMode.TABLE) {
                return nestTableColumn(Indent.TOP, new Insets(0));
            } else if (mode == RelationRenderMode.CROSSTAB) {
                // RF-5: invoke layoutCrosstab() when solver assigns CROSSTAB mode.
                // Guard: pivotStats must be non-null (populated during measure phase).
                MeasureResult mr = getMeasureResult();
                if (mr != null && mr.pivotStats() != null && !mr.pivotStats().pivotValues().isEmpty()) {
                    return layoutCrosstab(width, mr.pivotStats().pivotValues());
                }
                // Fallback: no pivot data available — degrade to OUTLINE
                LOG.fine(() -> "Solver assigned CROSSTAB but no pivot data for path: " + myPath
                          + "; degrading to OUTLINE");
                return columnWidth();
            } else {
                // OUTLINE: standard column layout
                return columnWidth();
            }
        }

        // Paper §3.3: use table whenever it fits the available width from parent
        // Include nestedHorizontalInset which nestTableColumn() will add
        if (tableWidth + style.getNestedHorizontalInset() <= width) {
            return nestTableColumn(Indent.TOP, new Insets(0));
        }
        return columnWidth();
    }

    @Override
    public double layoutWidth() {
        return useTable ? tableColumnWidth() : columnWidth();
    }

    @Override
    public double measure(JsonNode datum,
                          Function<JsonNode, JsonNode> extractor, Style model) {
        clear();
        children.clear();
        // Store extractor for build-phase extractFrom() calls that arrive
        // when measure() is invoked outside the normal fold() pipeline.
        if (this.extractor == null) {
            this.extractor = extractor;
        }
        // Compute sort comparator once per measure phase; stored for extractFrom().
        // Check stylesheet for sort-fields override; Relation is the fallback.
        {
            LayoutStylesheet _ss = (model != null) ? model.getStylesheet() : null;
            SchemaPath _path = getSchemaPath();
            Relation _relation = getNode();
            List<String> _relFields = _relation.getSortFields();
            if (!_relFields.isEmpty()) {
                // Relation has explicit sortFields — use them directly (highest priority)
                sortComparator = buildSortComparator(_relation);
            } else {
                // Check stylesheet for sort-fields
                String _stylesheetSort = (_ss != null && _path != null)
                    ? _ss.getString(_path, LayoutPropertyKeys.SORT_FIELDS, "") : "";
                if (!_stylesheetSort.isEmpty()) {
                    List<String> _fields = List.of(_stylesheetSort.split(","));
                    Comparator<JsonNode> _cmp = fieldComparator(_fields.get(0));
                    for (int _i = 1; _i < _fields.size(); _i++) {
                        _cmp = _cmp.thenComparing(fieldComparator(_fields.get(_i)));
                    }
                    sortComparator = _cmp;
                } else {
                    sortComparator = buildSortComparator(_relation);
                }
            }
        }
        // Sort the datum array before measuring children so that measurement
        // reflects data order consistent with the build phase.
        // Copy before sorting to avoid mutating the caller's datum in-place.
        if (sortComparator != null && datum instanceof ArrayNode datumArray) {
            ArrayNode sorted = datumArray.deepCopy();
            sortArrayNode(sorted, sortComparator);
            datum = sorted;
        }
        // Resolve hide-if-empty filter: only active when hideIfEmpty=true and
        // autoFoldable is null (filtering is incompatible with autoFold).
        // Priority: Relation.getHideIfEmpty() (when non-null) > stylesheet > false.
        Boolean hideOverride = getNode().getHideIfEmpty();
        boolean shouldHide;
        if (hideOverride != null) {
            shouldHide = hideOverride;
        } else {
            LayoutStylesheet _hideSheet = (model != null) ? model.getStylesheet() : null;
            SchemaPath _hidePath = getSchemaPath();
            shouldHide = (_hideSheet != null && _hidePath != null)
                && _hideSheet.getBoolean(_hidePath, LayoutPropertyKeys.HIDE_IF_EMPTY, false);
        }
        boolean shouldFilter = shouldHide && getNode().getAutoFoldable() == null;
        if (shouldFilter) {
            hideIfEmptyFilter = item -> hasNonEmptyChildren(item, getNode());
            datum = filterArrayNode(datum, hideIfEmptyFilter);
        } else {
            hideIfEmptyFilter = null;
        }

        // --- Expression pipeline (RDR-021): filter → formula → aggregate → sort ---
        SchemaPath myPath = getSchemaPath();
        LayoutStylesheet stylesheet = (model != null) ? model.getStylesheet() : null;
        ExpressionEvaluator evaluator = (model != null) ? model.getExpressionEvaluator() : null;
        if (evaluator != null && stylesheet != null) {
            evaluator.syncVersion(stylesheet.getVersion());
        }
        // Reset expression state from previous measure
        filterExprAst = null;
        formulaEntries = List.of();
        sortExprAst = null;
        cachedEvaluator = evaluator;
        aggregateResults = null;

        // 1. filter-expression: per-row boolean predicate; false/null rows excluded
        if (evaluator != null && stylesheet != null && myPath != null) {
            String filterExpr = stylesheet.getString(myPath, LayoutPropertyKeys.FILTER_EXPRESSION, null);
            if (filterExpr != null) {
                try {
                    filterExprAst = evaluator.compile(filterExpr);
                    final ExpressionEvaluator ev = evaluator;
                    final Expr fAst = filterExprAst;
                    datum = filterArrayNode(datum, row -> ev.toBoolean(ev.evaluate(fAst, row)));
                } catch (ParseException e) {
                    LOG.warning(() -> "Invalid filter-expression at " + myPath + ": " + e.getMessage());
                }
            }
        }

        // 2. formula-expression: per-row virtual field computation
        if (evaluator != null && stylesheet != null && myPath != null
                && datum instanceof ArrayNode datumArray) {
            Map<String, Expr> formulas = new LinkedHashMap<>();
            Map<String, Set<String>> deps = new LinkedHashMap<>();
            for (var child : getNode().getChildren()) {
                if (child instanceof com.chiralbehaviors.layout.schema.Primitive) {
                    SchemaPath childPath = myPath.child(child.getField());
                    String formulaExpr = stylesheet.getString(childPath,
                        LayoutPropertyKeys.FORMULA_EXPRESSION, null);
                    if (formulaExpr != null) {
                        try {
                            Expr ast = evaluator.compile(formulaExpr);
                            if (ExpressionEvaluator.containsAggregate(ast)) {
                                LOG.warning(() -> "formula-expression at " + childPath
                                    + " contains aggregate call; treating as absent");
                            } else {
                                formulas.put(child.getField(), ast);
                                deps.put(child.getField(),
                                    ExpressionEvaluator.extractFieldRefs(ast));
                            }
                        } catch (ParseException e) {
                            LOG.warning(() -> "Invalid formula-expression at " + childPath
                                + ": " + e.getMessage());
                        }
                    }
                }
            }
            if (!formulas.isEmpty()) {
                Set<String> cycles = ExpressionEvaluator.detectCycles(deps);
                if (!cycles.isEmpty()) {
                    LOG.warning(() -> "Circular formula references detected: " + cycles
                        + " at " + myPath + "; skipping cycle members");
                    cycles.forEach(formulas::remove);
                    cycles.forEach(deps::remove);
                }
                List<String> evalOrder = ExpressionEvaluator.topologicalSort(deps);
                // Store for extractFrom() reuse
                var entries = new ArrayList<Map.Entry<String, Expr>>();
                for (String field : evalOrder) {
                    Expr ast = formulas.get(field);
                    if (ast != null) {
                        entries.add(Map.entry(field, ast));
                    }
                }
                formulaEntries = List.copyOf(entries);
                // Overlay formula results on each row
                datum = applyFormulas(datumArray, evaluator);
            }
        }

        // 3. aggregate-expression: compute aggregates (rendering deferred)
        if (evaluator != null && stylesheet != null && myPath != null) {
            Map<String, Object> aggResults = null;
            for (var child : getNode().getChildren()) {
                if (child instanceof com.chiralbehaviors.layout.schema.Primitive) {
                    SchemaPath childPath = myPath.child(child.getField());
                    String aggExpr = stylesheet.getString(childPath,
                        LayoutPropertyKeys.AGGREGATE_EXPRESSION, null);
                    if (aggExpr != null) {
                        try {
                            Expr ast = evaluator.compile(aggExpr);
                            if (!ExpressionEvaluator.containsAggregate(ast)) {
                                LOG.warning(() -> "aggregate-expression at " + childPath
                                    + " has no aggregate call; treating as absent");
                            } else {
                                List<JsonNode> rows = new ArrayList<>();
                                datum.forEach(rows::add);
                                Object result;
                                if (ast instanceof Expr.AggregateCall agg) {
                                    result = evaluator.evaluateAggregate(agg, rows);
                                } else {
                                    result = evaluateWithAggregates(evaluator, ast, rows);
                                }
                                if (aggResults == null) aggResults = new HashMap<>();
                                aggResults.put(child.getField(), result);
                            }
                        } catch (ParseException e) {
                            LOG.warning(() -> "Invalid aggregate-expression at " + childPath
                                + ": " + e.getMessage());
                        }
                    }
                }
            }
            aggregateResults = aggResults;
        }

        // 4. sort-expression: per-row sort key, takes precedence over sort-fields
        if (evaluator != null && stylesheet != null && myPath != null) {
            String sortExpr = stylesheet.getString(myPath,
                LayoutPropertyKeys.SORT_EXPRESSION, null);
            if (sortExpr != null) {
                try {
                    sortExprAst = evaluator.compile(sortExpr);
                    final ExpressionEvaluator ev = evaluator;
                    final Expr sAst = sortExprAst;
                    Comparator<JsonNode> exprCmp = (a, b) -> {
                        Object va = ev.evaluate(sAst, a);
                        Object vb = ev.evaluate(sAst, b);
                        return compareValues(va, vb);
                    };
                    if (sortComparator != null) {
                        sortComparator = exprCmp.thenComparing(sortComparator);
                    } else {
                        sortComparator = exprCmp;
                    }
                    if (datum instanceof ArrayNode sortArray) {
                        ArrayNode sorted = sortArray.deepCopy();
                        sortArrayNode(sorted, sortComparator);
                        datum = sorted;
                    }
                } catch (ParseException e) {
                    LOG.warning(() -> "Invalid sort-expression at " + myPath
                        + ": " + e.getMessage());
                }
            }
        }
        // --- End expression pipeline ---

        // Collect pivot values AFTER sort+filter, BEFORE child iteration.
        // Reads pivot-field from stylesheet; if non-empty, scans datum for distinct values.
        PivotStats pivotStats = null;
        if (stylesheet != null && myPath != null) {
            String pivotField = stylesheet.getString(myPath, "pivot-field", "");
            if (!pivotField.isEmpty()) {
                LinkedHashSet<String> seen = new LinkedHashSet<>();
                if (datum instanceof ArrayNode pivotArray) {
                    for (JsonNode item : pivotArray) {
                        JsonNode fieldNode = item.get(pivotField);
                        if (fieldNode != null && !fieldNode.isNull()) {
                            seen.add(fieldNode.asText());
                        }
                    }
                }
                pivotStats = new PivotStats(new ArrayList<>(seen), seen.size());
            }
        }

        double sum = 0;
        columnWidth = 0;
        int singularChildren = 0;
        maxCardinality = datum.size();

        SchemaPath parentPath = myPath;
        for (SchemaNode child : getNode().getChildren()) {
            Fold fold = model.layout(child)
                             .fold(datum, extractor, model);
            SchemaNodeLayout childLayout = fold.layout();
            SchemaPath childPath = (parentPath != null)
                ? parentPath.child(child.getField()) : null;
            if (childPath != null) {
                childLayout.setSchemaPath(childPath);
            }
            // Skip invisible children — visible defaults to true.
            if (stylesheet != null && childPath != null
                    && !stylesheet.getBoolean(childPath, LayoutPropertyKeys.VISIBLE, true)) {
                continue;
            }
            children.add(childLayout);
            columnWidth = Style.snap(Math.max(columnWidth, childLayout
                                                               .measure(fold.datum(),
                                                                        n -> n,
                                                                        model)));
            if (fold.averageCardinality() == 1) {
                singularChildren++;
            } else {
                sum += fold.averageCardinality();
            }
        }
        int effectiveChildren = children.size() - singularChildren;
        averageChildCardinality = Math.max(1,
                                           Math.min(style.getMaxAverageCardinality(),
                                                    effectiveChildren == 0 ? 1
                                                                           : (int) Math.ceil(sum
                                                                                             / effectiveChildren)));

        labelWidth = children.stream()
                             .mapToDouble(child -> child.calculateLabelWidth())
                             .max()
                             .orElse(0.0);
        // Paper Table 1: OutlineMaxLabelWidth cap
        labelWidth = Math.min(labelWidth, style.getOutlineMaxLabelWidth());
        // Paper Table 1: Bullet width budget
        labelWidth += style.getBulletWidth();
        columnWidth = Style.snap(labelWidth + columnWidth);

        List<MeasureResult> childResults = children.stream()
            .map(c -> {
                if (c instanceof PrimitiveLayout pl) return pl.getMeasureResult();
                if (c instanceof RelationLayout rl) return rl.getMeasureResult();
                return null;
            })
            .toList();

        measureResult = new MeasureResult(
            labelWidth, columnWidth, 0, 0,
            0, false,
            averageChildCardinality, maxCardinality,
            extractor, childResults, null, null, pivotStats, null
        );

        return columnWidth + style.getElementHorizontalInset()
               + style.getColumnHorizontalInset()
               + style.getSpanHorizontalInset()
               + style.getOutlineCellHorizontalInset();
    }

    @Override
    public double nestTableColumn(Indent indent, Insets indentation) {
        useTable = true;
        cellHeight = -1.0;
        columnHeaderHeight = -1.0;
        height = -1.0;
        columnHeaderIndentation = indentation.getLeft() + indentation.getRight()
                                  + style.getNestedHorizontalInset();
        tableColumnWidth = Style.snap(children.stream()
                                              .mapToDouble(c -> {
                                                  Indent child = indent(indent,
                                                                        c);
                                                  return c.nestTableColumn(child,
                                                                           indent.indent(indentation,
                                                                                         child,
                                                                                         style.getNestedInsets()));
                                              })
                                              .sum());
        return tableColumnWidth();
    }

    @Override
    public void normalizeRowHeight(double normalized) {
        double deficit = normalized - height;
        double childDeficit = deficit / resolvedCardinality;
        cellHeight = Style.snap(cellHeight + childDeficit);
        height = normalized;

        children.forEach(c -> c.normalizeRowHeight(cellHeight));
    }

    @Override
    public double rowHeight(int cardinality, double justified) {
        resolvedCardinality = resolveCardinality(cardinality);
        cellHeight = calculateRowHeight();
        height = Style.snap((resolvedCardinality * cellHeight)
                            + style.getRowVerticalInset());
        return height;
    }

    @Override
    public double tableColumnWidth() {
        assert tableColumnWidth > 0.0 : String.format("%s tcw <= 0: %s",
                                                      node.getLabel(),
                                                      tableColumnWidth);
        return Style.snap(tableColumnWidth + columnHeaderIndentation);
    }

    @Override
    public String toString() {
        return String.format("RelationLayout [%s %s height x %s card, width {o: %s, t: %s, j: %s} ]",
                             node.getField(), height, averageChildCardinality,
                             columnWidth, tableColumnWidth, justifiedWidth);
    }

    protected void calculateOutlineHeight() {
        height = Style.snap((resolvedCardinality
                             * Style.snap(cellHeight
                                          + style.getOutlineCellVerticalInset()))
                            + style.getOutlineVerticalInset());
    }

    @Override
    protected void calculateRootHeight() {
        if (useTable) {
            int maxCard = measureResult != null ? measureResult.maxCardinality() : maxCardinality;
            cellHeight(maxCard, justifiedWidth);
        }
    }

    protected double calculateRowHeight() {
        double elementHeight = Style.snap(children.stream()
                                                  .mapToDouble(child -> child.rowHeight(averageChildCardinality,
                                                                                        child.getJustifiedWidth()))
                                                  .max()
                                                  .orElse(0.0));
        children.forEach(c -> c.normalizeRowHeight(elementHeight));
        return Style.snap(elementHeight + style.getRowCellVerticalInset());
    }

    protected void calculateTableHeight() {
        columnHeaderHeight();
        cellHeight = calculateRowHeight();
        height = Style.snap((resolvedCardinality * cellHeight)
                            + columnHeaderHeight)
                 + style.getRowVerticalInset() + style.getTableVerticalInset();
    }

    @Override
    protected void clear() {
        super.clear();
        useTable = false;
        useCrosstab = false;
        crosstabPivotValues = List.of();
        crosstabColumnWidth = 0.0;
        tableColumnWidth = -1.0;
        columnHeaderHeight = -1.0;
    }

    @Override
    protected Fold fold(JsonNode datum, Function<JsonNode, JsonNode> extractor,
                        Style model) {

        Relation fold = getNode().getAutoFoldable();
        if (fold != null) {
            ArrayNode flattened = flatten(getNode(), datum);
            return model.layout(fold)
                        .fold(flattened, item -> {
                            JsonNode extracted = extractor.apply(item);
                            ArrayNode flat = flatten(getNode(), extracted);
                            return flat;
                        }, model);
        }
        this.extractor = extractor;

        return fold(datum);
    }

    protected Indent indent(Indent parent, SchemaNodeLayout child) {

        boolean isFirst = isFirst(child);
        boolean isLast = isLast(child);
        if (isFirst && isLast) {
            return Indent.SINGULAR;
        }
        if (isFirst) {
            return Indent.LEFT;
        } else if (isLast) {
            return Indent.RIGHT;
        } else {
            return Indent.NONE;
        }
    }

    protected boolean isFirst(SchemaNodeLayout child) {
        if (children.isEmpty()) return false;
        return child.equals(children.get(0));
    }

    protected boolean isLast(SchemaNodeLayout child) {
        if (children.isEmpty()) return false;
        return child.equals(children.get(children.size() - 1));
    }

    protected void justifyColumn(double available) {
        justifiedWidth = Style.snap(available);
        if (children.isEmpty()) return;

        // Phase 1: greedy un-rotation in document order. Not optimal for all
        // configurations but avoids O(n log n) sort in the hot layout path.
        double extraSpace = available - tableColumnWidth;
        for (SchemaNodeLayout child : children) {
            if (child instanceof PrimitiveLayout pl && pl.isUseVerticalHeader()) {
                double needed = pl.getLabelWidth() - pl.tableColumnWidth();
                if (extraSpace >= needed) {
                    extraSpace -= needed;
                    pl.setUseVerticalHeader(false);
                }
            }
        }

        // extraSpace is used only to gate Phase 1 un-rotation decisions.
        // Phase 2 recomputes available width from effectiveChildWidth(),
        // which automatically reflects the post-un-rotation state.

        // Partition children into fixed-length and variable-length
        // RelationLayout children are treated as variable-length by default
        List<SchemaNodeLayout> variableChildren = new ArrayList<>();
        double fixedTotal = 0;
        double varNaturalTotal = 0;

        for (SchemaNodeLayout child : children) {
            double ew = effectiveChildWidth(child);
            if (child instanceof PrimitiveLayout pl && !pl.isVariableLength()) {
                fixedTotal += ew;
            } else {
                variableChildren.add(child);
                varNaturalTotal += ew;
            }
        }

        // Edge case: no variable-length children → proportional for all
        if (variableChildren.isEmpty()) {
            if (fixedTotal <= 0) {
                double equalShare = Style.relax(available / children.size());
                children.forEach(c -> c.justify(equalShare));
                return;
            }
            double totalEffective = fixedTotal;
            double rem = available;
            SchemaNodeLayout last = children.get(children.size() - 1);
            for (SchemaNodeLayout child : children) {
                double childJustified;
                if (child.equals(last)) {
                    childJustified = rem;
                } else {
                    double ew = effectiveChildWidth(child);
                    childJustified = Style.relax(available * (ew / totalEffective));
                    rem -= childJustified;
                }
                child.justify(childJustified);
            }
            return;
        }

        // Phase 2a: justify fixed-length children at effective width
        for (SchemaNodeLayout child : children) {
            if (child instanceof PrimitiveLayout pl && !pl.isVariableLength()) {
                child.justify(effectiveChildWidth(child));
            }
        }

        // Phase 2b: distribute remaining to variable-length children
        double varAvailable = available - fixedTotal;
        if (varNaturalTotal <= 0) {
            double equalShare = Style.relax(varAvailable / variableChildren.size());
            variableChildren.forEach(c -> c.justify(equalShare));
            return;
        }
        SchemaNodeLayout lastVar = variableChildren.get(variableChildren.size() - 1);
        double varRemaining = varAvailable;
        for (SchemaNodeLayout child : variableChildren) {
            double childJustified;
            if (child.equals(lastVar)) {
                childJustified = Math.max(0.0, varRemaining); // anti-drift, clamp
            } else {
                double ew = effectiveChildWidth(child);
                childJustified = Style.relax(varAvailable * (ew / varNaturalTotal));
                varRemaining -= childJustified;
            }
            child.justify(childJustified);
        }
    }

    private double effectiveChildWidth(SchemaNodeLayout child) {
        // Un-rotated (horizontal) primitive headers need at least labelWidth
        if (child instanceof PrimitiveLayout pl && !pl.isUseVerticalHeader()) {
            return Math.max(pl.tableColumnWidth(), pl.getLabelWidth());
        }
        return child.tableColumnWidth();
    }

    protected int resolveCardinality(int cardinality) {
        int maxCard = measureResult != null ? measureResult.maxCardinality() : maxCardinality;
        return Math.max(1, Math.min(cardinality, maxCard));
    }

    public double getJustifiedTableColumnWidth() {
        return snap(justifiedWidth + columnHeaderIndentation);
    }

    // ------------------------------------------------------------------ //
    //  Crosstab API                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Compute crosstab layout for the given pivot values and available width.
     *
     * <p>When {@code pivotValues} is empty the layout degrades to TABLE (or
     * OUTLINE if table does not fit) and a warning is logged. This prevents
     * a crash when no data exists for the pivot field.
     *
     * <p>The method sets {@link #useCrosstab} and caches the pivot column width
     * so that {@link #buildControl}, {@link #adjustHeight}, and
     * {@link #distributeExtraHeight} all branch into the CROSSTAB path.
     *
     * @param availableWidth total available width for this relation
     * @param pivotValues    ordered distinct values of the pivot field
     * @return total computed width used by the crosstab (row-header + all pivot columns)
     */
    public double layoutCrosstab(double availableWidth, List<String> pivotValues) {
        if (pivotValues == null || pivotValues.isEmpty()) {
            LOG.warning(() -> "layoutCrosstab called with empty pivotValues for "
                              + getNode().getField() + "; degrading to TABLE/OUTLINE");
            useCrosstab = false;
            crosstabPivotValues = List.of();
            crosstabColumnWidth = 0.0;
            // Re-run normal layout to ensure useTable is set appropriately
            layout(availableWidth);
            return layoutWidth();
        }

        useCrosstab = true;
        crosstabPivotValues = List.copyOf(pivotValues);

        // Row-header width = outline column width for non-pivot children
        double rowHeaderWidth = Style.snap(columnWidth > 0 ? columnWidth : availableWidth * 0.3);

        // Distribute remaining width equally across pivot columns
        double remaining = availableWidth - rowHeaderWidth;
        crosstabColumnWidth = Style.snap(remaining / pivotValues.size());

        // Overall width = rowHeader + all pivot columns
        double totalWidth = Style.snap(rowHeaderWidth + crosstabColumnWidth * pivotValues.size());

        // Ensure height state is reset so cellHeight() recalculates
        height = -1.0;
        cellHeight = -1.0;

        return totalWidth;
    }

    /** @return true when this layout is in CROSSTAB rendering mode */
    public boolean isCrosstab() {
        return useCrosstab;
    }

    /**
     * Sets the solver-driven render-mode map for this subtree.
     * Propagated to all RelationLayout children so each node can consult it
     * during its own layout() call.  Pass {@code null} to restore greedy behavior.
     */
    public void setSolverResults(Map<SchemaPath, RelationRenderMode> results) {
        this.solverResults = results;
        for (SchemaNodeLayout child : children) {
            if (child instanceof RelationLayout rl) {
                rl.setSolverResults(results);
            }
        }
    }

    /**
     * Build the fixed column-header bar for the crosstab.
     *
     * @return a {@link CrosstabHeader} placed outside the VirtualFlow
     */
    public CrosstabHeader buildCrosstabHeader() {
        return new CrosstabHeader(crosstabPivotValues, crosstabColumnWidth,
                                  columnHeaderHeight > 0 ? columnHeaderHeight
                                                         : Style.snap(labelStyle.getHeight()),
                                  style);
    }

    /**
     * Build the crosstab control. Falls back to a NestedTable placeholder until
     * a full VirtualFlow-based crosstab implementation exists.
     *
     * <p>The crosstab header is built here via {@link #buildCrosstabHeader()}.
     */
    public LayoutCell<?> buildCrosstab(FocusTraversal<?> parentTraversal,
                                        Style model) {
        // Build the stationary header
        buildCrosstabHeader();
        // For this bead, fall back to NestedTable as the scrollable body.
        // A full VirtualFlow<CrosstabRow> implementation is a follow-on bead.
        return buildNestedTable(parentTraversal, model);
    }

    /**
     * Calculate height for crosstab mode. Mirrors {@link #calculateTableHeight()}
     * but uses per-row height based solely on the child row height (pivot cells
     * are peers of the row header, not nested relations).
     */
    protected void calculateCrosstabHeight() {
        columnHeaderHeight();
        cellHeight = calculateRowHeight();
        height = Style.snap((resolvedCardinality * cellHeight) + columnHeaderHeight)
                 + style.getRowVerticalInset() + style.getTableVerticalInset();
    }

    /**
     * Test helper: resets measure-derived state so a second measure+layout
     * cycle can be run in the same test.
     */
    void clearForTest() {
        clear();
        children.clear();
        extractor = null;
        measureResult = null;
        averageChildCardinality = 0;
        maxCardinality = 0;
        labelWidth = 0;
        columnWidth = 0;
    }
}