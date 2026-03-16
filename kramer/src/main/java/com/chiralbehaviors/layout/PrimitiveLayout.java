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

import static com.chiralbehaviors.layout.schema.SchemaNode.asList;
import static com.chiralbehaviors.layout.style.Style.snap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.PrimitiveList;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle.PrimitiveBarStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle.PrimitiveBadgeStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle.PrimitiveSparklineStyle;
import com.chiralbehaviors.layout.style.Style;
import com.chiralbehaviors.layout.table.ColumnHeader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.geometry.Insets;
import javafx.scene.layout.Region;

/**
 *
 * @author halhildebrand
 *
 */
public final class PrimitiveLayout extends SchemaNodeLayout {
    protected int                  averageCardinality;
    protected double               dataWidth;
    private boolean                isVariableLength          = true;
    protected double               maxWidth;
    protected final PrimitiveStyle style;
    private double                 cellHeight;
    private boolean                useVerticalHeader         = false;
    private MeasureResult          measureResult;
    private PrimitiveRenderMode    renderMode                = PrimitiveRenderMode.TEXT;
    /** Sorted distinct string values for BADGE CSS class assignment; null when not BADGE. */
    private List<String>           badgeValues               = null;

    // Cached style instances — re-used across buildControl() calls to avoid per-call allocation
    private PrimitiveBarStyle        cachedBarStyle;
    private PrimitiveBadgeStyle      cachedBadgeStyle;
    private PrimitiveSparklineStyle  cachedSparklineStyle;

    // Convergence detection state (Kramer-16k)
    private MeasureResult          frozenResult;
    private long                   frozenStylesheetVersion   = -1;
    private long                   lastSeenStylesheetVersion = -1;
    private double                 lastP90Width              = Double.NaN;
    private int                    consecutiveStableCount    = 0;

    // Last stylesheet seen during buildControl — used by cell renderers (Kramer-7c4)
    private LayoutStylesheet       currentStylesheet;

    public PrimitiveLayout(Primitive p, PrimitiveStyle style) {
        super(p, style.getLabelStyle());
        this.style = style;
    }

    @Override
    public void buildPaths(SchemaPath path, Style model) {
        setSchemaPath(path);
    }

    public LayoutCell<?> buildCell(FocusTraversal<?> pt) {
        return style.build(pt, this);
    }

    @Override
    public LayoutCell<? extends Region> buildColumn(double rendered,
                                                    FocusTraversal<?> parentTraversal,
                                                    Style model) {
        LayoutCell<? extends Region> control = buildControl(parentTraversal,
                                                            model);
        control.getNode()
               .setMinSize(justifiedWidth, rendered);
        control.getNode()
               .setPrefSize(justifiedWidth, rendered);
        control.getNode()
               .setMaxSize(justifiedWidth, rendered);
        return control;
    }

    @Override
    public LayoutCell<? extends Region> buildControl(FocusTraversal<?> parentTraversal,
                                                     Style model) {
        currentStylesheet = (model != null) ? model.getStylesheet() : null;
        // SPARKLINE must preempt avgCard>1 guard: array-valued data has avgCard>1 by nature
        if (renderMode == PrimitiveRenderMode.SPARKLINE) {
            if (cachedSparklineStyle == null) {
                cachedSparklineStyle = new PrimitiveSparklineStyle(style.getLabelStyle(),
                                                                    javafx.geometry.Insets.EMPTY,
                                                                    style.getLabelStyle());
            }
            return cachedSparklineStyle.build(parentTraversal, this);
        }
        int avgCard = measureResult != null ? measureResult.averageCardinality() : averageCardinality;
        if (avgCard > 1) {
            return new PrimitiveList(this, parentTraversal);
        }
        if (renderMode == PrimitiveRenderMode.BAR) {
            if (cachedBarStyle == null) {
                cachedBarStyle = new PrimitiveBarStyle(style.getLabelStyle(),
                                                       javafx.geometry.Insets.EMPTY,
                                                       style.getLabelStyle());
            }
            return cachedBarStyle.build(parentTraversal, this);
        }
        if (renderMode == PrimitiveRenderMode.BADGE) {
            if (cachedBadgeStyle == null) {
                cachedBadgeStyle = new PrimitiveBadgeStyle(style.getLabelStyle(),
                                                           javafx.geometry.Insets.EMPTY,
                                                           style.getLabelStyle());
            }
            return cachedBadgeStyle.build(parentTraversal, this);
        }
        return buildCell(parentTraversal);
    }

    @Override
    public double calculateTableColumnWidth() {
        return tableColumnWidth();
    }

