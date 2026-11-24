# On-device receipt reading on a Pixel 8: Gemma vision vs ML Kit OCR

Research for issue #19 (PabloJimenezJimenez/financeTracker). Researched 2026-10-20.
Target: Google Pixel 8 (Tensor G3, Mali-G715 GPU, 8 GB RAM), Android 17, sideloaded Kotlin/Compose APK, all processing on device, free only.

Tags used below: **[doc]** stated by a primary source; **[issue]** reported by a user in the LiteRT-LM GitHub tracker (first-party repo, but not an official benchmark); **[estimate]** my own arithmetic or reasoning from the sourced numbers, not measured; **[unconfirmed]** looked for and not found.

## Answer

| | Gemma 4 E2B vision (LiteRT-LM) | Gemma 4 E4B vision (LiteRT-LM) | Gemma 3n E2B/E4B vision (LiteRT-LM) | ML Kit Text Recognition v2 (Latin) + parser |
|---|---|---|---|---|
| Image input on Android, on device | Yes. LiteRT-LM supports Gemma 4's "native vision and audio" on device and loads the image encoder on demand [1]; Kotlin API takes `Content.ImageBytes`/`ImageFile` with `visionBackend` [2] | Yes, same API [1][2] | Yes; the docs' own multimodal example names Gemma 3n [2][9] | Yes, made for this [13][14] |
| Published document/OCR scores | InfographicVQA 63.9, OmniDocBench 1.5 edit distance 0.290 (1120 visual tokens) [6] | InfographicVQA 70.0, OmniDocBench 0.181 [6] | No vision benchmarks published for 3n [8][9] | No accuracy figures published [13][14] |
| DocVQA / OCRBench / ChartQA / TextVQA | Not published for Gemma 4 [4][5][6] | Not published [4][5][6] | Not published [8][9] | n/a |
| Model download | 2,583 MB `.litertlm` [3][10] | 3,654 MB [3][11] | 2,965 / 4,235 MB [3] | About 4 MB per script per ABI if bundled; about 260 KB if unbundled (Play services) [14] |
| Published phone speed | S26 Ultra GPU: 3,808 prefill / 52 decode tok/s, 676 MB peak CPU memory, text only [3][10] | S26 Ultra GPU: 1,293 / 22 tok/s, 710 MB [3][11] | S24 Ultra GPU: 816 / 16 tok/s [3][9] | "Real-time on most devices" for Latin [14] |
| Pixel 8 numbers | Community report only, text only: GPU 427 prefill / 12 decode tok/s, CPU 53 / 11 tok/s on a patched 0.10.1 [issue, 20]. No image timings anywhere [unconfirmed] | None [unconfirmed] | None [unconfirmed] | None [unconfirmed] |
| Rough time per receipt on Pixel 8 | About 20 to 60 s, mostly output decoding [estimate] | Slower than E2B (about 2x fewer decode tok/s on S26) [estimate from 3] | n/a | Under about 1 s for OCR [estimate from "real-time" claim, 14] |
| Geometry (where each word is) | No; it returns text. Gemma 4 can output boxes on a 1000x1000 grid for detection prompts [7] | Same | Same | Yes: bounding box, corner points, angle and confidence for each block, line and element [13][15] |
| Main risk | Hallucinated or skipped lines; downscaling of long receipts; open Android vision bugs [6][issue 17, 18] | Same, plus more memory and slower decode | Weaker and older than Gemma 4 | Reading order and column pairing must be rebuilt in code; no semantics |
| Can it also do the advice feature? | Yes, the same 2.58 GB file; the vision encoder loads only when needed [1][10] | Yes | Yes | No (OCR only) |

**Bottom line.** Gemma 4 E2B, the model the app already uses for advice, does accept images on Android through LiteRT-LM. The same `.litertlm` file covers both features, and the 150M vision encoder loads only when an image arrives [1][6][10]. Google lists OCR, multilingual OCR and document parsing among its capabilities [4], and Google's own Android sample uses the Gemma-4-based Gemini Nano 4 to parse receipts [12]. That route (AICore/ML Kit GenAI) is not available on a Pixel 8 [16]. Three things are against using E2B vision as the only reader. Its document scores are modest and fall sharply at lower image budgets: OmniDocBench edit distance is 0.290 at 1,120 tokens and 0.496 at 280 [6]. There are no published Pixel 8 or image-latency numbers. And there are open Android crashes in the vision path [17][18]. The recommended pipeline is ML Kit Text Recognition v2 (bundled, Latin) for the text and its geometry. Next comes a deterministic row and column parser with a sum-equals-TOTAL check. Gemma 4 E2B is used only as a fallback, first text-only over the OCR lines and then optionally with the image at the 1,120-token budget. The user always confirms the result. Before committing, the owner should benchmark E2B image prompts on the actual Pixel 8 with 10 to 20 real Mercadona, Lidl and Carrefour receipts.

