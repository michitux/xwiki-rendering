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
 * An opaque snapshot of the state of {@link RenderingLimits}, to be carried over to an execution that runs in a
 * different thread and thus in a different execution context, in particular an asynchronous rendering.
 * <p>
 * The recursion depths are <em>copied</em>: the depths of the execution the snapshot is restored into go up and down
 * independently from the depths of the execution it was taken from, but they start from the depth that was already
 * reached, so that crossing a thread boundary cannot be used to get a fresh recursion budget.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Unstable
public interface RenderingLimitsSnapshot
{
    /**
     * @return {@code true} if there is nothing to carry over
     */
    boolean isEmpty();
}
