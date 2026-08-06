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
package org.xwiki.rendering.internal.transformation.macro;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.limits.RenderingLimitType;
import org.xwiki.rendering.limits.RenderingLimits;
import org.xwiki.rendering.macro.AbstractNoParameterMacro;
import org.xwiki.rendering.transformation.MacroTransformationContext;

/**
 * Stands in for a macro whose execution triggers a rendering that exhausts the size budget, like a macro that renders
 * the content of a page, without producing a large result itself.
 */
@Component
@Named("testexhaustingmacro")
@Singleton
public class TestExhaustingMacro extends AbstractNoParameterMacro
{
    /**
     * The amount this macro charges while it executes, i.e. what the rendering it triggers would have charged.
     */
    public static final long CHARGED_SIZE = 10000;

    @Inject
    private RenderingLimits renderingLimits;

    public TestExhaustingMacro()
    {
        super("Exhausting Macro");
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(Object parameters, String content, MacroTransformationContext context)
    {
        this.renderingLimits.charge(RenderingLimitType.DOCUMENT_SIZE, CHARGED_SIZE);

        return List.of(new ParagraphBlock(List.of(new WordBlock("exhaustingmacro"))));
    }

    @Override
    public boolean isExecutionIsolated(Object parameters, String content)
    {
        return true;
    }
}
