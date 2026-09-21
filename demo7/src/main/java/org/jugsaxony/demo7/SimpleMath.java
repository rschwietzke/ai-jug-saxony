package org.jugsaxony.demo7;

public class SimpleMath 
{
    public static int max(int a, int b)
    {
        return (a > b) ? a : b;
    }

    public static int max(Integer a, Integer b)
    {
        return (a > b) ? a : b;
    }

    public static long sum(int[] n)
    {
        int s = 0;
        for (int i : n)
        {
            s += i;
        }
        return s;
    }
}
