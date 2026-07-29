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
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.rendering.limits.RecursionType;
import org.xwiki.rendering.limits.RenderingLimitType;

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

    private static final String LIMITS_PREFIX = "rendering.limits.";

    private static final String PROFILE_INFIX = "profile.";

    private static final String LIMIT_SUFFIX = ".limit";

    private static final String MODE = "mode";

    @Inject
    @Named("restricted")
    private Provider<ConfigurationSource> configurationSourceProvider;

    @Inject
    private Logger logger;

    // Cache configuration values as converting the configuration value to Integer is kind of slow because it uses a
    // context component manager, while the limits are read for every single transformation and every single charge.
    private final Map<String, OptionalInt> recursionLimitCache = new ConcurrentHashMap<>();

    private final Map<String, OptionalLong> limitCache = new ConcurrentHashMap<>();

    private final Map<String, RenderingLimitsMode> modeCache = new ConcurrentHashMap<>();

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

    /**
     * Only the configured override is returned, the default limit is part of the {@link RenderingLimitType} itself.
     * This also means that a mock of this component behaves like a wiki without any configured limit.
     *
     * @param type the type of limit to get the budget for
     * @param profiles the profiles that apply to the current execution, the most specific one first
     * @return the configured budget for the given type, empty when it isn't configured
     */
    public OptionalLong getConfiguredLimit(RenderingLimitType type, List<String> profiles)
    {
        return this.limitCache.computeIfAbsent(getCacheKey(type.getName(), profiles), key -> {
            Long limit = getProperty(profiles, type.getName() + LIMIT_SUFFIX, Long.class);

            return limit == null ? OptionalLong.empty() : OptionalLong.of(limit);
        });
    }

    /**
     * @param profiles the profiles that apply to the current execution, the most specific one first
     * @return how the budgets shall be applied, {@link RenderingLimitsMode#ENFORCE} unless configured otherwise
     */
    public RenderingLimitsMode getMode(List<String> profiles)
    {
        return this.modeCache.computeIfAbsent(getCacheKey(MODE, profiles),
            key -> parseMode(getProperty(profiles, MODE, String.class)));
    }

    private RenderingLimitsMode parseMode(String value)
    {
        if (StringUtils.isBlank(value)) {
            return RenderingLimitsMode.ENFORCE;
        }

        RenderingLimitsMode parsedMode =
            EnumUtils.getEnum(RenderingLimitsMode.class, value.trim().toUpperCase(Locale.ROOT));

        if (parsedMode == null) {
            this.logger.warn("Ignoring the unknown rendering limits mode [{}], using [{}] instead. Supported modes"
                + " are {}.", value, RenderingLimitsMode.ENFORCE, RenderingLimitsMode.values());

            return RenderingLimitsMode.ENFORCE;
        }

        return parsedMode;
    }

    /**
     * @param profiles the profiles to look the property up in before falling back to the global configuration
     * @param suffix the part of the property name that follows the prefix of the limits and, if any, of the profile
     * @return the first configured value, {@code null} when it is configured in none of them
     */
    private <T> T getProperty(List<String> profiles, String suffix, Class<T> valueClass)
    {
        for (String profile : profiles) {
            T value = getProperty(LIMITS_PREFIX + PROFILE_INFIX + profile + '.' + suffix, valueClass);

            if (value != null) {
                return value;
            }
        }

        return getProperty(LIMITS_PREFIX + suffix, valueClass);
    }

    private <T> T getProperty(String key, Class<T> valueClass)
    {
        return this.configurationSourceProvider.get().getProperty(key, valueClass, null);
    }

    private static String getCacheKey(String suffix, List<String> profiles)
    {
        return profiles.isEmpty() ? suffix : String.join(",", profiles) + '|' + suffix;
    }
}
