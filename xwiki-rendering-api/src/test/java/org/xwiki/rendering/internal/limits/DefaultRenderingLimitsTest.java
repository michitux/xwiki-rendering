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

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.rendering.limits.RecursionLimitExceededException;
import org.xwiki.rendering.limits.RecursionType;
import org.xwiki.rendering.limits.RenderingLimitType;
import org.xwiki.rendering.limits.RenderingLimitsProfileResolver;
import org.xwiki.rendering.limits.RenderingLimitsScope;
import org.xwiki.rendering.limits.RenderingLimitsSnapshot;
import org.xwiki.rendering.transformation.RenderingContext;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final RenderingLimitType LIMIT_TYPE = new RenderingLimitType("test", 100, 10, "1");

    private static final RenderingLimitType OTHER_LIMIT_TYPE = new RenderingLimitType("other", 100, 10, "1");

    private static final RenderingLimitType TIME_TYPE = new RenderingLimitType("time", 10_000, 100, "ms");

    /**
     * How long to wait when the test needs some measurable time to have elapsed.
     */
    private static final long SLEEP_MILLIS = 50;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private DefaultRenderingLimits limits;

    @MockComponent
    private Execution execution;

    @MockComponent
    private RenderingLimitsConfiguration configuration;

    @MockComponent
    private RenderingLimitsProfileResolver profileResolver;

    @MockComponent
    private RenderingContext renderingContext;

    private ExecutionContext executionContext = new ExecutionContext();

    @BeforeEach
    void setUp()
    {
        when(this.execution.getContext()).thenAnswer(invocation -> this.executionContext);
        when(this.profileResolver.getCurrentProfiles()).thenReturn(List.of());
        when(this.configuration.getMode(List.of())).thenReturn(RenderingLimitsMode.ENFORCE);
    }

    @AfterEach
    void tearDown()
    {
        // Exceeding a limit is logged, the message itself is verified in the tests that are about the reporting.
        this.logCapture.ignoreAllMessages(
            List.of(event -> event.getMessage().contains("limit for rendering a page has been exceeded")));
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
    void budgetIsCharged()
    {
        assertEquals(0, this.limits.getCharged(LIMIT_TYPE));

        this.limits.charge(LIMIT_TYPE, 40);
        assertFalse(this.limits.isExceeded(LIMIT_TYPE));
        assertEquals(40, this.limits.getCharged(LIMIT_TYPE));
        assertEquals(0, this.limits.getCharged(OTHER_LIMIT_TYPE));

        // Charging exactly the limit doesn't exceed it.
        this.limits.charge(LIMIT_TYPE, 60);
        assertFalse(this.limits.isExceeded(LIMIT_TYPE));
        assertEquals(100, this.limits.getCharged(LIMIT_TYPE));

        // Beyond the limit the charge is still counted.
        this.limits.charge(LIMIT_TYPE, 1);
        assertTrue(this.limits.isExceeded(LIMIT_TYPE));
        assertFalse(this.limits.isExceeded(OTHER_LIMIT_TYPE));
        assertEquals(101, this.limits.getCharged(LIMIT_TYPE));
    }

    @Test
    void configuredLimitOverridesTheDefault()
    {
        when(this.configuration.getConfiguredLimit(LIMIT_TYPE, List.of())).thenReturn(OptionalLong.of(10));

        this.limits.charge(LIMIT_TYPE, 10);
        assertFalse(this.limits.isExceeded(LIMIT_TYPE));

        this.limits.charge(LIMIT_TYPE, 1);
        assertTrue(this.limits.isExceeded(LIMIT_TYPE));
    }

    @Test
    void exceedsDoesNotCharge()
    {
        assertFalse(this.limits.exceeds(LIMIT_TYPE, 100));
        assertTrue(this.limits.exceeds(LIMIT_TYPE, 101));
        assertEquals(0, this.limits.getCharged(LIMIT_TYPE));

        this.limits.charge(LIMIT_TYPE, 60);

        assertFalse(this.limits.exceeds(LIMIT_TYPE, 40));
        assertTrue(this.limits.exceeds(LIMIT_TYPE, 41));
    }

    @Test
    void reserveAllowsChargingMore()
    {
        this.limits.charge(LIMIT_TYPE, 101);
        assertTrue(this.limits.isExceeded(LIMIT_TYPE));

        try (RenderingLimitsScope reserve = this.limits.enterReserve()) {
            // The reserve of the type is 10 and 101 have been charged, so 9 more fit.
            assertFalse(this.limits.isExceeded(LIMIT_TYPE));
            assertFalse(this.limits.exceeds(LIMIT_TYPE, 9));
            assertTrue(this.limits.exceeds(LIMIT_TYPE, 10));

            this.limits.charge(LIMIT_TYPE, 9);
            assertFalse(this.limits.isExceeded(LIMIT_TYPE));
        }

        assertTrue(this.limits.isExceeded(LIMIT_TYPE));
    }

    @Test
    void chargeIsCappedJustAboveTheLimitSoThatTheReserveIsStillAvailable()
    {
        // A single charge can overshoot the limit by an arbitrary amount, e.g. when a macro produced a huge amount of
        // content, but only what is needed to see that the limit is exceeded is counted.
        this.limits.charge(LIMIT_TYPE, 1000);

        assertEquals(101, this.limits.getCharged(LIMIT_TYPE));
        assertTrue(this.limits.isExceeded(LIMIT_TYPE));

        try (RenderingLimitsScope reserve = this.limits.enterReserve()) {
            // Without the cap the overshoot would have consumed the reserve of 10 and reporting the exceeded limit
            // would immediately hit the limit again.
            assertFalse(this.limits.isExceeded(LIMIT_TYPE));
            assertFalse(this.limits.exceeds(LIMIT_TYPE, 9));
            assertTrue(this.limits.exceeds(LIMIT_TYPE, 10));
        }
    }

    @Test
    void chargeInsideAReserveIsCappedAtTheRaisedLimit()
    {
        try (RenderingLimitsScope reserve = this.limits.enterReserve()) {
            this.limits.charge(LIMIT_TYPE, 1000);

            // The cap follows the limit that applies, i.e. the one raised by the reserve of 10.
            assertEquals(111, this.limits.getCharged(LIMIT_TYPE));
            assertTrue(this.limits.isExceeded(LIMIT_TYPE));
        }

        // What has been charged in the reserve isn't given back, so the reserve is the headroom for all the error
        // messages of a rendering together and not one per message.
        assertTrue(this.limits.isExceeded(LIMIT_TYPE));
        assertEquals(111, this.limits.getCharged(LIMIT_TYPE));
    }

    @Test
    void isReserveOpen()
    {
        assertFalse(this.limits.isReserveOpen());

        try (RenderingLimitsScope reserve = this.limits.enterReserve()) {
            assertTrue(this.limits.isReserveOpen());

            try (RenderingLimitsScope nested = this.limits.enterReserve()) {
                assertTrue(this.limits.isReserveOpen());
            }

            // The nested scope isn't the one that opened the reserve, so closing it doesn't close the reserve.
            assertTrue(this.limits.isReserveOpen());
        }

        assertFalse(this.limits.isReserveOpen());
    }

    @Test
    void budgetSurvivesExecutionContextInheritance()
    {
        this.limits.charge(LIMIT_TYPE, 40);

        inheritExecutionContext();

        assertEquals(40, this.limits.getCharged(LIMIT_TYPE));
        this.limits.charge(LIMIT_TYPE, 2);
        assertEquals(42, this.limits.getCharged(LIMIT_TYPE));
    }

    @Test
    void eachOutermostTransformationGetsFreshBudgets() throws Exception
    {
        this.limits.charge(LIMIT_TYPE, 100);

        try (RenderingLimitsScope transformation = this.limits.enterTransformation()) {
            // The outermost transformation starts a new rendering, so it doesn't continue what was charged before it.
            assertEquals(0, this.limits.getCharged(LIMIT_TYPE));
            this.limits.charge(LIMIT_TYPE, 100);
            assertFalse(this.limits.isExceeded(LIMIT_TYPE));
        }

        // The budgets aren't dropped when the transformation ends, they are replaced by the next outermost one. This
        // is what lets charges from outside a transformation, like spawning an asynchronous execution while rendering
        // a template, accumulate instead of getting a throwaway budget each.
        assertEquals(100, this.limits.getCharged(LIMIT_TYPE));

        try (RenderingLimitsScope transformation = this.limits.enterTransformation()) {
            assertEquals(0, this.limits.getCharged(LIMIT_TYPE));
        }
    }

    @Test
    void nestedTransformationsShareTheBudgetsAndCountTheDepth() throws Exception
    {
        try (RenderingLimitsScope transformation = this.limits.enterTransformation()) {
            this.limits.charge(LIMIT_TYPE, 40);

            try (RenderingLimitsScope nested = this.limits.enterTransformation()) {
                assertEquals(2, this.limits.getDepth(DefaultRenderingLimits.TRANSFORMATION, null));
                assertEquals(40, this.limits.getCharged(LIMIT_TYPE));

                this.limits.charge(LIMIT_TYPE, 2);
            }

            assertEquals(1, this.limits.getDepth(DefaultRenderingLimits.TRANSFORMATION, null));
            assertEquals(42, this.limits.getCharged(LIMIT_TYPE));
        }

        assertEquals(0, this.limits.getDepth(DefaultRenderingLimits.TRANSFORMATION, null));
    }

    @Test
    void propagatedBudgetsAreNotResetByTheOutermostTransformation() throws Exception
    {
        this.limits.charge(LIMIT_TYPE, 40);

        RenderingLimitsSnapshot snapshot = this.limits.save();

        // Simulate the asynchronous rendering: another thread continuing the rendering that spawned it, so its
        // outermost transformation must keep the propagated budgets instead of starting a rendering of its own.
        this.executionContext = new ExecutionContext();
        this.limits.restore(snapshot);

        try (RenderingLimitsScope transformation = this.limits.enterTransformation()) {
            assertEquals(40, this.limits.getCharged(LIMIT_TYPE));
        }
    }

    @Test
    void aSecondRenderingInTheSameExecutionContextStartsFromZero() throws Exception
    {
        // The shape of an execution context that outlives a single rendering, like a mail preparation thread or a job
        // that renders many documents.
        try (RenderingLimitsScope first = this.limits.enterTransformation()) {
            this.limits.charge(LIMIT_TYPE, 100);
            this.limits.chargeElapsedTime(TIME_TYPE);
        }

        Thread.sleep(2 * SLEEP_MILLIS);

        try (RenderingLimitsScope second = this.limits.enterTransformation()) {
            assertEquals(0, this.limits.getCharged(LIMIT_TYPE));

            this.limits.chargeElapsedTime(TIME_TYPE);

            // The clock restarted with the budgets, so the time between the two renderings isn't charged to the second
            // one, which would otherwise refuse every macro after a delay.
            long charged = this.limits.getCharged(TIME_TYPE);
            assertTrue(charged < SLEEP_MILLIS, "Expected the second rendering to charge its own elapsed time only but"
                + " got [%d] ms".formatted(charged));
        }
    }

    @Test
    void profilesAreResolvedOnceForTheWholeRendering()
    {
        List<String> profiles = List.of("export.pdf", "job");
        when(this.profileResolver.getCurrentProfiles()).thenReturn(profiles);
        when(this.configuration.getMode(profiles)).thenReturn(RenderingLimitsMode.ENFORCE);
        when(this.configuration.getConfiguredLimit(LIMIT_TYPE, profiles)).thenReturn(OptionalLong.of(10));

        this.limits.charge(LIMIT_TYPE, 10);
        assertFalse(this.limits.isExceeded(LIMIT_TYPE));

        // Even when the resolver would answer differently now, the profiles of the ongoing rendering still apply.
        when(this.profileResolver.getCurrentProfiles()).thenReturn(List.of());

        this.limits.charge(LIMIT_TYPE, 1);
        assertTrue(this.limits.isExceeded(LIMIT_TYPE));
    }

    @Test
    void anOutermostTransformationResolvesTheProfilesAgain() throws Exception
    {
        this.limits.charge(LIMIT_TYPE, 1);

        List<String> profiles = List.of("export.pdf", "job");
        when(this.profileResolver.getCurrentProfiles()).thenReturn(profiles);
        when(this.configuration.getMode(profiles)).thenReturn(RenderingLimitsMode.ENFORCE);
        when(this.configuration.getConfiguredLimit(LIMIT_TYPE, profiles)).thenReturn(OptionalLong.of(10));

        try (RenderingLimitsScope transformation = this.limits.enterTransformation()) {
            this.limits.charge(LIMIT_TYPE, 10);
            assertFalse(this.limits.isExceeded(LIMIT_TYPE));

            this.limits.charge(LIMIT_TYPE, 1);
            assertTrue(this.limits.isExceeded(LIMIT_TYPE));
        }
    }

    @Test
    void restoredBudgetsKeepTheirProfiles()
    {
        List<String> profiles = List.of("export.pdf", "job");
        when(this.profileResolver.getCurrentProfiles()).thenReturn(profiles);
        when(this.configuration.getMode(profiles)).thenReturn(RenderingLimitsMode.ENFORCE);
        when(this.configuration.getConfiguredLimit(LIMIT_TYPE, profiles)).thenReturn(OptionalLong.of(10));

        this.limits.charge(LIMIT_TYPE, 10);
        RenderingLimitsSnapshot snapshot = this.limits.save();

        // Simulate the asynchronous rendering, which runs as a job of its own type but must not get fresh budgets.
        this.executionContext = new ExecutionContext();
        when(this.profileResolver.getCurrentProfiles()).thenReturn(List.of("asyncrenderer", "job"));
        this.limits.restore(snapshot);

        this.limits.charge(LIMIT_TYPE, 1);
        assertTrue(this.limits.isExceeded(LIMIT_TYPE));
    }

    @Test
    void logModeCountsWithoutEnforcing()
    {
        when(this.configuration.getMode(List.of())).thenReturn(RenderingLimitsMode.LOG);

        this.limits.charge(LIMIT_TYPE, 1000);

        // The charge isn't capped in this mode as its whole point is to observe what content really consumes.
        assertEquals(1000, this.limits.getCharged(LIMIT_TYPE));
        assertFalse(this.limits.isExceeded(LIMIT_TYPE));
        assertFalse(this.limits.exceeds(LIMIT_TYPE, 1000));
    }

    @Test
    void disabledModeDoesNotCount()
    {
        when(this.configuration.getMode(List.of())).thenReturn(RenderingLimitsMode.DISABLED);

        this.limits.charge(LIMIT_TYPE, 1000);
        this.limits.chargeElapsedTime(TIME_TYPE);

        assertEquals(0, this.limits.getCharged(LIMIT_TYPE));
        assertEquals(0, this.limits.getCharged(TIME_TYPE));
        assertFalse(this.limits.isExceeded(LIMIT_TYPE));
        assertFalse(this.limits.exceeds(LIMIT_TYPE, 1000));
        // Nothing has been stored in the execution context, in particular no final property that would prevent the
        // limits from being enabled again for an execution context inheriting from this one.
        assertFalse(this.executionContext.hasProperty(DefaultRenderingLimits.ECONTEXT_KEY));
    }

    @Test
    void saveAndRestoreCopyTheDepthsAndShareTheBudgets() throws Exception
    {
        RenderingLimitsSnapshot snapshot;

        try (RenderingLimitsScope first = this.limits.enter(TYPE, null);
            RenderingLimitsScope second = this.limits.enter(TYPE, "a")) {

            this.limits.charge(LIMIT_TYPE, 40);

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

        // The budgets are shared, so what is charged here also counts in the spawning execution.
        assertEquals(40, this.limits.getCharged(LIMIT_TYPE));
        this.limits.charge(LIMIT_TYPE, 2);

        this.executionContext = spawningContext;

        assertEquals(42, this.limits.getCharged(LIMIT_TYPE));
        // The depths of the other execution didn't leak into this one.
        assertEquals(0, this.limits.getDepth(TYPE, null));
    }

    @Test
    void saveWithoutAnyStateIsEmpty()
    {
        assertTrue(this.limits.save().isEmpty());
    }

    @Test
    void saveOfAChargeOnlyStateIsNotEmpty()
    {
        this.limits.charge(LIMIT_TYPE, 1);

        assertFalse(this.limits.save().isEmpty());
    }

    @Test
    void withoutExecutionContextNothingIsEnforced() throws Exception
    {
        when(this.execution.getContext()).thenReturn(null);

        for (int i = 0; i < 10; ++i) {
            this.limits.enter(TYPE, null);
        }

        assertEquals(0, this.limits.getDepth(TYPE, null));
        this.limits.charge(LIMIT_TYPE, 1000);
        assertEquals(0, this.limits.getCharged(LIMIT_TYPE));
        assertFalse(this.limits.isExceeded(LIMIT_TYPE));
        assertFalse(this.limits.exceeds(LIMIT_TYPE, 1000));
        assertTrue(this.limits.save().isEmpty());
        assertFalse(this.limits.isReserveOpen());
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
        assertEquals(0, this.limits.getCharged(LIMIT_TYPE));
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

    @Test
    void elapsedTimeIsChargedOnlyOnce() throws Exception
    {
        // The elapsed time is measured from the moment the limits started to be tracked.
        this.limits.chargeElapsedTime(TIME_TYPE);

        Thread.sleep(SLEEP_MILLIS);

        this.limits.chargeElapsedTime(TIME_TYPE);

        long charged = this.limits.getCharged(TIME_TYPE);
        assertTrue(charged >= SLEEP_MILLIS, "Expected at least [%d] ms to be charged but got [%d]"
            .formatted(SLEEP_MILLIS, charged));

        // Charging again doesn't charge the elapsed time a second time, which is what makes it safe to call this from
        // every nested rendering.
        this.limits.chargeElapsedTime(TIME_TYPE);

        long chargedAgain = this.limits.getCharged(TIME_TYPE);
        assertTrue(chargedAgain < charged + SLEEP_MILLIS / 2,
            "Expected the elapsed time not to be charged twice but got [%d] after [%d]".formatted(chargedAgain,
                charged));
    }

    @Test
    void elapsedTimeIsMeasuredPerExecutionAndChargedToTheSharedBudget() throws Exception
    {
        this.limits.chargeElapsedTime(TIME_TYPE);
        Thread.sleep(SLEEP_MILLIS);
        this.limits.chargeElapsedTime(TIME_TYPE);
        long charged = this.limits.getCharged(TIME_TYPE);

        RenderingLimitsSnapshot snapshot = this.limits.save();

        // Simulate another thread with its own, fresh execution context, which measures its own elapsed time but
        // charges it against the same budget.
        this.executionContext = new ExecutionContext();
        this.limits.restore(snapshot);

        assertEquals(charged, this.limits.getCharged(TIME_TYPE));

        this.limits.chargeElapsedTime(TIME_TYPE);

        long chargedAgain = this.limits.getCharged(TIME_TYPE);
        assertTrue(chargedAgain < charged + SLEEP_MILLIS / 2,
            "Expected the other execution to charge its own elapsed time only but got [%d] after [%d]"
                .formatted(chargedAgain, charged));
    }

    @Test
    void elapsedTimeLimitIsEnforced() throws Exception
    {
        when(this.configuration.getConfiguredLimit(TIME_TYPE, List.of())).thenReturn(OptionalLong.of(1));

        this.limits.chargeElapsedTime(TIME_TYPE);
        assertFalse(this.limits.isExceeded(TIME_TYPE));

        Thread.sleep(SLEEP_MILLIS);

        this.limits.chargeElapsedTime(TIME_TYPE);
        assertTrue(this.limits.isExceeded(TIME_TYPE));
    }

    @Test
    void getLimit()
    {
        assertEquals(100, this.limits.getLimit(LIMIT_TYPE));

        when(this.configuration.getConfiguredLimit(LIMIT_TYPE, List.of())).thenReturn(OptionalLong.of(10));

        assertEquals(10, this.limits.getLimit(LIMIT_TYPE));

        try (RenderingLimitsScope reserve = this.limits.enterReserve()) {
            assertEquals(20, this.limits.getLimit(LIMIT_TYPE));
        }
    }

    @Test
    void exceedingALimitIsReportedOnce()
    {
        when(this.renderingContext.getTransformationId()).thenReturn("xwiki:Space.Page");

        this.limits.charge(LIMIT_TYPE, 101);
        this.limits.charge(LIMIT_TYPE, 1);
        this.limits.charge(OTHER_LIMIT_TYPE, 1);

        assertEquals(1, this.logCapture.size());
        assertEquals("The [test] limit for rendering a page has been exceeded while rendering [xwiki:Space.Page]:"
            + " [101] instead of the limit of [100] (profiles: []). A wiki administrator can change the limit with"
            + " the [rendering.limits.test.limit] property in xwiki.properties.", this.logCapture.getMessage(0));
    }

    @Test
    void theRealAmountIsReportedEvenThoughTheChargeIsCapped()
    {
        when(this.renderingContext.getTransformationId()).thenReturn("xwiki:Space.Page");

        this.limits.charge(LIMIT_TYPE, 60);
        this.limits.charge(LIMIT_TYPE, 1000);

        // The stored total is capped, but the message tells by how much the limit was really exceeded so that an
        // administrator can tell a limit that is slightly too low from content that is out of control.
        assertEquals(101, this.limits.getCharged(LIMIT_TYPE));
        assertEquals(1, this.logCapture.size());
        assertEquals("The [test] limit for rendering a page has been exceeded while rendering [xwiki:Space.Page]:"
            + " [1060] instead of the limit of [100] (profiles: []). A wiki administrator can change the limit with"
            + " the [rendering.limits.test.limit] property in xwiki.properties.", this.logCapture.getMessage(0));
    }

    @Test
    void exceedingALimitIsReportedInTheLogMode()
    {
        when(this.configuration.getMode(List.of())).thenReturn(RenderingLimitsMode.LOG);

        this.limits.charge(LIMIT_TYPE, 101);

        assertEquals(1, this.logCapture.size());
    }

    @Test
    void exceedingALimitIsNotReportedInTheDisabledMode()
    {
        when(this.configuration.getMode(List.of())).thenReturn(RenderingLimitsMode.DISABLED);

        this.limits.charge(LIMIT_TYPE, 101);

        assertEquals(0, this.logCapture.size());
    }
}
