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
package org.xwiki.rendering.internal.transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.internal.limits.DefaultRenderingLimits;
import org.xwiki.rendering.internal.limits.DefaultRenderingLimitsProfileResolver;
import org.xwiki.rendering.internal.limits.RenderingLimitsConfiguration;
import org.xwiki.rendering.limits.RecursionType;
import org.xwiki.rendering.limits.RenderingLimits;
import org.xwiki.rendering.limits.RenderingLimitsScope;
import org.xwiki.rendering.transformation.Transformation;
import org.xwiki.rendering.transformation.TransformationContext;
import org.xwiki.rendering.transformation.TransformationException;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Component tests for {@link DefaultRenderingContext}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({ DefaultRenderingLimits.class, DefaultRenderingLimitsProfileResolver.class })
class DefaultRenderingContextTest
{
    /**
     * Must match {@code DefaultRenderingLimits#TRANSFORMATION}, though only by name as that is the identity of a
     * recursion type.
     */
    private static final RecursionType TRANSFORMATION_RECURSION = new RecursionType("transformation", 20, 5);

    private static final int LIMIT = TRANSFORMATION_RECURSION.getDefaultLimit();

    @InjectMockComponents
    private DefaultRenderingContext renderingContext;

    @MockComponent
    private Execution execution;

    @MockComponent
    private RenderingLimitsConfiguration configuration;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    private final ExecutionContext executionContext = new ExecutionContext();

    @BeforeEach
    void setUp()
    {
        when(this.execution.getContext()).thenReturn(this.executionContext);
    }

    @Test
    void recursiveTransformationsAreLimited() throws Exception
    {
        Transformation transformation = mock();
        MutableInt depth = new MutableInt(0);
        doAnswer(invocation -> {
            depth.increment();
            this.renderingContext.transformInContext(transformation, mock(), mock());
            return null;
        }).when(transformation).transform(any(Block.class), any(TransformationContext.class));

        TransformationException exception = assertThrows(TransformationException.class,
            () -> this.renderingContext.transformInContext(transformation, mock(), mock()));

        assertThat(exception.getMessage(),
            startsWith("The maximum recursion depth of [20] for [transformation] has been reached."));
        assertThat(exception.getMessage(), containsString("a transformation that triggers itself"));
        assertEquals(20, depth.intValue());
    }

    @Test
    void recursiveTransformationsCanBeLimitedByConfiguration() throws Exception
    {
        when(this.configuration.getConfiguredRecursionLimit(TRANSFORMATION_RECURSION)).thenReturn(OptionalInt.of(2));

        Transformation transformation = mock();
        MutableInt depth = new MutableInt(0);
        doAnswer(invocation -> {
            depth.increment();
            this.renderingContext.transformInContext(transformation, mock(), mock());
            return null;
        }).when(transformation).transform(any(Block.class), any(TransformationContext.class));

        assertThrows(TransformationException.class,
            () -> this.renderingContext.transformInContext(transformation, mock(), mock()));

        assertEquals(2, depth.intValue());
    }

    @Test
    void theReserveAllowsReportingTheExceededLimit() throws Exception
    {
        RenderingLimits renderingLimits = this.componentManager.getInstance(RenderingLimits.class);
        Transformation transformation = mock();

        // Fill the limit up, exactly as a recursion that reached it would.
        List<RenderingLimitsScope> levels = new ArrayList<>();
        try {
            for (int i = 0; i < LIMIT; ++i) {
                levels.add(renderingLimits.enter(TRANSFORMATION_RECURSION, null));
            }

            // Without a reserve, generating an error message that needs a transformation would fail too.
            assertThrows(TransformationException.class,
                () -> this.renderingContext.transformInContext(transformation, mock(), mock()));

            // Within a reserve scope, as opened when generating an error message, it succeeds.
            try (RenderingLimitsScope reserve = renderingLimits.enterReserve()) {
                this.renderingContext.transformInContext(transformation, mock(), mock());
            }
        } finally {
            levels.forEach(RenderingLimitsScope::close);
        }
    }

    @Test
    void nonRecursiveTransformationsAreNotLimited() throws Exception
    {
        RenderingLimits renderingLimits = this.componentManager.getInstance(RenderingLimits.class);
        Transformation transformation = mock();

        for (int i = 0; i < LIMIT * 5; ++i) {
            this.renderingContext.transformInContext(transformation, mock(), mock());
        }

        assertEquals(0, renderingLimits.getDepth(TRANSFORMATION_RECURSION, null));
    }
}
