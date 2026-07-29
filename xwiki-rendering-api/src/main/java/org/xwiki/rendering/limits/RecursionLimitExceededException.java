/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.rendering.limits;

import org.xwiki.stability.Unstable;

/**
 * Thrown when entering a recursion level would exceed the limit configured for its {@link RecursionType}.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Unstable
public class RecursionLimitExceededException extends Exception
{
    private static final long serialVersionUID = 1L;

    private final RecursionType type;

    private final String key;

    private final int limit;

    /**
     * @param message the message describing the exceeded limit
     * @param type the type of recursion whose limit has been exceeded
     * @param key the key within the type, may be {@code null}
     * @param limit the effective limit that has been reached
     */
    public RecursionLimitExceededException(String message, RecursionType type, String key, int limit)
    {
        super(message);

        this.type = type;
        this.key = key;
        this.limit = limit;
    }

    /**
     * @return the type of recursion whose limit has been exceeded
     */
    public RecursionType getType()
    {
        return this.type;
    }

    /**
     * @return the key within the type, {@code null} when the type is not keyed
     */
    public String getKey()
    {
        return this.key;
    }

    /**
     * @return the effective limit that has been reached
     */
    public int getLimit()
    {
        return this.limit;
    }
}