## Details

### 1. Gemma models with vision on device

**Which Gemma variants have vision**
- Gemma 4: all five sizes (E2B, E4B, 12B Unified, 26B A4B, 31B) accept images. E2B and E4B are aimed at "mobile and edge devices" [4]. The listed image capabilities include "Document/PDF parsing", "chart comprehension" and "OCR (including multilingual), handwriting recognition" [4].
- E2B has 2.3B effective parameters (5.1B with embeddings) and E4B has 4.5B effective (8B with embeddings). Both have a 128K context and a vision encoder of about 150M parameters [4][6]. The LiteRT-LM bundles state support "up to 32k context length" [10][11].
- Gemma 3n E2B/E4B take images normalized to 256x256, 512x512 or 768x768, encoded to 256 tokens each [8][9]. Its cards publish no vision benchmarks [8][9].
- Gemma 3 (4B and up) is multimodal at 896x896 / 256 tokens, with DocVQA 75.8, TextVQA 57.8, ChartQA 68.8 and InfoVQA 50.0 for 4B IT [19]. Gemma 3 4B is not in LiteRT-LM's supported-model table (only Gemma3-1B is listed) [3], so using it would mean converting it yourself [unconfirmed whether its vision path converts].

**Does Gemma 4 E2B accept images in LiteRT-LM on Android? Yes, with caveats.**
- The Google Developers Blog (2026-05-19) says LiteRT-LM supports Gemma 4's "native vision and audio capabilities" on device and loads image and audio encoders only when a task needs them. It shows Gemma 4 multimodality running on phones in AI Edge Gallery [1].
- Kotlin API: `EngineConfig(visionBackend = Backend.GPU() /* or NPU */)`, then `conversation.sendMessage(Contents.of(Content.ImageFile(path), Content.Text(prompt)))`. `Content.ImageBytes` also exists [2][21]. The docs only show GPU or NPU for `visionBackend` on Android [2][21]. A user issue shows `visionBackend = CPU` is accepted on Android but crashes on the second image turn [issue, 17].
- The Hugging Face cards for the litert-community Gemma 4 bundles say "Vision and Audio models are loaded on demand" [10][11].
- GPU use requires `uses-native-library` entries for `libOpenCL.so` and `libvndksupport.so` in the manifest [2].
- The docs do not say how to choose the visual token budget (70/140/280/560/1120) in LiteRT-LM, or which budget the bundled vision encoder uses [unconfirmed; searched 2, 21, 3, 10]. This matters a lot for OCR (see benchmarks below).
- The Web SDK is text-only, which does not affect this app [21].

**Benchmarks relevant to receipts** (Gemma 4 Technical Report, thinking mode) [6]

| Benchmark | E2B @1120 tok | E4B @1120 tok | E2B @280 tok | E4B @280 tok |
|---|---|---|---|---|
| InfographicVQA | 63.9 | 70.0 | 44.6 | 54.8 |
| OmniDocBench 1.5 (edit distance, lower is better) | 0.290 | 0.181 | 0.496 | 0.307 |
| MMMU Pro | 44.2 | 52.6 | 43.2 | 51.4 |

- DocVQA, OCRBench, ChartQA and TextVQA are not reported for Gemma 4 in the model card [4][5] or the technical report [6].
- The model card gives no recommended OCR budget. It says higher budgets suit "OCR, document parsing, and small text" [4][5].
- These scores are for the full-precision models. The LiteRT-LM bundle is quantized (int4 weights for Gemma 3n [9]; 2-bit/4-bit weight support is mentioned for Gemma 4 [22]). How much quantization costs in OCR accuracy is not published [unconfirmed].
- The technical report says W8A8 quantization of the 150M encoder halves its forward-pass memory from 400 MB to 200 MB, and that on-device latency is 44% lower than Gemma 3n "on newer hardware" (device not named) [6].
- How to read an edit distance of 0.29 [estimate]: on whole-page document parsing, roughly 3 of every 10 characters need correcting. That is far from good enough to trust prices without checking.

