package org.jugsaxony.demo0;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@DisplayName("Manual Simple Math Test")
class SimpleMathTest 
{
    @Nested
    @DisplayName("Max primitive integer tests")
    class MaxintTests 
    {
        @Test
        void happyPath() 
        {
            // pos
            assertEquals(86, SimpleMath.max(5, 86));
            assertEquals(531, SimpleMath.max(531, 186));
            // neg
            assertEquals(-2, SimpleMath.max(-2, -7));
            assertEquals(-3, SimpleMath.max(-6, -3));
            // neg, pos
            assertEquals(16, SimpleMath.max(-15, 16));
            assertEquals(26, SimpleMath.max(26, -16));
        }

        @Test
        void zero() 
        {
            // pos
            assertEquals(86, SimpleMath.max(0, 86));
            assertEquals(531, SimpleMath.max(531, 0));
            // neg
            assertEquals(0, SimpleMath.max(0, -7));
            assertEquals(0, SimpleMath.max(-6, 0));
        }

        @Test
        void aroundZero() 
        {
            // pos
            assertEquals(1, SimpleMath.max(0, 1));
            assertEquals(1, SimpleMath.max(1, 0));
            // neg
            assertEquals(0, SimpleMath.max(0, -1));
            assertEquals(0, SimpleMath.max(-1, 0));
        }

        @Test
        void same() 
        {
            // pos
            assertEquals(8, SimpleMath.max(8, 8));
            assertEquals(222, SimpleMath.max(222, 222));
            assertEquals(111, SimpleMath.max(111, 111));
            // neg
            assertEquals(-7, SimpleMath.max(-7, -7));
            assertEquals(-612, SimpleMath.max(-612, -612));
            // zero
            assertEquals(0, SimpleMath.max(0, 0));
        }

