# 🏆 AI JUG Saxony Master Executive Dashboard

Comprehensive benchmark, code quality, memory footprint, and mutation evaluation comparing **AI Model implementations** against the `demo0` baseline.

---

## ⚡ Part 1: FastHashMap — Quality, Coverage & Mutation Verification

### 🤖 FastHashMap AI-Generated Test Suites

| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **demo0** | Human Baseline | 29 ✅ (0.21s) | 100.0% (651/651) | 100.0% (130/130) | 100.0% (72/72) | 87.7% (142/162 killed) | 100% Passing ✅ |
| **demo1** | Gemini 3.7 Flash High (Antigravity) | 21 ✅ (0.64s) | 99.0% (520/525) | 99.1% (114/115) | 93.1% (54/58) | 80.6% (100/124 killed) | 100% Passing ✅ |
| **demo2** | Kimi K3 (Kilo Code) | 43 ✅ (0.38s) | 100.0% (415/415) | 100.0% (92/92) | 97.6% (41/42) | 86.7% (72/83 killed) | 100% Passing ✅ |
| **demo3** | OpenAI 5.6 Sol Max (Kilo Code) | 19 ✅ (0.27s) | 97.0% (423/436) | 96.0% (97/101) | 92.1% (35/38) | 88.9% (64/72 killed) | 100% Passing ✅ |
| **demo4** | Gemma 4 31B Thinking (Kilo Code) | 10 ✅ (0.03s) | 95.6% (409/428) | 96.7% (87/90) | 80.0% (40/50) | 78.2% (61/78 killed) | 100% Passing ✅ |
| **demo5** | Deepseek V4 Flash Max (Kilo Code) | 17 ✅ (0.34s) | 96.7% (437/452) | 98.0% (100/102) | 86.5% (45/52) | 79.8% (71/89 killed) | 100% Passing ✅ |
| **demo6** | Claude Opus 5 Ultra (Claude) | 44 ✅ (2.67s) | 87.7% (536/611) | 90.3% (131/145) | 85.3% (58/68) | 74.6% (97/130 killed) | 100% Passing ✅ |
| **demo7** | Qwen 38 max XHigh (Kilo Code) | 19 ✅ (0.11s) | 100.0% (371/371) | 100.0% (88/88) | 100.0% (34/34) | 86.0% (43/50 killed) | 100% Passing ✅ |
| **demo8** | Gemini 3.7 Flash High (Kilo Code) | 28 ✅ (0.34s) | 97.0% (447/461) | 96.4% (107/111) | 93.8% (45/48) | 84.2% (80/95 killed) | 100% Passing ✅ |
| **demo9** | Gemini 3.8 Flash High (Antigravity) | 29 ✅ (1.18s) | 96.4% (502/521) | 96.7% (117/121) | 92.3% (48/52) | 89.4% (101/113 killed) | 100% Passing ✅ |
| **demo11** | Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed) | 24 ✅ (0.23s) | 100.0% (544/544) | 100.0% (123/123) | 100.0% (50/50) | 100.0% (88/88 killed) | 100% Passing ✅ |
| **demo12** | Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed) | 27 ✅ (5.24s) | 99.6% (523/525) | 100.0% (115/115) | 96.6% (56/58) | 95.7% (90/94 killed) | 100% Passing ✅ |

### 🧪 FastHashMap Manual BlackBox — Quality, Coverage & Mutation Verification

| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **demo0** | Human Baseline | 27 ✅ (0.29s) | 98.6% (642/651) | 100.0% (131/131) | 97.2% (70/72) | 82.5% (104/126 killed) | 100% Passing ✅ |
| **demo1** | Gemini 3.7 Flash High (Antigravity) | 26 ✅ (0.23s) | 98.1% (515/525) | 97.4% (112/115) | 89.7% (52/58) | 78.7% (74/94 killed) | 100% Passing ✅ |
| **demo2** | Kimi K3 (Kilo Code) | 22 ✅ (0.30s) | 97.6% (405/415) | 98.9% (91/92) | 85.7% (36/42) | 81.7% (49/60 killed) | 100% Passing ✅ |
| **demo3** | OpenAI 5.6 Sol Max (Kilo Code) | 22 ✅ (0.34s) | 97.0% (423/436) | 96.0% (97/101) | 92.1% (35/38) | 88.7% (47/53 killed) | 100% Passing ✅ |
| **demo4** | Gemma 4 31B Thinking (Kilo Code) | 22 ✅ (0.42s) | 97.9% (419/428) | 98.9% (89/90) | 88.0% (44/50) | 92.0% (46/50 killed) | 100% Passing ✅ |
| **demo5** | Deepseek V4 Flash Max (Kilo Code) | 22 ✅ (0.27s) | 96.7% (437/452) | 98.0% (100/102) | 86.5% (45/52) | 81.8% (54/66 killed) | 100% Passing ✅ |
| **demo6** | Claude Opus 5 Ultra (Claude) | 22 ✅ (0.23s) | 67.6% (413/611) | 70.3% (102/145) | 55.9% (38/68) | 55.6% (50/90 killed) | 100% Passing ✅ |
| **demo7** | Qwen 38 max XHigh (Kilo Code) | 22 ✅ (0.28s) | 98.9% (367/371) | 98.9% (87/88) | 97.1% (33/34) | 75.0% (27/36 killed) | 100% Passing ✅ |
| **demo8** | Gemini 3.7 Flash High (Kilo Code) | 26 ✅ (0.33s) | 95.9% (442/461) | 94.6% (105/111) | 91.7% (44/48) | 77.5% (55/71 killed) | 100% Passing ✅ |
| **demo9** | Gemini 3.8 Flash High (Antigravity) | 26 ✅ (0.35s) | 94.6% (493/521) | 94.2% (114/121) | 90.4% (47/52) | 74.7% (65/87 killed) | 100% Passing ✅ |
| **demo11** | Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed) | 26 ✅ (0.30s) | 95.6% (520/544) | 94.3% (116/123) | 96.0% (48/50) | 76.4% (68/89 killed) | 100% Passing ✅ |
| **demo12** | Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed) | 26 ✅ (0.28s) | 98.1% (514/524) | 97.4% (111/114) | 89.7% (52/58) | 76.8% (73/95 killed) | 100% Passing ✅ |

### 💾 FastHashMap — JOL Memory Footprint & Layout Matrix

| Module | AI Model / Implementation | Memory @ 1k | Bytes / Entry @ 1k | Objs @ 10k | Empty Footprint | Shallow Size |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **demo0** | Human Baseline | 80,456 B | 80.5 B/e | 30,003 | 328 B | 40 B |
| **demo1** | Gemini 3.7 Flash High (Antigravity) | 80,456 B | 80.5 B/e | 30,003 | 200 B | 40 B |
| **demo2** | Kimi K3 (Kilo Code) | 80,448 B | 80.4 B/e | 30,003 | 192 B | 32 B |
| **demo3** | OpenAI 5.6 Sol Max (Kilo Code) | 80,448 B | 80.4 B/e | 30,003 | 192 B | 32 B |
| **demo4** | Gemma 4 31B Thinking (Kilo Code) | 84,584 B | 84.6 B/e | 30,005 | 264 B | 40 B |
| **demo5** | Deepseek V4 Flash Max (Kilo Code) | 96,240 B | 96.2 B/e | 40,002 | 112 B | 32 B |
| **demo6** | Claude Opus 5 Ultra (Claude) | 80,456 B | 80.5 B/e | 30,003 | 200 B | 40 B |
| **demo7** | Qwen 38 max XHigh (Kilo Code) | 104,240 B | 104.2 B/e | 40,002 | 112 B | 32 B |
| **demo8** | Gemini 3.7 Flash High (Kilo Code) | 96,240 B | 96.2 B/e | 40,002 | 112 B | 32 B |
| **demo9** | Gemini 3.8 Flash High (Antigravity) | 80,456 B | 80.5 B/e | 30,003 | 200 B | 40 B |
| **demo11** | Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed) | 80,456 B | 80.5 B/e | 30,003 | 200 B | 40 B |
| **demo12** | Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed) | 80,456 B | 80.5 B/e | 30,003 | 200 B | 40 B |

### 🚀 FastHashMap — Performance & Benchmark Speedup Matrix

