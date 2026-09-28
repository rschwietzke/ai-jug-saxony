package org.jugsaxony.demo8;

/**
 * Utility class for trivial static helpers.
 * 
 * @author Rene Schwietzke
 */
public class SimpleMathClean
{
    /**
     * Returns the larger value of both, effectively running
     * a max value calculation.
     * 
     * @param a value one
     * @param b value two
     * @return the larger value of both
     */
    public static int max(final int a, final int b)
    {
        return (a > b) ? a : b;
    }

    /**
     * Returns the larger value of both, effectively running
     * a max value calculation. This is for objects rather than
     * primitive types.
     * 
     * @param a value one
     * @param b value two
     * @return the larger value of both
     */
    public static int max(final Integer a, final Integer b)
    {
        return (a > b) ? a : b;
    }

    /**
     * Sums all integers in an array. 
     * 
     * @param numbers array of integers
     * @return sum of all integers
     */
    public static long sum(final int[] numbers)
    {
        int sum = 0;
        for (final int n : numbers)
        {
            sum += n;
        }
        return sum;
    }
}
