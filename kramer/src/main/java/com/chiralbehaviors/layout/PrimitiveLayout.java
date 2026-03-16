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
import java.util.function.Function;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.PrimitiveList;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.Style;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
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

    // Convergence detection state (Kramer-16k)
    private MeasureResult          frozenResult;
    private long                   frozenStylesheetVersion   = -1;
    private long                   lastSeenStylesheetVersion = -1;
    private double                 lastP90Width              = Double.NaN;
    private int                    consecutiveStableCount    = 0;

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
        int avgCard = measureResult != null ? measureResult.averageCardinality() : averageCardinality;
        return avgCard > 1 ? new PrimitiveList(this, parentTraversal)
                           : buildCell(parentTraversal);
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
        SchemaPath path = getSchemaPath();
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
        // This is independent of the MIN_SAMPLES requirement for percentile stats.
        NumericStats numericStats = null;
        String renderModeOverride = (stylesheet != null && path != null)
                                    ? stylesheet.getString(path, "render-mode", "auto")
                                    : "auto";
        if ("auto".equals(renderModeOverride)) {
            boolean allNumeric = !normalized.isEmpty();
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
                    if (!prim.isNumber()) {
                        allNumeric = false;
                        break;
                    }
                    double v = prim.doubleValue();
                    if (v < nMin) nMin = v;
                    if (v > nMax) nMax = v;
                }
            }
            if (allNumeric && !normalized.isEmpty()) {
                numericStats = new NumericStats(nMin, nMax);
                renderMode = PrimitiveRenderMode.BAR;
            } else {
                renderMode = PrimitiveRenderMode.TEXT;
            }
        } else {
            renderMode = switch (renderModeOverride.toUpperCase()) {
                case "BAR"   -> PrimitiveRenderMode.BAR;
                case "BADGE" -> PrimitiveRenderMode.BADGE;
                default      -> PrimitiveRenderMode.TEXT;
            };
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
            0, 0, null, List.of(), contentStats, numericStats, null
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

    public PrimitiveRenderMode getRenderMode() {
        return renderMode;
    }

    /** Returns true when a frozen (converged) result is cached and valid. */
    @Override
    public boolean isConverged() {
        return frozenResult != null;
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
    }

    protected double width(JsonNode row) {
        return style.width(row);
    }
}