**Memory and latency on a phone**
- E2B on S26 Ultra GPU: 3,808 prefill / 52 decode tok/s, TTFT 0.3 s, 676 MB peak CPU memory. On CPU: 557 / 47 tok/s, 1,733 MB. Text prompt of 1,024 tokens [3][10]. The blog says E2B can run in under 1.5 GB "on some devices" [22].
- Multi-token prediction gives up to 2.2x faster decode on mobile GPU [1][23].
- Pixel 8: there are no official numbers anywhere [unconfirmed; checked 1, 3, 10, 11, 23]. In issue #1850, on Pixel 8 / Android 16 with a self-patched 0.10.1 and text only, the reporter measured GPU prefill 426.6 tok/s, decode 11.9 tok/s, and CPU 53.4 / 10.6 tok/s. A maintainer closed it on 2026-09-30, saying internal tests show Gemma 4 E2B working on Pixel 8 GPU [issue, 20]. Related Mali-G715 OpenCL failures appeared on a Pixel 9a and stopped reproducing in v0.16.0 per the reporter, but the vision encoder on GPU was not retested [issue, 24].
- Image-prompt latency on Pixel 8 [estimate]: about 1,120 image tokens plus about 150 prompt tokens at about 430 tok/s is about 3 s prefill, plus unknown encoder time. A 30-item receipt written as compact `name|qty|price` lines is about 350 output tokens; at about 12 tok/s that is about 30 s, and JSON could double it. Expect roughly 20 to 60 s per receipt on E2B, with E4B slower. Measure before deciding.
- Memory [estimate]: 8 GB is enough for E2B text plus the encoder (about 0.7 to 1.7 GB peak in the S26 figures [3], plus about 200 to 400 MB for the encoder [6]), as long as the camera and other heavy objects are released first.

**One model for both features?** Yes. It is the same `litert-community/gemma-4-E2B-it-litert-lm` file [10], and image support needs only `visionBackend` set in `EngineConfig` [2]. Keep a single `Engine` and open a separate `Conversation` per task. Two separate issues show crashes on later image turns or near the token limit [issue, 17][issue, 18]. So use one fresh conversation per receipt, and set `visionBackend` to GPU, which the #2056 reporter found stable [issue, 17].

**Known open Android vision issues (as of 2026-10-20)**
- #2056: Gemma 4 E4B, SIGSEGV in the CPU vision path on the second image turn. The workaround is `visionBackend = GPU`. Still open [17].
- #2799: SIGSEGV in a multi-turn chat with several images near the token limit. Open [18].
- #3851: "image chronology errors" with Gemma 4 E2B on 0.17.1. Open (seen in issue search) [25].
- #3986: GPU engine build SIGSEGV on a Pixel 8a (Mali) with v0.18.0 for externalized weights. Fixed on main and not yet in a tagged release [26].

**Not an option on Pixel 8:** ML Kit GenAI Prompt API / AICore (Gemini Nano, image+text prompts). Its device lists cover Pixel 9, 10 and 11, not Pixel 8 [16]. Google's receipt sample (`ParsedReceipt` with title, amount, category; "Gemini Nano 4 ... OCR and visual data extraction") uses that API [12]. It only shows the model family can do receipts. It does not show anything you can run on this phone.

### 2. ML Kit Text Recognition v2 + parsing

- Scripts: Latin, Chinese, Devanagari, Japanese, Korean. Spanish (`es`) is a supported Latin language [13][27].
- Output hierarchy: Block, then Line, then Element (word), then Symbol. Each has a bounding box, corner points, rotation and confidence [13]. `Text.Line` exposes `getAngle()` in degrees, `getConfidence()` in [0, 1], `getCornerPoints()` (clockwise, not necessarily a rectangle), `getElements()` and `getRecognizedLanguage()` [15]. Confidence returns 0 on old unbundled Play services below 22.30 [15].
- Size and delivery: bundled `com.google.mlkit:text-recognition:16.0.1` adds about 4 MB per script per architecture and is available immediately. Unbundled `play-services-mlkit-text-recognition:19.0.1` adds about 260 KB but downloads the model through Play services [14]. Use bundled for a sideloaded, offline app. Min SDK is 23 [14].
- Speed: "Real-time on most devices for Latin script library" [14]. No millisecond figures are published [unconfirmed].
- Accuracy: no published accuracy figures [unconfirmed]. Image guidance: each character should be at least 16x16 px, with no gain above 24x24 px. A letter-size page needs about 720x1280. Poor focus hurts [14].
- On-device and privacy: "ML Kit's processing happens on-device ... works while offline" [28]. ML Kit does send device info, performance metrics such as latency, and API configuration such as image format and resolution to Google over HTTPS [29]. The disclosure page does not say whether this can be turned off, and does not explicitly say images are never sent [unconfirmed, 29].
- Optional pre-step: ML Kit Document Scanner does edge detection, cropping, perspective correction and shadow/stain removal, and "The entire document scanner flow operates on-device". Its models come through Play services, and the app needs no camera permission [30].

