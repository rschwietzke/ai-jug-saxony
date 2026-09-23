# Java Object Layout (JOL) Cross-Project Memory Footprint Report

Comprehensive memory layout and footprint analysis comparing all FastHashMap implementations.

## 1. Footprint & Efficiency Comparison

| Implementation | Model | Shallow Size | Empty (B) | N=100 (B) | N=100 (B/entry) | N=1,000 (B) | N=1,000 (B/entry) | N=10,000 (B) | N=10,000 (B/entry) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Demo 0** | Human Baseline | 40 B | 328 B | 8,520 B | 85.2 | 80,456 B | 80.5 | 902,216 B | 90.2 |
| **Demo 1** | Gemini 3.7 Flash High (Antigravity) | 40 B | 200 B | 8,520 B | 85.2 | 80,456 B | 80.5 | 771,144 B | 77.1 |
| **Demo 2** | Kimi K3 (Kilo Code) | 32 B | 192 B | 8,512 B | 85.1 | 80,448 B | 80.4 | 771,136 B | 77.1 |
| **Demo 3** | OpenAI 5.6 Sol Max (Kilo Code) | 32 B | 192 B | 8,512 B | 85.1 | 80,448 B | 80.4 | 771,136 B | 77.1 |
| **Demo 4** | Gemma 4 31B Thinking (Kilo Code) | 40 B | 264 B | 9,064 B | 90.6 | 84,584 B | 84.6 | 803,944 B | 80.4 |
| **Demo 5** | Deepseek V4 Flash Max (Kilo Code) | 32 B | 112 B | 9,872 B | 98.7 | 96,240 B | 96.2 | 945,584 B | 94.6 |
| **Demo 6** | Claude Opus 5 Ultra (Claude) | 40 B | 200 B | 8,520 B | 85.2 | 80,456 B | 80.5 | 902,216 B | 90.2 |
| **Demo 7** | Qwen 38 max XHigh (Kilo Code) | 32 B | 112 B | 10,672 B | 106.7 | 104,240 B | 104.2 | 1,025,584 B | 102.6 |
| **Demo 8** | Gemini 3.7 Flash High (Kilo Code) | 32 B | 112 B | 9,872 B | 98.7 | 96,240 B | 96.2 | 945,584 B | 94.6 |
| **Demo 9** | Gemini 3.8 Flash High (Antigravity) | 40 B | 200 B | 8,520 B | 85.2 | 80,456 B | 80.5 | 902,216 B | 90.2 |
| **Demo 11** | Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed) | 40 B | 200 B | 8,520 B | 85.2 | 80,456 B | 80.5 | 771,144 B | 77.1 |
| **Demo 12** | Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed) | 40 B | 200 B | 8,520 B | 85.2 | 80,456 B | 80.5 | 771,144 B | 77.1 |

## 2. Total Internal Object Count (GC Pressure)

| Implementation | Model | Empty Objects | Objects @ N=100 | Objects @ N=1,000 | Objects @ N=10,000 | Memory Strategy |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Demo 0** | Human Baseline | 3 | 303 | 3,003 | 30,003 | Node/Entry Objects |
| **Demo 1** | Gemini 3.7 Flash High (Antigravity) | 3 | 303 | 3,003 | 30,003 | Node/Entry Objects |
| **Demo 2** | Kimi K3 (Kilo Code) | 3 | 303 | 3,003 | 30,003 | Node/Entry Objects |
| **Demo 3** | OpenAI 5.6 Sol Max (Kilo Code) | 3 | 303 | 3,003 | 30,003 | Node/Entry Objects |
| **Demo 4** | Gemma 4 31B Thinking (Kilo Code) | 5 | 305 | 3,005 | 30,005 | Node/Entry Objects |
| **Demo 5** | Deepseek V4 Flash Max (Kilo Code) | 2 | 402 | 4,002 | 40,002 | Node/Entry Objects |
| **Demo 6** | Claude Opus 5 Ultra (Claude) | 3 | 303 | 3,003 | 30,003 | Node/Entry Objects |
| **Demo 7** | Qwen 38 max XHigh (Kilo Code) | 2 | 402 | 4,002 | 40,002 | Node/Entry Objects |
| **Demo 8** | Gemini 3.7 Flash High (Kilo Code) | 2 | 402 | 4,002 | 40,002 | Node/Entry Objects |
| **Demo 9** | Gemini 3.8 Flash High (Antigravity) | 3 | 303 | 3,003 | 30,003 | Node/Entry Objects |
| **Demo 11** | Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed) | 3 | 303 | 3,003 | 30,003 | Node/Entry Objects |
| **Demo 12** | Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed) | 3 | 303 | 3,003 | 30,003 | Node/Entry Objects |

