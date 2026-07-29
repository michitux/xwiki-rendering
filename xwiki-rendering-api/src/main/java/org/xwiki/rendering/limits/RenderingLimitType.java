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

import java.util.Objects;

import org.xwiki.stability.Unstable;

/**
 * A resource that is budgeted over one whole rendering, together with its default limit.
 * <p>
 * Components that consume such a resource declare a constant of this type and pass it to
 * {@link RenderingLimits#charge(RenderingLimitType, long)}. The default limit can be overridden in
 * {@code xwiki.properties} through the {@code rendering.limit.<name>} property.
 * <p>
 * Unlike a {@link RecursionType}, which limits how deeply something may be nested and therefore goes back down when a
 * level is left, a limit type is a budget: the amount charged against it only ever increases over the course of one
 * rendering, and it is shared by everything that rendering spawns, including asynchronous executions.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Unstable
public final class RenderingLimitType
{
    /**
     * The number of macros that may be executed in one rendering. Intentionally high: this is a backstop against
     * content that generates macros without end, not a limit that legitimate content is expected to come close to.
     */
    public static final RenderingLimitType MACRO_EXECUTIONS =
        new RenderingLimitType("macro.executions", 100_000, 100, "1");

    /**
     * The number of asynchronous executions that one rendering may spawn, to bound both the load they put on the
     * server and the number of requests the client needs to fetch their results. Any further asynchronous execution is
     * executed synchronously instead so that the page is still rendered.
     */
    public static final RenderingLimitType ASYNC_EXECUTIONS =
        new RenderingLimitType("async.executions", 100, 0, "1");

    /**
     * The size of the content that the macros of one rendering may produce, counted as a rough estimate that also
     * counts content several times when it passes through several macros, so the limit is set generously.
     */
    public static final RenderingLimitType DOCUMENT_SIZE =
        new RenderingLimitType("document.size", 10L * 1024 * 1024, 64L * 1024, "By");

    /**
     * The time that one rendering may take, checked between two macro executions, so a single macro that never
     * returns isn't caught by it.
     */
    public static final RenderingLimitType TIME = new RenderingLimitType("time", 60_000, 5_000, "ms");

    /**
     * The number of error messages that may be inserted by one rendering, so that content with a lot of failing
     * macros doesn't produce a huge result made of error messages.
     */
    public static final RenderingLimitType ERROR_MESSAGES = new RenderingLimitType("error.messages", 100, 0, "1");

    private final String name;

    private final long defaultLimit;

    private final long reserve;

    private final String unit;

    /**
     * @param name the name of this limit type, used both in error messages and to build the name of the configuration
     *            property overriding the default limit
     * @param defaultLimit the maximum amount that may be charged by default, i.e. unless overridden by the
     *            configuration
     * @param reserve the additional amount that may be charged inside a {@link RenderingLimits#enterReserve()} scope,
     *            so that reporting an exceeded limit doesn't hit the limit itself
     * @param unit the unit of the charged amounts, following the OpenTelemetry conventions, i.e. {@code 1} for a plain
     *            count, {@code By} for bytes and {@code ms} for milliseconds
     */
    public RenderingLimitType(String name, long defaultLimit, long reserve, String unit)
    {
        this.name = Objects.requireNonNull(name);
        this.defaultLimit = defaultLimit;
        this.reserve = reserve;
        this.unit = Objects.requireNonNull(unit);
    }

    /**
     * @return the name of this limit type
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * @return the maximum amount that may be charged by default, i.e. unless overridden by the configuration
     */
    public long getDefaultLimit()
    {
        return this.defaultLimit;
    }

    /**
     * @return the additional amount that may be charged inside a {@link RenderingLimits#enterReserve()} scope
     */
    public long getReserve()
    {
        return this.reserve;
    }

    /**
     * @return the unit of the charged amounts, following the OpenTelemetry conventions
     */
    public String getUnit()
    {
        return this.unit;
    }

    /**
     * The name is the identity of a limit type as it is what both the charged amounts and the configuration are keyed
     * on, so two instances with the same name are the same limit even when they declare different defaults.
     *
     * @param obj the object to compare to
     * @return {@code true} when the given object is a limit type with the same name
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }

        return obj instanceof RenderingLimitType other && this.name.equals(other.name);
    }

    @Override
    public int hashCode()
    {
        return this.name.hashCode();
    }

    @Override
    public String toString()
    {
        return this.name;
    }
}