    @Override
    public double cellHeight(int cardinality, double justified) {
        if (height > 0) {
            return height;
        }
        HeightResult result = computeCellHeight(cardinality, justified);
        cellHeight = result.cellHeight();
        height = result.height();
        return height;
    }

    @Override
    public Function<Double, ColumnHeader> columnHeader() {
        return rendered -> new ColumnHeader(Style.snap(justifiedWidth
                                                       + columnHeaderIndentation),
                                            rendered, this);
    }

    @Override
    public double columnWidth() {
        return Math.max(columnWidth, labelWidth);
    }

    @Override
    public void compress(double available) {
        CompressResult result = computeCompress(available);
        justifiedWidth = result.justifiedWidth();
    }

    @Override
    public JsonNode extractFrom(JsonNode datum) {
        return node.extractFrom(datum);
    }

    public double getCellHeight() {
        return cellHeight;
    }

    @Override
    public Primitive getNode() {
        return (Primitive) node;
    }

    @Override
    public double justify(double justified) {
        justifiedWidth = Style.snap(justified);
        return justifiedWidth;
    }

    @Override
    public double layout(double width) {
        clear();
        return width;
    }

    @Override
    public double layoutWidth() {
        return columnWidth();
    }

    /** Phase 1: minimum sample count required to compute percentile stats. */
    private static final int MIN_SAMPLES = 30;

