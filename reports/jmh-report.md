# JMH Microbenchmark Cross-Project Comparison Report

Microbenchmark results comparing all AI model implementations against `demo0` baseline (Size = 1,000 items).

> ⚡ **Hardware Performance Counters Enabled**: Includes Linux `perf` metrics (Cycles/op, Instructions/op, IPC, Branch Mispredictions, L1 D-Cache Misses).

## Operation: `getHit`

| Rank | Implementation | Model | Throughput (ops/µs) | Margin (±) | Speedup | Cycles/op | Insns/op | IPC | Branch Miss % | L1 D-Cache Miss % |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **demo7** | Demo 7 (Qwen 38 max XHigh) | 103.81 | ± 0.00 | **1.91x 🚀** | 30.1 | 75.3 | 2.50 | 0.32% | 18.52% |
| 2 | **demo11** | Demo 11 (Gemini 3.7 Flash High / Antigravity Rework) | 86.22 | ± 0.00 | **1.58x 🚀** | 35.0 | 98.7 | 2.82 | 0.10% | 7.18% |
| 3 | **demo3** | Demo 3 (OpenAI 5.6 Sol Max) | 84.95 | ± 0.00 | **1.56x 🚀** | 36.1 | 102.5 | 2.84 | 0.11% | 8.90% |
| 4 | **demo6** | Demo 6 (Claude Opus 5 Ultra) | 83.77 | ± 0.00 | **1.54x 🚀** | 33.2 | 90.1 | 2.72 | 0.09% | 8.87% |
| 5 | **demo5** | Demo 5 (Deepseek V4 Flash Max) | 81.12 | ± 0.00 | **1.49x 🚀** | 30.6 | 81.3 | 2.66 | 0.28% | 18.79% |
| 6 | **demo9** | Demo 9 (Gemini 3.8 Flash High / Antigravity) | 78.61 | ± 0.00 | **1.44x 🚀** | 36.1 | 102.0 | 2.83 | 0.09% | 9.06% |
| 7 | **demo12** | Demo 12 (Gemini 3.8 Flash High / Antigravity Rework) | 75.74 | ± 0.00 | **1.39x 🚀** | 37.4 | 98.4 | 2.63 | 0.12% | 9.55% |
| 8 | **demo2** | Demo 2 (Kimi K3) | 74.52 | ± 0.00 | **1.37x 🚀** | 37.1 | 101.3 | 2.73 | 0.09% | 9.48% |
| 9 | **demo1** | Demo 1 (Gemini 3.7 Flash High / Antigravity) | 74.08 | ± 0.00 | **1.36x 🚀** | 37.9 | 104.2 | 2.75 | 0.11% | 8.80% |
| 10 | **demo8** | Demo 8 (Gemini 3.7 Flash High / Kilo Code) | 64.30 | ± 0.00 | **1.18x 🚀** | 43.3 | 101.7 | 2.35 | 0.10% | 17.07% |
| 11 | **demo4** | Demo 4 (Gemma 4 31B Thinking) | 56.60 | ± 0.00 | 1.04x | 59.0 | 114.2 | 1.94 | 0.14% | 8.93% |
| 12 | **demo0** | Demo 0 (Human Baseline) | 54.41 | ± 0.00 | 1.00x | 54.7 | 129.6 | 2.37 | 0.86% | 5.10% |

## Operation: `getMiss`