**Rebuilding item and price columns from boxes** [estimate, design reasoning from 13][15]
1. Deskew: take the median `Line.angle` and rotate the box coordinates.
2. Find the price column: elements matching `-?\d{1,4},\d{2}` (plus an optional `€` or a VAT letter suffix like `A`/`B`) whose right edges cluster near the right margin. ML Kit often splits one visual row into separate blocks (left text, right prices), so pair them by geometry, not by reading order.
3. Group rows by vertical overlap: put an element in a row if its y-center lies within about 0.5 of the median line height. Then sort by x.
4. Classify each row: item, continuation (multi-buy or weight detail on the next line), discount (negative amount, or keywords such as "DTO", "DESCUENTO", "AHORRO", "PROMO"), subtotal/total ("TOTAL", "TOTAL (€)", "IMPORTE"), VAT table ("IVA", "BASE IMPONIBLE", "CUOTA"), payment ("TARJETA", "EFECTIVO", "CAMBIO").
5. Check: the sum of item amounts plus discounts should equal TOTAL within €0.01. If it fails, or any price element has low confidence, flag the receipt or send it to the LLM fallback.

### 3. Receipt failure modes and how each option handles them

Receipt layouts below are general knowledge, not from a primary source. Check them against the owner's own receipts.

| Failure mode | ML Kit + rules | Gemma 4 E2B vision |
|---|---|---|
| Faded thermal paper, low contrast | Characters drop out, but low `confidence` flags them [15]; Document Scanner cleaning helps [30] | May fill gaps with plausible but made-up text; the model card warns of "incorrect ... statements" [4]. No confidence score |
| Long receipts (tall aspect ratio) | Shoot in two or three overlapping photos so characters stay at 16 px or more [14], then de-duplicate rows | The image is resized to fit the token budget [7]; small text degrades sharply at lower budgets [6]. Tile into several images (multiple images per prompt are supported [7]) |
| Multi-buy, e.g. `2 x 1,25` on its own line, or Mercadona-style `qty name unit total` | Regex for `(\d+) ?[xX] ?(\d+,\d{2})` and a leading quantity column; check qty × unit = line total | Usually understood semantically; arithmetic can be wrong, so recheck qty × unit in code |
| Weighed items (`0,532 kg x 2,99 €/kg 1,59`) | Regex for kg, €/kg; the amount is the right-column value | Same as above; recheck in code |
| Discounts and promos (negative lines, "2ª unidad -50%") | Negative amounts or keywords; assign to the previous item | Often handled, but can be dropped or merged; the sum check catches it |
| Totals with VAT tables (IVA/base/cuota) | Stop item parsing at the TOTAL row; ignore the VAT block | May include VAT-table numbers as items; the prompt and the sum check must guard against this |
| Truncated or abbreviated item names ("LECHE SEMI DESN") | Kept verbatim | May "expand" names; useful for categories but risky as a record |
| Decimal comma, € formats | Parse `,` as the decimal separator | Handles it; ask for normalized output |
| Rotation and perspective | Document Scanner plus `angle`/corner points [15][30] | Handles mild rotation; not measured |

### 4. Recommended pipeline

1. Capture with the ML Kit Document Scanner (on-device; cropping, perspective fix, cleaning) [30], or CameraX at high resolution. For long receipts allow two or three shots.
2. Run ML Kit Text Recognition v2, bundled Latin [14].
3. Run the deterministic parser (geometry plus regex, section 2) and apply per-chain rules for Mercadona, Lidl and Carrefour once real samples exist.
4. Validate: sum equals TOTAL, and qty × unit and kg × €/kg add up.
5. Fallback A (cheap): send the OCR lines with their coordinates as text to the already-loaded Gemma 4 E2B. Ask for `name|qty|unit|amount` lines and validate again in code. This is text prefill only and needs no vision encoder.
6. Fallback B (optional, slow): send the image (or tiles) to Gemma 4 E2B with `visionBackend = GPU`, using a fresh conversation per receipt [17]. Reconcile with the OCR output.
7. Show an editable review screen and never save without user confirmation.