## 3. Class Layout Details

### Demo 0 (Human Baseline)
```
org.jugsaxony.demo0.FastHashMap object internals:
OFF  SZ                 TYPE DESCRIPTION                VALUE
  0   8                      (object header: mark)      N/A
  8   4                      (object header: class)     N/A
 12   4                float FastHashMap.m_fillFactor   N/A
 16   4                  int FastHashMap.m_threshold    N/A
 20   4                  int FastHashMap.m_size         N/A
 24   4                  int FastHashMap.m_mask         N/A
 28   4                  int FastHashMap.m_mask2        N/A
 32   4   java.lang.Object[] FastHashMap.m_data         N/A
 36   4                      (object alignment gap)     
Instance size: 40 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total
```

### Demo 1 (Gemini 3.7 Flash High (Antigravity))
```
org.jugsaxony.demo1.FastHashMap object internals:
OFF  SZ                 TYPE DESCRIPTION               VALUE
  0   8                      (object header: mark)     N/A
  8   4                      (object header: class)    N/A
 12   4                  int FastHashMap.size          N/A
 16   4                  int FastHashMap.threshold     N/A
 20   4                  int FastHashMap.mask          N/A
 24   4                float FastHashMap.loadFactor    N/A
 28   4   java.lang.Object[] FastHashMap.keys          N/A
 32   4   java.lang.Object[] FastHashMap.values        N/A
 36   4                      (object alignment gap)    
Instance size: 40 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total
```

### Demo 2 (Kimi K3 (Kilo Code))
```
org.jugsaxony.demo2.FastHashMap object internals:
OFF  SZ                 TYPE DESCRIPTION               VALUE
  0   8                      (object header: mark)     N/A
  8   4                      (object header: class)    N/A
 12   4                  int FastHashMap.size          N/A
 16   4                  int FastHashMap.mask          N/A
 20   4   java.lang.Object[] FastHashMap.keys          N/A
 24   4   java.lang.Object[] FastHashMap.values        N/A
 28   4                      (object alignment gap)    
Instance size: 32 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total
```

### Demo 3 (OpenAI 5.6 Sol Max (Kilo Code))
```
org.jugsaxony.demo3.FastHashMap object internals:
OFF  SZ                 TYPE DESCRIPTION                   VALUE
  0   8                      (object header: mark)         N/A
  8   4                      (object header: class)        N/A
 12   4                  int FastHashMap.size              N/A
 16   4                  int FastHashMap.mask              N/A
 20   4                  int FastHashMap.resizeThreshold   N/A
 24   4   java.lang.Object[] FastHashMap.keys              N/A
 28   4   java.lang.Object[] FastHashMap.values            N/A
Instance size: 32 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total
```

### Demo 4 (Gemma 4 31B Thinking (Kilo Code))
```
org.jugsaxony.demo4.FastHashMap object internals:
OFF  SZ                 TYPE DESCRIPTION               VALUE
  0   8                      (object header: mark)     N/A
  8   4                      (object header: class)    N/A
 12   4                  int FastHashMap.size          N/A
 16   4                  int FastHashMap.capacity      N/A
 20   4   java.lang.Object[] FastHashMap.keys          N/A
 24   4   java.lang.Object[] FastHashMap.values        N/A
 28   4            boolean[] FastHashMap.occupied      N/A
 32   4            boolean[] FastHashMap.deleted       N/A
 36   4                      (object alignment gap)    
Instance size: 40 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total
```

### Demo 5 (Deepseek V4 Flash Max (Kilo Code))
```
org.jugsaxony.demo5.FastHashMap object internals:
OFF  SZ                                      TYPE DESCRIPTION               VALUE
  0   8                                           (object header: mark)     N/A
  8   4                                           (object header: class)    N/A
 12   4                                       int FastHashMap.size          N/A
 16   4                                       int FastHashMap.capacity      N/A
 20   4                                       int FastHashMap.mask          N/A
 24   4                                       int FastHashMap.threshold     N/A
 28   4   org.jugsaxony.demo5.FastHashMap.Entry[] FastHashMap.data          N/A
Instance size: 32 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total
```

