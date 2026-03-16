// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for nested VirtualFlow chain navigation in LayoutSearch.
 *
 * <p>These tests exercise the async Platform.runLater chain without a live
 * JavaFX scene by supplying a synchronous runLater substitute and an
 * injectable flow resolver.
 */
class NestedNavigationTest {

    /** Synchronous substitute for Platform.runLater in tests. */
    private final List<Runnable> pendingRunnables = new ArrayList<>();

    private Relation                                     schema;
    private com.fasterxml.jackson.databind.node.ArrayNode data;

    @BeforeEach
    void setUp() {
        pendingRunnables.clear();
        schema = new Relation("items");
        schema.addChild(new Primitive("name"));

        data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 5; i++) {
            var row = JsonNodeFactory.instance.objectNode();
            row.put("name", "Item" + i);
            data.add(row);
        }
    }

    /** Drains all pending runnables synchronously (simulates JavaFX pulse pump). */
    private void drainAll() {
        while (!pendingRunnables.isEmpty()) {
            List<Runnable> batch = new ArrayList<>(pendingRunnables);
            pendingRunnables.clear();
            batch.forEach(Runnable::run);
        }
    }

    // -----------------------------------------------------------------------
    // navigationCancelled flag prevents further hops
    // -----------------------------------------------------------------------

    @Test
    void cancellationPreventsHops() {
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf0 = mock(VirtualFlow.class);
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf1 = mock(VirtualFlow.class);

        Function<Integer, VirtualFlow<?>> resolver = depth -> switch (depth) {
            case 0 -> vf0;
            case 1 -> vf1;
            default -> null;
        };

        var path = buildPath(0, 2);
        var search = new LayoutSearch(schema, data, resolver, pendingRunnables::add);
        search.navigateToMatch(path);

        // After first hop vf0.show(0) is called and a runLater is queued
        verify(vf0).show(0);
        assertEquals(1, pendingRunnables.size());

        // Cancel before draining — second hop must not execute
        search.cancelNavigation();
        drainAll();

        verifyNoInteractions(vf1);
        assertFalse(search.isNavigationInProgress());
    }

    // -----------------------------------------------------------------------
    // navigationInProgress guard prevents overlapping chains
    // -----------------------------------------------------------------------

    @Test
    void inProgressGuardPreventsOverlap() {
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf0 = mock(VirtualFlow.class);

        AtomicInteger showCount = new AtomicInteger();
        doAnswer(inv -> { showCount.incrementAndGet(); return null; }).when(vf0).show(anyInt());

        Function<Integer, VirtualFlow<?>> resolver = depth -> depth == 0 ? vf0 : null;

        var path = buildPath(0);   // single step
        var search = new LayoutSearch(schema, data, resolver, pendingRunnables::add);

        search.navigateToMatch(path);
        assertTrue(search.isNavigationInProgress());

        // Second call while still in progress must be a no-op
        search.navigateToMatch(path);
        drainAll();

        // vf0.show() called exactly once from the first chain
        assertEquals(1, showCount.get());
    }

    // -----------------------------------------------------------------------
    // null VirtualFlow at depth aborts chain gracefully
    // -----------------------------------------------------------------------

    @Test
    void nullFlowAtDepthAbortsGracefully() {
        // Resolver always returns null — should not throw
        Function<Integer, VirtualFlow<?>> resolver = depth -> null;

        var path = buildPath(3);
        var search = new LayoutSearch(schema, data, resolver, pendingRunnables::add);

        assertDoesNotThrow(() -> search.navigateToMatch(path));
        drainAll();

        assertFalse(search.isNavigationInProgress());
    }

    @Test
    void nullFlowAtSecondDepthAbortsAfterFirstHop() {
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf0 = mock(VirtualFlow.class);

        // depth 0 ok, depth 1 returns null
        Function<Integer, VirtualFlow<?>> resolver = depth -> depth == 0 ? vf0 : null;

        var path = buildPath(0, 2);  // two steps
        var search = new LayoutSearch(schema, data, resolver, pendingRunnables::add);

        search.navigateToMatch(path);
        verify(vf0).show(0);
        assertEquals(1, pendingRunnables.size());

        // Drain — second step should abort because vf1 is null
        drainAll();

        assertFalse(search.isNavigationInProgress());
        verifyNoMoreInteractions(vf0);
    }

    // -----------------------------------------------------------------------
    // NavigationPath with multiple steps chains correctly
    // -----------------------------------------------------------------------

    @Test
    void multiStepPathChainsCorrectly() {
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf0 = mock(VirtualFlow.class);
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf1 = mock(VirtualFlow.class);
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf2 = mock(VirtualFlow.class);

        Function<Integer, VirtualFlow<?>> resolver = depth -> switch (depth) {
            case 0 -> vf0;
            case 1 -> vf1;
            case 2 -> vf2;
            default -> null;
        };

        // Three steps: row 1, row 3, row 0
        var path = buildPath(1, 3, 0);
        var search = new LayoutSearch(schema, data, resolver, pendingRunnables::add);

        // Step 0: immediate
        search.navigateToMatch(path);
        verify(vf0).show(1);
        assertEquals(1, pendingRunnables.size());

        // Step 1: first pulse
        List<Runnable> pulse1 = new ArrayList<>(pendingRunnables);
        pendingRunnables.clear();
        pulse1.forEach(Runnable::run);
        verify(vf1).show(3);
        assertEquals(1, pendingRunnables.size());

        // Step 2: second pulse
        drainAll();
        verify(vf2).show(0);

        assertFalse(search.isNavigationInProgress());
    }

    @Test
    void singleStepPathCompletesWithoutRunLater() {
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf0 = mock(VirtualFlow.class);

        Function<Integer, VirtualFlow<?>> resolver = depth -> depth == 0 ? vf0 : null;

        var path = buildPath(2);  // single step, rowIndex=2
        var search = new LayoutSearch(schema, data, resolver, pendingRunnables::add);

        search.navigateToMatch(path);

        verify(vf0).show(2);
        // No more hops — navigationInProgress should clear without draining
        // (the runLater is fired but clears the flag)
        drainAll();
        assertFalse(search.isNavigationInProgress());
        assertTrue(pendingRunnables.isEmpty());
    }

    @Test
    void cancelNavigationResetsCancelledFlagOnNextNavigate() {
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf0 = mock(VirtualFlow.class);
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf1 = mock(VirtualFlow.class);

        Function<Integer, VirtualFlow<?>> resolver = depth -> switch (depth) {
            case 0 -> vf0;
            case 1 -> vf1;
            default -> null;
        };

        var path = buildPath(0, 1);
        var search = new LayoutSearch(schema, data, resolver, pendingRunnables::add);

        // First chain, then cancel
        search.navigateToMatch(path);
        search.cancelNavigation();
        drainAll();
        assertFalse(search.isNavigationInProgress());

        // Second chain must run fully despite prior cancellation
        search.navigateToMatch(path);
        verify(vf0, times(2)).show(0);

        drainAll();
        verify(vf1).show(1);
        assertFalse(search.isNavigationInProgress());
    }

    // -----------------------------------------------------------------------
    // Helper: build a NavigationPath from row indices (one step per index)
    // -----------------------------------------------------------------------

    private NavigationPath buildPath(int... rowIndices) {
        var fieldPath = new SchemaPath("items").child("name");
        List<NavigationPath.NavigationStep> steps = new ArrayList<>();
        for (int idx : rowIndices) {
            steps.add(new NavigationPath.NavigationStep(fieldPath, idx));
        }
        return new NavigationPath(steps);
    }
}
