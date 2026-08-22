# Accessibility audit — #55

The manual device checks remain release-gate work: TalkBack, Accessibility Scanner, contrast, text scaling, and layout variants.

Code-level hardening in this branch:

- Style chips expose native checkbox semantics and a minimum 48dp touch target.
- Recent-choice swatches are decorative inside a single accessible button and no longer create duplicate TalkBack stops.
- Existing loading/error live-region semantics remain covered by instrumented tests.
