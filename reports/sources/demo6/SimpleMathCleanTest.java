package org.jugsaxony.demo6;

import org.junit.jupiter.api.DisplayName;

/**
 * The test suite for {@link SimpleMathClean}. Everything lives in {@link AbstractSimpleMathContract},
 * this class only says which implementation is under test. {@code SimpleMathClean} is the documented
 * twin of {@code SimpleMath}; the documentation does not change what the code does, so the same contract
 * applies and the same expectations have to hold.
 */
@DisplayName("SimpleMathClean")
class SimpleMathCleanTest extends AbstractSimpleMathContract
{
    @Override
    protected int max(final int a, final int b)
    {
        return SimpleMathClean.max(a, b);
    }

    @Override
    protected int max(final Integer a, final Integer b)
    {
        return SimpleMathClean.max(a, b);
    }

    @Override
    protected long sum(final int[] numbers)
    {
        return SimpleMathClean.sum(numbers);
    }

    @Override
    protected Class<?> subject()
    {
        return SimpleMathClean.class;
    }
}
