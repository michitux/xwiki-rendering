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

import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.limits.RenderingLimitsProfileResolver;

/**
 * Default implementation of {@link RenderingLimitsProfileResolver} that always uses the global configuration.
 * <p>
 * Whether an execution is special enough to deserve its own profile depends on things that the rendering itself doesn't
 * know about, in particular whether it runs inside a job, so this implementation is expected to be overridden.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Component
@Singleton
public class DefaultRenderingLimitsProfileResolver implements RenderingLimitsProfileResolver
{
    @Override
    public List<String> getCurrentProfiles()
    {
        return List.of();
    }
}
