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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.rendering.limits.RecursionLimitExceededException;
import org.xwiki.rendering.limits.RecursionType;
import org.xwiki.rendering.limits.RenderingLimits;
import org.xwiki.rendering.limits.RenderingLimitsScope;
import org.xwiki.rendering.limits.RenderingLimitsSnapshot;

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

    /**
     * Used when there is no execution context to store the state in, in which case we cannot count anything and thus
     * cannot enforce anything either.
     */
    private static final RenderingLimitsScope NOOP_SCOPE = () -> {
    };

    /**
     * The mutable state stored in the execution context.
     * <p>
     * It is shared by reference with the execution contexts inheriting from the one it was declared in, so that
     * executing content in an isolated execution context keeps counting against the same depths.
     */
    private static final class State
    {
        private final Map<String, Integer> depths = new HashMap<>();

        /**
         * The number of currently open {@link RenderingLimits#enterReserve()} scopes.
         */
        private int reserve;
    }

    /**
     * The state of another thread's execution, carrying the depths as a copy.
     */
    private record Snapshot(Map<String, Integer> depths) implements RenderingLimitsSnapshot
    {
        @Override
        public boolean isEmpty()
        {
            return this.depths.isEmpty();
        }

        @Override
        public String toString()
        {
            return "depths: " + this.depths;
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
        return enter(TRANSFORMATION, null);
    }

    @Override
    public RenderingLimitsSnapshot save()
    {
        State state = getState(false);

        return state == null ? new Snapshot(Map.of()) : new Snapshot(Map.copyOf(state.depths));
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
        }
    }

    private void logMissingExecutionContext()
    {
        this.logger.debug("There is no execution context to store the rendering limits in, so no limit is enforced.");
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

    private State getState(boolean create)
    {
        ExecutionContext econtext = this.execution.getContext();

        if (econtext == null) {
            logMissingExecutionContext();

            return null;
        }

        State state = (State) econtext.getProperty(ECONTEXT_KEY);

        // Only create the state when something is actually counted so that reads stay side-effect-free.
        if (state == null && create) {
            state = new State();
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
}