    @Override
    public double measure(JsonNode data, Function<JsonNode, JsonNode> extractor,
                          Style model) {
        // Version-change detection: runs unconditionally before the frozen short-circuit
        // so that a stylesheet change mid-convergence always resets counters.
        LayoutStylesheet stylesheet = (model != null) ? model.getStylesheet() : null;
        long currentVersion = (stylesheet != null) ? stylesheet.getVersion() : -1L;
        if (currentVersion != lastSeenStylesheetVersion) {
            frozenResult = null;
            frozenStylesheetVersion = -1;
            lastP90Width = Double.NaN;
            consecutiveStableCount = 0;
            lastSeenStylesheetVersion = currentVersion;
        }
        if (frozenResult != null && currentVersion == frozenStylesheetVersion) {
            return frozenResult.columnWidth();
        }

        // Invisible primitives produce no width contribution.
        SchemaPath path = getSchemaPath();
        if (stylesheet != null && path != null
                && !stylesheet.getBoolean(path, LayoutPropertyKeys.VISIBLE, true)) {
            measureResult = new MeasureResult(0, 0, 0, 0, 0, false, 0, 0, null, List.of(), null, null, null, null);
            return 0;
        }

        clear();
        labelWidth = labelWidth(node.getLabel());
        double summedDataWidth = 0;
        maxWidth = 0;
        columnWidth = 0;
        List<JsonNode> normalized = asList(data);
        int cardSum = 0;

        // Collect all individual widths for percentile computation.
        List<Double> allWidths = new ArrayList<>();

        for (JsonNode prim : normalized) {
            if (prim.isArray()) {
                cardSum += prim.size();
                double summedWidth = 0;
                for (JsonNode row : prim) {
                    double w = width(row);
                    allWidths.add(w);
                    summedWidth += w;
                    maxWidth = Math.max(maxWidth, w);
                }
                summedDataWidth += prim.size() == 0 ? 1
                                                    : summedWidth / prim.size();
            } else {
                cardSum += 1;
                double w = width(prim);
                allWidths.add(w);
                summedDataWidth += w;
                maxWidth = Math.max(maxWidth, w);
            }
        }
        double averageWidth = 0;
        averageCardinality = 1;
        if (data.size() > 0) {
            averageCardinality = (int) Math.round((double) cardSum / data.size());
            averageWidth = summedDataWidth / data.size();
        }

        // Paper §Table 1: IsVariableLength determines width strategy
        if (averageWidth > 0) {
            isVariableLength = (maxWidth / averageWidth > style.getVariableLengthThreshold());
        } else {
            isVariableLength = true; // safe fallback for empty data
        }

        // Compute percentile stats when sample count is sufficient.
        // Read minSamples from stylesheet if available; fall back to compile-time default.
        int minSamples = MIN_SAMPLES;
        double epsilon = 1.0;
        int k = 3;
        if (stylesheet != null && path != null) {
            minSamples = stylesheet.getInt(path, "stat-min-samples", MIN_SAMPLES);
            epsilon    = stylesheet.getDouble(path, "stat-convergence-epsilon", 1.0);
            k          = stylesheet.getInt(path, "stat-convergence-k", 3);
        }

        ContentWidthStats contentStats = null;
        int sampleCount = allWidths.size();
        if (sampleCount >= minSamples) {
            Collections.sort(allWidths);
            double p50 = allWidths.get(sampleCount / 2);
            double p90 = allWidths.get(sampleCount * 9 / 10);

            // Convergence detection: track consecutive calls where p90 is stable.
            // The first qualifying call (lastP90Width == NaN) starts the stable run at 1.
            boolean converged = false;
            if (Double.isNaN(lastP90Width)) {
                consecutiveStableCount = 1;
            } else if (Math.abs(p90 - lastP90Width) < epsilon) {
                consecutiveStableCount++;
            } else {
                consecutiveStableCount = 1;
            }
            lastP90Width = p90;

            if (consecutiveStableCount >= k) {
                converged = true;
            }
            contentStats = new ContentWidthStats(p50, p90, sampleCount, converged);
        }

        // Numeric auto-detection: scan all raw values to determine if the field is entirely numeric.
        // Tracks isArrayValued separately from allNumeric to distinguish:
        //   scalar numeric values → BAR
        //   array-of-numbers      → SPARKLINE
        // This is independent of the MIN_SAMPLES requirement for percentile stats.
        NumericStats numericStats = null;
        SparklineStats sparklineStats = null;
        String renderModeOverride = (stylesheet != null && path != null)
                                    ? stylesheet.getString(path, "render-mode", "auto")
                                    : "auto";
        if ("auto".equals(renderModeOverride)) {
            boolean allNumeric = !normalized.isEmpty();
            boolean isArrayValued = false;
            boolean hasScalar = false;
            double nMin = Double.POSITIVE_INFINITY;
            double nMax = Double.NEGATIVE_INFINITY;
            outer:
            for (JsonNode prim : normalized) {
                if (prim.isArray()) {
                    if (prim.isEmpty()) {
                        // treat empty arrays as non-numeric
                        allNumeric = false;
                        break;
                    }
                    isArrayValued = true;
                    for (JsonNode row : prim) {
                        if (!row.isNumber()) {
                            allNumeric = false;
                            break outer;
                        }
                        double v = row.doubleValue();
                        if (v < nMin) nMin = v;
                        if (v > nMax) nMax = v;
                    }
                } else {
                    hasScalar = true;
                    if (!prim.isNumber()) {
                        allNumeric = false;
                        break;
                    }
                    double v = prim.doubleValue();
                    if (v < nMin) nMin = v;
                    if (v > nMax) nMax = v;
                }
            }
            // Mixed scalar+array → not purely one type → fall through to TEXT
            if (allNumeric && !normalized.isEmpty() && !(isArrayValued && hasScalar)) {
                if (isArrayValued) {
                    // Compute SparklineStats: flatten all array values, sort for quartiles
                    List<Double> allValues = new ArrayList<>();
                    for (JsonNode prim : normalized) {
                        if (prim.isArray()) {
                            for (JsonNode row : prim) {
                                allValues.add(row.doubleValue());
                            }
                        }
                    }
                    Collections.sort(allValues);
                    int n = allValues.size();
                    double q1 = n > 0 ? allValues.get(n / 4) : 0.0;
                    double q3 = n > 0 ? allValues.get(n * 3 / 4) : 0.0;
                    sparklineStats = new SparklineStats(nMin, nMax, q1, q3, n);
                    renderMode = PrimitiveRenderMode.SPARKLINE;
                } else {
                    numericStats = new NumericStats(nMin, nMax);
                    renderMode = PrimitiveRenderMode.BAR;
                }
            } else {
                renderMode = PrimitiveRenderMode.TEXT;
            }
        } else {
            renderMode = switch (renderModeOverride.toUpperCase()) {
                case "BAR"       -> PrimitiveRenderMode.BAR;
                case "BADGE"     -> PrimitiveRenderMode.BADGE;
                case "SPARKLINE" -> PrimitiveRenderMode.SPARKLINE;
                default          -> PrimitiveRenderMode.TEXT;
            };
        }

        // Badge auto-detection: when renderMode is still TEXT after numeric check,
        // count distinct string values and promote to BADGE if below cardinality threshold.
        // Two-pass approach is intentional: numeric width computation (pass 1) is independent
        // of badge cardinality detection (pass 2), so merging them would not reduce complexity.
        badgeValues = null;
        if (renderMode == PrimitiveRenderMode.TEXT) {
            int badgeThreshold = (stylesheet != null && path != null)
                                 ? stylesheet.getInt(path, "badge-cardinality-threshold", 10)
                                 : 10;
            TreeSet<String> distinct = new TreeSet<>();
            for (JsonNode prim : normalized) {
                if (prim.isArray()) {
                    for (JsonNode row : prim) {
                        distinct.add(row.asText());
                    }
                } else if (!prim.isNull() && !prim.isMissingNode()) {
                    distinct.add(prim.asText());
                }
            }
            if (!distinct.isEmpty() && distinct.size() < badgeThreshold) {
                renderMode = PrimitiveRenderMode.BADGE;
                badgeValues = List.copyOf(distinct); // TreeSet is already sorted
            }
        }

        // RF-3: p90 replaces averageWidth ONLY in isVariableLength==true branch,
        // and only when we have sufficient samples. Fixed-length always uses maxWidth.
        double effectiveWidth;
        if (isVariableLength) {
            effectiveWidth = (contentStats != null) ? contentStats.p90Width() : averageWidth;
        } else {
            effectiveWidth = maxWidth;
        }

        dataWidth = Style.snap(Math.max(getNode().getDefaultWidth(), effectiveWidth));
        columnWidth = Math.max(labelWidth, dataWidth);

        measureResult = new MeasureResult(
            labelWidth, columnWidth, dataWidth, maxWidth,
            averageCardinality, isVariableLength,
            0, 0, null, List.of(), contentStats, numericStats, null, sparklineStats
        );

        // Freeze result when convergence is achieved.
        if (contentStats != null && contentStats.converged()) {
            frozenResult = measureResult;
            frozenStylesheetVersion = (stylesheet != null) ? stylesheet.getVersion() : -1;
        }

        return columnWidth;
    }

