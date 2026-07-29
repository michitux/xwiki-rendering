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

import java.util.List;

import org.xwiki.component.annotation.Role;
import org.xwiki.stability.Unstable;

/**
 * Determines which configuration profiles apply to the current execution, so that a use case that legitimately needs
 * other budgets than a page view can be configured separately.
 * <p>
 * The profiles are resolved once, when the budgets of a rendering are created, and then apply to that whole rendering,
 * including the executions it spawns in other threads. This is why an implementation may rely on state that is only
 * available at the beginning of an execution.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Role
@Unstable
public interface RenderingLimitsProfileResolver
{
    /**
     * @return the names of the profiles that apply to the current execution, the most specific one first, empty when
     *         only the global configuration applies
     */
    List<String> getCurrentProfiles();
}
