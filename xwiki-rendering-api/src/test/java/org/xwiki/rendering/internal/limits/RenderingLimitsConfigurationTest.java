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
import java.util.OptionalInt;
import java.util.OptionalLong;

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.rendering.limits.RecursionType;
import org.xwiki.rendering.limits.RenderingLimitType;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RenderingLimitsConfiguration}.
 *
 * @version $Id$
 */
@ComponentTest
class RenderingLimitsConfigurationTest
{
    private static final RecursionType TYPE = new RecursionType("test", 3, 1);

    private static final String PROPERTY = "rendering.recursion.test.limit";

    private static final RenderingLimitType LIMIT_TYPE = new RenderingLimitType("test", 100, 10, "1");

    private static final String LIMIT_PROPERTY = "rendering.limits.test.limit";

    private static final String MODE_PROPERTY = "rendering.limits.mode";

    private static final List<String> PROFILES = List.of("export.pdf", "job");

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @MockComponent
    @Named("restricted")
    private ConfigurationSource configurationSource;

    @InjectMockComponents
    private RenderingLimitsConfiguration configuration;

    @Test
    void getConfiguredRecursionLimit()
    {
        when(this.configurationSource.getProperty(PROPERTY, Integer.class, null)).thenReturn(42);

        assertEquals(OptionalInt.of(42), this.configuration.getConfiguredRecursionLimit(TYPE));

        // Verify that the value is cached.
        assertEquals(OptionalInt.of(42), this.configuration.getConfiguredRecursionLimit(TYPE));
        verify(this.configurationSource).getProperty(PROPERTY, Integer.class, null);
        verifyNoMoreInteractions(this.configurationSource);
    }

    @Test
    void getConfiguredRecursionLimitWithoutConfiguration()
    {
        assertEquals(OptionalInt.empty(), this.configuration.getConfiguredRecursionLimit(TYPE));

        // Verify that the absence of a value is cached, too.
        assertEquals(OptionalInt.empty(), this.configuration.getConfiguredRecursionLimit(TYPE));
        verify(this.configurationSource).getProperty(PROPERTY, Integer.class, null);
        verifyNoMoreInteractions(this.configurationSource);
    }

    @Test
    void getConfiguredRecursionLimitForSeveralTypes()
    {
        when(this.configurationSource.getProperty(PROPERTY, Integer.class, null)).thenReturn(42);
        when(this.configurationSource.getProperty("rendering.recursion.other.limit", Integer.class, null))
            .thenReturn(7);

        assertEquals(OptionalInt.of(42), this.configuration.getConfiguredRecursionLimit(TYPE));
        assertEquals(OptionalInt.of(7),
            this.configuration.getConfiguredRecursionLimit(new RecursionType("other", 3, 1)));
    }

    @Test
    void getConfiguredLimit()
    {
        when(this.configurationSource.getProperty(LIMIT_PROPERTY, Long.class, null)).thenReturn(4200L);

        assertEquals(OptionalLong.of(4200), this.configuration.getConfiguredLimit(LIMIT_TYPE, List.of()));

        // Verify that the value is cached.
        assertEquals(OptionalLong.of(4200), this.configuration.getConfiguredLimit(LIMIT_TYPE, List.of()));
        verify(this.configurationSource).getProperty(LIMIT_PROPERTY, Long.class, null);
        verifyNoMoreInteractions(this.configurationSource);
    }

    @Test
    void getConfiguredLimitWithoutConfiguration()
    {
        assertEquals(OptionalLong.empty(), this.configuration.getConfiguredLimit(LIMIT_TYPE, List.of()));

        // Verify that the absence of a value is cached, too.
        assertEquals(OptionalLong.empty(), this.configuration.getConfiguredLimit(LIMIT_TYPE, List.of()));
        verify(this.configurationSource).getProperty(LIMIT_PROPERTY, Long.class, null);
        verifyNoMoreInteractions(this.configurationSource);
    }

    @Test
    void getConfiguredLimitForSeveralTypes()
    {
        when(this.configurationSource.getProperty(LIMIT_PROPERTY, Long.class, null)).thenReturn(4200L);
        when(this.configurationSource.getProperty("rendering.limits.other.limit", Long.class, null)).thenReturn(7L);

        assertEquals(OptionalLong.of(4200), this.configuration.getConfiguredLimit(LIMIT_TYPE, List.of()));
        assertEquals(OptionalLong.of(7),
            this.configuration.getConfiguredLimit(new RenderingLimitType("other", 100, 10, "1"), List.of()));
    }

    @ParameterizedTest
    @CsvSource({ "enforce, ENFORCE", "LOG, LOG", " disabled , DISABLED" })
    void getMode(String value, RenderingLimitsMode expectedMode)
    {
        when(this.configurationSource.getProperty(MODE_PROPERTY, String.class, null)).thenReturn(value);

        assertEquals(expectedMode, this.configuration.getMode(List.of()));

        // Verify that the value is cached.
        assertEquals(expectedMode, this.configuration.getMode(List.of()));
        verify(this.configurationSource).getProperty(MODE_PROPERTY, String.class, null);
        verifyNoMoreInteractions(this.configurationSource);
    }

    @Test
    void getModeWithoutConfiguration()
    {
        assertEquals(RenderingLimitsMode.ENFORCE, this.configuration.getMode(List.of()));
    }

    @Test
    void getModeWithUnknownValue()
    {
        when(this.configurationSource.getProperty(MODE_PROPERTY, String.class, null)).thenReturn("wrong");

        assertEquals(RenderingLimitsMode.ENFORCE, this.configuration.getMode(List.of()));
        assertEquals("Ignoring the unknown rendering limits mode [wrong], using [ENFORCE] instead. Supported modes"
            + " are [ENFORCE, LOG, DISABLED].", this.logCapture.getMessage(0));
    }

    @Test
    void getConfiguredLimitFromTheMostSpecificProfile()
    {
        when(this.configurationSource.getProperty(LIMIT_PROPERTY, Long.class, null)).thenReturn(1L);
        when(this.configurationSource.getProperty("rendering.limits.profile.job.test.limit", Long.class, null))
            .thenReturn(2L);
        when(this.configurationSource.getProperty("rendering.limits.profile.export.pdf.test.limit", Long.class, null))
            .thenReturn(3L);

        assertEquals(OptionalLong.of(3), this.configuration.getConfiguredLimit(LIMIT_TYPE, PROFILES));
        assertEquals(OptionalLong.of(2), this.configuration.getConfiguredLimit(LIMIT_TYPE, List.of("job")));
        assertEquals(OptionalLong.of(1), this.configuration.getConfiguredLimit(LIMIT_TYPE, List.of()));
    }

    @Test
    void getConfiguredLimitFallsBackToTheLessSpecificProfiles()
    {
        when(this.configurationSource.getProperty("rendering.limits.profile.job.test.limit", Long.class, null))
            .thenReturn(2L);

        assertEquals(OptionalLong.of(2), this.configuration.getConfiguredLimit(LIMIT_TYPE, PROFILES));
    }

    @Test
    void getModeFromTheMostSpecificProfile()
    {
        when(this.configurationSource.getProperty(MODE_PROPERTY, String.class, null)).thenReturn("disabled");
        when(this.configurationSource.getProperty("rendering.limits.profile.export.pdf.mode", String.class, null))
            .thenReturn("log");

        assertEquals(RenderingLimitsMode.LOG, this.configuration.getMode(PROFILES));
        assertEquals(RenderingLimitsMode.DISABLED, this.configuration.getMode(List.of()));
    }
}
