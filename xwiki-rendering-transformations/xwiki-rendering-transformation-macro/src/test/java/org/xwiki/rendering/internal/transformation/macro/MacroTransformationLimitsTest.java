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
import java.util.Map;
import java.util.OptionalLong;

import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.internal.limits.RenderingLimitsConfiguration;
import org.xwiki.rendering.limits.RenderingLimitType;
import org.xwiki.rendering.limits.RenderingLimits;
import org.xwiki.rendering.limits.RenderingLimitsScope;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.TransformationContext;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MacroTransformation} with the real {@link org.xwiki.rendering.limits.RenderingLimits}
 * implementation, so that the wiring between the transformation and the limits is covered and not only the calls the
 * transformation performs.
 *
 * @version $Id$
 */
@AllComponents(excludes = { IsolatedExecutionConfiguration.class, RenderingLimitsConfiguration.class })
@ComponentTest
class MacroTransformationLimitsTest
{
    private static final String RECURSIVE_MACRO = "testrecursivemacro";

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    @Named("macro")
    private MacroTransformation transformation;

    @MockComponent
    private IsolatedExecutionConfiguration isolatedExecutionConfiguration;

    /**
     * Mocked to lower the limits, everything else about the limits is the real implementation.
     */
    @MockComponent
    private RenderingLimitsConfiguration limitsConfiguration;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.isolatedExecutionConfiguration.isExecutionIsolated(anyString(), anyBoolean()))
            .thenAnswer(invocation -> invocation.getArgument(1));

        // The limits are stored in the execution context, so there needs to be one for anything to be counted.
        this.componentManager.<Execution>getInstance(Execution.class).setContext(new ExecutionContext());
    }

    @AfterEach
    void tearDown()
    {
        // Exceeding a limit is logged, that message itself is verified in DefaultRenderingLimitsTest.
        this.logCapture.ignoreAllMessages(
            List.of(event -> event.getMessage().contains("limit for rendering a page has been exceeded")));
    }

    @Test
    void macroExecutionsLimitStopsAMacroThatGeneratesItself() throws Exception
    {
        when(this.limitsConfiguration.getConfiguredLimit(eq(RenderingLimitType.MACRO_EXECUTIONS), any()))
            .thenReturn(OptionalLong.of(3));

        XDOM dom = new XDOM(List.of((Block) new MacroBlock(RECURSIVE_MACRO, Map.of(), false)));

        String result = transformAndRenderEvents(dom);

        // The macro generates itself again, so only the limit stops the transformation. Three macros have been
        // executed and the fourth one, generated by the third, is replaced by the limit error, so there are four
        // macro markers in total.
        assertEquals(4, StringUtils.countMatches(result, "beginMacroMarkerStandalone [" + RECURSIVE_MACRO + "] []"));
        assertEquals(1, StringUtils.countMatches(result, "beginGroup [[class]=[xwikirenderingerror]]"));
        assertTrue(result.contains("The [" + RECURSIVE_MACRO + "] macro couldn't be executed as the [macro.executions]"
            + " limit of [3] for rendering a page has been reached."), result);
    }

    @Test
    void documentSizeLimitDropsWhatTheMacroProduced() throws Exception
    {
        when(this.limitsConfiguration.getConfiguredLimit(eq(RenderingLimitType.DOCUMENT_SIZE), any()))
            .thenReturn(OptionalLong.of(1));

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("testsimplemacro", Map.of(), false)));

        String result = transformAndRenderEvents(dom);

        assertTrue(result.contains("The [testsimplemacro] macro couldn't be executed as the [document.size] limit of"
            + " [1] for rendering a page has been reached."), result);
        // What the macro produced is dropped instead of being kept and sent to the client.
        assertFalse(result.contains("onWord [simplemacro"), result);
    }

    @Test
    void whatAMacroWhoseExecutionExhaustedTheBudgetProducedIsKept() throws Exception
    {
        when(this.limitsConfiguration.getConfiguredLimit(eq(RenderingLimitType.DOCUMENT_SIZE), any()))
            .thenReturn(OptionalLong.of(TestExhaustingMacro.CHARGED_SIZE / 2));

        XDOM dom = new XDOM(List.of(new MacroBlock("testexhaustingmacro", Map.of(), false),
            new MacroBlock("testsimplemacro", Map.of(), false)));

        String result = transformAndRenderEvents(dom);

        // The macro isn't what produced the content that is too large, it only wraps content that has been counted and
        // reported already, like the [velocity] macro of a sheet whose result contains a whole page. Dropping its
        // result would remove that content from the document, which for such a macro means losing everything.
        assertTrue(result.contains("onWord [exhaustingmacro"), result);
        // The transformation still stops, so the next macro isn't executed and reports the exhausted budget.
        assertFalse(result.contains("onWord [simplemacro"), result);
        assertTrue(result.contains("The [testsimplemacro] macro couldn't be executed as the [document.size] limit of"
            + " [5000] for rendering a page has been reached."), result);
    }

    @Test
    void whatAMacroWhoseResultExhaustedTheBudgetProducedIsKept() throws Exception
    {
        // The budget is exactly used up by what the macro charges while executing, so it is the result of the macro
        // that exhausts it.
        when(this.limitsConfiguration.getConfiguredLimit(eq(RenderingLimitType.DOCUMENT_SIZE), any()))
            .thenReturn(OptionalLong.of(TestExhaustingMacro.CHARGED_SIZE));

        XDOM dom = new XDOM(List.of(new MacroBlock("testexhaustingmacro", Map.of(), false),
            new MacroBlock("testsimplemacro", Map.of(), false)));

        String result = transformAndRenderEvents(dom);

        // The result is far smaller than what a whole page may contain, so it isn't what makes the document too large.
        assertTrue(result.contains("onWord [exhaustingmacro"), result);
        assertFalse(result.contains("onWord [simplemacro"), result);
        assertTrue(result.contains("The [testsimplemacro] macro couldn't be executed as the [document.size] limit of"
            + " [10000] for rendering a page has been reached."), result);
    }

    @Test
    void whatAMacroWhoseExecutionExhaustedTheBudgetProducedIsKeptEvenWhenItIsLargerThanTheLimit() throws Exception
    {
        // The budget is exhausted many times over by what the macro triggers while executing, so its result is larger
        // than the limit even though the macro produced almost nothing itself. This is the shape of a [velocity] macro
        // of a sheet that displays a huge page: its result contains that whole page.
        when(this.limitsConfiguration.getConfiguredLimit(eq(RenderingLimitType.DOCUMENT_SIZE), any()))
            .thenReturn(OptionalLong.of(1));

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("testexhaustingmacro", Map.of(), false)));

        String result = transformAndRenderEvents(dom);

        assertTrue(result.contains("onWord [exhaustingmacro"), result);
    }

    @Test
    void whatAMacroOfAnErrorMessageProducedIsKept() throws Exception
    {
        when(this.limitsConfiguration.getConfiguredLimit(eq(RenderingLimitType.DOCUMENT_SIZE), any()))
            .thenReturn(OptionalLong.of(1));

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("testsimplemacro", Map.of(), false)));

        String result;
        RenderingLimits limits = this.componentManager.getInstance(RenderingLimits.class);

        // Simulate the rendering of an error message that has almost used up the reserve, so that the macro executed
        // here is the one whose result doesn't fit anymore.
        try (RenderingLimitsScope reserve = limits.enterReserve()) {
            limits.charge(RenderingLimitType.DOCUMENT_SIZE, RenderingLimitType.DOCUMENT_SIZE.getReserve());

            result = transformAndRenderEvents(dom);
        }

        // Dropping the content would drop the error message itself, so it is kept even though it doesn't fit.
        assertTrue(result.contains("onWord [simplemacro"), result);
        assertFalse(result.contains("xwikirenderingerror"), result);
    }

    @Test
    void noLimitIsReportedInThePlaceOfAMacroOfAnErrorMessage() throws Exception
    {
        when(this.limitsConfiguration.getConfiguredLimit(eq(RenderingLimitType.DOCUMENT_SIZE), any()))
            .thenReturn(OptionalLong.of(1));

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("testsimplemacro", Map.of(), false)));

        String result;
        RenderingLimits limits = this.componentManager.getInstance(RenderingLimits.class);

        // Simulate the rendering of an error message that used up the reserve, e.g. because it embeds a huge stack
        // trace, so that not even the first macro of the message can be executed.
        try (RenderingLimitsScope reserve = limits.enterReserve()) {
            limits.charge(RenderingLimitType.DOCUMENT_SIZE, RenderingLimitType.DOCUMENT_SIZE.getReserve() + 5);

            result = transformAndRenderEvents(dom);
        }

        // Reporting the limit here would replace the message about the macro that actually exhausted the limit with a
        // message about a macro of the error template.
        assertFalse(result.contains("xwikirenderingerror"), result);
        // No warning is logged for it either: a rendering that reached a limit stops a lot of macros, in every nested
        // and asynchronous execution it spawned, so only the exhausted limit itself is reported, once.
        assertEquals(1, this.logCapture.size());
        assertTrue(this.logCapture.getMessage(0).contains("[document.size] limit for rendering a page has been"
            + " exceeded"), this.logCapture.getMessage(0));
    }

    private String transformAndRenderEvents(XDOM dom) throws Exception
    {
        this.transformation.transform(dom, new TransformationContext(dom, Syntax.XWIKI_2_0));

        WikiPrinter printer = new DefaultWikiPrinter();
        BlockRenderer eventBlockRenderer =
            this.componentManager.getInstance(BlockRenderer.class, Syntax.EVENT_1_0.toIdString());
        eventBlockRenderer.render(dom, printer);
        return printer.toString();
    }
}
