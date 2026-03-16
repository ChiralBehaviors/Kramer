// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;

/**
 * Abstracts CSS measurement away from JavaFX, enabling alternative
 * implementations (e.g., headless, test doubles) to supply style metrics
 * without requiring a live JavaFX Application Thread.
 */
public interface MeasurementStrategy {

    /**
     * Measures and returns the {@link PrimitiveStyle} for the given primitive
     * node, applying the supplied stylesheets.
     *
     * @param primitive   the schema node to measure
     * @param stylesheets CSS stylesheet URIs to apply during measurement
     * @return computed style metrics for the primitive
     */
    PrimitiveStyle measurePrimitiveStyle(Primitive primitive, List<String> stylesheets);

    /**
     * Measures and returns the {@link RelationStyle} for the given relation
     * node, applying the supplied stylesheets.
     *
     * @param relation    the schema node to measure
     * @param stylesheets CSS stylesheet URIs to apply during measurement
     * @return computed style metrics for the relation
     */
    RelationStyle measureRelationStyle(Relation relation, List<String> stylesheets);
}
