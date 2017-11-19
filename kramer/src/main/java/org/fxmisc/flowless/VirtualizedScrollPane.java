package org.fxmisc.flowless;

import static javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED;

import org.reactfx.value.Val;
import org.reactfx.value.Var;

import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;

public class VirtualizedScrollPane<V extends Node & Virtualized> extends Region
        implements Virtualized {

    private static final PseudoClass              CONTENT_FOCUSED = PseudoClass.getPseudoClass("content-focused");

    private final ScrollBar                       vbar;
    private final V                               content;
    private final ChangeListener<Boolean>         contentFocusedListener;

    private Var<Double>                           vbarValue;

    /** The Policy for the Vertical ScrollBar */
    private final Var<ScrollPane.ScrollBarPolicy> vbarPolicy;

    public final ScrollPane.ScrollBarPolicy getVbarPolicy() {
        return vbarPolicy.getValue();
    }

    public final void setVbarPolicy(ScrollPane.ScrollBarPolicy value) {
        vbarPolicy.setValue(value);
    }

    public final Var<ScrollPane.ScrollBarPolicy> vbarPolicyProperty() {
        return vbarPolicy;
    }

    /**
     * Constructs a VirtualizedScrollPane with the given content and policies
     */
    public VirtualizedScrollPane(@NamedArg("content") V content,
                                 @NamedArg("vPolicy") ScrollPane.ScrollBarPolicy vPolicy) {
        this.getStyleClass()
            .add("virtualized-scroll-pane");
        this.content = content;

        // create scrollbars 
        vbar = new ScrollBar();
        vbar.setOrientation(Orientation.VERTICAL);

        // scrollbar ranges 
        vbar.setMin(0);
        vbar.maxProperty()
            .bind(content.totalHeightEstimateProperty());

        // scrollbar increments 
        setupUnitIncrement(vbar);
        vbar.blockIncrementProperty()
            .bind(vbar.visibleAmountProperty());

        // scrollbar positions 
        Var<Double> vPosEstimate = Val.combine(content.estimatedScrollYProperty(),
                                               Val.map(content.layoutBoundsProperty(),
                                                       Bounds::getHeight),
                                               content.totalHeightEstimateProperty(),
                                               VirtualizedScrollPane::offsetToScrollbarPosition)
                                      .orElseConst(0.0)
                                      .asVar(this::setVPosition);
        vbarValue = Var.doubleVar(vbar.valueProperty());
        Bindings.bindBidirectional(vbarValue, vPosEstimate);

        // scrollbar visibility 
        vbarPolicy = Var.newSimpleVar(vPolicy);
 
        Val<Double> layoutHeight = Val.map(layoutBoundsProperty(),
                                           Bounds::getHeight);
        Val<Boolean> needsVBar0 = Val.combine(content.totalHeightEstimateProperty(),
                                              layoutHeight,
                                              (ch, lh) -> ch > lh);
        Val<Boolean> needsVBar = Val.combine(needsVBar0,
                                             Var.newSimpleVar(false),
                                             content.totalHeightEstimateProperty(),
                                             Var.newSimpleVar(0.0),
                                             layoutHeight,
                                             (needsV, needsH, ch, hbh,
                                              lh) -> needsV
                                                     || needsH
                                                        && ch
                                                           + hbh.doubleValue() > lh);

        Val<Boolean> shouldDisplayVertical = Val.flatMap(vbarPolicy, policy -> {
            switch (policy) {
                case NEVER:
                    return Val.constant(false);
                case ALWAYS:
                    return Val.constant(true);
                default: // AS_NEEDED
                    return needsVBar;
            }
        });

        // request layout later, because if currently in layout, the request is ignored 
        shouldDisplayVertical.addListener(obs -> Platform.runLater(this::requestLayout));

        vbar.visibleProperty()
            .bind(shouldDisplayVertical);

        contentFocusedListener = (obs, ov,
                                  nv) -> pseudoClassStateChanged(CONTENT_FOCUSED,
                                                                 nv);
        content.focusedProperty()
               .addListener(contentFocusedListener);
        getChildren().addAll(content, vbar);
        getChildren().addListener((Observable obs) -> dispose());
    }

    /**
     * Constructs a VirtualizedScrollPane that only displays its horizontal and
     * vertical scroll bars as needed
     */
    public VirtualizedScrollPane(@NamedArg("content") V content) {
        this(content, AS_NEEDED);
    }

    /**
     * Does not unbind scrolling from Content before returning Content.
     * 
     * @return - the content
     */
    public V getContent() {
        return content;
    }

    /**
     * Unbinds scrolling from Content before returning Content.
     * 
     * @return - the content
     */
    public V removeContent() {
        getChildren().clear();
        return content;
    }

    private void dispose() {
        content.focusedProperty()
               .removeListener(contentFocusedListener);
        vbarValue.unbindBidirectional(content.estimatedScrollYProperty());
        unbindScrollBar(vbar);
    }

    private void unbindScrollBar(ScrollBar bar) {
        bar.maxProperty()
           .unbind();
        bar.unitIncrementProperty()
           .unbind();
        bar.blockIncrementProperty()
           .unbind();
        bar.visibleProperty()
           .unbind();
    }

    @Override
    public Val<Double> totalWidthEstimateProperty() {
        return content.totalWidthEstimateProperty();
    }

    @Override
    public Val<Double> totalHeightEstimateProperty() {
        return content.totalHeightEstimateProperty();
    }

    @Override
    public Var<Double> estimatedScrollXProperty() {
        return content.estimatedScrollXProperty();
    }

    @Override
    public Var<Double> estimatedScrollYProperty() {
        return content.estimatedScrollYProperty();
    }

    @Override
    public void scrollXBy(double deltaX) {
        content.scrollXBy(deltaX);
    }

    @Override
    public void scrollYBy(double deltaY) {
        content.scrollYBy(deltaY);
    }

    @Override
    public void scrollXToPixel(double pixel) {
        content.scrollXToPixel(pixel);
    }

    @Override
    public void scrollYToPixel(double pixel) {
        content.scrollYToPixel(pixel);
    }

    @Override
    protected double computePrefWidth(double height) {
        return content.prefWidth(height);
    }

    @Override
    protected double computePrefHeight(double width) {
        return content.prefHeight(width);
    }

    @Override
    protected double computeMinWidth(double height) {
        return vbar.minWidth(-1);
    }

    @Override
    protected double computeMinHeight(double width) {
        return -1;
    }

    @Override
    protected double computeMaxWidth(double height) {
        return content.maxWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width) {
        return content.maxHeight(width);
    }

    @Override
    protected void layoutChildren() {
        double layoutWidth = getLayoutBounds().getWidth();
        double layoutHeight = getLayoutBounds().getHeight();
        boolean vbarVisible = vbar.isVisible();
        double vbarWidth = vbarVisible ? vbar.prefWidth(-1) : 0;

        double w = layoutWidth;
        double h = layoutHeight;

        content.resize(w, h);

        vbar.setVisibleAmount(h);

        if (vbarVisible) {
            vbar.resizeRelocate(layoutWidth - vbarWidth, 0, vbarWidth, h);
        }
    }

    private void setVPosition(double pos) {
        double offset = scrollbarPositionToOffset(pos, content.getLayoutBounds()
                                                              .getHeight(),
                                                  content.totalHeightEstimateProperty()
                                                         .getValue());
        content.estimatedScrollYProperty()
               .setValue(offset);
    }

    private static void setupUnitIncrement(ScrollBar bar) {
        bar.unitIncrementProperty()
           .bind(new DoubleBinding() {
               {
                   bind(bar.maxProperty(), bar.visibleAmountProperty());
               }

               @Override
               protected double computeValue() {
                   double max = bar.getMax();
                   double visible = bar.getVisibleAmount();
                   return max > visible ? 16 / (max - visible) * max : 0;
               }
           });
    }

    private static double offsetToScrollbarPosition(double contentOffset,
                                                    double viewportSize,
                                                    double contentSize) {
        return contentSize > viewportSize ? contentOffset
                                            / (contentSize - viewportSize)
                                            * contentSize
                                          : 0;
    }

    private static double scrollbarPositionToOffset(double scrollbarPos,
                                                    double viewportSize,
                                                    double contentSize) {
        return contentSize > viewportSize ? scrollbarPos / contentSize
                                            * (contentSize - viewportSize)
                                          : 0;
    }
}