        @Test
        void edgeCases() 
        {
            // min
            assertEquals(Integer.MIN_VALUE, SimpleMath.max(Integer.MIN_VALUE, Integer.MIN_VALUE));
            // max
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MAX_VALUE, Integer.MAX_VALUE));
            // 0
            assertEquals(0, SimpleMath.max(0, Integer.MIN_VALUE));
            assertEquals(0, SimpleMath.max(Integer.MIN_VALUE, 0));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(0, Integer.MAX_VALUE));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MAX_VALUE, 0));
            // pos
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(5, Integer.MAX_VALUE));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MAX_VALUE, 10));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(60, Integer.MAX_VALUE));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MAX_VALUE, 22));
            // neg
            assertEquals(-91, SimpleMath.max(-91, Integer.MIN_VALUE));
            assertEquals(-33, SimpleMath.max(Integer.MIN_VALUE, -33));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(-321, Integer.MAX_VALUE));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MAX_VALUE, -98765));

            // close to min
            assertEquals(Integer.MIN_VALUE + 1, SimpleMath.max(Integer.MIN_VALUE, Integer.MIN_VALUE + 1));
            assertEquals(Integer.MIN_VALUE + 1, SimpleMath.max(Integer.MIN_VALUE + 1, Integer.MIN_VALUE));
            assertEquals(Integer.MIN_VALUE + 2, SimpleMath.max(Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 2));
            assertEquals(Integer.MIN_VALUE + 2, SimpleMath.max(Integer.MIN_VALUE + 2, Integer.MIN_VALUE + 1));

            // close to max
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MAX_VALUE, Integer.MAX_VALUE - 1));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
            assertEquals(Integer.MAX_VALUE - 1, SimpleMath.max(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 2));
            assertEquals(Integer.MAX_VALUE - 1, SimpleMath.max(Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1));
        }

        @Test
        void odd() 
        {
            assertEquals(101, SimpleMath.max(91, 101));
            assertEquals(97163, SimpleMath.max(1113, 97163));
            assertEquals(9, SimpleMath.max(5, 9));
            assertEquals(101, SimpleMath.max(101, 81));
            assertEquals(11131, SimpleMath.max(11131, 7163));
            assertEquals(9, SimpleMath.max(9, 5));
        }

        @Test
        void even() 
        {
            assertEquals(8, SimpleMath.max(4, 8));
            assertEquals(2212, SimpleMath.max(222, 2212));
            assertEquals(98768, SimpleMath.max(6122, 98768));
            assertEquals(8, SimpleMath.max(8, 2));
            assertEquals(2422, SimpleMath.max(2422, 220));
            assertEquals(996122, SimpleMath.max(996122, 8768));
        }

        @Test
        void distance1() 
        {
            assertEquals(2, SimpleMath.max(1, 2));
            assertEquals(3, SimpleMath.max(2, 3));
            assertEquals(19876, SimpleMath.max(19875, 19876));

            assertEquals(2, SimpleMath.max(2, 1));
            assertEquals(3, SimpleMath.max(3, 2));
            assertEquals(19876, SimpleMath.max(19876, 19875));
        }

        @Test
        void distance2() 
        {
            assertEquals(3, SimpleMath.max(1, 3));
            assertEquals(4, SimpleMath.max(2, 4));
            assertEquals(19877, SimpleMath.max(19875, 19877));

            assertEquals(3, SimpleMath.max(3, 1));
            assertEquals(4, SimpleMath.max(4, 2));
            assertEquals(19877, SimpleMath.max(19877, 19875));
        }

        @Test
        void distanceGreater() 
        {
            assertEquals(19999, SimpleMath.max(1, 19999));
            assertEquals(19999, SimpleMath.max(19999, 1));
            assertEquals(11018821, SimpleMath.max(1018821, 11018821));
            assertEquals(21018821, SimpleMath.max(21018821, 87632));
        }

        @Test
        void prime() 
        {
            assertEquals(5, SimpleMath.max(3, 5));
            assertEquals(7, SimpleMath.max(5, 7));
            assertEquals(11, SimpleMath.max(7, 11));

            assertEquals(5, SimpleMath.max(5, 3));
            assertEquals(7, SimpleMath.max(7, 5));
            assertEquals(91, SimpleMath.max(91, 89));
        }
    }

    @Nested
    @DisplayName("Max boxed integer tests")
    class MaxIntegerTests 
    {
        @Test
        void happyPath() 
        {
            // pos
            assertEquals(86, SimpleMath.max(Integer.valueOf(5), Integer.valueOf(86)));
            assertEquals(531, SimpleMath.max(Integer.valueOf(531), Integer.valueOf(186)));
            // neg
            assertEquals(-2, SimpleMath.max(Integer.valueOf(-2), Integer.valueOf(-7)));
            assertEquals(-3, SimpleMath.max(Integer.valueOf(-6), Integer.valueOf(-3)));
            // neg, pos
            assertEquals(16, SimpleMath.max(Integer.valueOf(-15), Integer.valueOf(16)));
            assertEquals(26, SimpleMath.max(Integer.valueOf(26), Integer.valueOf(-16)));
        }

        @Test
        void zero() 
        {
            // pos
            assertEquals(86, SimpleMath.max(Integer.valueOf(0), Integer.valueOf(86)));
            assertEquals(531, SimpleMath.max(Integer.valueOf(531), Integer.valueOf(0)));
            // neg
            assertEquals(0, SimpleMath.max(Integer.valueOf(0), Integer.valueOf(-7)));
            assertEquals(0, SimpleMath.max(Integer.valueOf(-6), Integer.valueOf(0)));
        }

        @Test
        void aroundZero() 
        {
            // pos
            assertEquals(1, SimpleMath.max(Integer.valueOf(0), Integer.valueOf(1)));
            assertEquals(1, SimpleMath.max(Integer.valueOf(1), Integer.valueOf(0)));
            // neg
            assertEquals(0, SimpleMath.max(Integer.valueOf(0), Integer.valueOf(-1)));
            assertEquals(0, SimpleMath.max(Integer.valueOf(-1), Integer.valueOf(0)));
        }

        @Test
        void same() 
        {
            // pos
            assertEquals(8, SimpleMath.max(Integer.valueOf(8), Integer.valueOf(8)));
            assertEquals(222, SimpleMath.max(Integer.valueOf(222), Integer.valueOf(222)));
            assertEquals(111, SimpleMath.max(Integer.valueOf(111), Integer.valueOf(111)));
            // neg
            assertEquals(-7, SimpleMath.max(Integer.valueOf(-7), Integer.valueOf(-7)));
            assertEquals(-612, SimpleMath.max(Integer.valueOf(-612), Integer.valueOf(-612)));
            // zero
            assertEquals(0, SimpleMath.max(Integer.valueOf(0), Integer.valueOf(0)));
        }

        @Test
        void edgeCases() 
        {
            // min
            assertEquals(Integer.MIN_VALUE, SimpleMath.max(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MIN_VALUE)));
            // max
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MAX_VALUE)));
            // 0
            assertEquals(0, SimpleMath.max(Integer.valueOf(0), Integer.valueOf(Integer.MIN_VALUE)));
            assertEquals(0, SimpleMath.max(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(0)));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.valueOf(0), Integer.valueOf(Integer.MAX_VALUE)));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(0)));
            // pos
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.valueOf(5), Integer.valueOf(Integer.MAX_VALUE)));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(10)));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.valueOf(60), Integer.valueOf(Integer.MAX_VALUE)));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(22)));
            // neg
            assertEquals(-91, SimpleMath.max(Integer.valueOf(-91), Integer.valueOf(Integer.MIN_VALUE)));
            assertEquals(-33, SimpleMath.max(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(-33)));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.valueOf(-321), Integer.valueOf(Integer.MAX_VALUE)));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(-98765)));

            // close to min
            assertEquals(Integer.MIN_VALUE + 1, SimpleMath.max(Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MIN_VALUE + 1)));
            assertEquals(Integer.MIN_VALUE + 1, SimpleMath.max(Integer.valueOf(Integer.MIN_VALUE + 1), Integer.valueOf(Integer.MIN_VALUE)));
            assertEquals(Integer.MIN_VALUE + 2, SimpleMath.max(Integer.valueOf(Integer.MIN_VALUE + 1), Integer.valueOf(Integer.MIN_VALUE + 2)));
            assertEquals(Integer.MIN_VALUE + 2, SimpleMath.max(Integer.valueOf(Integer.MIN_VALUE + 2), Integer.valueOf(Integer.MIN_VALUE + 1)));

            // close to max
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.valueOf(Integer.MAX_VALUE), Integer.valueOf(Integer.MAX_VALUE - 1)));
            assertEquals(Integer.MAX_VALUE, SimpleMath.max(Integer.valueOf(Integer.MAX_VALUE - 1), Integer.valueOf(Integer.MAX_VALUE)));
            assertEquals(Integer.MAX_VALUE - 1, SimpleMath.max(Integer.valueOf(Integer.MAX_VALUE - 1), Integer.valueOf(Integer.MAX_VALUE - 2)));
            assertEquals(Integer.MAX_VALUE - 1, SimpleMath.max(Integer.valueOf(Integer.MAX_VALUE - 2), Integer.valueOf(Integer.MAX_VALUE - 1)));
        }

        @Test
        void odd() 
        {
            assertEquals(101, SimpleMath.max(Integer.valueOf(91), Integer.valueOf(101)));
            assertEquals(97163, SimpleMath.max(Integer.valueOf(1113), Integer.valueOf(97163)));
            assertEquals(9, SimpleMath.max(Integer.valueOf(5), Integer.valueOf(9)));
            assertEquals(101, SimpleMath.max(Integer.valueOf(101), Integer.valueOf(81)));
            assertEquals(11131, SimpleMath.max(Integer.valueOf(11131), Integer.valueOf(7163)));
            assertEquals(9, SimpleMath.max(Integer.valueOf(9), Integer.valueOf(5)));
        }

        @Test
        void even() 
        {
            assertEquals(8, SimpleMath.max(Integer.valueOf(4), Integer.valueOf(8)));
            assertEquals(2212, SimpleMath.max(Integer.valueOf(222), Integer.valueOf(2212)));
            assertEquals(98768, SimpleMath.max(Integer.valueOf(6122), Integer.valueOf(98768)));
            assertEquals(8, SimpleMath.max(Integer.valueOf(8), Integer.valueOf(2)));
            assertEquals(2422, SimpleMath.max(Integer.valueOf(2422), Integer.valueOf(220)));
            assertEquals(996122, SimpleMath.max(Integer.valueOf(996122), Integer.valueOf(8768)));
        }

        @Test
        void distance1() 
        {
            assertEquals(2, SimpleMath.max(Integer.valueOf(1), Integer.valueOf(2)));
            assertEquals(3, SimpleMath.max(Integer.valueOf(2), Integer.valueOf(3)));
            assertEquals(19876, SimpleMath.max(Integer.valueOf(19875), Integer.valueOf(19876)));

            assertEquals(2, SimpleMath.max(Integer.valueOf(2), Integer.valueOf(1)));
            assertEquals(3, SimpleMath.max(Integer.valueOf(3), Integer.valueOf(2)));
            assertEquals(19876, SimpleMath.max(Integer.valueOf(19876), Integer.valueOf(19875)));
        }

        @Test
        void distance2() 
        {
            assertEquals(3, SimpleMath.max(Integer.valueOf(1), Integer.valueOf(3)));
            assertEquals(4, SimpleMath.max(Integer.valueOf(2), Integer.valueOf(4)));
            assertEquals(19877, SimpleMath.max(Integer.valueOf(19875), Integer.valueOf(19877)));

            assertEquals(3, SimpleMath.max(Integer.valueOf(3), Integer.valueOf(1)));
            assertEquals(4, SimpleMath.max(Integer.valueOf(4), Integer.valueOf(2)));
            assertEquals(19877, SimpleMath.max(Integer.valueOf(19877), Integer.valueOf(19875)));
        }

        @Test
        void distanceGreater() 
        {
            assertEquals(19999, SimpleMath.max(Integer.valueOf(1), Integer.valueOf(19999)));
            assertEquals(19999, SimpleMath.max(Integer.valueOf(19999), Integer.valueOf(1)));
            assertEquals(11018821, SimpleMath.max(Integer.valueOf(1018821), Integer.valueOf(11018821)));
            assertEquals(21018821, SimpleMath.max(Integer.valueOf(21018821), Integer.valueOf(87632)));
        }

        @Test
        void prime() 
        {
            assertEquals(5, SimpleMath.max(Integer.valueOf(3), Integer.valueOf(5)));
            assertEquals(7, SimpleMath.max(Integer.valueOf(5), Integer.valueOf(7)));
            assertEquals(11, SimpleMath.max(Integer.valueOf(7), Integer.valueOf(11)));

            assertEquals(5, SimpleMath.max(Integer.valueOf(5), Integer.valueOf(3)));
            assertEquals(7, SimpleMath.max(Integer.valueOf(7), Integer.valueOf(5)));
            assertEquals(91, SimpleMath.max(Integer.valueOf(91), Integer.valueOf(89)));
        }

        @Test
        void nullTest() 
        {
            assertThrows(NullPointerException.class, () -> SimpleMath.max(Integer.valueOf(3), null));
            assertThrows(NullPointerException.class, () -> SimpleMath.max(null, Integer.valueOf(3)));
            assertThrows(NullPointerException.class, () -> SimpleMath.max(null, null));
        }
    }

    @Nested
    @DisplayName("Sum test")
    class SumTest
    {
        @Test
        void empty() 
        {
            assertEquals(0, SimpleMath.sum(new int[]{}));
        }

        @Test
        void testNull() 
        {
            assertThrows(NullPointerException.class, () -> SimpleMath.sum(null));
        }

        @Test
        void oneItem() 
        {
            assertEquals(-1892171, SimpleMath.sum(new int[]{-1892171}));
            assertEquals(-1, SimpleMath.sum(new int[]{-1}));
            assertEquals(0, SimpleMath.sum(new int[]{0}));
            assertEquals(1, SimpleMath.sum(new int[]{1}));
            assertEquals(87342342, SimpleMath.sum(new int[]{87342342}));
        }

        @Test
        void happyPath() 
        {
            assertEquals(3, SimpleMath.sum(new int[]{1, 2}));
            assertEquals(6, SimpleMath.sum(new int[]{1, 2, 3}));
            assertEquals(10, SimpleMath.sum(new int[]{1, 2, 3, 4}));
            assertEquals(-10, SimpleMath.sum(new int[]{-1, -2, -3, -4}));
            assertEquals(-6, SimpleMath.sum(new int[]{-1, -2, -3}));
            assertEquals(-3, SimpleMath.sum(new int[]{-1, -2}));

            assertEquals(0, SimpleMath.sum(new int[]{0, 0, 0, 0}));
        }

        @Test
        void edgeCasesWorking() 
        {
            assertEquals(Integer.MIN_VALUE, SimpleMath.sum(new int[]{Integer.MIN_VALUE}));
            assertEquals(Integer.MAX_VALUE, SimpleMath.sum(new int[]{Integer.MAX_VALUE}));
        }

        @Test
        void edgeCases1() 
        {
            assertEquals(2L * Integer.MIN_VALUE, SimpleMath.sum(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE}));
        }

        @Test
        void edgeCases2() 
        {
            assertEquals(1L, SimpleMath.sum(new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE}));
        }

        @Test
        void edgeCases3() 
        {
            assertEquals(2L * Integer.MAX_VALUE, SimpleMath.sum(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}));
        }

        @Test
        void unmodified() 
        {
            var a1 = new int[]{-1, -3, 4, -8, 7};
            var a2 = new int[]{-1, -3, 4, -8, 7};

            assertEquals(-1, SimpleMath.sum(a1));

            assertArrayEquals(a2, a1);
        }
    }
}

