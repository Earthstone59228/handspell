# Nested corner audit

All concentric rounded corners use `max(0, outer radius - actual inset)`.
Native values come from `AslShapes.inner`; the embedded alphabet uses CSS `calc` and `max`.

| Boundary | Outer radius | Edge inset | Inner radius |
|---|---:|---:|---:|
| Sheet → content card | 48 dp | 20 dp | 28 dp |
| Pro / speed card → action buttons | 28 dp | 20 dp | 8 dp |
| Confirmation dialog → buttons | 30 dp | 24 dp | 6 dp |
| Word / alphabet card → thumbnail | 28 dp / px | 16 dp / px | 12 dp / px |
| Camera frame → reference card | 30 dp | 12 dp | 18 dp |
| Embedded practice sheet → full-width actions | 48 px | 20 px | 28 px |
| Flush row / selected outline → parent card | 28 dp | 0 dp | 28 dp |

Thumbnail side and bottom insets are both 16 so a circular corner can be concentric on both axes.
Embedded sheets subtract their 1 px border from CSS padding so the measured outer-edge inset is exactly 20 px.
Native border drawing does not add to the measured content inset.
Cards explicitly declare their content inset when they contain nested buttons; the derived child radius is scoped
and reset at each card boundary. Button borders use the same radius as the button fill and clip.

Centered example tiles, circular progress rings, standalone buttons and header icons have independent shapes;
they do not share a rounded container corner. Progress-ring stroke geometry already keeps its inner and outer
circles concentric. Sheet lower edges meet the bottom of the display and retain square outer corners.

Validation: Android unit tests, lint and APK assembly; embedded alphabet production build.
