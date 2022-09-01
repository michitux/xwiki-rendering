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
package org.xwiki.rendering.transformation.macro;

import org.xwiki.component.annotation.Role;
import org.xwiki.rendering.block.RawBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.stability.Unstable;

/**
 * Filtering for raw blocks to clean and sanitize raw content.
 *
 * @version $Id$
 * @since 14.8RC1
 */
@Role
@Unstable
public interface RawBlockFilter
{
    /**
     * Filter the given block according to the specified parameters.
     *
     * @param block the block to filter
     * @param parameters the filtering parameters
     * @return the filtered raw block
     * @throws MacroExecutionException when the filtering failed
     */
    RawBlock filter(RawBlock block, RawBlockFilterParameters parameters) throws MacroExecutionException;

    /**
     * The priority defines the order in which the filters will be executed since filters are executed on a block
     * that might have been already transformed by a previous filter.
     * The lower the number, the sooner the filter will be executed.
     *
     * @return the priority of execution of the filter.
     */
    int getPriority();
}
