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
 * The limits that apply to one rendering, guarding both against unbounded recursion and against unbounded resource
 * consumption.
 * <p>
 * Two kinds of limits are managed here. They have deliberately different semantics but a single store, so that both
 * cross thread boundaries through a single {@link RenderingLimitsSnapshot}:
 * <ul>
 * <li><em>Recursion depths</em>, identified by a {@link RecursionType}, protect against stack overflows and against
 * user error, like a page including itself or a macro that triggers a transformation calling the macro again. A depth
 * goes up when a level is entered and back down when it is left, and it applies per execution path.</li>
 * <li><em>Budgets</em>, identified by a {@link RenderingLimitType}, bound the total resources that one rendering may
 * consume, like the number of executed macros or the size of the produced content. A budget only ever increases and is
 * shared by everything that rendering spawns. What counts as one rendering is defined by
 * {@link #enterTransformation()}.</li>
 * </ul>
 * Both are stored in the execution context in a way that they survive a clone of it, so displaying content in an
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
     * Charge an amount against the budget of the given type.
     * <p>
     * The amount is charged whether or not it fits, as a budget is only ever supposed to grow, but the total is capped
     * just above the limit: a single charge can overshoot the limit by an arbitrary amount, and without the cap the
     * reserve of {@link #enterReserve()} would be consumed by that overshoot instead of being available for reporting
     * the exceeded limit. The real amount is reported in the log instead. Use {@link #isExceeded(RenderingLimitType)}
     * afterwards to know if the resource may still be consumed.
     *
     * @param type the type of limit to charge against
     * @param amount the amount to charge, in the unit of the type
     */
    void charge(RenderingLimitType type, long amount);

    /**
     * Charge the time that elapsed in the current execution since this was last called, in milliseconds.
     * <p>
     * This exists because time isn't additive the way the other resources are: a nested rendering runs inside the
     * elapsed time of the rendering that triggered it, so charging both would count the same time several times. What
     * is charged here is therefore the time of the current execution, no matter how many nested renderings report it,
     * while an execution in another thread charges its own time on top of it — which is what makes a budget for the
     * time of a rendering and all its asynchronous executions together.
     *
     * @param type the type of limit to charge against, its amounts being milliseconds
     */
    void chargeElapsedTime(RenderingLimitType type);

    /**
     * @param type the type of limit to check
     * @return {@code true} when the budget of the type is exhausted and the resource may not be consumed anymore
     */
    boolean isExceeded(RenderingLimitType type);

    /**
     * Check if charging an amount would exhaust the budget of the given type, without charging it.
     * <p>
     * This is meant for the cases where the work to produce the amount can be skipped, like checking if content still
     * fits before parsing it.
     *
     * @param type the type of limit to check
     * @param amount the amount to check, in the unit of the type
     * @return {@code true} when charging that amount would exhaust the budget of the type
     */
    boolean exceeds(RenderingLimitType type, long amount);

    /**
     * @param type the type of limit
     * @return the amount that has been charged against the budget of that type so far, capped just above the limit as
     *         described in {@link #charge(RenderingLimitType, long)}
     */
    long getCharged(RenderingLimitType type);

    /**
     * @param type the type of limit
     * @return the effective budget of that type, i.e. the configured value or the default of the type, plus the reserve
     *         when a reserve scope is open
     */
    long getLimit(RenderingLimitType type);

    /**
     * Enter a scope in which every limit is raised by the reserve of its type, see {@link RecursionType#getReserve()}
     * and {@link RenderingLimitType#getReserve()}.
     * <p>
     * This is meant for reporting an exceeded limit: generating an error message can itself require rendering, which
     * would otherwise immediately hit the very limit that is being reported. It must not be used to run content that
     * an untrusted user can influence beyond such reporting.
     * <p>
     * The reserve of a budget is what makes the cap of {@link #charge(RenderingLimitType, long)} necessary: only
     * because the charged total never grows beyond the limit is the reserve actual headroom. It is the headroom for all
     * the error messages of one rendering together, not per message, so it bounds how much the error messages of a
     * rendering may add to its result.
     *
     * @return the entered scope, to be used in a try-with-resources block
     */
    RenderingLimitsScope enterReserve();

    /**
     * @return {@code true} when a {@link #enterReserve()} scope is currently open, i.e. when the current execution is
     *         reporting an exceeded limit
     */
    boolean isReserveOpen();

    /**
     * Enter a transformation, i.e. enter a level of the recursion type that guards against transformations triggering
     * themselves and, when this is the outermost transformation, start a new rendering.
     * <p>
     * <strong>The outermost transformation is what defines "one rendering" and thus owns the budgets:</strong> when
     * one is entered, fresh budgets are installed and the clock of the elapsed time is restarted, so that a
     * transformation performed at depth zero never charges what an earlier one consumed. The depth is the only
     * mechanical signal that distinguishes a nested rendering from an independent one, as both reach the same seams.
     * Two consequences are worth knowing:
     * <ul>
     * <li>An execution context that performs several renderings, like a mail preparation thread or a job that renders
     * many documents, gets a budget per rendering and not one for its whole lifetime. This is what makes the budgets
     * usable outside a request.</li>
     * <li>Whether something counts as its own rendering depends on whether it is triggered from within a
     * transformation. Converting a template that triggers rendering, e.g. of the content or of the panels of a page,
     * from plain Velocity to wiki syntax would therefore make everything it triggers share a single budget. That is
     * stricter, never laxer, but it changes the granularity, so keep it in mind when changing such a template.</li>
     * </ul>
     * Budgets that were propagated with {@link #restore(RenderingLimitsSnapshot)} are an exception: an execution that
     * continues a rendering started elsewhere, like an asynchronous one, is part of that rendering, so its outermost
     * transformation keeps the propagated budgets.
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
     * it holds and charge against the budgets it holds. Only meant to be called once, at the very beginning of an
     * execution that continues a rendering started in another thread.
     *
     * @param snapshot the snapshot to restore, ignored when {@code null}
     */
    void restore(RenderingLimitsSnapshot snapshot);
}