**Validate before building:** on the Pixel 8, measure E2B image-prompt time to first token, total time and peak memory, using the litertlm-android version the app will ship. Check which visual-token budget the bundle uses [unconfirmed]. Also check the current state of #2056, #2799 and #3986.

## Sources

1. Google Developers Blog, "Blazing fast on-device GenAI with LiteRT-LM" (2026-05-19): https://developers.googleblog.com/en/blazing-fast-on-device-genai-with-litert-lm/
2. LiteRT-LM, Get started on Android: https://developers.google.com/edge/litert-lm/android (also https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md)
3. LiteRT-LM overview (supported models, Android benchmarks): https://developers.google.com/edge/litert-lm/overview
4. Gemma 4 model card: https://ai.google.dev/gemma/docs/core/model_card_4
5. Gemma 4 E2B-it model card (Google, Hugging Face): https://huggingface.co/google/gemma-4-E2B-it
6. Gemma 4 Technical Report, Gemma Team, arXiv:2607.02770 v2: https://arxiv.org/html/2607.02770v2
7. Gemma image understanding guide: https://ai.google.dev/gemma/docs/capabilities/vision/image
8. Gemma 3n model card: https://ai.google.dev/gemma/docs/gemma-3n/model_card (overview: https://ai.google.dev/gemma/docs/gemma-3n)
9. Gemma 3n E2B LiteRT-LM bundle card: https://huggingface.co/google/gemma-3n-E2B-it-litert-lm
10. Gemma 4 E2B LiteRT-LM bundle card: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
11. Gemma 4 E4B LiteRT-LM bundle card: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
12. Android Developers Blog, "Build intelligent Android apps: On-device inference" (2026-07): https://android-developers.googleblog.com/2026/07/android-on-device-inference.html
13. ML Kit Text Recognition v2 overview: https://developers.google.com/ml-kit/vision/text-recognition/v2
14. ML Kit Text Recognition v2, Android guide: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
15. `Text.Line` API reference: https://developers.google.com/android/reference/com/google/mlkit/vision/text/Text.Line
16. ML Kit GenAI APIs overview (supported devices): https://developers.google.com/ml-kit/genai
17. LiteRT-LM issue #2056 (CPU vision path SIGSEGV, Android): https://github.com/google-ai-edge/LiteRT-LM/issues/2056
18. LiteRT-LM issue #2799 (multi-image multi-turn crash), from the issue search: https://github.com/google-ai-edge/LiteRT-LM/issues?q=is%3Aissue+image+android
19. Gemma 3 model card: https://ai.google.dev/gemma/docs/core/model_card_3
20. LiteRT-LM issue #1850 (Gemma 4 E2B on Pixel 8 GPU): https://github.com/google-ai-edge/LiteRT-LM/issues/1850
21. LiteRT-LM API overview: https://developers.google.com/edge/litert-lm/api_overview
22. Google Developers Blog, "Bring state-of-the-art agentic skills to the edge with Gemma 4" (2026-04-02): https://developers.googleblog.com/bring-state-of-the-art-agentic-skills-to-the-edge-with-gemma-4/
23. LiteRT-LM Gemma 4 page: https://developers.google.com/edge/litert-lm/models/gemma-4
24. LiteRT-LM issue #2421 (Mali-G715 OpenCL, Pixel 9a): https://github.com/google-ai-edge/LiteRT-LM/issues/2421
25. LiteRT-LM issue #3851, from the issue search: https://github.com/google-ai-edge/LiteRT-LM/issues?q=is%3Aissue+image+android
26. LiteRT-LM issue #3986 (Pixel 8a GPU crash, v0.18.0): https://github.com/google-ai-edge/LiteRT-LM/issues/3986
27. ML Kit Text Recognition v2 languages: https://developers.google.com/ml-kit/vision/text-recognition/v2/languages
28. ML Kit home: https://developers.google.com/ml-kit
29. ML Kit Android data disclosure: https://developers.google.com/ml-kit/android-data-disclosure
30. ML Kit Document Scanner: https://developers.google.com/ml-kit/vision/doc-scanner
