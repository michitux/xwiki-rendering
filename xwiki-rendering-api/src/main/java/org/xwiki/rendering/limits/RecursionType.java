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
 * A kind of recursion that is guarded against by {@link RenderingLimits}, together with its default limit.
 * <p>
 * Components that recursively re-enter the rendering pipeline declare a constant of this type and pass it to
 * {@link RenderingLimits#enter(RecursionType, String)}. The default limit can be overridden in
 * {@code xwiki.properties} through the {@code rendering.recursion.<name>.limit} property.
 * <p>
 * These limits protect against stack overflows and against user error, they are not a budget for the whole rendering
 * of a page: the depth associated with a type goes up when a level is entered and back down when it is left. See
 * {@link RenderingLimitType} for the budgets.
 * <p>
 * Unlike the budgets, the types aren't declared here as each of them belongs to the component that guards it. As every
 * one of them has to be documented in {@code xwiki.properties} by hand, here is where the ones that ship with XWiki are
 * declared, so that the whole set can be found from one place:
 * <ul>
 *   <li>{@code transformation} in {@code DefaultRenderingLimits}, entered by
 *       {@link RenderingLimits#enterTransformation()}</li>
 *   <li>{@code macro.inclusion} in {@code AbstractIncludeMacro}, shared by the include and the display macro</li>
 *   <li>{@code display.content} and {@code display.title} in {@code DocumentDisplayerRecursion}</li>
 * </ul>
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Unstable
public final class RecursionType
{
    private final String name;

    private final int defaultLimit;

    private final int reserve;

    /**
     * @param name the name of this recursion type, used both in error messages and to build the name of the
     *            configuration property overriding the default limit
     * @param defaultLimit the maximum depth allowed by default, i.e. unless overridden by the configuration
     * @param reserve the number of additional levels allowed inside a {@link RenderingLimits#enterReserve()} scope,
     *            so that reporting an exceeded limit doesn't hit the limit itself
     */
    public RecursionType(String name, int defaultLimit, int reserve)
    {
        this.name = Objects.requireNonNull(name);
        this.defaultLimit = defaultLimit;
        this.reserve = reserve;
    }

    /**
     * @return the name of this recursion type
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * @return the maximum depth allowed by default, i.e. unless overridden by the configuration
     */
    public int getDefaultLimit()
    {
        return this.defaultLimit;
    }

    /**
     * @return the number of additional levels allowed inside a {@link RenderingLimits#enterReserve()} scope
     */
    public int getReserve()
    {
        return this.reserve;
    }

    /**
     * The name is the identity of a recursion type as it is what both the counted depths and the configuration are
     * keyed on, so two instances with the same name are the same recursion even when they declare different defaults.
     *
     * @param obj the object to compare to
     * @return {@code true} when the given object is a recursion type with the same name
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }

        return obj instanceof RecursionType other && this.name.equals(other.name);
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
