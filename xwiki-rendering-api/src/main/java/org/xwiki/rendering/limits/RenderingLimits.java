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

import java.util.Optional;

import org.xwiki.component.annotation.Role;
import org.xwiki.stability.Unstable;

/**
 * The limits that apply to one rendering, guarding against unbounded recursion.
 * <p>
 * <em>Recursion depths</em>, identified by a {@link RecursionType}, protect against stack overflows and against user
 * error, like a page including itself or a macro that triggers a transformation calling the macro again. A depth goes
 * up when a level is entered and back down when it is left, and it applies per execution path.
 * <p>
 * They are stored in the execution context in a way that they survive a clone of it, so displaying content in an
 * isolated execution context does not reset them. They do not automatically survive crossing a thread boundary: use
 * {@link #save()} and {@link #restore(RenderingLimitsSnapshot)} for that, as the asynchronous rendering does.
 * <p>
 * As a consequence, nothing can be counted and thus nothing is enforced when there is no execution context. Code that
 * renders content on its own thread therefore needs to initialize an execution context for the limits to apply.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Role
@Unstable
public interface RenderingLimits
{
    /**
     * Enter a recursion level, to be left by closing the returned scope.
     *
     * @param type the type of recursion, defines which limit applies
     * @param key the key within the type, {@code null} for a limit on the overall depth of the type
     * @return the entered level, to be used in a try-with-resources block
     * @throws RecursionLimitExceededException when entering the level would exceed the limit of the type
     */
    RenderingLimitsScope enter(RecursionType type, String key) throws RecursionLimitExceededException;

    /**
     * Enter a recursion level, to be left by closing the returned scope, unless the limit of the type has been reached.
     * <p>
     * This is the variant to use when reaching the limit has a sensible fallback, like displaying the name of a
     * document instead of its computed title, so no error needs to be reported.
     *
     * @param type the type of recursion, defines which limit applies
     * @param key the key within the type, {@code null} for a limit on the overall depth of the type
     * @return the entered level, to be used in a try-with-resources block, or an empty optional when entering the level
     *         would exceed the limit of the type
     */
    Optional<RenderingLimitsScope> tryEnter(RecursionType type, String key);

    /**
     * @param type the type of recursion
     * @param key the key within the type, {@code null} for the overall depth of the type
     * @return the number of levels of that type and key that are currently entered
     */
    int getDepth(RecursionType type, String key);

    /**
     * Enter a scope in which every limit is raised by the reserve of its type, see {@link RecursionType#getReserve()}.
     * <p>
     * This is meant for reporting an exceeded limit: generating an error message can itself require rendering, which
     * would otherwise immediately hit the very limit that is being reported. It must not be used to run content that
     * an untrusted user can influence beyond such reporting.
     *
     * @return the entered scope, to be used in a try-with-resources block
     */
    RenderingLimitsScope enterReserve();

    /**
     * Enter a transformation, i.e. enter a level of the recursion type that guards against transformations triggering
     * themselves.
     *
     * @return the entered level, to be used in a try-with-resources block
     * @throws RecursionLimitExceededException when the maximum depth of nested transformations has been reached, which
     *             means that a transformation is triggering itself
     */
    RenderingLimitsScope enterTransformation() throws RecursionLimitExceededException;

    /**
     * @return a snapshot of the current state, to be restored in an execution running in another thread
     */
    RenderingLimitsSnapshot save();

    /**
     * Continue the limits of the given snapshot in the current execution context, i.e. start from the recursion depths
     * it holds. Only meant to be called once, at the very beginning of an execution that continues a rendering started
     * in another thread.
     *
     * @param snapshot the snapshot to restore, ignored when {@code null}
     */
    void restore(RenderingLimitsSnapshot snapshot);
}
