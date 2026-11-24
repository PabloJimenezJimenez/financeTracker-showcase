# Finance Tracker design language

The style is editorial, built on bento tiles, greys first, with Material You colour on the hero
tile and the primary action only. Motion is quiet: it should feel like quality and never like a
show. Evidence and sources:
[`docs/research/design-language.md`](research/design-language.md).
"MUST" rules are checked in review. "Prefer" rules are defaults.

## 1. Principles
1. One serif number tells the story. Every other element is quiet sans and whitespace.
2. Grey does the hierarchy. Colour comes from Material You and appears only on the hero tile and
   the primary action; red appears only for errors.
3. Each tile holds one idea, and tile size shows its importance.
4. Motion explains where things came from and where they went. If it does not, remove it.

## 2. Colour (MaterialTheme.colorScheme only)
- MUST: Material You is **on**. On Android 12+ the scheme is `dynamicLightColorScheme(context)` /
  `dynamicDarkColorScheme(context)`, following the system dark setting. The grey scheme below is the
  fallback for Android 8–11, `@Preview`s and tests. Composables use only
  `MaterialTheme.colorScheme.*`; no `Color(0x…)` outside `ui/theme`.
- MUST: greys dominate. Pages, tiles, text and hairlines use only the neutral roles (`surface*`,
  `onSurface`, `onSurfaceVariant`, `outline*`), whose Material You tint is faint. The wallpaper
  colour appears only in:
  - `primary` / `onPrimary`: the FAB, the one filled button per screen, links, focus and selection;
  - `primaryContainer` / `onPrimaryContainer`: the hero tile (at most one per screen).
  `secondary*` and `tertiary*` are not used.
- Tip for the owner: Pixel "Wallpaper & style" can pick a monochrome colour style, which makes the
  whole app grey while still being Material You.

Grey fallback scheme (also the reference for contrast):

| Role | Light | Dark | Use |
|---|---|---|---|
| surface, background | #F9F9F9 | #0E0E0E | Page |
| surfaceContainerLowest | #FFFFFF | #090909 | Rarely used |
| surfaceContainerLow | #F0F0F0 | #161616 | **Default bento tile** |
| surfaceContainer | #E9E9E9 | #1C1C1C | Emphasised tile, sheet |
| surfaceContainerHigh / Highest | #E2E2E2 / #DBDBDB | #252525 / #2F2F2F | Pressed or selected, text field fill |
| onSurface | #141414 | #EDEDED | Primary text, hero numbers |
| onSurfaceVariant | #525252 | #A6A6A6 | Captions, secondary text, inactive icons |
| outline | #757575 | #808080 | Text-field borders, focus rings (≥3:1) |
| outlineVariant | #D6D6D6 | #2E2E2E | Hairlines and tile borders (decorative) |
| primary / onPrimary | #141414 / #FFFFFF | #EDEDED / #141414 | FAB, filled button, links |
| primaryContainer / onPrimaryContainer | #2B2B2B / #F0F0F0 | #E2E2E2 / #1C1C1C | Hero tile |
| error / onError | #B3261E / #FFFFFF | #F2B8B5 / #601410 | Errors and destructive confirmation only |
| errorContainer / onErrorContainer | #F9DEDC / #410E0B | #8C1D18 / #F9DEDC | Error banners |

- WCAG AA checked on the fallback: text roles ≥5.5:1 on every surface, outline ≥3.3:1, error ≥4.7:1.
  Dynamic schemes keep these pairings because Material You builds them by tone.
- MUST: on the hero tile use only `onPrimaryContainer`.
- MUST: no `tonalElevation` on tiles and no shadows (see §5), so surfaces don't pick up extra tint.
- MUST: income/expense are **not** shown by colour: sign "+" / "−" (U+2212), optionally weight
  (income 500, expense 400) or an arrow icon. Never `error` for an expense.
- MUST: no alpha-faded text (`copy(alpha = …)`); use `onSurfaceVariant`.

## 3. Typography (MaterialTheme.typography only)
Fonts are bundled in `res/font`. Both are SIL OFL 1.1, which allows bundling and has no Reserved
Font Name. Ship each `OFL.txt` in `assets/licenses/`.

