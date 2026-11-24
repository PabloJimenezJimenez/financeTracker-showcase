# On-device LLM options on a Pixel 8 running Android 17

Research for issue #11. The question is which small language model can run fully on one Google Pixel 8 (Android 17) and turn KPI results into 2-4 sentences of English advice. The app is a sideloaded Kotlin + Jetpack Compose APK, currently minSdk 26. Researched 2026-10-20.

## Answer

| | Gemini Nano via AICore / ML Kit GenAI | Gemma 4 E2B via LiteRT-LM | Gemma 3 1B (int4) via LiteRT-LM or MediaPipe | llama.cpp (GGUF) |
|---|---|---|---|---|
| Available on Pixel 8 (non-Pro)? | **No, as far as current docs show.** Pixel 8 is not in any ML Kit GenAI device list. The Prompt API list starts at Pixel 9 [1]. | Yes. Runs in-process on CPU or GPU. No published Tensor G3 NPU build [9]. | Yes (CPU/GPU) | Yes (CPU) |
| Size on disk | Managed by AICore, not by the app | 2583 MB `.litertlm` [7] | about 529 MB int4 [8] | Depends on the GGUF quant (not researched) |
| RAM | Managed by the system | 1733 MB peak on CPU and 676 MB on GPU, measured on an S26 Ultra, not a Pixel 8 [7] | Not stated for the 1B | Not researched |
| Speed on Pixel 8 | n/a | About 10.6 tok/s decode on CPU and about 11.9 tok/s on GPU (one user report on the official tracker, Pixel 8 on Android 16) [10] | No Pixel 8 figure. 47-55 tok/s decode on an S24 Ultra [8] | No official figure |
| Quality for short advice | Good if available | Best of the options here: MMLU-Pro 60.0, 128K context [5] | Weaker: MMLU-Pro 14.7, but IFEval 80.2 [11] | Depends on the model |
| How the model gets on device | AICore downloads it [2] | The app downloads it, or you `adb push` it [4][6] | Same as Gemma 4 E2B | Same |
| Licence | ML Kit GenAI Additional Terms [12] | Apache 2.0 [5][13] | Gemma Terms of Use + Prohibited Use Policy [14] | MIT for llama.cpp. The model has its own licence |
| Gradle | `com.google.mlkit:genai-prompt:1.0.0-beta4`, minSdk 26 [2][15] | `com.google.ai.edge.litertlm:litertlm-android:0.18.0`, AAR minSdk 24 [6][15] | LiteRT-LM as in the previous column, or `com.google.mediapipe:tasks-genai:0.10.35` (maintenance mode, AAR minSdk 21) [4][15] | No Maven artifact. Build with the NDK or the `examples/llama.android` module [16] |

**Bottom line:** Use **Gemma 4 E2B (instruction-tuned) through LiteRT-LM's Kotlin API (`litertlm-android`)**, with **Gemma 3 1B int4** as a lighter fallback.

- **Why not Gemini Nano.** The ML Kit GenAI device lists, including the Prompt API list, do not include the Pixel 8. The only time Pixel 8 got Gemini Nano was a 2024 *user* developer option for Pixel features such as Recorder summaries [3]. Nothing shows that third-party apps can reach it on that phone. Even where Nano works, it only runs while the app is in the foreground and it has per-app quotas [1].
- **Why Gemma 4 E2B.** It is Apache 2.0, it is a first-class LiteRT-LM model, and it has been reported running on a Pixel 8 at about 10-12 tokens/s. A 2-4 sentence reply (about 60-100 tokens) is therefore roughly 6-10 s plus prefill.
- **Costs.** Gemma 4 E2B takes about 2.6 GB of storage, and the model must be copied onto the phone outside the APK. Pixel 8 has 8 GB RAM [17], which comfortably fits the roughly 1.7 GB peak reported on CPU.
- **Not confirmed.** Pixel 8 + Android 17 behaviour of LiteRT-LM's GPU backend is unconfirmed. GPU decode crashed on 0.10.0 on a Pixel 8. A maintainer says internal tests now pass, and the issue was closed on 2026-09-30 [10]. Start on `Backend.CPU` and treat GPU as an optimisation to verify on the device.

## 1. Gemini Nano via AICore and the ML Kit GenAI APIs

### Pixel 8 availability

