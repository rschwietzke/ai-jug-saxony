/*
 * Copyright (c) 2005-2025 Xceptance Software Technologies GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xceptance.xlt.report.util.misc;

/**
 * Utility class providing high-performance bit twiddling routines for compressing
 * and scaling bitset representations.
 */
public final class BitCompression
{
    private BitCompression()
    {
    }

    /**
     * Combines adjacent bits by ORing each bit with its neighbor shifted left.
     * This places the result of bit (2k | 2k+1) at odd bit position 2k+1.
     *
     * @param value the input 64-bit word
     * @return the value with adjacent bits combined
     */
    public static long combineAdjacentBits(final long value)
    {
        return value | (value << 1);
    }

    /**
     * Extracts the odd bits (positions 1, 3, 5, ..., 63) and packs (compresses)
     * them into the lower 32 bits (positions 0, 1, 2, ..., 31).
     *
     * @param value the input 64-bit word
     * @return the compressed 32-bit word in the lower bits of a long
     */
    public static long compressAndShiftOddBits(final long value)
    {
        long v = (value >>> 1) & 0x5555555555555555L;
        v = (v | (v >>> 1)) & 0x3333333333333333L;
        v = (v | (v >>> 2)) & 0x0F0F0F0F0F0F0F0FL;
        v = (v | (v >>> 4)) & 0x00FF00FF00FF00FFL;
        v = (v | (v >>> 8)) & 0x0000FFFF0000FFFFL;
        v = (v | (v >>> 16)) & 0x00000000FFFFFFFFL;
        return v;
    }
}
