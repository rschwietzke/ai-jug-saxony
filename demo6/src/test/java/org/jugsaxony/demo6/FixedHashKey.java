/*
 * Copyright (c) 2005-2026 Xceptance Software Technologies GmbH
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
package org.jugsaxony.demo6;

/**
 * A test key with a hash code we fully control, so we can force collisions and place entries
 * on exact slots of the table.
 *
 * <p>Note on placement: keys with the same hash always share the same home slot, which is all a
 * collision test needs. Where a test needs one exact slot, it asks the map itself via
 * {@code homeSlot}, because the mapping from hash to slot is the map's business, not ours.
 *
 * @author René Schwietzke (Xceptance Software Technologies GmbH)
 */
final class FixedHashKey
{
    /**
     * Identity of this key, only this decides equality.
     */
    private final String name;

    /**
     * The hash code we want to report.
     */
    private final int hash;

    /**
     * Creates a key with a name and a hash code.
     *
     * @param name the identity of this key
     * @param hash the hash code to report
     */
    FixedHashKey(final String name, final int hash)
    {
        this.name = name;
        this.hash = hash;
    }

    @Override
    public int hashCode()
    {
        return this.hash;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof FixedHashKey other))
        {
            return false;
        }

        // deliberately ignores the hash, so we can have equal keys with different hashes if needed
        return this.name.equals(other.name);
    }

    @Override
    public String toString()
    {
        return this.name + "#" + this.hash;
    }
}
