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

package com.chiralbehaviors.layout;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.chiralbehaviors.layout.cell.Hit;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.LayoutContainer;
import com.chiralbehaviors.layout.cell.control.FocusController;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;

/**
 * @author hhildebrand
 *
 */
public class AutoLayout extends AnchorPane implements LayoutCell<AutoLayout> {
    /**
     * 
     */
    private static final String                    AUTO_LAYOUT = "auto-layout";
    private static final String                    DEFAULT_CSS = "default.css";
    private static final java.util.logging.Logger  log         = Logger.getLogger(AutoLayout.class.getCanonicalName());
    private static final String                    STYLE_SHEET = "auto-layout.css";

    private LayoutCell<? extends Region>                  control;
    private final FocusController<AutoLayout>             controller;
    private ConstraintSolver                              constraintSolver = new ExhaustiveConstraintSolver();
    private SimpleObjectProperty<JsonNode>                data         = new SimpleObjectProperty<>();
    private final Map<LayoutDecisionKey, LayoutResult>    decisionCache = new HashMap<>();
    private SchemaNodeLayout                              layout;
    private MeasureResult                                 measureResult;
    private double                                        layoutWidth  = 0.0;
    private Style                                        model;
    private final SimpleObjectProperty<SchemaNode>        root         = new SimpleObjectProperty<>();
    private final String                                  stylesheet;

    // Data snapshot state (Kramer-eyr): tracks primitive values across setContent() calls
    // to invalidate frozenResult on PrimitiveLayouts whose data has changed.
    private Map<SchemaPath, List<String>>                 dataSnapshot = DataSnapshot.EMPTY;
    private Map<SchemaPath, Double>                       p90Snapshot  = Map.of();

    // Search
    private SearchBar                                     searchBar;
    private LayoutSearch                                  layoutSearch;

    // Post-layout callback (installed by controller to wire sort handlers, etc.)
    private Runnable                                      postLayoutCallback;

    public void setPostLayoutCallback(Runnable callback) {
        this.postLayoutCallback = callback;
    }

    public AutoLayout() {
        this(null);
    }

    public AutoLayout(Relation root) {
        this(root, new Style() {
        });
    }

    public AutoLayout(Relation root, Style model) {
        URL url = getClass().getResource(STYLE_SHEET);
        stylesheet = url == null ? null : url.toExternalForm();
        getStyleClass().add(AUTO_LAYOUT);
        this.model = model;
        this.root.set(root);
        this.root.addListener((o, p, c) -> {
            layout = null;
            measureResult = null;
            decisionCache.clear();
            control = null;
            dataSnapshot = DataSnapshot.EMPTY;
            p90Snapshot  = Map.of();
        });
        data.addListener((o, p, c) -> setContent());
        controller = new FocusController<>(this);
        getStylesheets().addListener((ListChangeListener<String>) c -> {
            var newList = getStylesheets();
            if (newList.equals(model.styleSheets())) return;
            model.setStyleSheets(newList, this);
            layout = null;
            measureResult = null;
            decisionCache.clear();
            control = null;
            if (getData() != null) {
                autoLayout();
            }
        });
        getStylesheets().add(getClass().getResource(DEFAULT_CSS)
                                       .toExternalForm());
        installSearchKeyBindings();
    }

    public void autoLayout() {
        layoutWidth = 0.0;
        decisionCache.clear();
        Platform.runLater(() -> autoLayout(getData(), getWidth()));
    }

    public Property<JsonNode> dataProperty() {
        return data;
    }

    public JsonNode getData() {
        return data.get();
    }

    @Override
    public AutoLayout getNode() {
        return this;
    }

    public SchemaNode getRoot() {
        return root.get();
    }

    /** Package-visible for testing: the current layout width. */
    double getLayoutWidth() {
        return layoutWidth;
    }

    /** Package-visible for testing: the internal layout tree. */
    SchemaNodeLayout getLayoutTree() {
        return layout;
    }

    @Override
    public String getUserAgentStylesheet() {
        return stylesheet;
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    public void measure(JsonNode data) {
        SchemaNode top = root.get();
        if (top == null || data == null || data.isNull() || data.size() == 0) {
            return;
        }
        try {
            layout = model.layout(top);
            layout.buildPaths(new SchemaPath(top.getField()), model);
            layout.measure(data, model);
            measureResult = layout.getMeasureResult();
        } catch (Throwable e) {
            log.log(Level.SEVERE, "cannot measure data", e);
        }
    }

    public MeasureResult getMeasureResult() {
        return measureResult;
    }

    @Override
    public void resize(double width, double height) {
        super.resize(width, height);
        if (layoutWidth == width || width < 10.0 || height < 10.0) {
            return;
        }

        layoutWidth = width;

        SchemaNode node = root.get();
        if (node == null) {
            return;
        }

        JsonNode zeeData = data.get();
        if (zeeData == null) {
            return;
        }

        try {
            autoLayout(zeeData, width);
        } catch (Throwable e) {
            log.log(Level.SEVERE,
                    String.format("Unable to resize to %s", width), e);
        }
    }

    public SchemaNode root() {
        return root.get();
    }

    public Property<SchemaNode> rootProperty() {
        return root;
    }

    public void setRoot(SchemaNode rootNode) {
        root.set(rootNode);
    }

    @Override
    public void updateItem(JsonNode item) {
        data.set(item);
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_EMPTY, item == null);
    }