| File in res/font | Source (google/fonts) | Licence |
|---|---|---|
| `newsreader_variable.ttf` | `ofl/newsreader/Newsreader[opsz,wght].ttf` (opsz 6–72, wght 200–800) | OFL 1.1, © 2020 The Newsreader Project Authors |
| `inter_variable.ttf` | `ofl/inter/Inter[opsz,wght].ttf` (opsz 14–32, wght 100–900) | OFL 1.1, © 2020 The Inter Project Authors |

- Each weight: `Font(R.font.x, weight, variationSettings = FontVariation.Settings(FontVariation.weight(..)))`;
  Newsreader also `FontVariation.Setting("opsz", ≈point size)`. Stable in ui-text 1.12.1 (minSdk 26).
  `FontFamily` values are top-level `val`s in `ui/theme`.
- Keep the M3 sizes (sp only in `Typography`); swap only the families:

| Roles | Family / weight | Use |
|---|---|---|
| displayLarge 57/64, displayMedium 45/52, displaySmall 36/44 | Newsreader 400, opsz 72 | Hero balance (one per screen) |
| headlineLarge/Medium/Small 32/28/24 | Newsreader 400, opsz ≈ size | Screen titles, section headings |
| titleLarge/Medium/Small 22/16/14 | Inter 500 | Tile titles, list primary line, amounts in lists |
| bodyLarge/Medium/Small 16/14/12 | Inter 400 | Body text, list secondary line |
| labelLarge/Medium/Small 14/12/11 | Inter 500 | Buttons, chips, overline captions |

- MUST: every style sets `fontFeatureSettings = "tnum"`, so figures are tabular, columns align and
  digits do not shift while they animate.
- Prefer one hero number per screen, two type sizes per tile, sentence case. Overline captions:
  `labelMedium` + `onSurfaceVariant` (uppercase via `uppercase()`). Money only via `formatCents`.

## 4. Spacing and grid
- The base unit is 4 dp. The spacing tokens are 4, 8, 12, 16, 24, 32, 48. Use no other values.
- Screen margin 16 dp. Bento grid: 2 columns, 12 dp gutter (≈183 dp columns on a 411 dp Pixel 8).
- Section gap 32 dp; hero to first tiles 24 dp. Touch targets ≥48 dp.
- Prefer vertical whitespace over dividers; any divider is a hairline.

## 5. Shapes
- `Shapes(extraSmall 4, small 8, medium 12, large 20, extraLarge 28)` dp, where only `large`
  changes from the M3 defaults.
- Tiles `large`; fields and grouped lists `medium`; chips `small`; sheets `extraLarge` (top);
  pills and FAB `CircleShape`. Nested radius = parent radius − padding (20 − 16 → 4 dp).
- MUST: no elevation shadows and no `tonalElevation` on tiles. Separate tiles with fill, plus an
  optional 1 dp (`DividerDefaults.Thickness`) or `Dp.Hairline` border in `outlineVariant`.

## 6. Bento tile rules
1. A tile holds one idea, made of a caption, one number or a short chart, and an optional footnote.
2. Allowed spans are 1×1, 2×1 (full width) and 2×2 (hero). Use at most one 2×2 per screen. No
   other ratios.
3. Inner padding is 16 dp, or 20 dp on the hero.
4. Inside: caption top-left (`labelMedium`, `onSurfaceVariant`), number bottom-left
   (`headlineSmall` or display), optional icon top-right (20–24 dp, `onSurfaceVariant`).
5. At most one tile per screen is the hero tile (`primaryContainer`). Use it for the most important
   figure.
6. A tile that can be tapped is the whole tile: `Card(onClick)` with no inner buttons, plus a
   `testTag` and a `contentDescription` built from its caption and value. One exception: a list
   tile may carry a single trailing `Switch` (e.g. "Pin as KPI" on the Metrics list) that stays
   its own accessibility node and does not trigger the tile's tap. Home's bento tiles never do.
7. Charts are monochrome: `onSurface` lines, `outlineVariant` grid, no fills except one
   `surfaceContainerHigh` area.

## 7. Iconography
- Material Symbols **Outlined**, weight 300, 24 dp, fill 0 (fill 1 only when selected), Apache 2.0,
  downloaded as Android XML from fonts.google.com/icons into `res/drawable`.
- `Icon(painterResource(...), contentDescription = stringResource(...))`, `null` if decorative.
  No `material-icons-extended` (unmaintained). Tint only via `LocalContentColor` or theme roles.

