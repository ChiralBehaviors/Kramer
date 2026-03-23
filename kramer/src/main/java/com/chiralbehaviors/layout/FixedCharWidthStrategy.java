// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;
import java.util.Objects;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle.PrimitiveTextStyle;
import com.chiralbehaviors.layout.style.RelationStyle;

import javafx.geometry.Insets;

/**
 * Fixed character-width measurement strategy for cross-platform parity testing.
 * Returns {@code text.length() * charWidth} for all text measurements.
 * No JavaFX Application Thread required.
 *
 * <p>Matches the TypeScript {@code FixedMeasurement(charWidth)} exactly,
 * enabling identical layout decisions on both platforms for the same
 * schema + data + width input.
 */
public class FixedCharWidthStrategy implements MeasurementStrategy {

    private final double charWidth;
    private final double lineHeight;
    private final double inset;

    public FixedCharWidthStrategy() {
        this(7.0, 20.0, 4.0);
    }

    public FixedCharWidthStrategy(double charWidth) {
        this(charWidth, 20.0, 4.0);
    }

    public FixedCharWidthStrategy(double charWidth, double lineHeight, double inset) {
        this.charWidth = charWidth;
        this.lineHeight = lineHeight;
        this.inset = inset;
    }

    @Override
    public PrimitiveStyle measurePrimitiveStyle(Primitive primitive,
                                                List<String> stylesheets) {
        Objects.requireNonNull(primitive, "primitive must not be null");
        Insets labelInsets = new Insets(inset);
        LabelStyle labelStyle = new FixedCharWidthLabelStyle(charWidth, lineHeight, labelInsets);
        return new PrimitiveTextStyle(labelStyle, labelInsets, labelStyle);
    }

    @Override
    public RelationStyle measureRelationStyle(Relation relation,
                                              List<String> stylesheets) {
        Objects.requireNonNull(relation, "relation must not be null");
        Insets labelInsets = new Insets(inset);
        LabelStyle labelStyle = new FixedCharWidthLabelStyle(charWidth, lineHeight, labelInsets);
        return new RelationStyle(labelStyle, labelInsets, 10);
    }

    /**
     * LabelStyle that uses fixed character width instead of JavaFX text measurement.
     */
    private static class FixedCharWidthLabelStyle extends LabelStyle {

        private final double charWidth;
        private final Insets labelInsets;

        FixedCharWidthLabelStyle(double charWidth, double lineHeight, Insets insets) {
            super(lineHeight, insets);
            this.charWidth = charWidth;
            this.labelInsets = insets;
        }

        @Override
        public double width(String text) {
            if (text == null || text.isEmpty()) return 0;
            return Math.ceil(text.length() * charWidth)
                   + labelInsets.getLeft() + labelInsets.getRight();
        }
    }
}