| Module | AI Model / Implementation | Put Speedup | Get Hit Speedup | Cycles/op | IPC | Branch Miss % | L1 Miss % |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **demo0** | Human Baseline | 1.00x (52.5 ops/µs) | 1.00x (54.4 ops/µs) | 54.7 | 2.37 | 0.86% | 5.10% |
| **demo1** | Gemini 3.7 Flash High (Antigravity) | 0.97x (51.2 ops/µs) | 1.36x (74.1 ops/µs) | 37.9 | 2.75 | 0.11% | 8.80% |
| **demo2** | Kimi K3 (Kilo Code) | 1.06x (55.8 ops/µs) | 1.37x (74.5 ops/µs) | 37.1 | 2.73 | 0.09% | 9.48% |
| **demo3** | OpenAI 5.6 Sol Max (Kilo Code) | 1.04x (54.7 ops/µs) | 1.56x (85.0 ops/µs) | 36.1 | 2.84 | 0.11% | 8.90% |
| **demo4** | Gemma 4 31B Thinking (Kilo Code) | 0.74x (38.8 ops/µs) | 1.04x (56.6 ops/µs) | 59.0 | 1.94 | 0.14% | 8.93% |
| **demo5** | Deepseek V4 Flash Max (Kilo Code) | 1.17x (61.4 ops/µs) | 1.49x (81.1 ops/µs) | 30.6 | 2.66 | 0.28% | 18.79% |
| **demo6** | Claude Opus 5 Ultra (Claude) | 1.08x (56.8 ops/µs) | 1.54x (83.8 ops/µs) | 33.2 | 2.72 | 0.09% | 8.87% |
| **demo7** | Qwen 38 max XHigh (Kilo Code) | 1.11x (58.1 ops/µs) | 1.91x (103.8 ops/µs) | 30.1 | 2.50 | 0.32% | 18.52% |
| **demo8** | Gemini 3.7 Flash High (Kilo Code) | 0.92x (48.4 ops/µs) | 1.18x (64.3 ops/µs) | 43.3 | 2.35 | 0.10% | 17.07% |
| **demo9** | Gemini 3.8 Flash High (Antigravity) | 0.83x (43.4 ops/µs) | 1.44x (78.6 ops/µs) | 36.1 | 2.83 | 0.09% | 9.06% |
| **demo11** | Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed) | 1.08x (56.6 ops/µs) | 1.58x (86.2 ops/µs) | 35.0 | 2.82 | 0.10% | 7.18% |
| **demo12** | Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed) | 0.97x (51.0 ops/µs) | 1.39x (75.7 ops/µs) | 37.4 | 2.63 | 0.12% | 9.55% |

---

## ⏰ Part 2: LRUClockMap — Quality, Coverage & Mutation Verification

| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **demo0** | Human Baseline | 37 ✅ (0.55s) | 97.3% (681/700) | 98.2% (160/163) | 96.8% (60/62) | 92.6% (126/136 killed) | 100% Passing ✅ |
| **demo1** | Gemini 3.7 Flash High (Antigravity) | 21 ✅ (0.37s) | 97.9% (685/700) | 98.2% (160/163) | 96.8% (60/62) | 86.0% (117/136 killed) | 100% Passing ✅ |
| **demo2** | Kimi K3 (Kilo Code) | 38 ✅ (0.25s) | 96.9% (678/700) | 97.5% (159/163) | 95.2% (59/62) | 90.4% (123/136 killed) | 100% Passing ✅ |
| **demo3** | OpenAI 5.6 Sol Max (Kilo Code) | 25 ✅ (0.50s) | 98.3% (708/720) | 98.8% (166/168) | 98.4% (61/62) | 91.9% (125/136 killed) | 100% Passing ✅ |
| **demo4** | Gemma 4 31B Thinking (Kilo Code) | 11 ✅ (0.04s) | 58.5% (421/720) | 62.5% (105/168) | 50.0% (31/62) | 49.3% (67/136 killed) | 100% Passing ✅ |
| **demo5** | Deepseek V4 Flash Max (Kilo Code) | 26 ✅ (1.60s) | 96.3% (693/720) | 97.0% (163/168) | 95.2% (59/62) | 79.4% (108/136 killed) | 100% Passing ✅ |
| **demo6** | Claude Opus 5 Ultra (Claude) | 65 ✅ (3.75s) | 97.4% (701/720) | 98.2% (165/168) | 96.8% (60/62) | 92.6% (126/136 killed) | 100% Passing ✅ |
| **demo7** | Qwen 38 max XHigh (Kilo Code) | 24 ✅ (0.14s) | 97.4% (701/720) | 98.2% (165/168) | 96.8% (60/62) | 90.4% (123/136 killed) | 100% Passing ✅ |
| **demo8** | Gemini 3.7 Flash High (Kilo Code) | 37 ✅ (0.45s) | 97.4% (701/720) | 98.2% (165/168) | 96.8% (60/62) | 86.0% (117/136 killed) | 100% Passing ✅ |
| **demo9** | Gemini 3.8 Flash High (Antigravity) | 52 ✅ (0.28s) | 100.0% (720/720) | 100.0% (168/168) | 100.0% (62/62) | 98.5% (134/136 killed) | 100% Passing ✅ |
| **demo11** | Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed) | 24 ✅ (0.19s) | 100.0% (683/683) | 100.0% (161/161) | 100.0% (60/60) | 100.0% (102/102 killed) | 100% Passing ✅ |
| **demo12** | Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed) | 29 ✅ (0.28s) | 100.0% (700/700) | 100.0% (163/163) | 100.0% (62/62) | 100.0% (104/104 killed) | 100% Passing ✅ |