    @Override
    public SchemaNodeLayout measure(JsonNode datum, Style layout) {
        ArrayNode setOf = JsonNodeFactory.instance.arrayNode();
        setOf.add(datum);
        measure(setOf, n -> n, layout);
        return this;
    }

    @Override
    public double nestTableColumn(Indent indent, Insets inset) {
        columnHeaderIndentation = switch (indent) {
            case LEFT -> inset.getLeft();
            case NONE -> 0.0;
            case RIGHT -> inset.getRight();
            case SINGULAR -> inset.getLeft() + inset.getRight();
            default -> throw new IllegalArgumentException(String.format("%s is not a valid primitive indentation",
                                                                        indent));
        };
        // Paper Table 1: UseVerticalTableHeader — rotate label when column
        // is narrow relative to label text
        double tcw = tableColumnWidth();
        useVerticalHeader = tcw > 0 && labelWidth > tcw * style.getVerticalHeaderThreshold();
        return tcw;
    }

    @Override
    public void normalizeRowHeight(double normalized) {
        height = normalized;
    }

    @Override
    public double rowHeight(int averageCardinality, double justifiedWidth) {
        return cellHeight(1, justifiedWidth);
    }

    @Override
    public double tableColumnWidth() {
        double dw = measureResult != null ? measureResult.dataWidth() : dataWidth;
        return Math.min(dw, style.getMaxTablePrimitiveWidth());
    }

    @Override
    public String toString() {
        return String.format("PrimitiveLayout [%s %s height, width {c: %s, j: %s} ]",
                             node.getField(), height, columnWidth,
                             justifiedWidth);
    }

    public boolean isVariableLength() {
        return measureResult != null ? measureResult.isVariableLength() : isVariableLength;
    }

    public boolean isUseVerticalHeader() {
        return useVerticalHeader;
    }

    public MeasureResult getMeasureResult() {
        return measureResult;
    }

    /**
     * Returns the {@link LayoutStylesheet} last captured during {@link #buildControl}.
     * May be {@code null} if {@code buildControl} has not yet been called or was called
     * with a {@code null} model.
     */
    public LayoutStylesheet getStylesheet() {
        return currentStylesheet;
    }

    public PrimitiveRenderMode getRenderMode() {
        return renderMode;
    }

    /**
     * Returns the sorted list of distinct string values used for BADGE CSS class assignment,
     * or {@code null} if the current render mode is not BADGE.
     */
    public List<String> getBadgeValues() {
        return badgeValues;
    }

    /**
     * Returns the sorted index of {@code value} in the badge value set, or {@code -1}
     * if the value is not present (unknown value — TEXT fallback applies).
     */
    public int badgeIndex(String value) {
        if (badgeValues == null || value == null) {
            return -1;
        }
        int idx = Collections.binarySearch(badgeValues, value);
        return idx >= 0 ? idx : -1;
    }

    /** Returns true when a frozen (converged) result is cached and valid. */
    @Override
    public boolean isConverged() {
        return frozenResult != null;
    }