    // -----------------------------------------------------------------------
    // Search bar
    // -----------------------------------------------------------------------

    /**
     * Shows the search bar at the bottom of the AutoLayout pane and requests
     * focus on the query field.
     */
    public void showSearchBar() {
        if (searchBar == null) {
            searchBar = new SearchBar();
            searchBar.setOnFindNext(this::doFindNext);
            searchBar.setOnFindPrevious(this::doFindPrevious);
            searchBar.getSearchField().textProperty().addListener((obs, old, nv) -> rebuildSearch(nv));
        }
        if (!getChildren().contains(searchBar)) {
            setBottomAnchor(searchBar, 0d);
            setLeftAnchor(searchBar, 0d);
            setRightAnchor(searchBar, 0d);
            getChildren().add(searchBar);
        }
        searchBar.setVisible(true);
        searchBar.getSearchField().requestFocus();
    }

    /**
     * Hides the search bar and returns focus to the main content pane.
     */
    public void hideSearchBar() {
        if (searchBar != null) {
            getChildren().remove(searchBar);
            searchBar.setVisible(false);
        }
        layoutSearch = null;
        requestFocus();
    }

    /** Returns the SearchBar, or {@code null} if it has never been shown. */
    public SearchBar getSearchBar() {
        return searchBar;
    }

    // -----------------------------------------------------------------------
    // Private search helpers
    // -----------------------------------------------------------------------

