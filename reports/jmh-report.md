# JMH Microbenchmark Cross-Project Comparison Report

Microbenchmark results comparing all AI model implementations against `demo0` baseline (Size = 1,000 items).

> ⚡ **Hardware Performance Counters Enabled**: Includes Linux `perf` metrics (Cycles/op, Instructions/op, IPC, Branch Mispredictions, L1 D-Cache Misses).

## Operation: `getHit`

| Rank | Implementation | Model | Throughput (ops/µs) | Margin (±) | Speedup | Cycles/op | Insns/op | IPC | Branch Miss % | L1 D-Cache Miss % |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **demo7** | Demo 7 (Qwen 38 max XHigh) | 138.60 | ± 17.18 | **1.81x 🚀** | 28.2 | 63.5 | 2.25 | 0.23% | 13.48% |
| 2 | **demo5** | Demo 5 (Deepseek V4 Flash Max) | 135.73 | ± 10.02 | **1.77x 🚀** | 29.4 | 76.4 | 2.60 | 0.17% | 18.99% |
| 3 | **demo2** | Demo 2 (Kimi K3) | 115.66 | ± 1.89 | **1.51x 🚀** | 33.7 | 90.8 | 2.70 | 0.08% | 10.01% |
| 4 | **demo3** | Demo 3 (OpenAI 5.6 Sol Max) | 113.17 | ± 3.96 | **1.47x 🚀** | 34.4 | 94.5 | 2.74 | 0.09% | 9.46% |
| 5 | **demo12** | Demo 12 (Gemini 3.8 Flash High / Antigravity Rework) | 111.95 | ± 1.64 | **1.46x 🚀** | 35.5 | 90.9 | 2.56 | 0.11% | 10.12% |
| 6 | **demo1** | Demo 1 (Gemini 3.7 Flash High / Antigravity) | 111.46 | ± 12.93 | **1.45x 🚀** | 35.9 | 93.6 | 2.61 | 0.08% | 10.02% |
| 7 | **demo11** | Demo 11 (Gemini 3.7 Flash High / Antigravity Rework) | 110.74 | ± 23.19 | **1.44x 🚀** | 34.9 | 93.1 | 2.67 | 0.11% | 10.05% |
| 8 | **demo9** | Demo 9 (Gemini 3.8 Flash High / Antigravity) | 107.76 | ± 4.80 | **1.40x 🚀** | 34.5 | 91.8 | 2.66 | 0.08% | 10.83% |
| 9 | **demo8** | Demo 8 (Gemini 3.7 Flash High / Kilo Code) | 101.95 | ± 3.32 | **1.33x 🚀** | 37.5 | 89.0 | 2.38 | 0.09% | 20.95% |
| 10 | **demo6** | Demo 6 (Claude Opus 5 Ultra) | 95.11 | ± 1.71 | **1.24x 🚀** | 41.8 | 91.7 | 2.19 | 0.08% | 8.45% |
| 11 | **demo0** | Demo 0 (Baseline / FastRandom) | 76.73 | ± 4.99 | 1.00x | 50.6 | 124.2 | 2.46 | 0.73% | 4.17% |
| 12 | **demo4** | Demo 4 (Gemma 4 31B Thinking) | 69.30 | ± 1.85 | 0.90x 🔻 | 58.9 | 109.0 | 1.85 | 0.16% | 7.00% |

## Operation: `getMiss`

