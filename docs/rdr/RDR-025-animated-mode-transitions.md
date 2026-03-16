# RDR-025: Animated Mode Transitions

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P4
- **Created**: 2026-03-16
- **Related**: RDR-012 (constraint solver), RDR-016 (layout stability / incremental update), RDR-023 (partial re-solve), RDR-024 (expanded solver modes — CROSSTAB ternary variable; TABLE/OUTLINE/CROSSTAB transitions are the primary animation trigger)

## Problem Statement

When the constraint solver produces a different TABLE/OUTLINE assignment than the previous one — triggered by a data change, a resize crossing a width-bucket boundary, or a stylesheet update — Kramer currently rebuilds the affected subtree's control tree synchronously and replaces the old node graph with the new one. The transition is instantaneous: the layout jumps from one arrangement to another with no visual continuity.

For live data scenarios (streaming dashboards, responsive resizing) this abrupt switch is jarring. The user has no visual signal that the layout structure changed — a column-based table view can silently become a vertical outline without any indication. Users lose their spatial context for where data was on screen.

The constraint solver and RDR-016's delta infrastructure together provide exactly the information needed to animate: the solver knows the old mode assignment and the new one, and RDR-023's `partialResolve` path (builds on RDR-016's decision cache) identifies which subtree changed. The delta is known before any control tree rebuild begins.

---

## Proposed Solution

When `AutoLayout` determines that a subtree's mode assignment has changed (the `partialResolve` return value from RDR-023 is non-empty — see interface note below — or a width-bucket crossing triggers a different `LayoutResult`), run a short CSS transition instead of an immediate node replacement.

> **Interface dependency (RDR-023):** `partialResolve()` in RDR-023 must be extended to return `Optional<SchemaPath>` (the changed subtree root) rather than `boolean`. This gives animation code the precise subtree handle needed to target the transition. If the RDR-023 interface is preserved as boolean-only, the scope of RDR-025 must be reduced to root-level cross-fade only (Alt B), since per-subtree targeting is not possible without the path.

### Mechanism

1. **Capture the before-state snapshot.** Before calling `buildControl()` for the affected subtree, snapshot the current visual bounds of each affected `Region` in the subtree (position, size, opacity).

2. **Build the after-state off-screen.** Call `buildControl()` for the affected subtree into a detached scene graph. Do not replace the visible nodes yet.

3. **Animate.** Add the new subtree to the scene at zero opacity. Use a `ParallelTransition` combining:
   - `FadeTransition`: old subtree fades out (duration: 120ms).
   - `FadeTransition`: new subtree fades in (duration: 120ms), starting 60ms after the old begins fading.
   - `ScaleTransition` (optional): new subtree scales from 0.95 to 1.0 during fade-in.

4. **Commit.** On transition completion, remove the old subtree from the scene graph. The new subtree is now the live control.

5. **Skip animation when not appropriate.** Transitions are skipped when:
   - The `AutoLayout` is not attached to a scene (off-screen initialization).
   - The transition would affect more than 50% of the visible area (prefer a direct swap to avoid disorienting large-scale restructuring).
   - `AutoLayout.animateTransitions` is `false` (user-controlled override, default `true`).

     **Limitation**: JavaFX does not expose the OS-level `prefers-reduced-motion` accessibility setting. There is no supported API to query it from Java on any platform as of JavaFX 25. `AutoLayout.animateTransitions` is the only mechanism for motion-sensitive users; applications must surface it as a preference.

### Configuration

A single `LayoutStylesheet` property `transition-duration-ms` (integer, default `120`) controls the animation duration per path. Setting it to `0` disables animation for a specific subtree.

---

## Alternatives Considered

### Alt A: CSS transition on the container Region

Apply a CSS `transition` property to the `AutoLayout` container so that layout changes animate via the JavaFX CSS animation engine.

**Pros**: no Java animation code; purely declarative.

**Cons**: JavaFX CSS transitions apply to continuous properties (opacity, translate, scale) but not to structural changes (node replacement). When `buildControl()` replaces the child node graph, the CSS engine sees a structural mutation, not a property animation — no CSS transition fires. This approach does not work for mode transitions.

**Rejected**: technically infeasible for structural node replacement.

### Alt B: Cross-fade only at the AutoLayout root level

Rather than animating per-subtree, cross-fade the entire `AutoLayout` content whenever any mode assignment changes.

**Pros**: one fade implementation, always correct regardless of subtree depth.

**Cons**: a small structural change in one nested child triggers a full-component cross-fade, making minor changes look like major ones; scroll position is lost during the cross-fade since both old and new states are briefly visible simultaneously; does not leverage the delta information available from RDR-023's partial re-solve.

**Staging note**: If RDR-023's `partialResolve` interface remains boolean-only (see interface note in Proposed Solution), Alt B becomes the only viable implementation path and should be staged first as a lower-risk starting point before pursuing per-subtree animation.

**Rejected** (for full implementation): wastes the per-subtree delta information that RDR-016/RDR-023 provide and degrades the user experience for partial updates. But recommended as a staging milestone.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Animation during rapid data updates creates queued transitions | Medium | Cancel in-progress animation before starting new one; if update arrives during fade, complete the new state immediately (skip animation) |
| Off-screen `buildControl()` adds latency to the `setContent()` hot path | Medium | Build the new subtree on the JavaFX Application Thread but defer attachment until the animation's first frame via `AnimationTimer` |
| Accessibility: users with motion sensitivity | Low | Expose `AutoLayout.animateTransitions` property; default respect for reduced-motion preference |
| VirtualFlow scroll state lost during subtree replacement | Medium | Capture `getFirstVisibleIndex()` before building new subtree; restore after transition completes only when the new subtree has the same rendering mode (TABLE→TABLE or OUTLINE→OUTLINE). On a mode flip (TABLE→OUTLINE or reverse), reset scroll to index 0: TABLE row indices and OUTLINE item indices are not interchangeable. |

---

## Implementation Plan

1. Add `AnimatedModeTransition` helper class to `AutoLayout` package.
2. Coordinate with RDR-023 to ensure `partialResolve` returns `Optional<SchemaPath>`; if that interface change is deferred, stage Alt B (root cross-fade) first.
3. Modify `AutoLayout.setContent()` and the resize path to call `AnimatedModeTransition.apply(oldSubtree, newSubtreeSupplier, durationMs)` when `partialResolve` returns a non-empty `Optional<SchemaPath>` or a width-bucket crossing produces a different `LayoutResult`.
4. `AnimatedModeTransition` manages the `ParallelTransition` lifecycle, cancellation on re-entry, and scroll-position restoration (reset to 0 on mode flip).
5. Add `transition-duration-ms` to `LayoutPropertyKeys`.

---

## Finalization Gate

### 1. Has the problem been validated with real user scenarios?

Not yet. The abruptness of mode transitions is an anticipated usability concern for streaming data scenarios. No user study or A/B comparison exists. Animation should be validated against non-animation in the explorer app before treating this as a settled design.

### 2. Is the solution the simplest that could work?

Yes. A cross-fade is the simplest structural transition available in JavaFX. The implementation touches only `AutoLayout` and a new helper class; no changes to layout engine internals are required.

### 3. Are all assumptions verified or explicitly acknowledged?

Acknowledged: the `ParallelTransition` approach requires the old and new subtrees to briefly coexist in the scene graph. Whether this causes layout pressure or rendering artifacts in the parent `AnchorPane` during the 120ms window has not been verified. A `StackPane` wrapper may be needed to isolate the transition region from the rest of the layout.

### 4. What is the rollback strategy if this fails?

Set `AutoLayout.animateTransitions = false` to disable animation entirely. The layout reverts to the current synchronous swap behavior. The `AnimatedModeTransition` helper class can remain as dead code until root-caused and re-enabled.

### 5. Are there cross-cutting concerns with other RDRs?

- **RDR-016**: Scroll preservation (Phase 1) must be coordinated with animation. Capture `getFirstVisibleIndex()` before the new subtree is built; restore only when the rendering mode is unchanged across the transition (see Risks table).
- **RDR-023**: Phase 3's `partialResolve` return value is the trigger for animation. RDR-025 depends on RDR-023 being implemented; without partial re-solve, the delta information that identifies which subtree changed is not available. **Critical dependency**: `partialResolve` must return `Optional<SchemaPath>` (not `boolean`) for per-subtree animation. If it remains boolean, this RDR must be downscoped to root-level cross-fade (Alt B).
- **RDR-024**: RDR-024 expands the rendering mode set. Transitions defined here apply uniformly to all mode pairs introduced by RDR-024; no special-casing per mode pair is required.
- **RDR-012**: The solver's mode assignment change is the semantic event being animated. Animation is meaningful only when the solver is in place (Phase 1 of RDR-012 complete).

---

## Success Criteria

- [ ] Mode transition (TABLE → OUTLINE or reverse) produces a cross-fade rather than an instantaneous swap when `animateTransitions == true`
- [ ] Animation completes within 250ms (fade-out + fade-in overlap)
- [ ] Scroll position is restored after same-mode transition; reset to 0 on mode flip (TABLE↔OUTLINE)
- [ ] Setting `transition-duration-ms: 0` disables animation for a specific subtree
- [ ] Rapid successive updates cancel in-progress animation and commit the latest state immediately
- [ ] All existing layout and scroll tests pass with `animateTransitions == false`