## 8. Motion tokens
Define these as an app `FinanceMotion` object provided by `FinanceTrackerTheme`.
`MaterialTheme.motionScheme` is internal in material3 1.4.0; switch to it when material3 1.5
stable lands.

| Token | Value |
|---|---|
| Duration short / medium / long / hero | 150 / 300 / 400 / 500 ms |
| Easing enter | EmphasizedDecelerate `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)` |
| Easing exit | EmphasizedAccelerate `CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)` |
| Easing on-screen | Standard `CubicBezierEasing(0.2f, 0f, 0f, 1f)` |
| Spring spatial fast / default / slow | damping 0.9; stiffness 1400 / 700 / 300 |
| Spring effects (alpha, colour) | damping 1.0; stiffness 1600 (3800 for small parts) |
| Shared-axis slide distance | 30 dp |

- MUST: no bouncy springs (damping < 0.9), no infinite or looping animations, no parallax, no
  shake, and no confetti.
- Exits are shorter than enters. Nothing that matters takes longer than 500 ms.

## 9. Which transition for which event
| Event | Transition | API |
|---|---|---|
| Bento tile → detail (account, category) | Container transform: the tile bounds grow into the screen. The hero number is shared. The rest fades in after ≈1/3. Slow spatial spring. | `SharedTransitionLayout` around `NavDisplay`, `sharedBounds` + `OverlayClip(shapes.large)`, `LocalNavAnimatedContentScope` |
| List → form or detail, without a shared container | Shared axis X: slide 30 dp + fade, 400 ms in, 300 ms back | `NavDisplay(transitionSpec, popTransitionSpec)` |
| Tab or top-level switch | Fade through: out in the first 35 %, then in from 0.92 scale, 300 ms | `AnimatedContent` |
| Predictive back | The outgoing screen scales to 0.9 with the gesture and fades. A shared tile follows back. | `NavDisplay(predictivePopTransitionSpec)` |
| Add an item | The new row fades in, the others slide | `LazyColumn(key = id)` + `Modifier.animateItem()` |
| Delete an item | Confirmation dialog first ("Delete this transaction?", Cancel / Delete in `error`); on Delete the row collapses and fades, the others slide up. No undo. | `AlertDialog` + `animateItem` |
| Balance changes | Rolling digits: only the changed digits slide up or down, 300 ms | `AnimatedContent` per character, `SizeTransform(clip = false)` |
| First load of the home hero (optional, once) | Count-up over at most 500 ms | `Animatable(0f)` fraction → interpolate the `Long` cents (never animate cents as `Float`) |
| Save | The button label crossfades to a check, a confirm haptic fires, then shared axis back | `AnimatedContent` in the `Button`, `HapticFeedbackType.Confirm` |
| Field error | Helper text expands and fades in, 150 ms | `OutlinedTextField(isError, supportingText)` or `AnimatedVisibility` |
| Dialog, bottom sheet | Keep the M3 defaults. Never shared elements across windows. | `AlertDialog`, `ModalBottomSheet` |
| Press or selection | Colour only, effects spring | `animateColorAsState` read in `drawBehind` |

Performance: read animated values only in `graphicsLayer {}`, `offset {}` or `drawBehind {}`; stable
lazy keys; data-class shared keys (`SharedKey(id, Part)`); `sharedBounds` before size modifiers;
judge smoothness on a release build only.

## 10. Accessibility
- Contrast: AA as checked in §2. Information is never shown by colour alone.
- Remove animations: Compose already snaps frame-clock animations (`ANIMATOR_DURATION_SCALE` = 0).
  Also provide `LocalReduceMotion` from `ValueAnimator.areAnimatorsEnabled()`; when true, skip
  count-ups, delays and haptic-timed sequences, and use plain fades instead of slides.
- Animated numbers: hide the per-digit nodes from semantics and expose one final value
  (`clearAndSetSemantics { contentDescription = formatted }`). Never announce intermediate values.
- Fonts scale with system font size. Do not fix heights on text containers; let tiles grow.
- Tappable elements: ≥48 dp, text or `contentDescription`, visible focus (`outline`).
- Tests: `mainClock.autoAdvance = false` + `advanceTimeBy` for mid-animation checks, default clock
  for end states; test both reduce-motion branches.
