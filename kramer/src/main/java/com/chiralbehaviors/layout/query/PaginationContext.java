// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.query;

/**
 * Tracks whether the current data was fetched via cursor-based pagination.
 * When pagination is active, client-side sort operates on the current page
 * only — the result is a page-local sort, not a globally sorted dataset.
 * <p>
 * UI layers should display a visual indicator (tooltip, badge) when
 * {@link #isPageLocal()} returns {@code true} and a sort is active.
 *
 * @param paginated   true if the data source uses cursor-based pagination
 * @param currentPage human-readable page identifier (e.g., cursor value or page number)
 *
 * @author hhildebrand
 */
public record PaginationContext(boolean paginated, String currentPage) {

    /** No pagination — all data is present. */
    public static final PaginationContext NONE = new PaginationContext(false, null);

    /**
     * Returns true when client-side sort will only sort the current page,
     * not the full dataset.
     */
    public boolean isPageLocal() {
        return paginated;
    }
}