- The ML Kit GenAI overview lists the devices for the feature APIs (Summarization, Proofreading, Rewriting, Image Description). The Google devices are Pixel 9, 10 and 11 families only. The list does **not** include Pixel 8 or Pixel 8 Pro. The page notes "plans to expand support to additional devices" [1].
- The Prompt API has a narrower list, grouped by Nano version: nano-v2 (non-Google devices), nano-v3 (Pixel 9 and 10 families) and nano-v4 (Pixel 11 family). Again there is no Pixel 8 [1].
- History, for context only. Pixel 8 Pro was the launch device for AICore. In June 2024 Google said "you can use Pixel 8 and Pixel 8a to access Gemini Nano as a developer option" (Settings > Developer options), for features like Recorder summaries [3]. That was for Google's own features. No primary source says a third-party app can call ML Kit GenAI on a Pixel 8, on Android 17 or otherwise.
- The 2024 experimental "Google AI Edge SDK" access to Gemini Nano was "on Pixel 9 series devices" only [18].
- The AICore Developer Preview, which provides preview models and a quota bypass, is "available for the devices that support the Prompt API" [19]. It therefore also excludes the Pixel 8.
- **Not confirmed:** whether `checkStatus()` on a Pixel 8 running Android 17 returns anything other than `UNAVAILABLE`. If the phone is at hand, a 10-line probe app calling `checkStatus()` would settle it. On the docs alone, assume it is unavailable.

### APIs a sideloaded third-party app may call (on supported devices)

- **Available APIs:** Prompt (Beta), Summarization (Beta), Proofreading (Beta), Rewriting (Beta), Image description (Beta) and Speech recognition (Alpha) [1].
- **No Play or allowlist requirement in the docs.** No source found requires Play distribution or app allowlisting. The library declares the permission `com.google.android.apps.aicore.service.BIND_SERVICE` in its AAR manifest [15].
- **Terms that apply:**
  - The ML Kit GenAI Additional Terms apply.
  - Users must be 18 or older.
  - You may not use "Preview" or "Experimental Access" services in production.
  - The terms say "Don't rely on the Services for medical, legal, financial, or other professional advice". That is relevant wording for a finance-advice feature [12].
- **Summarization is not a good fit:** it is fixed at "1-3 bullet points" [20]. For free-form 2-4 sentence advice, the Prompt API would be the right call.
- **Unlocked bootloaders:** The API is "not supported on devices with an unlocked bootloader" [2].

### Quota, foreground and offline behaviour

- **Per-app quota:**
  - "AICore enforces an inference quota per app". Going over it returns `ErrorCode.BUSY`.
  - A long-duration (for example daily) battery quota returns `PER_APP_BATTERY_USE_QUOTA_EXCEEDED`. No numbers are published [1].
- **Foreground only:** Inference is "permitted only when the app is the top foreground application". Background use, including from a foreground service, returns `BACKGROUND_USE_BLOCKED` [1].
- **Offline:** Data is processed locally, and "functionality remains the same without reliable internet connection" [1]. The model download itself needs a network, and AICore routes it through Private Compute Services [21].
- **Limits:** Input must be under 4000 tokens. You can set `temperature`, `topK`, `maxOutputTokens` and similar parameters [2].

### Kotlin / Gradle

- **Dependency:** `implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")`. This is the latest on Google Maven [2][15].
- **minSdk:** "API level 26 or higher" [2]. The AAR manifest also says minSdk 26 [15].
- **Flow:** call `checkStatus()`, which returns UNAVAILABLE, DOWNLOADABLE, DOWNLOADING or AVAILABLE. Then call `download()`, which gives a Flow of progress, and then `generateContent` [2].

## 2. Small open models in-process (Gemma via LiteRT-LM or MediaPipe)

### Runtime choice

- **MediaPipe LLM Inference API** (`com.google.mediapipe:tasks-genai`) is in **"maintenance-only mode"**. Google recommends migrating to the LiteRT-LM Kotlin API [4]. It is still "optimized for high-end Android devices, such as Pixel 8 and Samsung S23 or later" [4].
- **LiteRT-LM** is Google's "production-ready" LLM runtime. Its Kotlin API is marked **Stable** [22].
  - Supported chat models: Gemma4-E2B/E4B, Gemma-3n-E2B/E4B, Gemma3-1B, FunctionGemma, phi-4-mini, Qwen2.5-1.5B, Qwen3-0.6B and Qwen2.5-0.5B [22].
  - Latest release: v0.18.0 [15][23].
- **NPU:** LiteRT-LM NPU support for Google Tensor lists only Tensor_G5 and Tensor_G6. **Tensor G3 (Pixel 8) is not listed** [9]. On a Pixel 8, use CPU or GPU.

### Gemma 4 E2B (recommended)