| Rank | Implementation | Model | Throughput (ops/µs) | Margin (±) | Speedup | Cycles/op | Insns/op | IPC | Branch Miss % | L1 D-Cache Miss % |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **demo7** | Demo 7 (Qwen 38 max XHigh) | 170.54 | ± 64.37 | **1.90x 🚀** | 24.0 | 45.9 | 1.92 | 0.02% | 12.13% |
| 2 | **demo5** | Demo 5 (Deepseek V4 Flash Max) | 134.98 | ± 22.96 | **1.51x 🚀** | 27.7 | 72.7 | 2.62 | 0.03% | 12.04% |
| 3 | **demo2** | Demo 2 (Kimi K3) | 106.29 | ± 52.84 | **1.19x 🚀** | 36.8 | 93.1 | 2.53 | 0.02% | 13.59% |
| 4 | **demo9** | Demo 9 (Gemini 3.8 Flash High / Antigravity) | 104.86 | ± 3.24 | **1.17x 🚀** | 36.8 | 93.0 | 2.53 | 0.02% | 14.08% |
| 5 | **demo3** | Demo 3 (OpenAI 5.6 Sol Max) | 103.57 | ± 4.94 | **1.16x 🚀** | 38.1 | 98.2 | 2.58 | 0.02% | 12.38% |
| 6 | **demo12** | Demo 12 (Gemini 3.8 Flash High / Antigravity Rework) | 96.32 | ± 24.74 | **1.07x 🚀** | 40.6 | 103.9 | 2.56 | 0.02% | 13.24% |
| 7 | **demo8** | Demo 8 (Gemini 3.7 Flash High / Kilo Code) | 93.16 | ± 0.92 | 1.04x | 42.1 | 97.5 | 2.32 | 0.03% | 18.73% |
| 8 | **demo11** | Demo 11 (Gemini 3.7 Flash High / Antigravity Rework) | 91.26 | ± 2.79 | 1.02x | 42.8 | 105.7 | 2.47 | 0.02% | 12.88% |
| 9 | **demo0** | Demo 0 (Baseline / FastRandom) | 89.64 | ± 5.42 | 1.00x | 41.6 | 114.5 | 2.75 | 0.06% | 7.06% |
| 10 | **demo1** | Demo 1 (Gemini 3.7 Flash High / Antigravity) | 87.56 | ± 2.17 | 0.98x | 42.8 | 105.9 | 2.47 | 0.02% | 13.04% |
| 11 | **demo6** | Demo 6 (Claude Opus 5 Ultra) | 84.79 | ± 3.52 | 0.95x 🔻 | 46.8 | 102.8 | 2.20 | 0.02% | 11.52% |
| 12 | **demo4** | Demo 4 (Gemma 4 31B Thinking) | 56.19 | ± 7.67 | 0.63x 🔻 | 72.2 | 146.5 | 2.03 | 0.04% | 8.02% |

## Operation: `put`

| Rank | Implementation | Model | Throughput (ops/µs) | Margin (±) | Speedup | Cycles/op | Insns/op | IPC | Branch Miss % | L1 D-Cache Miss % |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **demo7** | Demo 7 (Qwen 38 max XHigh) | 100.52 | ± 4.14 | **1.46x 🚀** | 38.5 | 89.9 | 2.34 | 0.37% | 11.54% |
| 2 | **demo5** | Demo 5 (Deepseek V4 Flash Max) | 98.79 | ± 3.70 | **1.43x 🚀** | 38.4 | 98.1 | 2.55 | 0.26% | 9.54% |
| 3 | **demo8** | Demo 8 (Gemini 3.7 Flash High / Kilo Code) | 84.14 | ± 10.25 | **1.22x 🚀** | 45.9 | 118.6 | 2.59 | 0.11% | 11.52% |
| 4 | **demo9** | Demo 9 (Gemini 3.8 Flash High / Antigravity) | 75.71 | ± 2.89 | **1.10x 🚀** | 50.1 | 137.9 | 2.75 | 0.25% | 5.82% |
| 5 | **demo12** | Demo 12 (Gemini 3.8 Flash High / Antigravity Rework) | 72.98 | ± 3.80 | **1.06x 🚀** | 53.0 | 140.1 | 2.64 | 0.26% | 5.77% |
| 6 | **demo11** | Demo 11 (Gemini 3.7 Flash High / Antigravity Rework) | 72.91 | ± 6.27 | **1.06x 🚀** | 53.3 | 140.2 | 2.63 | 0.27% | 5.25% |
| 7 | **demo6** | Demo 6 (Claude Opus 5 Ultra) | 72.84 | ± 50.55 | **1.06x 🚀** | 51.5 | 141.5 | 2.75 | 0.28% | 5.01% |
| 8 | **demo1** | Demo 1 (Gemini 3.7 Flash High / Antigravity) | 72.65 | ± 0.99 | **1.05x 🚀** | 53.2 | 141.3 | 2.66 | 0.23% | 5.92% |
| 9 | **demo2** | Demo 2 (Kimi K3) | 72.59 | ± 1.99 | **1.05x 🚀** | 50.2 | 138.9 | 2.77 | 0.26% | 7.49% |
| 10 | **demo0** | Demo 0 (Baseline / FastRandom) | 69.01 | ± 1.98 | 1.00x | 53.7 | 153.7 | 2.87 | 0.22% | 6.26% |
| 11 | **demo3** | Demo 3 (OpenAI 5.6 Sol Max) | 61.62 | ± 1.88 | 0.89x 🔻 | 57.2 | 158.8 | 2.78 | 0.38% | 5.14% |
| 12 | **demo4** | Demo 4 (Gemma 4 31B Thinking) | 47.61 | ± 1.18 | 0.69x 🔻 | 76.8 | 189.7 | 2.47 | 0.23% | 4.17% |

