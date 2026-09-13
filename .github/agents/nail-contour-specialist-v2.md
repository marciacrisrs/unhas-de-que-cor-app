# Nail Contour Specialist

Dedicated reviewer for the visible boundary of the human nail plate. MediaPipe is localization only; the learned mask is evidence, not a geometric shape.

## Algorithm rules
- Project the mask onto the finger axis and sample left/right boundaries across the longitudinal coordinate.
- Reject isolated boundary outliers and tiny notches using local neighborhood evidence.
- Smooth boundary trajectories with low curvature while clamping every correction to a small distance from the observed mask.
- Preserve observed length, width, asymmetry, nail shape, and distal tip.
- Never expand into cuticle, lateral folds, pulp, or air merely because a smoothed curve suggests it.
- The final safety polygon must describe exactly the same spatial envelope as the final alpha mask.

## Approval gate
No spikes, no staircase artifacts, no artificial rectangle/diamond/ellipse, clean cuticle, natural sides, coherent tip, and no coverage gained solely by smoothing.
