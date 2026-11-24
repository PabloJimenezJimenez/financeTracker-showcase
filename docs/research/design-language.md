# Design language research: editorial, bento, greyscale, with a quiet motion system

Date: 2026-10-20. Scope: Finance Tracker (Kotlin 2.4, Compose BOM 2026.09.00, minSdk 26,
targetSdk 37), sideloaded onto one Pixel 8 running Android 17.

The owner asked for "a clean, minimalist design, editorial style, with bento grids, dominated by
colours in a single grey scale". They also want animations that feel like "quality, like there is
money behind it, but without overdoing it".

The prescriptive summary is in `design-language.md` next to this file. This file records the
evidence. Each claim names its source. Where a statement is my own design judgement and not a
sourced fact, it is labelled **[judgement]**.

---

## 0. Source availability and method

| Source | Result |
|---|---|
| Mobbin (`mobbin.com/search/apps/android?...Finance`) | **HTTP 403**. Not readable without login. |
| Dribbble search (`dribbble.com/search/...`) and shot pages (`dribbble.com/shots/23440662-Bento-grid-v2-0`) | Empty, rendered by JavaScript. A collection page showed one title and then "Sign up to continue". **Not usable.** |
| m3.material.io (easing tokens page) | Only the page title comes back, because the page is rendered by JavaScript. **I used the Material token sources in AndroidX and the MDC-Android docs instead**, which hold the same values. |
| developer.android.com | Readable. Used for Compose animation, shared elements, Navigation 3, predictive back, testing, fonts and icons. |
| Google Maven (`dl.google.com/android/maven2`) | Used to read the BOM POM and the **source jars of the exact versions in the BOM**. These are the strongest evidence for whether an API is stable, experimental or internal. |
| google/fonts GitHub (`METADATA.pb`, `OFL.txt`) | Font licences, axes and file names. |

Versions fetched from `compose-bom-2026.09.00.pom`
(https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom):

- `androidx.compose.animation:animation` / `animation-core` **1.12.1**
- `androidx.compose.foundation:foundation` **1.12.1**
- `androidx.compose.ui:ui` (and `ui-text`, `ui-test`) **1.12.1**
- `androidx.compose.material3:material3` **1.4.0**. The latest material3 on Maven is `1.5.0-beta01`; it is not stable yet.
- `androidx.compose.material:material-icons-core` **1.7.8**, frozen. See §1.7.

Navigation libraries, read from `maven-metadata.xml` on Google Maven:

- `androidx.navigation3:navigation3-runtime` / `navigation3-ui`: latest stable **1.2.0** (1.3.0-alpha02 exists).
- `androidx.lifecycle:lifecycle-viewmodel-navigation3`: stable **2.11.0**. This matches the project's `lifecycle = 2.11.0`.
- `androidx.navigation:navigation-compose`: stable **2.10.2**. This is the alternative to Navigation 3.

The project does not have a navigation library yet (`gradle/libs.versions.toml`).

---

## 1. What "editorial + bento + greyscale" means for a mobile finance app

### 1.1 Reference apps and shots

Mobbin and Dribbble were not readable, so these come from first-party pages and studio case
studies.

