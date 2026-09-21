package org.jugsaxony.demo6;

import org.junit.jupiter.api.DisplayName;

/**
 * The test suite for {@link SimpleMath}. Everything lives in {@link AbstractSimpleMathContract}, this
 * class only says which implementation is under test, so {@code SimpleMath} and {@code SimpleMathClean}
 * are held to exactly the same expectations and cannot drift apart.
 */
@DisplayName("SimpleMath")
class SimpleMathTest extends AbstractSimpleMathContract
{
    @Override
    protected int max(final int a, final int b)
    {
        return SimpleMath.max(a, b);
    }

    @Override
    protected int max(final Integer a, final Integer b)
    {
        return SimpleMath.max(a, b);
    }

    @Override
    protected long sum(final int[] numbers)
    {
        return SimpleMath.sum(numbers);
    }

    @Override
    protected Class<?> subject()
    {
        return SimpleMath.class;
    }
}
