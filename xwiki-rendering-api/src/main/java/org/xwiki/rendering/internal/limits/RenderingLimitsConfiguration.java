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

import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.rendering.limits.RecursionType;

/**
 * A caching wrapper around the configuration of the limits that apply to the rendering of a page.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Component(roles = RenderingLimitsConfiguration.class)
@Singleton
public class RenderingLimitsConfiguration
{
    private static final String RECURSION_PREFIX = "rendering.recursion.";

    private static final String LIMIT_SUFFIX = ".limit";

    @Inject
    @Named("restricted")
    private Provider<ConfigurationSource> configurationSourceProvider;

    // Cache configuration values as converting the configuration value to Integer is kind of slow because it uses a
    // context component manager, while the limits are read for every single transformation.
    private final Map<String, OptionalInt> recursionLimitCache = new ConcurrentHashMap<>();

    /**
     * Only the configured override is returned, the default limit is part of the {@link RecursionType} itself. This
     * also means that a mock of this component behaves like a wiki without any configured limit.
     *
     * @param type the type of recursion to get the limit for
     * @return the configured maximum recursion depth for the given type, empty when it isn't configured
     */
    public OptionalInt getConfiguredRecursionLimit(RecursionType type)
    {
        return this.recursionLimitCache.computeIfAbsent(type.getName(), name -> {
            Integer limit = getProperty(RECURSION_PREFIX + name + LIMIT_SUFFIX, Integer.class);

            return limit == null ? OptionalInt.empty() : OptionalInt.of(limit);
        });
    }

    private <T> T getProperty(String key, Class<T> valueClass)
    {
        return this.configurationSourceProvider.get().getProperty(key, valueClass, null);
    }
}