    /**
     * Clears the frozen (converged) result and resets all convergence tracking
     * state so that the next {@link #measure} call performs a full re-measurement.
     *
     * <p>Called by {@link AutoLayout} when data changes are detected at this
     * primitive's path so that new data content is reflected in width calculations.
     */
    public void clearFrozenResult() {
        frozenResult = null;
        frozenStylesheetVersion = -1;
        lastP90Width = Double.NaN;
        consecutiveStableCount = 0;
    }

    public LayoutResult computeLayout(double width) {
        clear();
        return new LayoutResult(
            RelationRenderMode.OUTLINE,    // primitives never use table mode
            PrimitiveRenderMode.TEXT,
            useVerticalHeader,             // preserved from nestTableColumn if set
            0,                             // tableColumnWidth — not applicable
            columnHeaderIndentation,
            width,                         // constrainedColumnWidth
            List.of()
        );
    }

    /**
     * Snapshot the current layout state WITHOUT calling {@code clear()}.
     * Safe to call after {@code autoLayout()} completes; reads fields directly.
     * Unlike {@link #computeLayout(double)}, this does not reset useVerticalHeader
     * or any other mutable state.
     */
    public LayoutResult snapshotLayoutResult() {
        return new LayoutResult(
            RelationRenderMode.OUTLINE,    // primitives never use table mode
            renderMode,
            useVerticalHeader,
            0,                             // tableColumnWidth — not applicable
            columnHeaderIndentation,
            justifiedWidth,               // use post-compress justifiedWidth
            List.of()
        );
    }

    /**
     * Compose a leaf {@link LayoutDecisionNode} from current field state.
     * No layout side effects.
     */
    @Override
    public LayoutDecisionNode snapshotDecisionTree() {
        SchemaPath path = getSchemaPath();
        String field = path != null ? path.leaf() : getField();
        CompressResult compressSnap = new CompressResult(justifiedWidth, List.of(), cellHeight, List.of());
        HeightResult heightSnap = new HeightResult(height, cellHeight, averageCardinality, columnHeaderIndentation, List.of());
        return new LayoutDecisionNode(
            path,
            field,
            measureResult,
            snapshotLayoutResult(),
            compressSnap,
            heightSnap,
            List.of(),   // primitives have no column sets
            List.of()    // leaf — no child nodes
        );
    }

    public CompressResult computeCompress(double available) {
        double floor = style.getMinValueWidth();
        double effective = Math.max(available, floor);
        double justified;
        boolean varLen = measureResult != null ? measureResult.isVariableLength() : isVariableLength;
        double mw = measureResult != null ? measureResult.maxWidth() : maxWidth;
        if (!varLen && mw > 0) {
            double target = mw;
            double snapWidth = style.getOutlineSnapValueWidth();
            if (snapWidth > 0) {
                target = Math.ceil(target / snapWidth) * snapWidth;
            }
            justified = Style.snap(Math.min(effective, target));
        } else {
            justified = Style.snap(effective);
        }
        return new CompressResult(justified, List.of(), 0, List.of());
    }

    public HeightResult computeCellHeight(int cardinality, double justified) {
        int avgCard = measureResult != null ? measureResult.averageCardinality() : averageCardinality;
        double mw = measureResult != null ? measureResult.maxWidth() : maxWidth;
        int resolved = Math.min(cardinality, avgCard);
        boolean list = resolved > 1;
        double cell = snap(style.getHeight(mw, justified));
        double h;
        if (list) {
            h = (cell * resolved) + style.getListVerticalInset();
        } else {
            h = cell;
        }
        return new HeightResult(h, cell, resolved, 0, List.of());
    }

    void setUseVerticalHeader(boolean useVerticalHeader) {
        this.useVerticalHeader = useVerticalHeader;
    }

    @Override
    public double columnHeaderHeight() {
        if (useVerticalHeader) {
            return Style.snap(labelWidth);
        }
        return super.columnHeaderHeight();
    }

    @Override
    protected void calculateRootHeight() {
        int avgCard = measureResult != null ? measureResult.averageCardinality() : averageCardinality;
        cellHeight(avgCard, justifiedWidth);
    }

    @Override
    protected void clear() {
        // Intentionally does NOT reset convergence state (frozenResult, consecutiveStableCount,
        // lastP90Width). Convergence persists across layout cycles; only stylesheet version
        // change resets it.
        super.clear();
        // isVariableLength lifecycle is now explicit in MeasureResult (RDR-009 SC-6).
        // useVerticalHeader resets because nestTableColumn() runs after clear().
        useVerticalHeader = false;
        // Invalidate cached style objects so they are rebuilt after a layout cycle.
        cachedBarStyle = null;
        cachedBadgeStyle = null;
        cachedSparklineStyle = null;
    }

    protected double width(JsonNode row) {
        return style.width(row);
    }
}