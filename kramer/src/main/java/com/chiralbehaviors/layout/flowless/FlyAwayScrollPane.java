package com.chiralbehaviors.layout.flowless;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.reactfx.value.Val;
import org.reactfx.value.Var;

import com.chiralbehaviors.layout.cell.VerticalCell;

import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.value.ChangeListener;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Region;

/**
 * I am the kindly one
 * 
 * @author halhildebrand
 *
 * @param <V>
 */
public class FlyAwayScrollPane<V extends Node & Virtualized>
        extends VerticalCell<FlyAwayScrollPane<V>> implements Virtualized {
    public static final String                                     STYLE_CLASS     = "region-cell";

    private static final PseudoClass                               CONTENT_FOCUSED = PseudoClass.getPseudoClass("content-focused");

    private static final String                                    STYLE_SHEET     = "fly-away-scroll.css";

    private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

    static {
        List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Region.getClassCssMetaData());
        STYLEABLES = Collections.unmodifiableList(styleables);
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return STYLEABLES;
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

    protected final V                     content;
    private final ChangeListener<Boolean> contentFocusedListener;
    private final FlyAwayScrollBar        vbar;
    private Var<Double>                   vbarValue;

    public FlyAwayScrollPane(@NamedArg("content") V content) {
        super(STYLE_SHEET);
        getStyleClass().add(STYLE_CLASS);
        this.getStyleClass()
            .add("virtualized-scroll-pane");
        this.content = content;

        // create scrollbars
        vbar = new FlyAwayScrollBar();
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
                                               FlyAwayScrollPane::offsetToScrollbarPosition)
                                      .orElseConst(0.0)
                                      .asVar(this::setVPosition);
        vbarValue = Var.doubleVar(vbar.valueProperty());
        Bindings.bindBidirectional(vbarValue, vPosEstimate);

        contentFocusedListener = (obs, ov, nv) -> {
            pseudoClassStateChanged(CONTENT_FOCUSED, nv);
            if (nv) {
                vbar.setVisible(true);
            } else {
                vbar.setVisible(false);
            }
        };
        content.focusedProperty()
               .addListener(contentFocusedListener);
        getChildren().addAll(content, vbar);
        getChildren().addListener((Observable obs) -> dispose());

        vbar.setVisible(false);
        Platform.runLater(this::requestLayout);
    }

    @Override
    public Var<Double> estimatedScrollYProperty() {
        return content.estimatedScrollYProperty();
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

    @Override
    public void scrollYBy(double deltaY) {
        content.scrollYBy(deltaY);
    }

    @Override
    public void scrollYToPixel(double pixel) {
        content.scrollYToPixel(pixel);
    }

    @Override
    public Val<Double> totalHeightEstimateProperty() {
        return content.totalHeightEstimateProperty();
    }

    @Override
    public Val<Double> totalWidthEstimateProperty() {
        return content.totalWidthEstimateProperty();
    }

    @Override
    protected double computeMaxHeight(double width) {
        return content.maxHeight(width);
    }

    @Override
    protected double computeMaxWidth(double height) {
        return content.maxWidth(height);
    }

    @Override
    protected double computeMinHeight(double width) {
        return -1;
    }

    @Override
    protected double computeMinWidth(double height) {
        return vbar.minWidth(-1);
    }

    @Override
    protected double computePrefHeight(double width) {
        return content.prefHeight(width);
    }

    @Override
    protected double computePrefWidth(double height) {
        return content.prefWidth(height);
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

    public void dispose() {
        content.focusedProperty()
               .removeListener(contentFocusedListener);
        vbarValue.unbindBidirectional(content.estimatedScrollYProperty());
        unbindScrollBar(vbar);
    }

    private void setVPosition(double pos) {
        double offset = scrollbarPositionToOffset(pos, content.getLayoutBounds()
                                                              .getHeight(),
                                                  content.totalHeightEstimateProperty()
                                                         .getValue());
        content.estimatedScrollYProperty()
               .setValue(offset);
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
}