---

## 🧰 Part 3: com.xceptance.xlt.report.util — Quality, Coverage & Mutation Verification

| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **demo0** | Human Baseline | 92 ✅ (1.48s) | [58.3% (3635/6230)](jacoco/demo0/xlt-util-coverage.html) | [45.3% (316/698)](jacoco/demo0/xlt-util-coverage.html) | [59.2% (129/218)](jacoco/demo0/xlt-util-coverage.html) | 29.4% (346/1177 killed) | 100% Passing ✅ |
| **demo1** | Gemini 3.7 Flash High (Antigravity) | 103 ✅ (0.60s) | [99.7% (6212/6230)](jacoco/demo1/xlt-util-coverage.html) | [99.1% (692/698)](jacoco/demo1/xlt-util-coverage.html) | [96.8% (211/218)](jacoco/demo1/xlt-util-coverage.html) | 93.5% (1101/1177 killed) | 100% Passing ✅ |
| **demo2** | Kimi K3 (Kilo Code) | 95 ✅ (0.55s) | [57.7% (3592/6230)](jacoco/demo2/xlt-util-coverage.html) | [43.6% (304/698)](jacoco/demo2/xlt-util-coverage.html) | [54.1% (118/218)](jacoco/demo2/xlt-util-coverage.html) | 25.5% (300/1177 killed) | 100% Passing ✅ |
| **demo3** | OpenAI 5.6 Sol Max (Kilo Code) | 40 ✅ (0.47s) | [99.6% (6208/6230)](jacoco/demo3/xlt-util-coverage.html) | [98.9% (690/698)](jacoco/demo3/xlt-util-coverage.html) | [95.9% (209/218)](jacoco/demo3/xlt-util-coverage.html) | 96.5% (1136/1177 killed) | 100% Passing ✅ |
| **demo4** | Gemma 4 31B Thinking (Kilo Code) | 15 ✅ (0.22s) | [52.2% (3252/6230)](jacoco/demo4/xlt-util-coverage.html) | [33.4% (233/698)](jacoco/demo4/xlt-util-coverage.html) | [36.2% (79/218)](jacoco/demo4/xlt-util-coverage.html) | 13.2% (155/1177 killed) | 100% Passing ✅ |
| **demo5** | Deepseek V4 Flash Max (Kilo Code) | 71 ✅ (1.39s) | [97.9% (6097/6230)](jacoco/demo5/xlt-util-coverage.html) | [97.3% (679/698)](jacoco/demo5/xlt-util-coverage.html) | [87.6% (191/218)](jacoco/demo5/xlt-util-coverage.html) | 91.2% (1073/1177 killed) | 100% Passing ✅ |
| **demo6** | Claude Opus 5 Ultra (Claude) | 203 ✅ (3.34s) | [100.0% (6230/6230)](jacoco/demo6/xlt-util-coverage.html) | [100.0% (698/698)](jacoco/demo6/xlt-util-coverage.html) | [100.0% (218/218)](jacoco/demo6/xlt-util-coverage.html) | 98.6% (1160/1177 killed) | 100% Passing ✅ |
| **demo7** | Qwen 38 max XHigh (Kilo Code) | 106 ✅ (0.91s) | [100.0% (6228/6230)](jacoco/demo7/xlt-util-coverage.html) | [99.9% (697/698)](jacoco/demo7/xlt-util-coverage.html) | [99.5% (217/218)](jacoco/demo7/xlt-util-coverage.html) | 96.5% (1136/1177 killed) | 100% Passing ✅ |
| **demo8** | Gemini 3.7 Flash High (Kilo Code) | 45 ✅ (0.36s) | [99.8% (6220/6230)](jacoco/demo8/xlt-util-coverage.html) | [99.3% (693/698)](jacoco/demo8/xlt-util-coverage.html) | [97.2% (212/218)](jacoco/demo8/xlt-util-coverage.html) | 92.3% (1086/1177 killed) | 100% Passing ✅ |
| **demo9** | Gemini 3.8 Flash High (Antigravity) | 86 ✅ (1.61s) | [99.7% (6214/6230)](jacoco/demo9/xlt-util-coverage.html) | [98.9% (690/698)](jacoco/demo9/xlt-util-coverage.html) | [95.9% (209/218)](jacoco/demo9/xlt-util-coverage.html) | 94.1% (1108/1177 killed) | 100% Passing ✅ |
| **demo11** | Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed) | 113 ✅ (6.98s) | [100.0% (6230/6230)](jacoco/demo11/xlt-util-coverage.html) | [100.0% (698/698)](jacoco/demo11/xlt-util-coverage.html) | [100.0% (218/218)](jacoco/demo11/xlt-util-coverage.html) | 99.1% (1046/1056 killed) | 100% Passing ✅ |
| **demo12** | Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed) | 117 ✅ (2.07s) | [100.0% (6230/6230)](jacoco/demo12/xlt-util-coverage.html) | [100.0% (698/698)](jacoco/demo12/xlt-util-coverage.html) | [100.0% (218/218)](jacoco/demo12/xlt-util-coverage.html) | 99.1% (1046/1056 killed) | 100% Passing ✅ |

