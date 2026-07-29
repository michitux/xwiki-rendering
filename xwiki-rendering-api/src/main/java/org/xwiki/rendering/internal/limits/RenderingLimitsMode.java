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
package org.xwiki.rendering.internal.limits;

/**
 * How the budgets of the rendering limits are applied.
 * <p>
 * This only concerns the budgets, not the recursion depths: those protect against stack overflows, which cannot
 * sensibly be turned into a warning.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
public enum RenderingLimitsMode
{
    /**
     * Refuse to consume more of a resource once its budget is exhausted.
     */
    ENFORCE,

    /**
     * Keep counting and reporting, but never refuse anything. Meant for discovering suitable limits on an existing
     * wiki without breaking any of its content.
     */
    LOG,

    /**
     * Don't count anything, and thus don't report anything either.
     */
    DISABLED
}
