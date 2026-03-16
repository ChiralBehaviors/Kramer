// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;

/**
 * JavaFX implementation of {@link MeasurementStrategy}. Delegates to the
 * existing {@link Style#computePrimitiveStyle} and
 * {@link Style#computeRelationStyle} logic, which must run on the JavaFX
 * Application Thread.
 *
 * <p>Extends {@link Style} to access the protected measurement methods.
 * A dedicated {@link Style} instance is created per
 * {@code measurePrimitiveStyle}/{@code measureRelationStyle} call with the
 * provided stylesheets so that callers supply their own stylesheet context
 * rather than sharing mutable state.
 */
public class JavaFxMeasurementStrategy implements MeasurementStrategy {

    /**
     * Minimal {@link Style} subclass that exposes the protected compute
     * methods and accepts a stylesheet list at construction time.
     */
    private static final class MeasurementStyle extends Style {
        MeasurementStyle(List<String> stylesheets) {
            if (stylesheets != null && !stylesheets.isEmpty()) {
                setStyleSheets(stylesheets, this);
            }
        }

        // Access widened from protected to public deliberately — allows outer class to call
        // these methods. No measurementStrategy injected, so JAT assert fires as intended.
        @Override
        public PrimitiveStyle computePrimitiveStyle(Primitive p) {
            return super.computePrimitiveStyle(p);
        }

        @Override
        public RelationStyle computeRelationStyle(Relation r) {
            return super.computeRelationStyle(r);
        }
    }

    @Override
    public PrimitiveStyle measurePrimitiveStyle(Primitive primitive,
                                                List<String> stylesheets) {
        return new MeasurementStyle(stylesheets).computePrimitiveStyle(primitive);
    }

    @Override
    public RelationStyle measureRelationStyle(Relation relation,
                                              List<String> stylesheets) {
        return new MeasurementStyle(stylesheets).computeRelationStyle(relation);
    }
}
