# JMH Microbenchmark Cross-Project Comparison Report

Microbenchmark results comparing all AI model implementations against `demo0` baseline (Size = 1,000 items).

## Operation: `getHit`

| Rank | Implementation | Model | Throughput (ops/µs) | Margin (±) | Speedup vs Demo 0 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **demo7** | Demo 7 (Qwen 38 max XHigh) | 126.38 | ± 133.28 | **2.07x 🚀** |
| 2 | **demo5** | Demo 5 (Deepseek V4 Flash Max) | 124.89 | ± 9.19 | **2.04x 🚀** |
| 3 | **demo9** | Demo 9 (Gemini 3.8 Flash High / Antigravity) | 106.97 | ± 3.99 | **1.75x 🚀** |
| 4 | **demo3** | Demo 3 (OpenAI 5.6 Sol Max) | 104.10 | ± 5.73 | **1.70x 🚀** |
| 5 | **demo11** | Demo 11 (Gemini 3.7 Flash High / Antigravity Rework) | 103.33 | ± 10.99 | **1.69x 🚀** |
| 6 | **demo12** | Demo 12 (Gemini 3.8 Flash High / Antigravity Rework) | 103.07 | ± 17.57 | **1.69x 🚀** |
| 7 | **demo8** | Demo 8 (Gemini 3.7 Flash High / Kilo Code) | 100.27 | ± 6.22 | **1.64x 🚀** |
| 8 | **demo2** | Demo 2 (Kimi K3) | 97.58 | ± 86.68 | **1.60x 🚀** |
| 9 | **demo6** | Demo 6 (Claude Opus 5 Ultra) | 96.62 | ± 95.89 | **1.58x 🚀** |
| 10 | **demo1** | Demo 1 (Gemini 3.7 Flash High / Antigravity) | 90.53 | ± 88.16 | **1.48x 🚀** |
| 11 | **demo4** | Demo 4 (Gemma 4 31B Thinking) | 64.49 | ± 8.23 | **1.06x 🚀** |
| 12 | **demo0** | Demo 0 (Baseline / FastRandom) | 61.12 | ± 22.66 | 1.00x |

## Operation: `getMiss`

| Rank | Implementation | Model | Throughput (ops/µs) | Margin (±) | Speedup vs Demo 0 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **demo7** | Demo 7 (Qwen 38 max XHigh) | 159.94 | ± 25.71 | **1.95x 🚀** |
| 2 | **demo5** | Demo 5 (Deepseek V4 Flash Max) | 133.77 | ± 20.06 | **1.63x 🚀** |
| 3 | **demo2** | Demo 2 (Kimi K3) | 101.50 | ± 25.78 | **1.24x 🚀** |
| 4 | **demo6** | Demo 6 (Claude Opus 5 Ultra) | 99.99 | ± 4.26 | **1.22x 🚀** |
| 5 | **demo9** | Demo 9 (Gemini 3.8 Flash High / Antigravity) | 92.49 | ± 80.24 | **1.13x 🚀** |
| 6 | **demo12** | Demo 12 (Gemini 3.8 Flash High / Antigravity Rework) | 91.60 | ± 17.18 | **1.12x 🚀** |
| 7 | **demo3** | Demo 3 (OpenAI 5.6 Sol Max) | 91.23 | ± 14.92 | **1.11x 🚀** |
| 8 | **demo8** | Demo 8 (Gemini 3.7 Flash High / Kilo Code) | 88.66 | ± 20.02 | **1.08x 🚀** |
| 9 | **demo1** | Demo 1 (Gemini 3.7 Flash High / Antigravity) | 88.14 | ± 9.05 | **1.08x 🚀** |
| 10 | **demo11** | Demo 11 (Gemini 3.7 Flash High / Antigravity Rework) | 86.96 | ± 11.47 | **1.06x 🚀** |
| 11 | **demo0** | Demo 0 (Baseline / FastRandom) | 81.93 | ± 16.91 | 1.00x |
| 12 | **demo4** | Demo 4 (Gemma 4 31B Thinking) | 53.11 | ± 7.74 | 0.65x 🔻 |

## Operation: `put`

| Rank | Implementation | Model | Throughput (ops/µs) | Margin (±) | Speedup vs Demo 0 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **demo7** | Demo 7 (Qwen 38 max XHigh) | 83.56 | ± 71.21 | **1.27x 🚀** |
| 2 | **demo5** | Demo 5 (Deepseek V4 Flash Max) | 69.49 | ± 84.27 | **1.06x 🚀** |
| 3 | **demo1** | Demo 1 (Gemini 3.7 Flash High / Antigravity) | 67.78 | ± 15.13 | 1.03x |
| 4 | **demo0** | Demo 0 (Baseline / FastRandom) | 65.84 | ± 23.73 | 1.00x |
| 5 | **demo8** | Demo 8 (Gemini 3.7 Flash High / Kilo Code) | 61.72 | ± 22.91 | 0.94x 🔻 |
| 6 | **demo12** | Demo 12 (Gemini 3.8 Flash High / Antigravity Rework) | 61.21 | ± 47.35 | 0.93x 🔻 |
| 7 | **demo2** | Demo 2 (Kimi K3) | 60.86 | ± 8.82 | 0.92x 🔻 |
| 8 | **demo11** | Demo 11 (Gemini 3.7 Flash High / Antigravity Rework) | 58.32 | ± 136.45 | 0.89x 🔻 |
| 9 | **demo6** | Demo 6 (Claude Opus 5 Ultra) | 54.57 | ± 18.21 | 0.83x 🔻 |
| 10 | **demo9** | Demo 9 (Gemini 3.8 Flash High / Antigravity) | 54.51 | ± 82.90 | 0.83x 🔻 |
| 11 | **demo3** | Demo 3 (OpenAI 5.6 Sol Max) | 44.66 | ± 79.43 | 0.68x 🔻 |
| 12 | **demo4** | Demo 4 (Gemma 4 31B Thinking) | 38.80 | ± 11.00 | 0.59x 🔻 |

