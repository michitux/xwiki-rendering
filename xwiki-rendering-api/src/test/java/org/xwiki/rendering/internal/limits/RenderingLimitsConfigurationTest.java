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

import java.util.OptionalInt;

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.rendering.limits.RecursionType;
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
}
