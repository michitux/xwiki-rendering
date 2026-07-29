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
 * A scope that has been entered in {@link RenderingLimits} and needs to be left again, for example a recursion level
 * or a reserve.
 * <p>
 * Always use it in a try-with-resources block so that the scope is left even when the guarded code throws.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Unstable
public interface RenderingLimitsScope extends AutoCloseable
{
    /**
     * Leave this scope. Leaving a scope twice has no effect.
     */
    @Override
    void close();
}
