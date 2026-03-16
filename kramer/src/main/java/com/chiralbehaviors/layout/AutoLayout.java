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
        return new RelationConstraint(
                rl.getSchemaPath(),
                tableWidth,
                nestedInset,
                width,
                availableWidthAsTable,
                childConstraints,
                rl.isCrosstab()
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
        node.setMinWidth(width);
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
        node.setMinWidth(width);
        node.setPrefWidth(width);
        node.setMaxWidth(width);
        control.updateItem(zeeData);

        findVirtualFlow(node).ifPresent(vf -> controller.recoverCursor(savedCursor, vf));
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
            // Detect which primitive paths have changed values and clear their
            // frozenResult so the next measure() call re-computes widths.
            if (layout != null && datum != null) {
                Set<SchemaPath> changedPaths = detectChangedPaths(datum);
                clearFrozenResultForPaths(changedPaths);
            }
            if (control == null) {
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
     * {@link PrimitiveLayout} via {@link Style#layout(SchemaNode)} and calls
     * {@link PrimitiveLayout#clearFrozenResult()}, saving the p90Width into
     * {@link #p90Snapshot} before clearing.
     *
     * <p>PrimitiveLayouts with no frozen result are silently skipped.
     */
    private void clearFrozenResultForPaths(Set<SchemaPath> paths) {
        if (paths.isEmpty() || layout == null) return;
        Map<SchemaPath, Double> newP90 = new HashMap<>(p90Snapshot);
        clearFrozenResultInTree(layout, paths, newP90);
        p90Snapshot = Map.copyOf(newP90);
    }

    /**
     * Recursively walk the layout tree, clearing frozenResult on any
     * {@link PrimitiveLayout} whose path is in {@code paths}.
     */
    private static void clearFrozenResultInTree(SchemaNodeLayout snl,
                                                Set<SchemaPath> paths,
                                                Map<SchemaPath, Double> p90Out) {
        if (snl instanceof PrimitiveLayout pl) {
            SchemaPath path = pl.getSchemaPath();
            if (path != null && paths.contains(path)) {
                // Capture p90Width before clearing
                MeasureResult mr = pl.getMeasureResult();
                if (mr != null && mr.contentStats() != null) {
                    p90Out.put(path, mr.contentStats().p90Width());
                }
                pl.clearFrozenResult();
            }
        } else if (snl instanceof RelationLayout rl) {
            for (SchemaNodeLayout child : rl.getChildren()) {
                clearFrozenResultInTree(child, paths, p90Out);
            }
        }
    }
}