### Demo 6 (Claude Opus 5 Ultra (Claude))
```
org.jugsaxony.demo6.FastHashMap object internals:
OFF  SZ                 TYPE DESCRIPTION               VALUE
  0   8                      (object header: mark)     N/A
  8   4                      (object header: class)    N/A
 12   4                  int FastHashMap.size          N/A
 16   4                  int FastHashMap.mask          N/A
 20   4                  int FastHashMap.shift         N/A
 24   4                  int FastHashMap.threshold     N/A
 28   4   java.lang.Object[] FastHashMap.keys          N/A
 32   4   java.lang.Object[] FastHashMap.values        N/A
 36   4                      (object alignment gap)    
Instance size: 40 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total
```

### Demo 7 (Qwen 38 max XHigh (Kilo Code))
```
org.jugsaxony.demo7.FastHashMap object internals:
OFF  SZ                                      TYPE DESCRIPTION                   VALUE
  0   8                                           (object header: mark)         N/A
  8   4                                           (object header: class)        N/A
 12   4                                       int FastHashMap.mask              N/A
 16   4                                       int FastHashMap.resizeThreshold   N/A
 20   4                                       int FastHashMap.size              N/A
 24   4   org.jugsaxony.demo7.FastHashMap.Entry[] FastHashMap.table             N/A
 28   4                                           (object alignment gap)        
Instance size: 32 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total
```

### Demo 8 (Gemini 3.7 Flash High (Kilo Code))
```
org.jugsaxony.demo8.FastHashMap object internals:
OFF  SZ                                      TYPE DESCRIPTION               VALUE
  0   8                                           (object header: mark)     N/A
  8   4                                           (object header: class)    N/A
 12   4                                       int FastHashMap.size          N/A
 16   4                                       int FastHashMap.mask          N/A
 20   4                                     float FastHashMap.loadFactor    N/A
 24   4                                       int FastHashMap.threshold     N/A
 28   4   org.jugsaxony.demo8.FastHashMap.Entry[] FastHashMap.data          N/A
Instance size: 32 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total
```

### Demo 9 (Gemini 3.8 Flash High (Antigravity))
```
org.jugsaxony.demo9.FastHashMap object internals:
OFF  SZ                 TYPE DESCRIPTION               VALUE
  0   8                      (object header: mark)     N/A
  8   4                      (object header: class)    N/A
 12   4                  int FastHashMap.size          N/A
 16   4                  int FastHashMap.mask          N/A
 20   4                  int FastHashMap.threshold     N/A
 24   4                float FastHashMap.loadFactor    N/A
 28   4   java.lang.Object[] FastHashMap.keys          N/A
 32   4   java.lang.Object[] FastHashMap.values        N/A
 36   4                      (object alignment gap)    
Instance size: 40 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total
```

### Demo 11 (Gemini 3.7 Flash High (Antigravity Rework - 100% Mutation Killed))
```
org.jugsaxony.demo11.FastHashMap object internals:
OFF  SZ                 TYPE DESCRIPTION               VALUE
  0   8                      (object header: mark)     N/A
  8   4                      (object header: class)    N/A
 12   4                  int FastHashMap.size          N/A
 16   4                  int FastHashMap.threshold     N/A
 20   4                  int FastHashMap.mask          N/A
 24   4                float FastHashMap.loadFactor    N/A
 28   4   java.lang.Object[] FastHashMap.keys          N/A
 32   4   java.lang.Object[] FastHashMap.values        N/A
 36   4                      (object alignment gap)    
Instance size: 40 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total
```

### Demo 12 (Gemini 3.8 Flash High (Antigravity Rework - 100% Mutation Killed))
```
org.jugsaxony.demo12.FastHashMap object internals:
OFF  SZ                 TYPE DESCRIPTION               VALUE
  0   8                      (object header: mark)     N/A
  8   4                      (object header: class)    N/A
 12   4                  int FastHashMap.size          N/A
 16   4                  int FastHashMap.threshold     N/A
 20   4                  int FastHashMap.mask          N/A
 24   4                float FastHashMap.loadFactor    N/A
 28   4   java.lang.Object[] FastHashMap.keys          N/A
 32   4   java.lang.Object[] FastHashMap.values        N/A
 36   4                      (object alignment gap)    
Instance size: 40 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total
```