1. **Robinhood rebrand by Porto Rocha.** https://www.portorocha.com/robinhood
   - Sans for the product is *RH Phonic* (Schick Toikka). Headlines use the serif *Martina Plantijn*
     (Klim), "a warm yet intelligent serif".
   - The palette is "black, white, and mature neutrals", described as a contrast to competitors'
     "rainbow approach". One accent, "Robin Neon", is used only for "pops".
   - The system is "smart, modular", meant to flex across products.
   - **What to take:** the serif-headline-plus-sans-UI pairing in a money app, and a neutral palette
     with at most one accent used sparingly. Trade press notes the serif marks the premium tier
     (Design Compass, https://designcompass.org/en/2024/10/18/robinhood-rebranding).
     Third-party sites disagree on Robinhood's fonts, so treat the Porto Rocha page as the
     authority.
2. **Linear, "How we redesigned the Linear UI".** https://linear.app/now/how-we-redesigned-the-linear-ui
   - Themes are generated in LCH from three inputs, "base color, accent color, and contrast",
     instead of 98 variables.
   - They reduced "how much chrome (blue in our case)" appears. Text and neutral icons were made
     darker in light mode and lighter in dark mode.
   - "Inter Display to add more expression to our headings"; regular Inter for everything else.
   - **What to take:** a grey scale built from perceptually even lightness steps, accent kept nearly
     out of the chrome, and a display cut for headings over a workhorse sans. This is the
     closest published reasoning for a "single grey scale" product UI.
3. **Wealthsimple.**
   - Typewolf: https://www.typewolf.com/blog/favorite-sites-of-march-2016
   - Wealthsimple's own magazine: https://www.wealthsimple.com/en-us/magazine/new-wealthsimple-15
   - The brand pairs a serif (Caslon) with a geometric sans (Futura) "to convey the security of
     your money … in a way that is fun and approachable". Reviewers describe the app as very clean,
     with lots of white space.
   - **What to take:** a serif signals trust and an editorial voice in finance; whitespace does
     most of the work. Do **not** take its warm palette, which is the opposite of greyscale.
4. **Bento grids, popularised by Apple's product spec slides.** Overview:
   https://www.freecodecamp.org/news/bento-grids-in-web-design/
   - This is a secondary source; there is no first-party "bento" spec.
   - Principles the sources agree on: one idea per compartment, size as hierarchy (the most
     important content gets the largest tile), and even gutters everywhere.
   - **What to take:** the home screen as a small grid of tiles. The largest tile holds the net
     balance and the smaller tiles hold accounts and this month's figures. Use one gutter value
     throughout.
5. **Jetsnack (official Compose sample).** https://github.com/android/compose-samples (`Jetsnack/`)
   - Wraps `NavHost` in `SharedTransitionLayout`. It uses `Modifier.sharedBounds(...)` with a
     shared `boundsTransform` to turn a snack card into its detail screen, and delays secondary
     content (`scaleIn(tween(300, delayMillis = 300))`).
   - Checked in `JetsnackApp.kt`, `snackdetail/SnackDetail.kt` and `components/Snacks.kt`.
   - **What to take:** the working pattern for a "card becomes the detail screen" container
     transform in Compose.
6. **Mercury (business banking)** is often cited for minimal monochrome finance UI. Every source I
   found was a third-party reverse-engineering, and they contradicted each other (light vs dark,
   Arcadia vs "Mercury Grotesque").
   - Example: https://www.925studios.co/blog/mercury-design-breakdown
   - **Not used as evidence.** The owner may want to check it on Mobbin with a login.

### 1.2 Editorial typography: the pairing

The concept **[judgement, informed by references 1–3]**:

- A serif display face sets the big numbers (net balance, account balance) and the screen titles,
  like a magazine masthead and headline.
- A neutral sans sets every functional piece of UI: labels, list rows, buttons and fields.
- All numbers use tabular figures, so columns align and digits do not jitter while they animate.

Candidates checked on google/fonts (all **SIL OFL 1.1**, all variable, all bundleable):

| Family | Role | File(s) on google/fonts | Axes | Notes |
|---|---|---|---|---|
| **Newsreader** (Production Type) | Display / headline serif | `Newsreader[opsz,wght].ttf` (~450 KB), `Newsreader-Italic[opsz,wght].ttf` | opsz 6–72, wght 200–800 | Designed for on-screen editorial reading. Has a `tnum` feature tag (byte scan of the font file). |
| **Inter** (Rasmus Andersson) | UI sans | `Inter[opsz,wght].ttf` (~877 KB), `Inter-Italic[opsz,wght].ttf` | opsz 14–32, wght 100–900 | Has `tnum`, `cv11`, `ss01`, `zero`. Linear uses it, and its opsz axis reaches the "Display" cut. |
| Instrument Serif | Alternative display | `InstrumentSerif-Regular.ttf` (70 KB) + Italic | none (single weight) | **No `tnum`**, so proportional digits shift width while a balance animates. Rejected for numbers. |
| Source Serif 4 (Adobe) | Alternative display | `SourceSerif4[opsz,wght].ttf` (~1.2 MB) | opsz 8–60, wght 200–900 | Has `tnum`, `lnum`, `onum`. A solid fallback; heavier file. |
| Fraunces | Alternative display | `Fraunces[SOFT,WONK,opsz,wght].ttf` | 4 axes | Too quirky ("WONK") for a restrained grey look **[judgement]**. |
| Geist | Alternative sans | `Geist[wght].ttf` | wght | OFL; fine, but no opsz. |

- **Licence.** `ofl/newsreader/OFL.txt` and `ofl/inter/OFL.txt` both say "Original or Modified
  Versions of the Font Software may be bundled, redistributed and/or sold with any software". The
  only restriction is not selling the font by itself.
- **Reserved Font Name.** Neither file declares one, so subsetting the fonts and keeping the name is
  allowed.
- Copyright lines:
  - "Copyright 2020 The Newsreader Project Authors (http://github.com/productiontype/Newsreader)"
  - "Copyright 2020 The Inter Project Authors (https://github.com/rsms/inter)"
- Ship each `OFL.txt` in the app, for example under `assets/licenses/`.
- Sources: https://github.com/google/fonts/tree/main/ofl/newsreader and
  https://github.com/google/fonts/tree/main/ofl/inter.

Compose facts about the font APIs:

- `Font(resId, weight, style, variationSettings: FontVariation.Settings)` lives in
  `androidx.compose.ui.text.font`. In **ui-text 1.12.1** neither it nor `FontVariation` carries an
  `@Experimental*` annotation (source jar scan).
  - The docs page still shows `@OptIn(ExperimentalTextApi::class)` in its sample. That is out of
    date.
- "Variable fonts are only supported on Android O and above", which is minSdk 26 here.
- Custom axes such as `opsz` are set with `FontVariation.Setting("opsz", value)`.
- File names in `res/font` must be "all lowercase and doesn't contain any special characters".
  Rename `Newsreader[opsz,wght].ttf` to something like `newsreader_variable.ttf`.
- "Variable fonts are not currently supported via downloadable fonts", which does not matter here
  because we bundle the fonts.
- Source: https://developer.android.com/develop/ui/compose/text/fonts
- Tabular figures come from `TextStyle(fontFeatureSettings = "tnum")`. The CSS-syntax feature
  string is passed to the platform Paint.

### 1.3 Type scale

The M3 baseline scale in material3 1.4.0 (`tokens/TypeScaleTokens.kt`), as size/line-height in sp:

| Role group | Large | Medium | Small |
|---|---|---|---|
| Display | 57/64 | 45/52 | 36/44 |
| Headline | 32/40 | 28/36 | 24/32 |
| Title | 22/28 | 16/24 | 14/20 |
| Body | 16/24 | 14/20 | 12/16 |
| Label | 14/20 | 12/16 | 11/16 |

**[judgement]** How to apply it:

- Keep the M3 sizes and roles so components inherit sensible defaults. Swap only the families:
  - `display*` and `headline*` use Newsreader, with opsz near the point size (the opsz axis exists
    for exactly this) and weight 400 or a light 350.
  - `title*`, `body*` and `label*` use Inter at weights 400/500, with opsz left at the default.
- Use **two sizes per screen for the dominant contrast**: the hero number (`displayMedium` or
  `displayLarge`) against everything else (`body`/`label`). This is the main editorial device:
  a large serif number next to a small sans caption.
- Apply `fontFeatureSettings = "tnum"` to every style in the `Typography`, so every number in the
  app is tabular.
- All sp values live in the theme's `Typography`. Composables reference
  `MaterialTheme.typography.*` only, as CLAUDE.md requires.
- **The M3 Expressive "emphasized" styles do not exist publicly yet.** In material3 1.4.0,
  `displayLargeEmphasized` and the others are `internal` (`Typography.kt`: "internal val
  displayLargeEmphasized"). They are public in 1.5.0-beta01. Do not plan on them until a BOM ships
  material3 1.5.0 stable.

### 1.4 Grid, spacing and the Pixel 8

The Pixel 8 display is 6.2-inch, 1080 × 2400, 428 PPI, "Smooth Display (60–120 Hz)". It has a
Tensor G3 chip and 8 GB of RAM (Google support page,
https://support.google.com/pixelphone/answer/7158570). At 428 PPI the device sits in the 420 dpi
bucket (density 2.625), so the width is about 411 dp.

**[judgement]** Layout:

- A 4 dp base unit.
- Screen side margin 16 dp, which is the M3 compact-window margin and the 16 dp gutter CLAUDE.md
  already uses for artifacts.
- Bento gutter 12 dp: 411 − 2·16 − 12 = 367 dp, so each of the two columns is about 183 dp.
- Tile spans: 1×1, 2×1 (full width) and 2×2 (hero).
- 16 dp inner padding, or 20 dp on the hero tile.
- 32–48 dp between sections. This large vertical whitespace is what reads as "editorial".

### 1.5 Shapes

M3 shape tokens in material3 1.4.0 (`tokens/ShapeTokens.kt`):

- extraSmall 4, small 8, medium 12, large 16, extraLarge 28 dp.
- LargeIncreased 20, ExtraLargeIncreased 32 and ExtraExtraLarge 48 exist as tokens, but the
  `Shapes` class only exposes extraSmall to extraLarge publicly: its constructor is `internal` and
  the expressive slots are not public in 1.4.0.

**[judgement]** Override `Shapes(large = RoundedCornerShape(20.dp))` for bento tiles and keep the
others at their defaults. When a tile nests a shape, use inner radius = outer radius − padding.

### 1.6 Hairlines and surfaces

From the M3 1.4.0 `ColorScheme` KDoc:

- `outline` is "Subtle color used for boundaries. Outline color role adds contrast for
  accessibility purposes."
- `outlineVariant` is "for decorative elements when strong contrast is not required".
- The `surfaceContainer*` roles "affect containers of components, such as cards, sheets, and
  menus".

Therefore:

- Hairline rules (dividers, tile borders) use `outlineVariant`.
- Field outlines use `outline`.
- Tiles use `surfaceContainerLow`, or `surfaceContainer` for more emphasis.
- `HorizontalDivider` defaults to 1 dp; `Dp.Hairline` draws exactly one pixel.

### 1.7 Icons

The Compose docs say the predefined Material Icons library is "no longer maintained or recommended
… older look and feel and can also increase the build time … significantly". The recommended
alternative is to download Material Symbols as Android XML from fonts.google.com/icons into
`res/drawable` and draw them with `Icon(painterResource(...))`. Source:
https://developer.android.com/develop/ui/compose/graphics/images/material

- The BOM confirms `material-icons-core` is frozen at 1.7.8.
- Material Symbols are Apache-2.0
  (https://github.com/google/material-design-icons/blob/master/LICENSE).
- **[judgement]** Use the Outlined style, weight 300, size 24, fill 0. Use fill 1 only for a
  selected state. This keeps icons as thin as the hairlines.

---

## 2. Greyscale ColorScheme, light and dark

### 2.1 Proposed roles

These are neutral greys (R = G = B) except the error roles. The error values are the M3 baseline
palette from material3 1.4.0 `PaletteTokens`: Error40 = #B3261E, Error90 = #F9DEDC,
Error10 = #410E0B, Error80 = #F2B8B5, Error20 = #601410, Error30 = #8C1D18.

| Role | Light | Dark |
|---|---|---|
| background, surface, surfaceBright (light) | #F9F9F9 | #0E0E0E |
| surfaceDim | #D6D6D6 | #0E0E0E |
| surfaceBright (dark) | — | #343434 |
| surfaceContainerLowest | #FFFFFF | #090909 |
| surfaceContainerLow (default tile) | #F0F0F0 | #161616 |
| surfaceContainer | #E9E9E9 | #1C1C1C |
| surfaceContainerHigh | #E2E2E2 | #252525 |
| surfaceContainerHighest, surfaceVariant | #DBDBDB | #2F2F2F |
| onSurface, onBackground | #141414 | #EDEDED |
| onSurfaceVariant (secondary text) | #525252 | #A6A6A6 |
| outline (field borders, focus) | #757575 | #808080 |
| outlineVariant (hairlines) | #D6D6D6 | #2E2E2E |
| primary / onPrimary | #141414 / #FFFFFF | #EDEDED / #141414 |
| primaryContainer / onPrimaryContainer | #E2E2E2 / #141414 | #333333 / #EDEDED |
| secondary / onSecondary | #474747 / #FFFFFF | #BDBDBD / #1F1F1F |
| secondaryContainer / onSecondaryContainer | #E2E2E2 / #1F1F1F | #2F2F2F / #E2E2E2 |
| tertiary / onTertiary | #5E5E5E / #FFFFFF | #9E9E9E / #141414 |
| tertiaryContainer / onTertiaryContainer | #E9E9E9 / #1F1F1F | #252525 / #E2E2E2 |
| inverseSurface / inverseOnSurface | #2B2B2B / #F0F0F0 | #E2E2E2 / #1C1C1C |
| inversePrimary | #C6C6C6 | #2B2B2B |
| error / onError | #B3261E / #FFFFFF | #F2B8B5 / #601410 |
| errorContainer / onErrorContainer | #F9DEDC / #410E0B | #8C1D18 / #F9DEDC |
| surfaceTint | = surface (no tint) | = surface |
| scrim | #000000 | #000000 |

- Set the `*Fixed` roles to the matching greys.
- **[judgement]** The dark background is #0E0E0E and not #000000, so tiles one step lighter still
  separate from the page on OLED.

### 2.2 WCAG checks

WCAG 2.x relative-luminance formula, computed with a script
(`scratchpad/contrast2.py`). AA requires 4.5:1 for normal text, 3:1 for large text, and 3:1 for UI
component boundaries (SC 1.4.11).

| Pair | Light | Dark | Verdict |
|---|---|---|---|
| onSurface on all surfaces and containers | 13.3–18.4 | 11.4–17.0 | AAA |
| onSurfaceVariant on all surfaces and containers | 5.6–7.8 | 5.5–8.2 | AA body text |
| outline on all surfaces and containers | 3.3–4.6 | 3.4–5.0 | AA for UI boundaries (≥3) |
| error on all surfaces and containers | 4.7–6.5 | 7.8–11.7 | AA text |
| onPrimary / primary | 18.4 | 15.7 | AAA |
| onError / error | 6.5 | 7.7 | AA |
| onErrorContainer / errorContainer | 12.8 | 7.2 | AA |
| inverseOnSurface / inverseSurface | 12.4 | 13.2 | AAA |
| **onSurfaceVariant on inverseSurface** | 1.8 | 1.9 | **Fails.** On an inverted hero tile, use only `inverseOnSurface`. |
| tile fill (Low) vs page | 1.08 | 1.07 | Decorative only. SC 1.4.11 does not require container fill contrast when the tile is identified by its text, but add an `outlineVariant` hairline if separation looks too weak. |

### 2.3 One accent, or none?

- **Error states need colour.** M3 already ships the error roles, and the baseline reds above pass
  AA. Keep them as the **only** chromatic colour. Robinhood ("one neon accent for pops") and Linear
  ("limiting how much chrome… was used") both show the same restraint.
- **Income and expense should not depend on green and red. [judgement]**
  - WCAG SC 1.4.1 (Use of Color, https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html)
    says colour must not be the only means of conveying information.
  - A greyscale brief also rules out a green/red pair.
  - Encode direction with the **sign and glyph**: "+" for income, U+2212 "−" for expense. Optionally
    vary the **weight**: income in onSurface at 500, expense in onSurface at 400. Do not put
    expenses in `error`, because that tells the user something is wrong.
  - An optional small arrow icon gives a second cue.
- **Dynamic colour (Material You) should be off.**
  - `dynamicLightColorScheme`/`dynamicDarkColorScheme` derive the scheme from the wallpaper, which
    would break the greyscale identity.
  - The current `FinanceTrackerTheme` already uses only `lightColorScheme()`/`darkColorScheme()`.
    Keep it that way and do not add a `dynamicColor` parameter.

---

## 3. Motion system

### 3.1 Tokens (from source)

`androidx.compose.material3.tokens.MotionTokens` (androidx-main, and the same in 1.4.0) and
MDC-Android `docs/theming/Motion.md` define these tokens.

- **Durations (ms):**
  - Short1–4 = 50/100/150/200
  - Medium1–4 = 250/300/350/400
  - Long1–4 = 450/500/550/600
  - ExtraLong1–4 = 700–1000
- **Easings (cubic-bezier):**
  - Standard (0.2, 0, 0, 1)
  - StandardDecelerate (0, 0, 0, 1)
  - StandardAccelerate (0.3, 0, 1, 1)
  - EmphasizedDecelerate (0.05, 0.7, 0.1, 1)
  - EmphasizedAccelerate (0.3, 0, 0.8, 0.15)
  - In Compose, `EasingEmphasizedCubicBezier` is (0.2, 0, 0, 1), the same curve as Standard,
    because true "emphasized" is a two-segment path that one cubic cannot express.
  - Legacy (0.4, 0, 0.2, 1)
- **Springs, standard scheme (`StandardMotionTokens`, mirrored in MDC docs):**
  - fastSpatial: damping 0.9, stiffness 1400. "small components like switches and buttons".
  - defaultSpatial: 0.9 / 700. "animations that partially cover the screen like a bottom sheet".
  - slowSpatial: 0.9 / 300. "full screen animations".
  - fast, default and slow effects: damping 1.0, stiffness 3800 / 1600 / 800. These are for colour
    and opacity, "where the property's value should not be overshot".
- **Springs, expressive scheme (`ExpressiveMotionTokens`):** spatial damping 0.6–0.8 and stiffness
  200–800, which is a visible bounce.
  - **[judgement]** Use the **standard** scheme. "Without overdoing it" rules out bounce on money
    figures.
- **Stability:** `MotionScheme`, `MaterialTheme.motionScheme` and `MaterialExpressiveTheme` are
  **`internal` in material3 1.4.0**, the version in BOM 2026.09.00 (source jar:
  `internal interface MotionScheme`, `internal fun MaterialExpressiveTheme`). They are public in
  1.5.0-beta01.
  - Until a BOM ships 1.5.0 stable, define an app-level motion object that copies the values above.
  - Expose it through a `CompositionLocal` on `FinanceTrackerTheme`.
  - Migrate to `MaterialTheme.motionScheme` later.

### 3.2 Transition patterns (Material)

MDC-Android `docs/theming/Motion.md` and the transition sources define four patterns:

- **Container transform.** "transitions between UI elements that include a container… A card into
  a details page".
  - `MaterialContainerTransform` uses Long2 (500 ms) entering and Medium4 (400 ms) returning.
- **Shared axis.** "UI elements that have a spatial or navigational relationship".
  - Slide distance 30 dp (`mtrl_transition_shared_axis_slide_distance`), duration Long1 (450 ms),
    emphasized easing.
- **Fade through.** For "UI elements that do not have a strong relationship", such as bottom-nav
  tabs.
  - The outgoing view fades during the first 35 % (`FADE_THROUGH_THRESHOLD = 0.35`). The incoming
    view scales 0.92 → 1 (`DEFAULT_START_SCALE = 0.92f`).
- **Fade.** "UI elements that enter or exit within the bounds of the screen, such as a dialog…
  menu… snackbar".
  - `MaterialFade` scales in from 0.8, takes Medium4 in and Short3 (150 ms) out, and the fade
    finishes at 30 % of the enter.

Source: https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md

The principle: "duration should increase as the area/traversal of an animation increases".

### 3.3 Event-to-transition map and Compose APIs

All animation APIs below are in `androidx.compose.animation:animation` 1.12.1 or
`androidx.compose.foundation:foundation` 1.12.1. The source-jar scan found **no**
`@ExperimentalAnimationApi` or `@ExperimentalSharedTransitionApi` on:

- `SharedTransitionLayout`, `SharedTransitionScope`
- `Modifier.sharedElement`, `sharedBounds`, `skipToLookaheadSize`,
  `renderInSharedTransitionScopeOverlay`
- `AnimatedContent`, `AnimatedVisibility`, `Modifier.animateBounds`
- `LazyItemScope.animateItem`

What does remain experimental:

- `ExperimentalDeferredTransitionApi` items inside `SharedTransitionScope`
- the old `with` infix (deprecated in favour of `togetherWith`)
- `unveilIn`/`veilOut`
- `Transition.Crossfade`

| Event | Pattern **[judgement on mapping, values sourced above]** | Compose API |
|---|---|---|
| Home bento tile → account detail | Container transform. The tile's bounds grow into the detail screen. The serif balance moves as its own shared bounds. Other detail content fades in after about 1/3 of the motion (Jetsnack does `delayMillis = 300`). | `SharedTransitionLayout` wrapping `NavDisplay`. On the tile and on the detail root, `Modifier.sharedBounds(rememberSharedContentState(key), animatedVisibilityScope = LocalNavAnimatedContentScope.current, boundsTransform = …, clipInOverlayDuringTransition = OverlayClip(shape))`. For the balance text, `sharedBounds(…, resizeMode = ScaleToBounds())`; the docs say "Prefer `sharedBounds()` for text that changes font weight, style". |
| List → transaction form (forward), and back | Shared axis X: 30 dp slide plus fade. | `NavDisplay(transitionSpec = { slideInHorizontally { 30dp } + fadeIn() togetherWith slideOutHorizontally { −30dp } + fadeOut() }, popTransitionSpec = mirror)`, or per entry with `metadata { put(NavDisplay.TransitionKey) {…} }`. |
| Top-level tab switch (if tabs exist) | Fade through. | `AnimatedContent` / `NavDisplay` spec: `fadeIn(delay ≈ 35 %) + scaleIn(0.92f) togetherWith fadeOut(first 35 %)`. |
| Predictive back (Android 17) | Follows the finger. The outgoing screen shrinks slightly and fades; a shared tile shrinks back into place. | `NavDisplay(predictivePopTransitionSpec = { swipeEdge -> … })`. The Nav3 default is `fadeIn(spring(1.0, 1600)) togetherWith scaleOut(0.7f)` (`NavDisplay.android.kt`); **[judgement]** 0.9 is calmer. Shared bounds follow the gesture because the transition is seekable. |
| Add a list item | The new row fades in and the others slide down. | `LazyColumn` with stable `key = { it.id }` and `Modifier.animateItem(fadeInSpec, placementSpec, fadeOutSpec)`. |
| Delete a list item | Swipe to dismiss, the row fades and collapses, and a snackbar offers Undo. | `SwipeToDismissBox` (material3), `animateItem(fadeOutSpec = …)`, `SnackbarHost` (fade pattern is built in). |
| Balance changes (after add, delete or save) | Rolling digits: each changed digit slides up (increase) or down (decrease) with a fade. Unchanged digits do not move. | Per character, `AnimatedContent(targetState = char, transitionSpec = { slideInVertically { h -> ±h } + fadeIn() togetherWith slideOutVertically { … } + fadeOut() using SizeTransform(clip = false) })`. This is the Compose docs counter pattern (https://developer.android.com/develop/ui/compose/animation/composables-modifiers). It needs `tnum` so widths stay constant. |
| Count-up on first load (optional, at most once per app start) | Animate a 0→1 fraction and interpolate the `Long`. | `Animatable(0f).animateTo(1f, tween(...))`, then `from + ((to − from) * f).roundToLong()`. **Pitfall:** never animate cents as `Float`. Float has 24 bits of mantissa, so values above 16,777,216 cents (€167,772.16) lose precision. |
| Form saved | The button content crossfades from the label to a check mark, a confirm haptic fires, and the app navigates back with shared axis. On the list the new row animates in and the balance rolls. | `AnimatedContent` / `Crossfade` inside the `Button`, `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.Confirm)` (ui 1.12.1 also has `Reject`, `ToggleOn/Off`, `SegmentTick`, …). |
| Validation error | Helper text expands and fades in. No shake. | `AnimatedVisibility(enter = expandVertically() + fadeIn())`, or the `supportingText` / `isError` on `OutlinedTextField`. |
| Dialogs | Keep the platform and M3 behaviour (fade pattern). | `AlertDialog`. Shared elements cannot cross into a `Dialog` or `ModalBottomSheet`: the shared-elements page lists "View interop: Not supported. This includes `Dialog` and `ModalBottomSheet`". |
| Bottom sheets | Keep the M3 defaults. | `ModalBottomSheet` already wires predictive back (`ModalBottomSheet.android.kt` uses the back-handler internals in 1.4.0). |
| Small state changes (selected chip, pressed tile) | Fast effects spring, colour only. | `animateColorAsState(spring(1f, 3800f))`, drawn via `drawBehind` (see §4.2). |

### 3.4 Predictive back on Android 16/17

- Android 16 behaviour changes:
  - "For apps targeting Android 16 (API level 36) or higher … the predictive back system animations
    (back-to-home, cross-task, and cross-activity) are enabled by default. Additionally,
    onBackPressed is not called and KeyEvent.KEYCODE_BACK is not dispatched anymore."
  - Source: https://developer.android.com/about/versions/16/behavior-changes-16
- Android 17 behaviour-changes pages (both the "targeting 17" and "all apps" pages) contain **no**
  back-navigation changes. Their items are MessageQueue, background audio and the large-screen
  orientation opt-out removal.
  - Sources: https://developer.android.com/about/versions/17/behavior-changes-17 and
    https://developer.android.com/about/versions/17/behavior-changes-all
- What this means for the app:
  - With targetSdk 37, all back handling must go through AndroidX (Nav3 `onBack`, `BackHandler`,
    `PredictiveBackHandler`, `NavigationEventHandler`).
  - "All versions of Navigation 3 support predictive back."
  - No manifest flag is needed when targeting 15 or higher.
  - Source: https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation

### 3.5 Respecting "Remove animations"

- **What the setting does.** AOSP Settings' `DisableAnimationsPreferenceController` writes "0" to
  `Settings.Global.WINDOW_ANIMATION_SCALE`, `TRANSITION_ANIMATION_SCALE` and
  `ANIMATOR_DURATION_SCALE`.
  - Read from a Replicant mirror of AOSP:
    https://git.replicant.us/replicant-next/packages_apps_Settings/plain/src/com/android/settings/accessibility/DisableAnimationsPreferenceController.java
  - The Android 17 class may be renamed; **not confirmed against current AOSP**.
- **Compose follows it automatically for frame-clock animations.**
  - `WindowRecomposer.android.kt` (ui 1.12.1) reads
    `Settings.Global.getFloat(…, ANIMATOR_DURATION_SCALE, 1f)` and observes changes.
  - It feeds that value into the recomposer as `MotionDurationScale`.
  - `Transition.kt` (animation-core 1.12.1) checks `durationScale == 0f` and jumps to the end.
  - So `animate*AsState`, `AnimatedContent`, `AnimatedVisibility`, `animateItem`, shared elements
    and `NavDisplay` transitions all snap to their end state.
- **What does not follow it:**
  - `delay()`-driven effects, such as a delayed reveal or a count-up written with
    `delay`-loops (`delay` is not scaled).
  - Haptics.
  - Anything where "snap" leaves a bad intermediate layout.
  - For these, read `ValueAnimator.areAnimatorsEnabled()` (API 26+) once at theme level. Provide it
    as a `LocalReduceMotion`-style `CompositionLocal`, and branch on it to skip count-up and
    delays.
  - This also lets tests force either mode, because the Compose test rule builds its own
    recomposer. I did not verify whether that recomposer reads the global scale.

---

## 4. Implementation notes and pitfalls

### 4.1 Shared elements with Navigation 3

- **Library choice. [judgement]** Use Navigation 3 (`androidx.navigation3:navigation3-runtime` and
  `navigation3-ui` 1.2.0, plus `androidx.lifecycle:lifecycle-viewmodel-navigation3` 2.11.0).
  - The back stack is a plain `SnapshotStateList` the app owns, which is easy to unit-test.
  - It has first-class `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec`, and
    it exposes `LocalNavAnimatedContentScope`.
  - Navigation Compose 2.10.2 also works, with `this@composable` as the scope.
- **Wiring.** Wrap `NavDisplay` in `SharedTransitionLayout`. Pass `sharedTransitionScope = this` to
  `NavDisplay` (the 1.2.0 signature has it), and in each entry read
  `LocalNavAnimatedContentScope.current`.
  - The docs say: "you must first wrap your app's `NavDisplay` in a `SharedTransitionLayout`"
    (https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation).
- **Default Nav3 transitions on Android** are a 700 ms crossfade (`DEFAULT_TRANSITION_DURATION_MILLISECOND = 700`).
  That is slower than M3's Long2 (500 ms), so always override them.
- **Passing scopes.** Use a `CompositionLocal` for the `SharedTransitionScope` rather than context
  receivers. The docs warn that "context receivers can be overridden by nested `AnimatedContent`".
- **Keys.** "Strings are error-prone, so data class keys are recommended", for example
  `SharedKey(accountId, Part.Card)`.
- **Modifier order.**
  - Put anything not shared *before* `sharedElement`/`sharedBounds`.
  - Put size modifiers *after*, except `requiredSize`.
  - Keep padding identical on both sides, or the element jumps.
- **No automatic shape morph.** "there is no automatic shape-to-shape animation". Use `sharedBounds`
  plus `clipInOverlayDuringTransition = OverlayClip(shape)`, with the same corner radius on both
  ends, or animate the radius yourself.
- **Overlay.**
  - During a transition, shared content renders in the `SharedTransitionLayout` overlay, above
    everything.
  - Use `Modifier.renderInSharedTransitionScopeOverlay(...)` so a bottom bar or FAB stays on top.
  - `sharedElementWithCallerManagedVisibility` must be removed once `isTransitionActive` is false.
- **Lazy lists.** On return, the source tile must be composed for a match. Check
  `SharedContentState.isMatchFound` if you need a fallback.
- **Dialogs and bottom sheets** are separate windows. Shared elements do not cross them.

### 4.2 Performance on Pixel 8

- The display runs at up to 120 Hz, which leaves an **8.3 ms** frame budget.
- Read animated values in the layout or draw phase, not in composition. Use
  `Modifier.offset { }`, `graphicsLayer { }` and `drawBehind { }`.
  - "When you are passing frequently changing State variables into modifiers, you should use the
    lambda versions of the modifiers whenever possible."
  - "Avoid `Modifier.background()` for color animations".
  - Sources: https://developer.android.com/develop/ui/compose/performance/bestpractices and
    https://developer.android.com/develop/ui/compose/animation/quick-guide
- Give every lazy item a stable `key`. Without keys Compose "recomposes every item on the list". Keys
  are also required for `animateItem` to know which item moved.
- Animate alpha and translation, not layout size, where possible: "Animating in the draw phase is
  cheaper than in the layout phase".
- Judge smoothness only on a **release (R8) build**. Debug Compose builds are much slower.
  - Compose libraries ship their own baseline profiles.
  - Optionally add `androidx.profileinstaller:profileinstaller` and a Baseline Profile module later
    (https://developer.android.com/develop/ui/compose/performance/baseline-profiles).
- Variable fonts: create each `FontFamily` once, as top-level `val`s in the theme package, so
  Typefaces are not rebuilt on recomposition **[judgement]**.

### 4.3 Testing animations under Robolectric

The project already uses `androidx.compose.ui.test.junit4.v2.createComposeRule` with Robolectric
4.17 at `sdk=36` (`app/src/test/resources/robolectric.properties`).

- **v2 rule.** The v1 `createComposeRule` is deprecated (ui-test-junit4 1.12.1): "The v2 APIs use
  StandardTestDispatcher instead of UnconfinedTestDispatcher … Tests relying on immediate execution
  may require explicit synchronization." Call `waitForIdle()` / `runOnIdle {}` after triggering
  coroutines.
- **Clock control.** `MainTestClock` (ui-test 1.12.1) has `autoAdvance`, `advanceTimeByFrame()`,
  `advanceTimeBy(ms, ignoreFrameDuration = false)` and `advanceTimeUntil(timeoutMillis = 1000) {
  condition }`.
  - Pattern: `mainClock.autoAdvance = false`, trigger the event, `advanceTimeBy(150)`, then assert
    an intermediate state (for example both old and new digits exist, or a node's `boundsInRoot`).
  - Then `advanceTimeBy(1000)` and assert the end state.
  - `advanceTimeBy` "rounds up to the nearest multiple of the frame duration".
  - Source: https://developer.android.com/develop/ui/compose/animation/testing
- **Frame loop.** `while (hasPendingWork()) { mainClock.advanceTimeByFrame(); waitForIdle() }` with
  `runWithoutImplicitWait {}` (ui-test 1.12.0-alpha03+) gives fast frame stepping.
- **Default behaviour.** With `autoAdvance = true` (the default), `waitForIdle` runs animations to
  completion. Most behaviour tests should therefore assert end states and not depend on timing.
- **Test tags and semantics.**
  - For rolling digits, hide each animating character from semantics.
  - Expose one `contentDescription` / text with the final formatted value, for example
    `clearAndSetSemantics { contentDescription = formatted }` on the container.
  - Then TalkBack reads one number, and tests find it with `onNodeWithText` or
    `onNodeWithContentDescription`.
- **Reduce-motion branch.** Provide the app's `LocalReduceMotion` explicitly in tests, and test both
  branches.
- **Screenshots, not verified.** Pixel capture (`captureToImage`) under Robolectric needs native
  graphics mode (`@GraphicsMode(NATIVE)`). I did not check Robolectric 4.17's default.

---

## 5. Open points and things I could not confirm

- Mobbin and Dribbble shots: blocked (403 or JavaScript-only). The references are first-party pages
  and studio case studies instead.
- The exact m3.material.io wording for Material 3 Expressive motion, and the predictive-back scale
  values: the site renders client-side. The values here come from AndroidX token sources and
  MDC-Android.
- The name and behaviour of the Android 17 AOSP class behind "Remove animations": confirmed only on
  an older AOSP mirror.
- Whether the Compose test recomposer honours `ANIMATOR_DURATION_SCALE` under Robolectric: not
  verified. Use an explicit `CompositionLocal`.
- The font feature check (`tnum` present) was a byte scan of the font files, not an fontTools
  parse. Confirm by rendering `"1111"` vs `"0000"` widths in a Compose test.