| Rank | Implementation | Model | Throughput (ops/µs) | Margin (±) | Speedup | Cycles/op | Insns/op | IPC | Branch Miss % | L1 D-Cache Miss % |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **demo7** | Demo 7 (Qwen 38 max XHigh) | 138.58 | ± 0.00 | **1.88x 🚀** | 23.2 | 51.4 | 2.22 | 0.03% | 12.40% |
| 2 | **demo5** | Demo 5 (Deepseek V4 Flash Max) | 117.55 | ± 0.00 | **1.60x 🚀** | 26.7 | 72.9 | 2.73 | 0.02% | 14.96% |
| 3 | **demo9** | Demo 9 (Gemini 3.8 Flash High / Antigravity) | 78.89 | ± 0.00 | **1.07x 🚀** | 37.7 | 102.9 | 2.73 | 0.03% | 12.81% |
| 4 | **demo1** | Demo 1 (Gemini 3.7 Flash High / Antigravity) | 76.28 | ± 0.00 | 1.04x | 41.0 | 113.9 | 2.78 | 0.03% | 12.74% |
| 5 | **demo6** | Demo 6 (Claude Opus 5 Ultra) | 74.51 | ± 0.00 | 1.01x | 38.9 | 109.0 | 2.80 | 0.02% | 12.69% |
| 6 | **demo0** | Demo 0 (Human Baseline) | 73.55 | ± 0.00 | 1.00x | 42.2 | 119.6 | 2.83 | 0.18% | 8.57% |
| 7 | **demo2** | Demo 2 (Kimi K3) | 73.22 | ± 0.00 | 1.00x | 40.9 | 111.2 | 2.72 | 0.03% | 12.89% |
| 8 | **demo11** | Demo 11 (Gemini 3.7 Flash High / Antigravity Rework) | 71.68 | ± 0.00 | 0.97x | 42.6 | 114.3 | 2.68 | 0.03% | 13.21% |
| 9 | **demo8** | Demo 8 (Gemini 3.7 Flash High / Kilo Code) | 70.97 | ± 0.00 | 0.96x | 43.6 | 107.9 | 2.47 | 0.03% | 15.65% |
| 10 | **demo12** | Demo 12 (Gemini 3.8 Flash High / Antigravity Rework) | 70.88 | ± 0.00 | 0.96x | 43.9 | 120.3 | 2.74 | 0.02% | 13.53% |
| 11 | **demo3** | Demo 3 (OpenAI 5.6 Sol Max) | 64.23 | ± 0.00 | 0.87x 🔻 | 39.1 | 101.0 | 2.58 | 0.03% | 12.77% |
| 12 | **demo4** | Demo 4 (Gemma 4 31B Thinking) | 39.11 | ± 0.00 | 0.53x 🔻 | 75.5 | 168.3 | 2.23 | 0.13% | 6.33% |

## Operation: `put`

| Rank | Implementation | Model | Throughput (ops/µs) | Margin (±) | Speedup | Cycles/op | Insns/op | IPC | Branch Miss % | L1 D-Cache Miss % |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **demo5** | Demo 5 (Deepseek V4 Flash Max) | 61.44 | ± 0.00 | **1.17x 🚀** | 49.9 | 126.4 | 2.54 | 0.44% | 9.20% |
| 2 | **demo7** | Demo 7 (Qwen 38 max XHigh) | 58.14 | ± 0.00 | **1.11x 🚀** | 52.8 | 120.8 | 2.29 | 0.68% | 9.30% |
| 3 | **demo6** | Demo 6 (Claude Opus 5 Ultra) | 56.80 | ± 0.00 | **1.08x 🚀** | 54.1 | 156.8 | 2.90 | 0.30% | 4.80% |
| 4 | **demo11** | Demo 11 (Gemini 3.7 Flash High / Antigravity Rework) | 56.64 | ± 0.00 | **1.08x 🚀** | 54.0 | 149.7 | 2.77 | 0.25% | 5.21% |
| 5 | **demo2** | Demo 2 (Kimi K3) | 55.82 | ± 0.00 | **1.06x 🚀** | 53.2 | 152.1 | 2.86 | 0.21% | 6.10% |
| 6 | **demo3** | Demo 3 (OpenAI 5.6 Sol Max) | 54.73 | ± 0.00 | 1.04x | 54.4 | 158.1 | 2.91 | 0.21% | 5.61% |
| 7 | **demo0** | Demo 0 (Human Baseline) | 52.53 | ± 0.00 | 1.00x | 54.8 | 162.5 | 2.97 | 0.14% | 5.42% |
| 8 | **demo1** | Demo 1 (Gemini 3.7 Flash High / Antigravity) | 51.18 | ± 0.00 | 0.97x | 56.6 | 146.6 | 2.59 | 0.29% | 5.79% |
| 9 | **demo12** | Demo 12 (Gemini 3.8 Flash High / Antigravity Rework) | 50.96 | ± 0.00 | 0.97x | 54.4 | 138.9 | 2.55 | 0.28% | 5.19% |
| 10 | **demo8** | Demo 8 (Gemini 3.7 Flash High / Kilo Code) | 48.44 | ± 0.00 | 0.92x 🔻 | 57.5 | 148.8 | 2.59 | 0.29% | 9.26% |
| 11 | **demo9** | Demo 9 (Gemini 3.8 Flash High / Antigravity) | 43.42 | ± 0.00 | 0.83x 🔻 | 56.4 | 144.3 | 2.56 | 0.24% | 5.68% |
| 12 | **demo4** | Demo 4 (Gemma 4 31B Thinking) | 38.76 | ± 0.00 | 0.74x 🔻 | 77.8 | 183.5 | 2.36 | 0.36% | 4.93% |