### 📊 com.xceptance.xlt.report.util — Class-Level Instruction Coverage Matrix

| Module | AI Model / Implementation | RuntimeHistogram | BitUtil | BitCompression | IntTimeSeries | IntTimeSeriesEntry | Total Suite |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **demo0** | Human Baseline | 98.3% | 44.8% | 100.0% | 98.5% | 99.6% | **58.3%** |
| **demo1** | Gemini 3.7 Flash High (Antigravity) | 100.0% | 100.0% | 100.0% | 98.7% | 98.2% | **99.7%** |
| **demo2** | Kimi K3 (Kilo Code) | 100.0% | 44.8% | 100.0% | 98.7% | 90.4% | **57.7%** |
| **demo3** | OpenAI 5.6 Sol Max (Kilo Code) | 100.0% | 100.0% | 100.0% | 98.7% | 97.4% | **99.6%** |
| **demo4** | Gemma 4 31B Thinking (Kilo Code) | 76.0% | 47.9% | 100.0% | 53.9% | 66.5% | **52.2%** |
| **demo5** | Deepseek V4 Flash Max (Kilo Code) | 100.0% | 97.7% | 100.0% | 98.7% | 96.7% | **97.9%** |
| **demo6** | Claude Opus 5 Ultra (Claude) | 100.0% | 100.0% | 100.0% | 100.0% | 100.0% | **100.0%** |
| **demo7** | Qwen 38 max XHigh (Kilo Code) | 100.0% | 100.0% | 100.0% | 100.0% | 99.6% | **100.0%** |
| **demo8** | Gemini 3.7 Flash High (Kilo Code) | 100.0% | 100.0% | 100.0% | 100.0% | 98.2% | **99.8%** |
| **demo9** | Gemini 3.8 Flash High (Antigravity) | 100.0% | 100.0% | 100.0% | 100.0% | 97.0% | **99.7%** |
| **demo11** | Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed) | 100.0% | 100.0% | 100.0% | 100.0% | 100.0% | **100.0%** |
| **demo12** | Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed) | 100.0% | 100.0% | 100.0% | 100.0% | 100.0% | **100.0%** |

