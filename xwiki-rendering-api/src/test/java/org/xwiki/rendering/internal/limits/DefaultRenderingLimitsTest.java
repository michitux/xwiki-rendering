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

import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.rendering.limits.RecursionLimitExceededException;
import org.xwiki.rendering.limits.RecursionType;
import org.xwiki.rendering.limits.RenderingLimitsScope;
import org.xwiki.rendering.limits.RenderingLimitsSnapshot;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultRenderingLimits}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultRenderingLimitsTest
{
    private static final RecursionType TYPE = new RecursionType("test", 3, 2);

    private static final RecursionType OTHER_TYPE = new RecursionType("other", 3, 2);

    @InjectMockComponents
    private DefaultRenderingLimits limits;

    @MockComponent
    private Execution execution;

    @MockComponent
    private RenderingLimitsConfiguration configuration;

    private ExecutionContext executionContext = new ExecutionContext();

    @BeforeEach
    void setUp()
    {
        when(this.execution.getContext()).thenAnswer(invocation -> this.executionContext);
    }

    @Test
    void depthIsIncrementedAndDecremented() throws Exception
    {
        assertEquals(0, this.limits.getDepth(TYPE, null));

        try (RenderingLimitsScope level = this.limits.enter(TYPE, null)) {
            assertEquals(1, this.limits.getDepth(TYPE, null));

            try (RenderingLimitsScope nested = this.limits.enter(TYPE, null)) {
                assertEquals(2, this.limits.getDepth(TYPE, null));
            }

            assertEquals(1, this.limits.getDepth(TYPE, null));
        }

        assertEquals(0, this.limits.getDepth(TYPE, null));
    }

    @Test
    void recursionLimitIsEnforced() throws Exception
    {
        try (RenderingLimitsScope first = this.limits.enter(TYPE, null);
            RenderingLimitsScope second = this.limits.enter(TYPE, null);
            RenderingLimitsScope third = this.limits.enter(TYPE, null)) {

            RecursionLimitExceededException exception =
                assertThrows(RecursionLimitExceededException.class, () -> this.limits.enter(TYPE, null));

            assertEquals("The maximum recursion depth of [3] for [test] has been reached.", exception.getMessage());
            assertEquals(3, exception.getLimit());
            assertEquals(TYPE, exception.getType());
            // The failed attempt must not have changed the depth.
            assertEquals(3, this.limits.getDepth(TYPE, null));
        }
    }

    @Test
    void typesAndKeysAreCountedSeparately() throws Exception
    {
        try (RenderingLimitsScope level = this.limits.enter(TYPE, "a")) {
            assertEquals(1, this.limits.getDepth(TYPE, "a"));
            assertEquals(0, this.limits.getDepth(TYPE, "b"));
            assertEquals(0, this.limits.getDepth(TYPE, null));
            assertEquals(0, this.limits.getDepth(OTHER_TYPE, "a"));
        }
    }

    @Test
    void keyedLimitMentionsTheKey()
    {
        RecursionLimitExceededException exception = assertThrows(RecursionLimitExceededException.class, () -> {
            for (int i = 0; i < 4; ++i) {
                this.limits.enter(TYPE, "mykey");
            }
        });

        assertEquals("The maximum recursion depth of [3] for [test] with key [mykey] has been reached.",
            exception.getMessage());
        assertEquals("mykey", exception.getKey());
    }

    @Test
    void configuredRecursionLimitOverridesTheDefault() throws Exception
    {
        when(this.configuration.getConfiguredRecursionLimit(TYPE)).thenReturn(OptionalInt.of(1));

        try (RenderingLimitsScope level = this.limits.enter(TYPE, null)) {
            assertThrows(RecursionLimitExceededException.class, () -> this.limits.enter(TYPE, null));
        }
    }

    @Test
    void tryEnterReturnsAnEmptyOptionalWhenTheLimitIsReached()
    {
        Optional<RenderingLimitsScope> first = this.limits.tryEnter(TYPE, "a");
        Optional<RenderingLimitsScope> second = this.limits.tryEnter(TYPE, "a");
        Optional<RenderingLimitsScope> third = this.limits.tryEnter(TYPE, "a");

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertTrue(third.isPresent());
        assertEquals(3, this.limits.getDepth(TYPE, "a"));

        assertTrue(this.limits.tryEnter(TYPE, "a").isEmpty());
        // The failed attempt must not have changed the depth.
        assertEquals(3, this.limits.getDepth(TYPE, "a"));

        third.get().close();
        second.get().close();
        first.get().close();

        assertEquals(0, this.limits.getDepth(TYPE, "a"));
    }

    @Test
    void reserveAllowsAdditionalLevels() throws Exception
    {
        try (RenderingLimitsScope first = this.limits.enter(TYPE, null);
            RenderingLimitsScope second = this.limits.enter(TYPE, null);
            RenderingLimitsScope third = this.limits.enter(TYPE, null)) {

            assertThrows(RecursionLimitExceededException.class, () -> this.limits.enter(TYPE, null));

            try (RenderingLimitsScope reserve = this.limits.enterReserve()) {
                // The reserve of the type is 2, so two more levels are allowed.
                try (RenderingLimitsScope fourth = this.limits.enter(TYPE, null);
                    RenderingLimitsScope fifth = this.limits.enter(TYPE, null)) {

                    assertThrows(RecursionLimitExceededException.class, () -> this.limits.enter(TYPE, null));
                }
            }

            // Outside of the reserve scope the normal limit applies again.
            assertThrows(RecursionLimitExceededException.class, () -> this.limits.enter(TYPE, null));
        }
    }

    @Test
    void depthSurvivesExecutionContextInheritance() throws Exception
    {
        try (RenderingLimitsScope level = this.limits.enter(TYPE, "a")) {
            inheritExecutionContext();

            assertEquals(1, this.limits.getDepth(TYPE, "a"));

            // Entering in the cloned context counts against the same depth.
            try (RenderingLimitsScope nested = this.limits.enter(TYPE, "a")) {
                assertEquals(2, this.limits.getDepth(TYPE, "a"));
            }
        }
    }

    @Test
    void nestedTransformationsCountTheDepth() throws Exception
    {
        try (RenderingLimitsScope transformation = this.limits.enterTransformation()) {
            assertEquals(1, this.limits.getDepth(DefaultRenderingLimits.TRANSFORMATION, null));

            try (RenderingLimitsScope nested = this.limits.enterTransformation()) {
                assertEquals(2, this.limits.getDepth(DefaultRenderingLimits.TRANSFORMATION, null));
            }

            assertEquals(1, this.limits.getDepth(DefaultRenderingLimits.TRANSFORMATION, null));
        }

        assertEquals(0, this.limits.getDepth(DefaultRenderingLimits.TRANSFORMATION, null));
    }

    @Test
    void saveAndRestoreCopyTheDepths() throws Exception
    {
        RenderingLimitsSnapshot snapshot;

        try (RenderingLimitsScope first = this.limits.enter(TYPE, null);
            RenderingLimitsScope second = this.limits.enter(TYPE, "a")) {

            snapshot = this.limits.save();
        }

        ExecutionContext spawningContext = this.executionContext;

        assertEquals(0, this.limits.getDepth(TYPE, null));

        // Simulate another thread with its own, fresh execution context.
        this.executionContext = new ExecutionContext();
        this.limits.restore(snapshot);

        // The depths are copied, so they start where the spawning execution was.
        assertEquals(1, this.limits.getDepth(TYPE, null));
        assertEquals(1, this.limits.getDepth(TYPE, "a"));

        try (RenderingLimitsScope nested = this.limits.enter(TYPE, null)) {
            assertEquals(2, this.limits.getDepth(TYPE, null));
        }

        this.executionContext = spawningContext;

        // The depths of the other execution didn't leak into this one.
        assertEquals(0, this.limits.getDepth(TYPE, null));
    }

    @Test
    void saveWithoutAnyStateIsEmpty()
    {
        assertTrue(this.limits.save().isEmpty());
    }

    @Test
    void withoutExecutionContextNothingIsEnforced() throws Exception
    {
        when(this.execution.getContext()).thenReturn(null);

        for (int i = 0; i < 10; ++i) {
            this.limits.enter(TYPE, null);
        }

        assertEquals(0, this.limits.getDepth(TYPE, null));
        assertTrue(this.limits.save().isEmpty());
        this.limits.enterReserve().close();
        this.limits.enterTransformation().close();
    }

    @Test
    void restoreOfNullOrForeignSnapshotIsIgnored()
    {
        this.limits.restore(null);
        this.limits.restore(() -> true);
        // A snapshot from another implementation cannot be restored as its state is inaccessible.
        this.limits.restore(() -> false);

        assertEquals(0, this.limits.getDepth(TYPE, null));
    }

    /**
     * Simulate what {@code ExecutionContextManager#clone} does: a fresh context inheriting from the current one.
     */
    private void inheritExecutionContext()
    {
        ExecutionContext clonedContext = new ExecutionContext();
        clonedContext.inheritFrom(this.executionContext);
        this.executionContext = clonedContext;
    }
}
