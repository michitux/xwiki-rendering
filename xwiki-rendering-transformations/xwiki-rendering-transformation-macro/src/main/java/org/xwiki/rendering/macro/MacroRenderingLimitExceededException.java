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
package org.xwiki.rendering.macro;

import org.xwiki.rendering.limits.RenderingLimitType;
import org.xwiki.stability.Unstable;

/**
 * Indicates that a macro couldn't be executed because one of the limits for rendering a page has been reached.
 * <p>
 * The macro transformation looks for this exception in the cause chain of the exceptions thrown by macros so that it
 * can report the exceeded limit instead of a generic macro failure. Macros that wrap what they catch, which is the
 * usual case, therefore don't need to do anything to get the better error message.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Unstable
public class MacroRenderingLimitExceededException extends MacroExecutionException
{
    private static final long serialVersionUID = 1L;

    private final RenderingLimitType limitType;

    /**
     * @param limitType the type of the limit that has been reached
     * @param message the detail message (which is saved for later retrieval by the {@link #getMessage()} method)
     */
    public MacroRenderingLimitExceededException(RenderingLimitType limitType, String message)
    {
        super(message);

        this.limitType = limitType;
    }

    /**
     * @return the type of the limit that has been reached
     */
    public RenderingLimitType getLimitType()
    {
        return this.limitType;
    }
}