---

## 🧮 Part 4: SimpleMath & SimpleMathClean — Quality, Coverage & Mutation Verification

### 📐 SimpleMath Test Suites (SimpleMathTest)

| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **demo0** | Human Baseline | 32 ✅ (0.30s) | [93.3% (42/45)](jacoco-simplemath/demo0/org.jugsaxony.demo0/SimpleMath.java.html) | [85.7% (6/7)](jacoco-simplemath/demo0/org.jugsaxony.demo0/SimpleMath.java.html) | [100.0% (6/6)](jacoco-simplemath/demo0/org.jugsaxony.demo0/SimpleMath.java.html) | 75.0% (6/8 killed) | 100% Passing ✅ |
| **demo1** | Gemini 3.7 Flash High (Antigravity) | 61/68 ✅ (1.15s) | [100.0% (45/45)](jacoco-simplemath/demo1/org.jugsaxony.demo1/SimpleMath.java.html) | [100.0% (7/7)](jacoco-simplemath/demo1/org.jugsaxony.demo1/SimpleMath.java.html) | [100.0% (6/6)](jacoco-simplemath/demo1/org.jugsaxony.demo1/SimpleMath.java.html) | 75.0% (6/8 killed) | ⚠ 7 Failed |
| **demo2** | Kimi K3 (Kilo Code) | 66/73 ✅ (1.02s) | [100.0% (45/45)](jacoco-simplemath/demo2/org.jugsaxony.demo2/SimpleMath.java.html) | [100.0% (7/7)](jacoco-simplemath/demo2/org.jugsaxony.demo2/SimpleMath.java.html) | [100.0% (6/6)](jacoco-simplemath/demo2/org.jugsaxony.demo2/SimpleMath.java.html) | 75.0% (6/8 killed) | ⚠ 7 Failed |
| **demo3** | OpenAI 5.6 Sol Max (Kilo Code) | 63/71 ✅ (0.40s) | [100.0% (45/45)](jacoco-simplemath/demo3/org.jugsaxony.demo3/SimpleMath.java.html) | [100.0% (7/7)](jacoco-simplemath/demo3/org.jugsaxony.demo3/SimpleMath.java.html) | [100.0% (6/6)](jacoco-simplemath/demo3/org.jugsaxony.demo3/SimpleMath.java.html) | 75.0% (6/8 killed) | ⚠ 8 Failed |
| **demo4** | Gemma 4 31B Thinking (Kilo Code) | 17 ✅ (0.25s) | [93.3% (42/45)](jacoco-simplemath/demo4/org.jugsaxony.demo4/SimpleMath.java.html) | [85.7% (6/7)](jacoco-simplemath/demo4/org.jugsaxony.demo4/SimpleMath.java.html) | [100.0% (6/6)](jacoco-simplemath/demo4/org.jugsaxony.demo4/SimpleMath.java.html) | 75.0% (6/8 killed) | 100% Passing ✅ |
| **demo5** | Deepseek V4 Flash Max (Kilo Code) | 73/81 ✅ (1.13s) | [100.0% (45/45)](jacoco-simplemath/demo5/org.jugsaxony.demo5/SimpleMath.java.html) | [100.0% (7/7)](jacoco-simplemath/demo5/org.jugsaxony.demo5/SimpleMath.java.html) | [100.0% (6/6)](jacoco-simplemath/demo5/org.jugsaxony.demo5/SimpleMath.java.html) | 75.0% (6/8 killed) | ⚠ 8 Failed |
| **demo6** | Claude Opus 5 Ultra (Claude) | 134/153 ✅ (1.47s) | [93.3% (42/45)](jacoco-simplemath/demo6/org.jugsaxony.demo6/SimpleMath.java.html) | [85.7% (6/7)](jacoco-simplemath/demo6/org.jugsaxony.demo6/SimpleMath.java.html) | [100.0% (6/6)](jacoco-simplemath/demo6/org.jugsaxony.demo6/SimpleMath.java.html) | 75.0% (6/8 killed) | ⚠ 19 Failed |
| **demo7** | Qwen 38 max XHigh (Kilo Code) | 33/37 ✅ (0.25s) | [93.3% (42/45)](jacoco-simplemath/demo7/org.jugsaxony.demo7/SimpleMath.java.html) | [85.7% (6/7)](jacoco-simplemath/demo7/org.jugsaxony.demo7/SimpleMath.java.html) | [100.0% (6/6)](jacoco-simplemath/demo7/org.jugsaxony.demo7/SimpleMath.java.html) | 75.0% (6/8 killed) | ⚠ 4 Failed |
| **demo8** | Gemini 3.7 Flash High (Kilo Code) | 56/59 ✅ (1.13s) | [100.0% (45/45)](jacoco-simplemath/demo8/org.jugsaxony.demo8/SimpleMath.java.html) | [100.0% (7/7)](jacoco-simplemath/demo8/org.jugsaxony.demo8/SimpleMath.java.html) | [100.0% (6/6)](jacoco-simplemath/demo8/org.jugsaxony.demo8/SimpleMath.java.html) | 75.0% (6/8 killed) | ⚠ 3 Failed |
| **demo9** | Gemini 3.8 Flash High (Antigravity) | 60/66 ✅ (1.27s) | [100.0% (45/45)](jacoco-simplemath/demo9/org.jugsaxony.demo9/SimpleMath.java.html) | [100.0% (7/7)](jacoco-simplemath/demo9/org.jugsaxony.demo9/SimpleMath.java.html) | [100.0% (6/6)](jacoco-simplemath/demo9/org.jugsaxony.demo9/SimpleMath.java.html) | 75.0% (6/8 killed) | ⚠ 6 Failed |
| **demo11** | Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed) | - | - | - | - | N/A | No Tests |
| **demo12** | Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed) | - | - | - | - | N/A | No Tests |