- **Size and features:**
  - 2.3B effective parameters (5.1B with embeddings) and a 128K context.
  - Accepts text, image and audio input.
  - Intended uses include text generation and summarization [5].
- **Quality** (instruction-tuned E2B): MMLU-Pro 60.0%, MMMLU 67.4%, GPQA Diamond 43.4% [5]. This is far above Gemma 3 1B and comfortably enough for 2-4 sentences of KPI commentary.
- **On-disk size:** 2583 MB for the standard `.litertlm` file [7].
- **Memory and speed (official, S26 Ultra):**
  - CPU: 557 tok/s prefill, 46.9 tok/s decode, 1733 MB.
  - GPU: 3808 tok/s prefill, 52.1 tok/s decode, 676 MB.
  - With speculative decoding, GPU decode reaches up to 91.7 tok/s on summarization [7].
  - There is no official Pixel 8 figure.
- **Pixel 8 report** (GitHub issue on the official repo, Pixel 8 on Android 16) [10]:
  - CPU: 53.35 tok/s prefill and 10.57 tok/s decode.
  - Patched GPU: 426.64 tok/s prefill and 11.94 tok/s decode, with TTFT of about 2.6 s in one run.
  - Stock 0.10.0 crashed in GPU decode.
  - A collaborator said internal tests showed it working on Pixel 8 GPU, and the issue was closed on 2026-09-30.
  - This is one user's measurement, not a Google benchmark.
- **Licence:** Apache 2.0 [5][13]. The Hugging Face repo shows no gating [7].

### Gemma 3 1B int4 (lighter fallback)

- **Model:** Text model with a 32K context [11].
- **Quality** (IT): IFEval 80.2, GSM8K 62.8, MMLU-Pro 14.7 [11]. It follows instructions well, but its reasoning and knowledge are weak. It is acceptable for templated "turn these numbers into a sentence" tasks, but more likely to produce generic or wrong advice.
- **Size:** about 529 MB int4 (QAT). `.litertlm` and MediaPipe `.task` variants are available [8].
- **Speed:** S24 Ultra, 47-55 tok/s decode on CPU with LiteRT-LM or MediaPipe [8]. There is no Pixel 8 figure.
- **Licence:** Gemma Terms of Use, which incorporate the Prohibited Use Policy. Redistribution must pass the terms on and include the notice [14]. The Hugging Face repo requires accepting the Gemma licence before download [8].

### Getting the model file onto the phone

- Google says "the model is too large to be bundled in an APK". For deployment, download it at runtime. For development, `adb push` it to `/data/local/tmp/llm/` [4].
- LiteRT-LM takes a file path (`EngineConfig(modelPath = ...)`). Setting `cacheDir = context.cacheDir.path` speeds up later loads [6].
- Two practical routes for a single sideloaded device:
  - `adb push` the file, then have the app copy it into app-private storage.
  - Have the app download it once on first run from Hugging Face (`litert-community`) or your own host [6][7].
- After that, inference is fully offline.

### Kotlin / Gradle (LiteRT-LM)

- **Dependency:** `implementation("com.google.ai.edge.litertlm:litertlm-android:<version>")` [6]. Pin `0.18.0` instead of `latest.release` [15].
- **minSdk:** The AAR manifest declares `minSdkVersion 24` [15], so minSdk 26 is fine. The docs do not state a minSdk.
- **ABIs:** The AAR ships native libraries for arm64-v8a and x86_64 (about 22 MB for arm64) [15].
- **API:**
  - Create the engine with `Engine(EngineConfig(modelPath, backend = Backend.CPU / Backend.GPU))` and call `engine.initialize()` off the main thread. Loading can take about 10 s.
  - Then call `engine.createConversation()` and `sendMessageAsync(...)`, which gives a Flow for streaming [6].
- **GPU:** Add `<uses-native-library android:name="libOpenCL.so" android:required="false"/>` and the same for `libvndksupport.so` in the manifest [6].
- **MediaPipe alternative:** `com.google.mediapipe:tasks-genai:0.10.27` per the docs. The latest on Maven is 0.10.35, with AAR minSdk 21. It is in maintenance mode, so not recommended for new code [4][15].

### llama.cpp

- llama.cpp supports Android in two ways [16]:
  - Through `examples/llama.android`, a Kotlin binding with an `AiChat` facade, an `InferenceEngine` and a Flow of tokens.
  - Through an NDK cross-compile or a Termux build.
- It accelerates Arm CPUs up to SME2 and optionally uses KleidiAI [16].
- No Maven artifact. You must build and ship the native library yourself, and GGUF models are separate downloads. There are no Google or llama.cpp benchmarks for Pixel 8.
- It is only worth considering if you want a model that LiteRT-LM does not support.

