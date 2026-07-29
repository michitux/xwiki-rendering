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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.rendering.limits.RecursionLimitExceededException;
import org.xwiki.rendering.limits.RecursionType;
import org.xwiki.rendering.limits.RenderingLimitType;
import org.xwiki.rendering.limits.RenderingLimits;
import org.xwiki.rendering.limits.RenderingLimitsScope;
import org.xwiki.rendering.limits.RenderingLimitsSnapshot;
import org.xwiki.rendering.transformation.RenderingContext;

/**
 * Default implementation of {@link RenderingLimits}, storing the state in the execution context.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Component
@Singleton
public class DefaultRenderingLimits implements RenderingLimits
{
    /**
     * Key of the limits state in the execution context.
     */
    static final String ECONTEXT_KEY = "rendering.limits";

    /**
     * Recursive transformations, i.e. macros that trigger a transformation of content that ends up calling the same
     * macro again. This is not about macro nesting in the content, only about transformations that are actually
     * executed from within another transformation, so the limit can be low.
     */
    static final RecursionType TRANSFORMATION = new RecursionType("transformation", 20, 5);

    private static final long NANOS_PER_MILLI = 1_000_000;

    /**
     * Used when there is no execution context to store the state in, in which case we cannot count anything and thus
     * cannot enforce anything either.
     */
    private static final RenderingLimitsScope NOOP_SCOPE = () -> {
    };

    /**
     * The budgets of a rendering, shared by reference by every execution that rendering spawns, including the ones
     * running in another thread, which is why it needs to be thread-safe.
     */
    private static final class Budgets
    {
        private final Map<String, AtomicLong> charged = new ConcurrentHashMap<>();

        /**
         * The limits whose exceeding has already been reported, so that an exhausted budget is reported once and not
         * for every further charge against it.
         */
        private final Set<String> reportedLimits = ConcurrentHashMap.newKeySet();

        /**
         * @return the total amount charged against the given limit, including this charge
         */
        private long charge(String name, long amount)
        {
            return this.charged.computeIfAbsent(name, key -> new AtomicLong()).addAndGet(amount);
        }

        private long getCharged(String name)
        {
            AtomicLong value = this.charged.get(name);

            return value == null ? 0 : value.get();
        }

        /**
         * @return {@code true} when the exceeding of the given limit hasn't been reported yet
         */
        private boolean shallReportExceeded(String name)
        {
            return this.reportedLimits.add(name);
        }
    }

    /**
     * The mutable state stored in the execution context.
     * <p>
     * It is shared by reference with the execution contexts inheriting from the one it was declared in, so that
     * executing content in an isolated execution context keeps counting against the same depths and budgets.
     */
    private static final class State
    {
        private final Map<String, Integer> depths = new HashMap<>();

        /**
         * When the rendering that is currently being performed started, to measure the elapsed time against. Reset
         * together with the budgets as it belongs to them.
         */
        private long startNanos = System.nanoTime();

        /**
         * The elapsed time that has already been charged, keyed by the name of the limit type. It belongs to the
         * copied half of the state as the elapsed time of an execution in another thread is measured from its own
         * start.
         */
        private final Map<String, Long> chargedTimes = new HashMap<>();

        /**
         * The number of currently open {@link RenderingLimits#enterReserve()} scopes.
         */
        private int reserve;

        /**
         * Not final as the outermost transformation replaces the budgets, see
         * {@link RenderingLimits#enterTransformation()}.
         */
        private Budgets budgets;

        /**
         * Whether the budgets have been propagated from the rendering that spawned this execution, in which case the
         * outermost transformation of this execution must not replace them: an execution that continues a rendering,
         * like an asynchronous one, is part of that rendering and not a rendering of its own.
         */
        private boolean budgetsPropagated;

        State(Budgets budgets)
        {
            this.budgets = budgets;
        }
    }

    /**
     * The state of another thread's execution, carrying the depths as a copy and the budgets by reference.
     */
    private record Snapshot(Map<String, Integer> depths, Budgets budgets) implements RenderingLimitsSnapshot
    {
        @Override
        public boolean isEmpty()
        {
            return this.depths.isEmpty() && this.budgets == null;
        }

        @Override
        public String toString()
        {
            return "depths: " + this.depths + ", budgets: " + (this.budgets == null ? "none" : this.budgets.charged);
        }
    }

    /**
     * A level of a given type and key that has been entered.
     */
    private static final class DepthScope implements RenderingLimitsScope
    {
        private State state;

        private final String depthKey;

        DepthScope(State state, String depthKey)
        {
            this.state = state;
            this.depthKey = depthKey;
        }

        @Override
        public void close()
        {
            if (this.state != null) {
                // Remove the entry when the depth drops back to zero so the map doesn't keep growing with keys that
                // contain, e.g., document references.
                this.state.depths.compute(this.depthKey, (k, depth) -> depth == null || depth <= 1 ? null : depth - 1);

                this.state = null;
            }
        }
    }

    /**
     * An open reserve scope.
     */
    private static final class ReserveScope implements RenderingLimitsScope
    {
        private State state;

        ReserveScope(State state)
        {
            this.state = state;
        }

        @Override
        public void close()
        {
            if (this.state != null) {
                --this.state.reserve;

                this.state = null;
            }
        }
    }

    @Inject
    private Execution execution;

    @Inject
    private RenderingLimitsConfiguration configuration;

    /**
     * Used to report which page exceeded a limit. A provider as the default implementation of the rendering context
     * uses the limits itself.
     */
    @Inject
    private Provider<RenderingContext> renderingContextProvider;

    @Inject
    private Logger logger;

    @Override
    public RenderingLimitsScope enter(RecursionType type, String key) throws RecursionLimitExceededException
    {
        State state = getState(true);

        if (state == null) {
            return NOOP_SCOPE;
        }

        String depthKey = getDepthKey(type, key);
        int depth = state.depths.getOrDefault(depthKey, 0);
        int limit = getLimit(type, state);

        if (depth >= limit) {
            throw new RecursionLimitExceededException(getErrorMessage(type, key, limit), type, key, limit);
        }

        state.depths.put(depthKey, depth + 1);

        return new DepthScope(state, depthKey);
    }

    @Override
    public Optional<RenderingLimitsScope> tryEnter(RecursionType type, String key)
    {
        try {
            return Optional.of(enter(type, key));
        } catch (RecursionLimitExceededException e) {
            return Optional.empty();
        }
    }

    @Override
    public int getDepth(RecursionType type, String key)
    {
        State state = getState(false);

        return state == null ? 0 : state.depths.getOrDefault(getDepthKey(type, key), 0);
    }

    @Override
    public void charge(RenderingLimitType type, long amount)
    {
        State state = getChargingState();

        if (state == null) {
            return;
        }

        long charged = state.budgets.charge(type.getName(), amount);
        long limit = getLimit(type, state);

        if (charged > limit) {
            reportExceededLimit(type, state, charged, limit);
        }
    }

    @Override
    public void chargeElapsedTime(RenderingLimitType type)
    {
        State state = getChargingState();

        if (state == null) {
            return;
        }

        long elapsedMillis = (System.nanoTime() - state.startNanos) / NANOS_PER_MILLI;
        long chargedMillis = state.chargedTimes.getOrDefault(type.getName(), 0L);

        if (elapsedMillis > chargedMillis) {
            state.chargedTimes.put(type.getName(), elapsedMillis);

            charge(type, elapsedMillis - chargedMillis);
        }
    }

    @Override
    public boolean isExceeded(RenderingLimitType type)
    {
        return exceeds(type, 0);
    }

    @Override
    public boolean exceeds(RenderingLimitType type, long amount)
    {
        // Unlike a charge, this doesn't create the state as it doesn't count anything, so the absence of an execution
        // context needs to be checked separately from the absence of a state.
        if (this.execution.getContext() == null) {
            logMissingExecutionContext();

            return false;
        }

        State state = getState(false);
        RenderingLimitsMode mode = this.configuration.getMode();

        // In the logging mode the budgets are only observed, never enforced.
        if (mode == RenderingLimitsMode.DISABLED || mode == RenderingLimitsMode.LOG) {
            return false;
        }

        long charged = state == null ? 0 : state.budgets.getCharged(type.getName());

        return charged + amount > getLimit(type, state);
    }

    @Override
    public long getCharged(RenderingLimitType type)
    {
        State state = getState(false);

        return state == null ? 0 : state.budgets.getCharged(type.getName());
    }

    @Override
    public long getLimit(RenderingLimitType type)
    {
        return getLimit(type, getState(false));
    }

    @Override
    public RenderingLimitsScope enterReserve()
    {
        State state = getState(true);

        if (state == null) {
            return NOOP_SCOPE;
        }

        ++state.reserve;

        return new ReserveScope(state);
    }

    @Override
    public RenderingLimitsScope enterTransformation() throws RecursionLimitExceededException
    {
        // The outermost transformation is what defines "one rendering", so it is what owns the budgets.
        boolean outermost = getDepth(TRANSFORMATION, null) == 0;

        RenderingLimitsScope level = enter(TRANSFORMATION, null);

        if (outermost) {
            State state = getState(false);

            // Nothing is reset when the budgets have been propagated as this execution is then part of the rendering
            // that propagated them.
            if (state != null && !state.budgetsPropagated) {
                state.budgets = new Budgets();
                state.startNanos = System.nanoTime();
                state.chargedTimes.clear();
            }
        }

        return level;
    }

    @Override
    public RenderingLimitsSnapshot save()
    {
        State state = getState(false);

        return state == null ? new Snapshot(Map.of(), null) : new Snapshot(Map.copyOf(state.depths), state.budgets);
    }

    @Override
    public void restore(RenderingLimitsSnapshot snapshot)
    {
        if (!(snapshot instanceof Snapshot restoredState) || restoredState.isEmpty()) {
            return;
        }

        State state = getState(true);

        if (state != null) {
            state.depths.putAll(restoredState.depths());

            if (restoredState.budgets() != null) {
                state.budgets = restoredState.budgets();
                // Remember that these budgets belong to the rendering that spawned this execution so that the
                // outermost transformation here doesn't replace them with fresh ones.
                state.budgetsPropagated = true;
            }
        }
    }

    private void logMissingExecutionContext()
    {
        this.logger.debug("There is no execution context to store the rendering limits in, so no limit is enforced.");
    }

    private void reportExceededLimit(RenderingLimitType type, State state, long charged, long limit)
    {
        if (state.budgets.shallReportExceeded(type.getName())) {
            this.logger.warn("The [{}] limit for rendering a page has been exceeded while rendering [{}]: [{}]"
                + " instead of the limit of [{}]. A wiki administrator can change the limit with the"
                + " [rendering.limits.{}.limit] property in xwiki.properties.", type.getName(),
                this.renderingContextProvider.get().getTransformationId(), charged, limit, type.getName());
        }
    }

    private static String getDepthKey(RecursionType type, String key)
    {
        return key == null ? type.getName() : type.getName() + '/' + key;
    }

    private static String getErrorMessage(RecursionType type, String key, int limit)
    {
        if (key == null) {
            return "The maximum recursion depth of [%d] for [%s] has been reached.".formatted(limit, type.getName());
        }

        return "The maximum recursion depth of [%d] for [%s] with key [%s] has been reached."
            .formatted(limit, type.getName(), key);
    }

    /**
     * @return the state to charge against, {@code null} when nothing shall be charged, i.e. when there is no execution
     *     context to store the state in or the limits are disabled
     */
    private State getChargingState()
    {
        if (this.execution.getContext() == null) {
            logMissingExecutionContext();

            return null;
        }

        // Resolve the mode before creating the state so that in the disabled mode no budgets are allocated and no final
        // property is declared in the execution context.
        if (this.configuration.getMode() == RenderingLimitsMode.DISABLED) {
            return null;
        }

        return getState(true);
    }

    private State getState(boolean create)
    {
        ExecutionContext econtext = this.execution.getContext();

        if (econtext == null) {
            logMissingExecutionContext();

            return null;
        }

        State state = (State) econtext.getProperty(ECONTEXT_KEY);

        // Only create the state when something is actually counted so that reads stay side-effect-free and don't
        // allocate budgets that would then be locked in as a final context property.
        if (state == null && create) {
            state = new State(new Budgets());
            // Declare the property as inherited so it survives cloning the execution context, and as final so it
            // cannot be replaced by a fresh one to get new limits.
            econtext.newProperty(ECONTEXT_KEY).inherited().initial(state).makeFinal().declare();
        }

        return state;
    }

    /**
     * @return the effective limit of the given recursion type, taking the open reserve scopes into account
     */
    private int getLimit(RecursionType type, State state)
    {
        int limit = this.configuration.getConfiguredRecursionLimit(type).orElseGet(type::getDefaultLimit);

        return limit + (state.reserve > 0 ? type.getReserve() : 0);
    }

    /**
     * @param state the state to get the open reserve scopes from, may be {@code null} when nothing has been counted yet
     * @return the effective limit of the given limit type, taking the open reserve scopes into account
     */
    private long getLimit(RenderingLimitType type, State state)
    {
        long limit = this.configuration.getConfiguredLimit(type).orElseGet(type::getDefaultLimit);

        return limit + (state != null && state.reserve > 0 ? type.getReserve() : 0);
    }
}
