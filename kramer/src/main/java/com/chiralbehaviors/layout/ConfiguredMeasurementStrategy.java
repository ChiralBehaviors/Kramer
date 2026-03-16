// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle.PrimitiveTextStyle;
import com.chiralbehaviors.layout.style.RelationStyle;

import javafx.geometry.Insets;

/**
 * A headless {@link MeasurementStrategy} that returns pre-configured style
 * values without requiring a JavaFX Application Thread or CSS measurement.
 *
 * <p>This is intended for server-side or test use where exact CSS metrics are
 * not available, but layout decisions (table vs. outline, column count) need
 * approximate dimensions.
 */
public class ConfiguredMeasurementStrategy implements MeasurementStrategy {

    private static final double DEFAULT_LINE_HEIGHT = 20.0;
    private static final double DEFAULT_INSET       = 4.0;
    private static final int    DEFAULT_MAX_CARDINALITY = 10;

    private final double defaultLineHeight;
    private final double defaultInset;

    /** Constructs with default line height (20.0) and inset (4.0). */
    public ConfiguredMeasurementStrategy() {
        this(DEFAULT_LINE_HEIGHT, DEFAULT_INSET);
    }

    /**
     * Constructs with explicit metrics.
     *
     * @param lineHeight single-line text height in logical pixels
     * @param inset      uniform padding applied to all style regions
     */
    public ConfiguredMeasurementStrategy(double lineHeight, double inset) {
        this.defaultLineHeight = lineHeight;
        this.defaultInset = inset;
    }

    @Override
    public PrimitiveStyle measurePrimitiveStyle(Primitive primitive,
                                                List<String> stylesheets) {
        Insets labelInsets = new Insets(defaultInset);
        LabelStyle labelStyle = new LabelStyle(defaultLineHeight, labelInsets);
        LabelStyle primitiveTextStyle = new LabelStyle(defaultLineHeight, labelInsets);
        Insets listInsets = new Insets(defaultInset);
        return new PrimitiveTextStyle(labelStyle, listInsets, primitiveTextStyle);
    }

    @Override
    public RelationStyle measureRelationStyle(Relation relation,
                                              List<String> stylesheets) {
        Insets labelInsets = new Insets(defaultInset);
        LabelStyle labelStyle = new LabelStyle(defaultLineHeight, labelInsets);
        Insets regionInsets = new Insets(defaultInset);
        return new RelationStyle(labelStyle, regionInsets, DEFAULT_MAX_CARDINALITY);
    }
}