## Pixel 8 hardware

- Google Tensor G3, **8 GB LPDDR5X RAM**, 128/256 GB UFS 3.1. Pixel 8 Pro has 12 GB [17].

## Things that could not be confirmed

- Whether AICore / ML Kit GenAI returns AVAILABLE for any feature on a Pixel 8 running Android 17. No primary source lists Pixel 8, and the docs suggest it does not.
- Official Pixel 8 / Tensor G3 throughput or memory figures for any Gemma model. The only Pixel 8 numbers are from one user report [10].
- Whether LiteRT-LM 0.18.0 GPU decode works on Pixel 8 + Android 17. The fix was reported closed but not tied to a named change [10].
- Peak RAM for Gemma 3 1B on any phone. The card's memory columns were garbled in the fetch [8].

## Sources

1. ML Kit GenAI APIs overview (supported devices, quota, background restrictions, offline): https://developers.google.com/ml-kit/genai
2. ML Kit Prompt API, get started (artifact, API 26, `checkStatus`/`download`, token limits, unlocked bootloader): https://developers.google.com/ml-kit/genai/prompt/android/get-started
3. Google blog, June 2024 Pixel Feature Drop (Pixel 8/8a Gemini Nano as developer option): https://blog.google/products/pixel/pixel-feature-drop-june-2024/
4. MediaPipe LLM Inference guide for Android (maintenance mode, `tasks-genai:0.10.27`, adb push path, "optimized for ... Pixel 8"): https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android
5. Gemma 4 model card (sizes, context, benchmarks, Apache 2.0): https://ai.google.dev/gemma/docs/core/model_card_4
6. LiteRT-LM Android (Kotlin) guide (artifact, Engine API, GPU manifest entries, cacheDir): https://developers.google.com/edge/litert-lm/android
7. Hugging Face, litert-community/gemma-4-E2B-it-litert-lm (file sizes, benchmarks, licence): https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
8. Hugging Face, litert-community/Gemma3-1B-IT (file sizes, benchmarks, Gemma licence gate): https://huggingface.co/litert-community/Gemma3-1B-IT
9. LiteRT-LM on NPU (Google Tensor table: G5, G6 only): https://developers.google.com/edge/litert/next/litert_lm_npu
10. LiteRT-LM issue #1850, "Gemma 4 E2B on Pixel 8" (Pixel 8 measurements, closed 2026-09-30): https://github.com/google-ai-edge/LiteRT-LM/issues/1850
11. Gemma 3 model card (1B context and benchmarks): https://ai.google.dev/gemma/docs/core/model_card_3
12. ML Kit GenAI API Additional Terms of Service: https://developers.google.com/ml-kit/genai-terms
13. Gemma 4 licence (Apache 2.0): https://ai.google.dev/gemma/apache_2
14. Gemma Terms of Use (covers Gemma 1-3, 3n; not Gemma 4): https://ai.google.dev/gemma/terms
15. Google Maven metadata and AAR manifests, checked 2026-10-20:
    - https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml (latest 0.18.0; AAR `minSdkVersion 24`)
    - https://dl.google.com/android/maven2/com/google/mlkit/genai-prompt/maven-metadata.xml (latest 1.0.0-beta4; AAR minSdk 26, AICore BIND_SERVICE permission)
    - https://dl.google.com/android/maven2/com/google/mediapipe/tasks-genai/maven-metadata.xml (latest 0.10.35; AAR minSdk 21)
16. llama.cpp Android docs: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
17. Pixel phone hardware tech specs (Pixel 8: Tensor G3, 8 GB RAM; 8 Pro: 12 GB): https://support.google.com/pixelphone/answer/7158570?hl=en
18. Android Developers Blog, Gemini Nano experimental access (Pixel 9 series): https://android-developers.googleblog.com/2024/10/gemini-nano-experimental-access-available-on-android.html
19. AICore Developer Preview: https://developers.google.com/ml-kit/genai/aicore-dev-preview
20. ML Kit Prompt API overview ("Summarizations can only be in 1-3 bullet points"): https://developers.google.com/ml-kit/genai/prompt/android
21. Gemini Nano on Android Developers (AICore, Private Compute Services): https://developer.android.com/ai/gemini-nano
22. LiteRT-LM overview (Kotlin stable, supported models): https://developers.google.com/edge/litert-lm/overview
23. LiteRT-LM GitHub repository (v0.18.0, Apache-2.0): https://github.com/google-ai-edge/LiteRT-LM