    private void installSearchKeyBindings() {
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleSearchKeyPressed);
    }

    private void handleSearchKeyPressed(KeyEvent evt) {
        boolean shortcutDown = evt.isShortcutDown();
        KeyCode code = evt.getCode();

        if (shortcutDown && code == KeyCode.F) {
            showSearchBar();
            evt.consume();
            return;
        }

        if (searchBar != null && searchBar.isVisible()) {
            if (code == KeyCode.F3 && !evt.isShiftDown()) {
                doFindNext();
                evt.consume();
                return;
            }
            if (code == KeyCode.F3 && evt.isShiftDown()) {
                doFindPrevious();
                evt.consume();
                return;
            }
            if (code == KeyCode.ESCAPE) {
                hideSearchBar();
                evt.consume();
            }
        }
    }

    private void rebuildSearch(String query) {
        SchemaNode schemaRoot = root.get();
        JsonNode currentData  = data.get();
        if (schemaRoot == null || currentData == null) {
            layoutSearch = null;
            updateMatchDisplay(0, 0);
            return;
        }
        layoutSearch = new LayoutSearch(schemaRoot, currentData,
                                        getOutermostVirtualFlow().orElse(null),
                                        null);
        layoutSearch.setQuery(query);
        updateMatchDisplay(0, layoutSearch.countMatches());
    }

    private void doFindNext() {
        if (searchBar == null) return;
        ensureSearch();
        if (layoutSearch == null) return;
        layoutSearch.findNext().ifPresentOrElse(
            r -> updateMatchDisplay(layoutSearch.getCursorIndex() + 1, layoutSearch.countMatches()),
            () -> updateMatchDisplay(0, 0)
        );
    }

    private void doFindPrevious() {
        if (searchBar == null) return;
        ensureSearch();
        if (layoutSearch == null) return;
        layoutSearch.findPrevious().ifPresentOrElse(
            r -> updateMatchDisplay(layoutSearch.getCursorIndex() + 1, layoutSearch.countMatches()),
            () -> updateMatchDisplay(0, 0)
        );
    }

    private void ensureSearch() {
        if (layoutSearch == null && searchBar != null) {
            rebuildSearch(searchBar.getSearchField().getText());
        }
    }

    private void updateMatchDisplay(int current, int total) {
        if (searchBar != null) {
            searchBar.setMatchInfo(current, total);
        }
    }

    /** Returns true when a layout has been measured and all primitives have converged. */
    public boolean allConverged() {
        return layout != null && layout.isConverged();
    }

    /**
     * Returns the current layout decision tree as an {@link Optional}.
     *
     * <p>Returns {@link Optional#empty()} if no layout has been measured yet,
     * or if the layout has not yet converged (i.e., primitive widths are still
     * stabilizing across resize cycles).
     *
     * <p>When present, the returned tree reflects all four layout phases
     * (measure, layout, compress, height) at the time of the last resize.
     *
     * @return the decision tree rooted at the top-level schema node, or empty
     */
    public Optional<LayoutDecisionNode> getLayoutDecisionTree() {
        if (layout == null || !layout.isConverged()) return Optional.empty();
        return Optional.of(layout.snapshotDecisionTree());
    }

    /**
     * Renders the current layout decision tree via {@link JavaFxLayoutRenderer}.
     *
     * <p>Returns {@link Optional#empty()} if no converged layout is available
     * (same conditions as {@link #getLayoutDecisionTree()}).
     *
     * @param data the JSON data node to render
     * @return an Optional containing the rendered {@link javafx.scene.Node}, or empty
     */
    public Optional<javafx.scene.Node> renderViaProtocol(JsonNode data) {
        return getLayoutDecisionTree().map(tree -> new JavaFxLayoutRenderer().render(tree, data));
    }

    /**
     * Replaces the default {@link ExhaustiveConstraintSolver} with a custom
     * implementation.  Useful for testing or for plugging in a heuristic solver
     * when the schema is too large for exhaustive enumeration.
     */
    public void setConstraintSolver(ConstraintSolver solver) {
        this.constraintSolver = Objects.requireNonNull(solver, "solver");
    }

    /**
     * Builds a {@link RelationConstraint} tree from the already-measured layout
     * tree so the solver can determine render modes globally before layout().
     *
     * <p>Each constraint records two available-width estimates:
     * <ul>
     *   <li>{@code availableWidthAsOutline}: the width seen when the parent renders
     *       as OUTLINE — {@code (parentWidth - labelWidth) - outlineCellHorizontalInset}
     *       at root this equals {@code width}.</li>
     *   <li>{@code availableWidthAsTable}: the approximate width per column when the
     *       parent renders as TABLE — {@code parentTableWidth / numRelationChildren};
     *       {@code Double.MAX_VALUE} at the root where there is no TABLE parent.</li>
     * </ul>
     *
     * @param snl                  the layout node to analyse; may be null or a PrimitiveLayout
     * @param width                the outline-mode available width for this node
     * @param availableWidthAsTable the table-mode available width for this node
     *                             ({@code Double.MAX_VALUE} at root)
     * @return a {@link RelationConstraint} for this node, or {@code null} if the
     *         node is not a RelationLayout
     */
    private RelationConstraint buildConstraintTree(SchemaNodeLayout snl, double width,
                                                    double availableWidthAsTable) {
        if (!(snl instanceof RelationLayout rl)) {
            return null;
        }
        double tableWidth = rl.calculateTableColumnWidth();
        double nestedInset = rl.getStyle().getNestedHorizontalInset();
        // Outline-mode child width: (parentWidth - labelWidth) - outlineCellHorizontalInset
        double lw = rl.getLabelWidth();
        double childAvailableOutline = (width - lw) - rl.getStyle().getOutlineCellHorizontalInset();
        // Table-mode child width approximation: parent tableWidth / number of Relation children
        long numRelChildren = rl.getChildren().stream()
                .filter(c -> c instanceof RelationLayout)
                .count();
        double childAvailableTable = numRelChildren > 0 ? tableWidth / numRelChildren
                                                        : Double.MAX_VALUE;
        List<RelationConstraint> childConstraints = rl.getChildren()
                .stream()
                .filter(c -> c instanceof RelationLayout)
                .map(c -> buildConstraintTree(c, childAvailableOutline, childAvailableTable))
                .filter(Objects::nonNull)
                .toList();
        // RDR-024: compute CROSSTAB feasibility fields
        MeasureResult mr = rl.getMeasureResult();
        String pivotField = model.getStylesheet().getString(rl.getSchemaPath(),
                                                            LayoutPropertyKeys.PIVOT_FIELD, "");
        boolean crosstabEligible = !pivotField.isEmpty()
                && mr != null
                && mr.pivotStats() != null
                && mr.pivotStats().pivotCount() > 0;
        double crosstabWidth = crosstabEligible ? mr.columnWidth() : 0.0;
        double readable = rl.readableTableWidth();
        return new RelationConstraint(
                rl.getSchemaPath(),
                tableWidth,
                readable,
                nestedInset,
                width,
                availableWidthAsTable,
                childConstraints,
                rl.isCrosstab(),
                crosstabWidth,
                crosstabEligible
        );
    }

    private void autoLayout(JsonNode zeeData, double width) {
        if (width < 10.0) {
            return;
        }
        if (layout == null) {
            measure(zeeData);
        }
        if (layout == null) {
            return;
        }

        // Convergence short-circuit: if all primitives have stable widths AND
        // the decision cache has a result for this width bucket, skip layout+compress
        // and go straight to buildControl using the existing layout tree state.
        // Cache entries are only written for RelationLayout roots (see below), so
        // restrict the read to that case as well to avoid a spurious cache miss branch.
        int dataCardinality = zeeData != null ? zeeData.size() : 0;
        SchemaPath rootPath = layout.getSchemaPath();
        if (allConverged() && rootPath != null && layout instanceof RelationLayout) {
            LayoutDecisionKey key = LayoutDecisionKey.of(rootPath, width, dataCardinality,
                                                         model.getStylesheet().getVersion());
            if (decisionCache.containsKey(key)) {
                buildAndInstallControl(zeeData, width);
                return;
            }
        }

        LayoutCell<?> old = control;
        // Save cursor state before unbind (which nulls it)
        var savedCursor = controller.getCursorState();
        // Clear old keyboard bindings before rebuilding the control tree;
        // new VirtualFlows will re-register via bindKeyboard in their constructors.
        controller.unbind();

        // Solver pre-pass: build constraint tree from post-measure state and
        // inject render-mode assignments into the RelationLayout tree so that
        // layout() uses global decisions instead of the greedy per-node check.
        if (layout instanceof RelationLayout rootRl) {
            RelationConstraint constraintRoot = buildConstraintTree(rootRl, width, Double.MAX_VALUE);
            if (constraintRoot != null) {
                Map<SchemaPath, RelationRenderMode> solverMap = constraintSolver.solve(constraintRoot);
                rootRl.setSolverResults(solverMap);
                try {
                    control = layout.autoLayout(width, getHeight(), controller, model);
                } finally {
                    rootRl.setSolverResults(null);
                }
            } else {
                control = layout.autoLayout(width, getHeight(), controller, model);
            }
        } else {
            control = layout.autoLayout(width, getHeight(), controller, model);
        }
        Region node = control.getNode();

        setTopAnchor(node, 0d);
        setRightAnchor(node, 0d);
        setBottomAnchor(node, 0d);
        setLeftAnchor(node, 0d);

        getChildren().setAll(node);
        if (searchBar != null && searchBar.isVisible()) {
            if (!getChildren().contains(searchBar)) {
                getChildren().add(searchBar);
            }
        }
        if (old != null) {
            old.dispose();
        }
        node.setMinWidth(0);
        node.setPrefWidth(width);
        node.setMaxWidth(width);
        control.updateItem(zeeData);

        // Cache the layout decision for this width bucket.
        // Use snapshotLayoutResult() to read existing state without re-running layout().
        if (rootPath != null) {
            LayoutDecisionKey key = LayoutDecisionKey.of(rootPath, width, dataCardinality,
                                                         model.getStylesheet().getVersion());
            if (!decisionCache.containsKey(key) && layout instanceof RelationLayout rl) {
                decisionCache.put(key, rl.snapshotLayoutResult());
            }
        }

        // Recover cursor position after layout rebuild.
        // Find the first VirtualFlow in the new tree for cursor recovery.
        findVirtualFlow(node).ifPresent(vf -> controller.recoverCursor(savedCursor, vf));
        if (postLayoutCallback != null) postLayoutCallback.run();
    }

    /**
     * Build and install a new control using the current (cached) layout tree state.
     * Called when convergence + cache hit allow skipping layout+compress.
     */
    private void buildAndInstallControl(JsonNode zeeData, double width) {
        LayoutCell<?> old = control;
        var savedCursor = controller.getCursorState();
        controller.unbind();
        // buildControl uses the already-computed justifiedWidth/useTable from last run.
        layout.rootLevel = true;
        try {
            control = layout.buildControl(controller, model);
        } finally {
            layout.rootLevel = false;
        }
        Region node = control.getNode();

        setTopAnchor(node, 0d);
        setRightAnchor(node, 0d);
        setBottomAnchor(node, 0d);
        setLeftAnchor(node, 0d);

        getChildren().setAll(node);
        if (searchBar != null && searchBar.isVisible()) {
            if (!getChildren().contains(searchBar)) {
                getChildren().add(searchBar);
            }
        }
        if (old != null) {
            old.dispose();
        }
        node.setMinWidth(0);
        node.setPrefWidth(width);
        node.setMaxWidth(width);
        control.updateItem(zeeData);

        findVirtualFlow(node).ifPresent(vf -> controller.recoverCursor(savedCursor, vf));
        if (postLayoutCallback != null) postLayoutCallback.run();
    }

    /**
     * Root-level hit dispatch using scene coordinates. Walks the current
     * control tree to find which container (VirtualFlow, OutlineCell, etc.)
     * contains the given scene point, then delegates to its hitScene().
     * Returns null if no container contains the point.
     */
    public Hit<?> hitSceneRoot(Point2D scenePoint) {
        if (control == null) return null;
        Region root = control.getNode();
        return hitSceneRecursive(root, scenePoint);
    }

    private Hit<?> hitSceneRecursive(javafx.scene.Node node, Point2D scenePoint) {
        if (node instanceof LayoutContainer<?, ?, ?> container) {
            Hit<?> hit = container.hitScene(scenePoint);
            if (hit != null && hit.isCellHit()) {
                return hit;
            }
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                Hit<?> hit = hitSceneRecursive(child, scenePoint);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    /**
     * Map a local (x, y) coordinate to the {@link SchemaPath} of the deepest
     * schema node in the layout tree, or the root if no finer match is found.
     * Returns {@code null} if no layout exists.
     */
    public SchemaPath hitSchemaPath(double x, double y) {
        if (layout == null) return null;
        javafx.geometry.Point2D scenePoint = localToScene(x, y);
        // Walk the layout tree and find the deepest child whose bounds contain the point
        return hitSchemaPathRecursive(layout, scenePoint);
    }

    private SchemaPath hitSchemaPathRecursive(SchemaNodeLayout node,
                                               javafx.geometry.Point2D scenePoint) {
        if (control == null) return node.getSchemaPath();
        SchemaPath found = hitSchemaPathFromScene(control.getNode(), scenePoint);
        return found != null ? found : node.getSchemaPath();
    }

    /**
     * Walk the JavaFX scene graph depth-first looking for the deepest node
     * carrying a {@link SchemaPath} whose bounds contain the given scene point.
     * SchemaPath carriers: {@link VirtualFlow} ({@code getSchemaPath()}) and
     * any node with SchemaPath as {@code getUserData()}.
     */
    private SchemaPath hitSchemaPathFromScene(javafx.scene.Node node,
                                              javafx.geometry.Point2D scenePoint) {
        javafx.geometry.Point2D local = node.sceneToLocal(scenePoint);
        if (local == null || !node.contains(local)) {
            return null;
        }

        // Check children first for deeper (more specific) matches
        if (node instanceof javafx.scene.Parent parent) {
            // Reverse iteration: later children are rendered on top
            var children = parent.getChildrenUnmodifiable();
            for (int i = children.size() - 1; i >= 0; i--) {
                SchemaPath childPath = hitSchemaPathFromScene(children.get(i), scenePoint);
                if (childPath != null) return childPath;
            }
        }

        // Check this node
        if (node instanceof VirtualFlow<?> vf && vf.getSchemaPath() != null) {
            return vf.getSchemaPath();
        }
        if (node.getUserData() instanceof SchemaPath sp) {
            return sp;
        }

        return null;
    }

    /**
     * Map a local (x, y) coordinate to the text content of the deepest
     * text-displaying node at that point. Returns {@code null} if no text
     * node is found or no layout exists.
     */
    public String hitCellText(double x, double y) {
        if (layout == null || control == null) return null;
        javafx.geometry.Point2D scenePoint = localToScene(x, y);
        return hitTextFromScene(control.getNode(), scenePoint);
    }

    private String hitTextFromScene(javafx.scene.Node node,
                                     javafx.geometry.Point2D scenePoint) {
        javafx.geometry.Point2D local = node.sceneToLocal(scenePoint);
        if (local == null || !node.contains(local)) return null;

        if (node instanceof javafx.scene.Parent parent) {
            var children = parent.getChildrenUnmodifiable();
            for (int i = children.size() - 1; i >= 0; i--) {
                String text = hitTextFromScene(children.get(i), scenePoint);
                if (text != null) return text;
            }
        }

        if (node instanceof javafx.scene.control.Label label
            && label.getText() != null && !label.getText().isEmpty()) {
            return label.getText();
        }
        return null;
    }

    /**
     * Returns the outermost VirtualFlow in the current control tree, if any.
     * Useful for programmatic navigation via {@link FocusController#navigateTo}.
     *
     * @return the first VirtualFlow found by depth-first search, or empty
     */
    public Optional<VirtualFlow<?>> getOutermostVirtualFlow() {
        return control == null ? Optional.empty() : findVirtualFlow(control.getNode());
    }

    private Optional<VirtualFlow<?>> findVirtualFlow(javafx.scene.Node node) {
        if (node instanceof VirtualFlow<?> vf) {
            return Optional.of(vf);
        }
        if (node instanceof javafx.scene.Parent p) {
            for (javafx.scene.Node child : p.getChildrenUnmodifiable()) {
                var found = findVirtualFlow(child);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }

    private void setContent() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::setContent);
            return;
        }
        JsonNode datum = data.get();
        try {
            // Phase 3b: selective re-measure with bucket comparison
            if (layout != null && datum != null) {
                Set<SchemaPath> changedPaths = detectChangedPaths(datum);

                if (changedPaths.isEmpty()) {
                    // Case (a): no data changes — just rebind and update snapshot
                    if (control != null) {
                        control.updateItem(datum);
                    }
                    if (layout != null) {
                        dataSnapshot = DataSnapshot.buildSnapshot(layout, datum);
                    }
                    return;
                }

                clearFrozenResultForPaths(changedPaths);

                // Re-measure only affected primitives and compare p90 buckets
                Set<SchemaPath> bucketChangedPaths = remeasureChanged(changedPaths, datum);

                if (control == null) {
                    // Cold start: control tree not yet built — must run
                    // full autoLayout regardless of bucket changes. If the
                    // layout has been sized, run immediately; otherwise defer
                    // to let resize() trigger it with the real width.
                    double w = getWidth();
                    if (w >= 10.0) {
                        autoLayout(datum, w);
                    }
                    // Don't update dataSnapshot — let autoLayout handle it.
                    return;
                } else if (bucketChangedPaths.isEmpty()) {
                    // P90 shifted but stayed in same bucket — just rebind, no re-layout
                    control.updateItem(datum);
                    dataSnapshot = DataSnapshot.buildSnapshot(layout, datum);
                    return;
                } else if (detectModeFlip(bucketChangedPaths)) {
                    // Phase 3c: bucket change flips a TABLE/OUTLINE decision
                    autoLayout(datum, getWidth());
                } else {
                    // Width shifted but mode unchanged — rebind only
                    control.updateItem(datum);
                }
                updateSnapshots(datum);
                return;
            } else if (control == null) {
                Platform.runLater(() -> autoLayout(datum, getWidth()));
            } else {
                control.updateItem(datum);
            }
            layout();
            // Update snapshot after successful pipeline
            if (layout != null && datum != null) {
                dataSnapshot = DataSnapshot.buildSnapshot(layout, datum);
            }
        } catch (Throwable e) {
            log.log(Level.SEVERE, "cannot set content", e);
            // Exception during pipeline: clear both snapshots so the next call starts fresh
            dataSnapshot = DataSnapshot.EMPTY;
            p90Snapshot  = Map.of();
        }
    }

    /**
     * Compares {@code data} against the last captured {@link #dataSnapshot} and
     * returns the set of {@link SchemaPath}s whose primitive values have changed.
     *
     * <p>On cold start (empty snapshot) or row-count change, all known paths are
     * returned. On an identical repeat, an empty set is returned.
     */
    private Set<SchemaPath> detectChangedPaths(JsonNode data) {
        return DataSnapshot.detectChangedPaths(layout, data, dataSnapshot);
    }

    /**
     * For each path in {@code paths}, locates the corresponding
     * {@link PrimitiveLayout} via the layout tree and calls
     * {@link PrimitiveLayout#clearFrozenResult()}, saving the p90Width into
     * {@link #p90Snapshot} before clearing.
     *
     * <p><b>Convergence fast-path</b>: if the PrimitiveLayout at a given path is
     * already converged and the new data's maximum value width does not exceed the
     * snapshot p90, clearing is skipped entirely — the frozen result remains valid.
     *
     * <p>PrimitiveLayouts with no frozen result are silently skipped.
     */
    private void clearFrozenResultForPaths(Set<SchemaPath> paths) {
        if (paths.isEmpty() || layout == null) return;
        Map<SchemaPath, Double> newP90 = new HashMap<>(p90Snapshot);
        clearFrozenResultInTree(layout, paths, newP90, data.get(), model);
        p90Snapshot = Map.copyOf(newP90);
    }

    /**
     * Updates {@link #dataSnapshot} from the current layout tree and datum.
     * Called after the Phase 3c fast-path (rebind without full re-layout) to
     * keep the snapshot current for the next {@link #setContent()} comparison.
     */
    private void updateSnapshots(JsonNode datum) {
        if (layout != null && datum != null) {
            dataSnapshot = DataSnapshot.buildSnapshot(layout, datum);
        }
    }

    /**
     * Recursively walk the layout tree, clearing frozenResult on any
     * {@link PrimitiveLayout} whose path is in {@code paths}.
     *
     * <p><b>Convergence fast-path</b>: if the layout is converged and the new data
     * produces a max value width {@code <= p90Snapshot[path]}, skip clearing so
     * the frozen result stays valid.  The fast-path only fires when {@code datum}
     * is non-null and a snapshot value exists for the path.
     */
    private static void clearFrozenResultInTree(SchemaNodeLayout snl,
                                                Set<SchemaPath> paths,
                                                Map<SchemaPath, Double> p90Out,
                                                JsonNode datum,
                                                Style model) {
        if (snl instanceof PrimitiveLayout pl) {
            SchemaPath path = pl.getSchemaPath();
            if (path != null && paths.contains(path)) {
                // Convergence fast-path: if converged and new data fits within snapshot p90, skip
                MeasureResult mr = pl.getMeasureResult();
                if (mr != null && mr.contentStats() != null && mr.contentStats().converged()
                        && datum != null) {
                    Double snapshotP90 = p90Out.get(path);
                    if (snapshotP90 != null) {
                        double newMaxWidth = computeMaxWidthForPath(pl, datum, model);
                        if (newMaxWidth <= snapshotP90) {
                            // New data fits within previous p90 — frozen result stays valid
                            return;
                        }
                    }
                }
                // Capture p90Width before clearing
                if (mr != null && mr.contentStats() != null) {
                    p90Out.put(path, mr.contentStats().p90Width());
                }
                pl.clearFrozenResult();
            }
        } else if (snl instanceof RelationLayout rl) {
            for (SchemaNodeLayout child : rl.getChildren()) {
                clearFrozenResultInTree(child, paths, p90Out, datum, model);
            }
        }
    }

    /**
     * Compute the maximum rendered width for {@code pl}'s field values in
     * {@code datum} by extracting the field from each data row.  Returns 0.0
     * when the data is null or contains no rows with this field.
     *
     * <p>This is a lightweight scan used only for the convergence fast-path —
     * it does NOT call {@code measure()} and has no side-effects on the layout.
     */
    private static double computeMaxWidthForPath(PrimitiveLayout pl,
                                                  JsonNode datum,
                                                  Style model) {
        if (datum == null) return 0.0;
        double max = 0.0;
        List<JsonNode> rows = SchemaNode.asList(datum);
        for (JsonNode row : rows) {
            JsonNode value = pl.extractFrom(row);
            if (value == null || value.isNull() || value.isMissingNode()) continue;
            if (value.isArray()) {
                for (JsonNode elem : value) {
                    double w = pl.width(elem);
                    if (w > max) max = w;
                }
            } else {
                double w = pl.width(value);
                if (w > max) max = w;
            }
        }
        return max;
    }

    /**
     * Re-measures only the {@link PrimitiveLayout}s whose paths are in
     * {@code changedPaths}, then compares each new p90 against the value in
     * {@link #p90Snapshot} using 10-unit bucket identity:
     * {@code (int)(p90/10)}.
     *
     * <p><b>OUTLINE sibling expansion</b>: if a changed primitive's direct parent
     * {@link RelationLayout} is in OUTLINE mode ({@code !useTable}), all sibling
     * primitives are also re-measured, because OUTLINE column widths are shared
     * across siblings.
     *
     * @param changedPaths paths whose frozen results have already been cleared
     * @param datum        the new JSON data
     * @return set of paths where the p90 bucket actually changed; empty when all
     *         p90s stayed within the same bucket
     */
    private Set<SchemaPath> remeasureChanged(Set<SchemaPath> changedPaths,
                                              JsonNode datum) {
        return remeasureChangedImpl(changedPaths, layout, datum, model, p90Snapshot);
    }

    /**
     * Phase 3c: Lightweight mode-flip detection.
     *
     * <p>After {@link #remeasureChanged} identifies bucket-changed paths, this
     * method checks whether any affected {@link RelationLayout} ancestor would
     * actually flip its TABLE/OUTLINE decision.  Uses a direct threshold
     * comparison — {@code calculateTableColumnWidth() + nestedHorizontalInset <= availableWidth}
     * — against each RelationLayout ancestor of the changed paths, without
     * running the full constraint solver.
     *
     * @param bucketChangedPaths paths whose p90 bucket changed after re-measure
     * @return {@code true} if any ancestor RelationLayout would change its
     *         TABLE/OUTLINE mode, requiring a full re-layout; {@code false} if
     *         the mode is stable and a rebind suffices
     */
    private boolean detectModeFlip(Set<SchemaPath> bucketChangedPaths) {
        if (layout == null || decisionCache.isEmpty()) return true;
        return detectModeFlipInTree(bucketChangedPaths, layout, getWidth());
    }

    /**
     * Recursively walks the layout tree rooted at {@code node}, checking each
     * {@link RelationLayout} that is a strict ancestor of any path in
     * {@code changedPaths}. For each such relation, recomputes
     * {@code calculateTableColumnWidth()} and compares the threshold
     * {@code newTableWidth + nestedInset <= availableWidth} against the current
     * {@link RelationLayout#isUseTable()} state. Returns {@code true} on the
     * first detected flip.
     *
     * @param changedPaths  paths whose p90 bucket changed
     * @param node          current layout node being evaluated
     * @param availableWidth width available to {@code node} from its parent
     * @return {@code true} if a mode flip is detected anywhere in the subtree
     */
    private static boolean detectModeFlipInTree(Set<SchemaPath> changedPaths,
                                                 SchemaNodeLayout node,
                                                 double availableWidth) {
        if (!(node instanceof RelationLayout rl)) return false;

        SchemaPath rlPath = rl.getSchemaPath();
        if (rlPath != null && isAncestorOfAny(rlPath, changedPaths)) {
            boolean currentUseTable = rl.isUseTable();
            double newTableWidth    = rl.calculateTableColumnWidth();
            double nestedInset      = rl.getStyle().getNestedHorizontalInset();
            boolean wouldUseTable   = newTableWidth + nestedInset <= availableWidth;
            if (currentUseTable != wouldUseTable) {
                return true;
            }
        }

        // Recurse into child RelationLayouts.
        // In OUTLINE mode the child's available width = (parentWidth - labelWidth)
        // - outlineCellHorizontalInset. In TABLE mode the top-level layout pass
        // already committed the tree; we use a conservative full-width estimate
        // for children to avoid false negatives.
        double lw = rl.getLabelWidth();
        double childWidth = rl.isUseTable()
                ? availableWidth
                : availableWidth - lw - rl.getStyle().getOutlineCellHorizontalInset();

        for (SchemaNodeLayout child : rl.getChildren()) {
            if (detectModeFlipInTree(changedPaths, child, childWidth)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code candidate} is a strict ancestor of at
     * least one path in {@code paths}: i.e., {@code candidate} is a proper
     * prefix of that path (has fewer segments and the segments match).
     */
    private static boolean isAncestorOfAny(SchemaPath candidate,
                                            Set<SchemaPath> paths) {
        List<String> candSegs = candidate.segments();
        for (SchemaPath p : paths) {
            List<String> segs = p.segments();
            if (segs.size() > candSegs.size()
                    && segs.subList(0, candSegs.size()).equals(candSegs)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Core implementation of {@link #remeasureChanged} extracted as a static
     * helper so tests can exercise it without a live {@link AutoLayout} instance.
     */
    private static Set<SchemaPath> remeasureChangedImpl(Set<SchemaPath> changedPaths,
                                                         SchemaNodeLayout rootLayout,
                                                         JsonNode datum,
                                                         Style model,
                                                         Map<SchemaPath, Double> p90Snapshot) {
        if (changedPaths.isEmpty() || rootLayout == null || datum == null) {
            return Set.of();
        }

        // Expand changedPaths with OUTLINE siblings, collecting (pl, parentRl) pairs
        Set<SchemaPath> toMeasure = expandWithOutlineSiblings(changedPaths, rootLayout);

        // Re-measure each primitive in toMeasure and collect bucket changes
        Set<SchemaPath> bucketChanged = new HashSet<>();
        remeasureInTree(rootLayout, toMeasure, changedPaths, datum, model, p90Snapshot,
                        bucketChanged);
        return bucketChanged;
    }

    /**
     * Expand {@code changedPaths} to include OUTLINE siblings: for each path in
     * {@code changedPaths}, if the parent {@link RelationLayout} is OUTLINE mode
     * ({@code !useTable}), add all sibling primitive paths to the result set.
     */
    private static Set<SchemaPath> expandWithOutlineSiblings(Set<SchemaPath> changedPaths,
                                                              SchemaNodeLayout rootLayout) {
        Set<SchemaPath> expanded = new HashSet<>(changedPaths);
        expandSiblingsInTree(rootLayout, changedPaths, expanded);
        return expanded;
    }

    private static void expandSiblingsInTree(SchemaNodeLayout snl,
                                              Set<SchemaPath> changedPaths,
                                              Set<SchemaPath> expanded) {
        if (snl instanceof RelationLayout rl) {
            // Check if any child is in changedPaths AND this relation is OUTLINE mode
            boolean anyChildChanged = false;
            for (SchemaNodeLayout child : rl.getChildren()) {
                if (child instanceof PrimitiveLayout pl && pl.getSchemaPath() != null
                        && changedPaths.contains(pl.getSchemaPath())) {
                    anyChildChanged = true;
                    break;
                }
            }
            if (anyChildChanged && !rl.isUseTable()) {
                // OUTLINE: add all sibling primitive paths
                for (SchemaNodeLayout child : rl.getChildren()) {
                    if (child instanceof PrimitiveLayout pl && pl.getSchemaPath() != null) {
                        expanded.add(pl.getSchemaPath());
                    }
                }
            }
            // Recurse into children
            for (SchemaNodeLayout child : rl.getChildren()) {
                expandSiblingsInTree(child, changedPaths, expanded);
            }
        }
    }

    /**
     * Recursively walk the layout tree, re-measuring each {@link PrimitiveLayout}
     * whose path is in {@code toMeasure}.  For each re-measured primitive,
     * compare the new p90 bucket against {@code p90Snapshot}; if the bucket
     * changed, add the path to {@code bucketChangedOut}.
     *
     * <p>Only paths also present in {@code originalChanged} can be added to
     * {@code bucketChangedOut} — siblings added by OUTLINE expansion do not
     * trigger bucket-change notifications on their own.
     */
    private static void remeasureInTree(SchemaNodeLayout snl,
                                         Set<SchemaPath> toMeasure,
                                         Set<SchemaPath> originalChanged,
                                         JsonNode datum,
                                         Style model,
                                         Map<SchemaPath, Double> p90Snapshot,
                                         Set<SchemaPath> bucketChangedOut) {
        if (snl instanceof PrimitiveLayout pl) {
            SchemaPath path = pl.getSchemaPath();
            if (path != null && toMeasure.contains(path)) {
                pl.measure(datum, n -> n, model);
                // Only report bucket changes for originally-changed paths
                if (originalChanged.contains(path)) {
                    MeasureResult newMr = pl.getMeasureResult();
                    Double oldP90 = p90Snapshot.get(path);
                    if (newMr != null && newMr.contentStats() != null) {
                        double newP90 = newMr.contentStats().p90Width();
                        if (oldP90 == null || (int)(newP90 / 10) != (int)(oldP90 / 10)) {
                            bucketChangedOut.add(path);
                        }
                    } else if (oldP90 == null) {
                        // No stats available yet — treat as changed to be safe
                        bucketChangedOut.add(path);
                    }
                }
            }
        } else if (snl instanceof RelationLayout rl) {
            for (SchemaNodeLayout child : rl.getChildren()) {
                remeasureInTree(child, toMeasure, originalChanged, datum, model,
                                p90Snapshot, bucketChangedOut);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Package-private test hooks (no-JavaFX headless tests)
    // -----------------------------------------------------------------------

    /**
     * Test-visible entry-point for {@link #remeasureChangedImpl}.
     * Allows headless unit tests to exercise the bucket-comparison logic without
     * a live {@link AutoLayout} instance.
     */
    static Set<SchemaPath> remeasureChangedForTest(Set<SchemaPath> changedPaths,
                                                    SchemaNodeLayout rootLayout,
                                                    JsonNode datum,
                                                    Style model,
                                                    Map<SchemaPath, Double> p90Snapshot) {
        return remeasureChangedImpl(changedPaths, rootLayout, datum, model, p90Snapshot);
    }

    /**
     * Test-visible entry-point for {@link #detectModeFlipInTree}.
     * Allows headless unit tests to exercise the mode-flip detection logic
     * without a live {@link AutoLayout} instance.
     *
     * @param bucketChangedPaths paths whose p90 bucket changed after re-measure
     * @param rootLayout         the root of the layout tree (may be null)
     * @param availableWidth     the width available to the root node
     * @return {@code true} if any ancestor RelationLayout would change its
     *         TABLE/OUTLINE mode; {@code true} when {@code rootLayout} is null
     */
    static boolean detectModeFlipForTest(Set<SchemaPath> bucketChangedPaths,
                                          SchemaNodeLayout rootLayout,
                                          double availableWidth) {
        if (rootLayout == null) return true;
        return detectModeFlipInTree(bucketChangedPaths, rootLayout, availableWidth);
    }

    /**
     * Test-visible entry-point for the convergence fast-path in
     * {@link #clearFrozenResultInTree}.  Drives the same logic used by
     * {@link #clearFrozenResultForPaths} without requiring a live AutoLayout.
     */
    static void clearFrozenResultForPathsForTest(Set<SchemaPath> paths,
                                                  SchemaNodeLayout rootLayout,
                                                  JsonNode datum,
                                                  Style model,
                                                  Map<SchemaPath, Double> p90Snapshot) {
        if (paths.isEmpty() || rootLayout == null) return;
        Map<SchemaPath, Double> p90Out = new HashMap<>(p90Snapshot);
        clearFrozenResultInTree(rootLayout, paths, p90Out, datum, model);
        p90Snapshot.putAll(p90Out);
    }
}