### 🧼 SimpleMathClean Test Suites (SimpleMathCleanTest)

| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **demo0** | Human Baseline | 32 ✅ (0.30s) | [93.3% (42/45)](jacoco-simplemath/demo0/org.jugsaxony.demo0/SimpleMathClean.java.html) | [85.7% (6/7)](jacoco-simplemath/demo0/org.jugsaxony.demo0/SimpleMathClean.java.html) | [100.0% (6/6)](jacoco-simplemath/demo0/org.jugsaxony.demo0/SimpleMathClean.java.html) | 75.0% (6/8 killed) | 100% Passing ✅ |
| **demo1** | Gemini 3.7 Flash High (Antigravity) | 61/68 ✅ (1.18s) | [100.0% (45/45)](jacoco-simplemath/demo1/org.jugsaxony.demo1/SimpleMathClean.java.html) | [100.0% (7/7)](jacoco-simplemath/demo1/org.jugsaxony.demo1/SimpleMathClean.java.html) | [100.0% (6/6)](jacoco-simplemath/demo1/org.jugsaxony.demo1/SimpleMathClean.java.html) | 75.0% (6/8 killed) | ⚠ 7 Failed |
| **demo2** | Kimi K3 (Kilo Code) | 66/73 ✅ (1.35s) | [100.0% (45/45)](jacoco-simplemath/demo2/org.jugsaxony.demo2/SimpleMathClean.java.html) | [100.0% (7/7)](jacoco-simplemath/demo2/org.jugsaxony.demo2/SimpleMathClean.java.html) | [100.0% (6/6)](jacoco-simplemath/demo2/org.jugsaxony.demo2/SimpleMathClean.java.html) | 75.0% (6/8 killed) | ⚠ 7 Failed |
| **demo3** | OpenAI 5.6 Sol Max (Kilo Code) | 63/71 ✅ (0.39s) | [100.0% (45/45)](jacoco-simplemath/demo3/org.jugsaxony.demo3/SimpleMathClean.java.html) | [100.0% (7/7)](jacoco-simplemath/demo3/org.jugsaxony.demo3/SimpleMathClean.java.html) | [100.0% (6/6)](jacoco-simplemath/demo3/org.jugsaxony.demo3/SimpleMathClean.java.html) | 75.0% (6/8 killed) | ⚠ 8 Failed |
| **demo4** | Gemma 4 31B Thinking (Kilo Code) | 17 ✅ (0.35s) | [93.3% (42/45)](jacoco-simplemath/demo4/org.jugsaxony.demo4/SimpleMathClean.java.html) | [85.7% (6/7)](jacoco-simplemath/demo4/org.jugsaxony.demo4/SimpleMathClean.java.html) | [100.0% (6/6)](jacoco-simplemath/demo4/org.jugsaxony.demo4/SimpleMathClean.java.html) | 75.0% (6/8 killed) | 100% Passing ✅ |
| **demo5** | Deepseek V4 Flash Max (Kilo Code) | 73/81 ✅ (1.40s) | [100.0% (45/45)](jacoco-simplemath/demo5/org.jugsaxony.demo5/SimpleMathClean.java.html) | [100.0% (7/7)](jacoco-simplemath/demo5/org.jugsaxony.demo5/SimpleMathClean.java.html) | [100.0% (6/6)](jacoco-simplemath/demo5/org.jugsaxony.demo5/SimpleMathClean.java.html) | 75.0% (6/8 killed) | ⚠ 8 Failed |
| **demo6** | Claude Opus 5 Ultra (Claude) | 134/153 ✅ (1.64s) | [93.3% (42/45)](jacoco-simplemath/demo6/org.jugsaxony.demo6/SimpleMathClean.java.html) | [85.7% (6/7)](jacoco-simplemath/demo6/org.jugsaxony.demo6/SimpleMathClean.java.html) | [100.0% (6/6)](jacoco-simplemath/demo6/org.jugsaxony.demo6/SimpleMathClean.java.html) | 75.0% (6/8 killed) | ⚠ 19 Failed |
| **demo7** | Qwen 38 max XHigh (Kilo Code) | 33/37 ✅ (0.22s) | [93.3% (42/45)](jacoco-simplemath/demo7/org.jugsaxony.demo7/SimpleMathClean.java.html) | [85.7% (6/7)](jacoco-simplemath/demo7/org.jugsaxony.demo7/SimpleMathClean.java.html) | [100.0% (6/6)](jacoco-simplemath/demo7/org.jugsaxony.demo7/SimpleMathClean.java.html) | 75.0% (6/8 killed) | ⚠ 4 Failed |
| **demo8** | Gemini 3.7 Flash High (Kilo Code) | 56/59 ✅ (1.29s) | [100.0% (45/45)](jacoco-simplemath/demo8/org.jugsaxony.demo8/SimpleMathClean.java.html) | [100.0% (7/7)](jacoco-simplemath/demo8/org.jugsaxony.demo8/SimpleMathClean.java.html) | [100.0% (6/6)](jacoco-simplemath/demo8/org.jugsaxony.demo8/SimpleMathClean.java.html) | 75.0% (6/8 killed) | ⚠ 3 Failed |
| **demo9** | Gemini 3.8 Flash High (Antigravity) | 60/66 ✅ (1.53s) | [100.0% (45/45)](jacoco-simplemath/demo9/org.jugsaxony.demo9/SimpleMathClean.java.html) | [100.0% (7/7)](jacoco-simplemath/demo9/org.jugsaxony.demo9/SimpleMathClean.java.html) | [100.0% (6/6)](jacoco-simplemath/demo9/org.jugsaxony.demo9/SimpleMathClean.java.html) | 75.0% (6/8 killed) | ⚠ 6 Failed |
| **demo11** | Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed) | - | - | - | - | N/A | No Tests |
| **demo12** | Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed) | - | - | - | - | N/A | No Tests |

---

## 📑 Linked Detailed Reports

- 🧪 **Unit Tests**: [Surefire Aggregated Report](surefire.html) (100% passing tests)
- 🎯 **Code Coverage**: [JaCoCo Aggregate Coverage Report](coverage-aggregate/index.html)
- 🧬 **Mutation Testing**: [PIT Mutation Reports](pit-reports/index.html)
- 💾 **Memory Footprint & Layout**: [JOL Memory Report](jol-report.html) / [Markdown](jol-report.md)
- ⚡ **Microbenchmarks & Perf Counters**: [JMH Benchmark Report](jmh-report.html) / [Markdown](jmh-report.md)
- 🧮 **SimpleMath Evaluation**: [SimpleMath Report](simplemath.html)

Generated automatically by `GlobalDashboardGenerator` on 2026-09-23T15:01:00.154343530Z
