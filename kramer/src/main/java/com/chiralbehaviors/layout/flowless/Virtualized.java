package com.chiralbehaviors.layout.flowless;

import org.reactfx.value.Val;
import org.reactfx.value.Var;

/**
 * Specifies an object that does not have scroll bars by default but which can
 * have scroll bars added to it by wrapping it in a
 * {@link VirtualizedScrollPane}.
 */
public interface Virtualized {

    Var<Double> estimatedScrollYProperty();

    default double getEstimatedScrollY() {
        return estimatedScrollYProperty().getValue();
    }

    default double getTotalHeightEstimate() {
        return totalHeightEstimateProperty().getValue();
    }

    default double getTotalWidthEstimate() {
        return totalHeightEstimateProperty().getValue();
    }

    /**
     * Scroll the content vertically by the given amount.
     *
     * @param deltaY
     *            positive value scrolls down, negative value scrolls up
     */
    void scrollYBy(double deltaY);

    /**
     * Scroll the content vertically to the pixel
     *
     * @param pixel
     *            - the pixel position to which to scroll
     */
    void scrollYToPixel(double pixel);

    Val<Double> totalHeightEstimateProperty();

    Val<Double> totalWidthEstimateProperty();
}
