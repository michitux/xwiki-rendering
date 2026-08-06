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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.properties.internal.DefaultBeanDescriptor;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.internal.limits.DefaultRenderingLimits;
import org.xwiki.rendering.limits.RenderingLimitType;
import org.xwiki.rendering.limits.RenderingLimits;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroRenderingLimitExceededException;
import org.xwiki.rendering.macro.descriptor.DefaultMacroDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.transformation.TransformationContext;
import org.xwiki.rendering.transformation.TransformationException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MacroTransformation}.
 *
 * @version $Id$
 */
@AllComponents(excludes = { IsolatedExecutionConfiguration.class, DefaultRenderingLimits.class })
@ComponentTest
class MacroTransformationTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    @Named("macro")
    private MacroTransformation transformation;

    @MockComponent
    private IsolatedExecutionConfiguration isolatedExecutionConfiguration;

    @MockComponent
    private RenderingLimits renderingLimits;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @BeforeEach
    void setUp()
    {
        // By default, return whatever the macro specified.
        when(this.isolatedExecutionConfiguration.isExecutionIsolated(anyString(), anyBoolean()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        // No limit is reached by default, which is also what an unstubbed mock answers - apart from the limit values
        // themselves, where the zero of an unstubbed mock would mean that no content fits at all.
        when(this.renderingLimits.getLimit(any())).thenReturn(Long.MAX_VALUE);
    }

    /**
     * Test that a simple macro is correctly evaluated.
     */
    @Test
    void transformSimpleMacro() throws Exception
    {
        String expected = """
            beginDocument
            beginMacroMarkerStandalone [testsimplemacro] []
            beginParagraph
            onWord [simplemacro0]
            endParagraph
            endMacroMarkerStandalone [testsimplemacro] []
            endDocument""";

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("testsimplemacro", Collections.emptyMap(), false)));

        this.transformation.transform(dom, new TransformationContext(dom, Syntax.XWIKI_2_0));

        WikiPrinter printer = new DefaultWikiPrinter();
        BlockRenderer eventBlockRenderer =
            this.componentManager.getInstance(BlockRenderer.class, Syntax.EVENT_1_0.toIdString());
        eventBlockRenderer.render(dom, printer);
        assertEquals(expected, printer.toString());
    }

    /**
     * Test that a macro can generate another macro.
     */
    @Test
    void transformNestedMacro() throws Exception
    {
        String expected = """
            beginDocument
            beginMacroMarkerStandalone [testnestedmacro] []
            beginMacroMarkerStandalone [testsimplemacro] []
            beginParagraph
            onWord [simplemacro0]
            endParagraph
            endMacroMarkerStandalone [testsimplemacro] []
            endMacroMarkerStandalone [testnestedmacro] []
            endDocument""";

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("testnestedmacro", Collections.emptyMap(), false)));

        this.transformation.transform(dom, new TransformationContext(dom, Syntax.XWIKI_2_0));

        WikiPrinter printer = new DefaultWikiPrinter();
        BlockRenderer eventBlockRenderer =
            this.componentManager.getInstance(BlockRenderer.class, Syntax.EVENT_1_0.toIdString());
        eventBlockRenderer.render(dom, printer);
        assertEquals(expected, printer.toString());
    }

    /**
     * Test that we have a safeguard against infinite recursive macros.
     */
    @Test
    void transformMacroWithInfiniteRecursion() throws Exception
    {
        String expected = "beginDocument\n"
            + StringUtils.repeat("beginMacroMarkerStandalone [testrecursivemacro] []\n", 129)
            + "onMacroStandalone [testrecursivemacro] []\n"
            + StringUtils.repeat("endMacroMarkerStandalone [testrecursivemacro] []\n", 129)
            + "endDocument";

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("testrecursivemacro", Collections.emptyMap(), false)));

        // To have a fast test, set the max macro execution to 128 macros.
        this.transformation.setMaxRecursions(128);

        this.transformation.transform(dom, new TransformationContext(dom, Syntax.XWIKI_2_0));

        WikiPrinter printer = new DefaultWikiPrinter();
        BlockRenderer eventBlockRenderer =
            this.componentManager.getInstance(BlockRenderer.class, Syntax.EVENT_1_0.toIdString());
        eventBlockRenderer.render(dom, printer);
        assertEquals(expected, printer.toString());
    }

    @Test
    void transformWhenLotsOfMacrosButNoInfiniteRecursion() throws Exception
    {
        StringBuilder expected = new StringBuilder("beginDocument\n");
        for (int i = 0; i < 10; i++) {
            expected.append("beginMacroMarkerStandalone [testsimplemacro] []\n")
                .append("beginParagraph\n")
                .append("onWord [simplemacro").append(i).append("]\n")
                .append("endParagraph\n")
                .append("endMacroMarkerStandalone [testsimplemacro] []\n");
        }
        expected.append("endDocument");

        List<Block> macroBlocks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            macroBlocks.add(new MacroBlock("testsimplemacro", Collections.emptyMap(), false));
        }
        XDOM dom = new XDOM(macroBlocks);

        // Make sure that the max macro execution is less than the # of macros we have in the content to prove that
        // we only stop on real recursion.
        this.transformation.setMaxRecursions(5);

        this.transformation.transform(dom, new TransformationContext(dom, Syntax.XWIKI_2_0));

        WikiPrinter printer = new DefaultWikiPrinter();
        BlockRenderer eventBlockRenderer =
            this.componentManager.getInstance(BlockRenderer.class, Syntax.EVENT_1_0.toIdString());
        eventBlockRenderer.render(dom, printer);
        assertEquals(expected.toString(), printer.toString());
    }

    /**
     * Test that macro priorities are working.
     */
    @Test
    void transformMacrosWithPriorities() throws Exception
    {
        String expected = """
            beginDocument
            beginMacroMarkerStandalone [testsimplemacro] []
            beginParagraph
            onWord [simplemacro1]
            endParagraph
            endMacroMarkerStandalone [testsimplemacro] []
            beginMacroMarkerStandalone [testprioritymacro] []
            beginParagraph
            onWord [word]
            endParagraph
            endMacroMarkerStandalone [testprioritymacro] []
            endDocument""";

        // "testprioritymacro" has a highest priority than "testsimplemacro" and will be executed first.
        // This is verified as follows:
        // - "testprioritymacro" generates a WordBlock
        // - "testsimplemacro" outputs "simplemacro" followed by the number of WordBlocks that exist in the document
        // Thus if "testsimplemacro" is executed before "testprioritymacro" it would print "simplemacro0"
        XDOM dom = new XDOM(Arrays.<Block>asList(
            new MacroBlock("testsimplemacro", Collections.emptyMap(), false),
            new MacroBlock("testprioritymacro", Collections.emptyMap(), false)));

        this.transformation.transform(dom, new TransformationContext(dom, Syntax.XWIKI_2_0));

        WikiPrinter printer = new DefaultWikiPrinter();
        BlockRenderer eventBlockRenderer =
            this.componentManager.getInstance(BlockRenderer.class, Syntax.EVENT_1_0.toIdString());
        eventBlockRenderer.render(dom, printer);
        assertEquals(expected, printer.toString());
    }

    @Test
    void transformWithSeveralNestedMacros() throws Exception
    {
        String expected = """
            beginDocument
            beginMacroMarkerStandalone [testsimplemacro] []
            beginParagraph
            onWord [simplemacro1]
            endParagraph
            endMacroMarkerStandalone [testsimplemacro] []
            beginMacroMarkerStandalone [testprioritymacro] []
            beginParagraph
            onWord [word]
            endParagraph
            endMacroMarkerStandalone [testprioritymacro] []
            beginMacroMarkerStandalone [testnestedmacro] []
            beginMacroMarkerStandalone [testsimplemacro] []
            beginParagraph
            onWord [simplemacro2]
            endParagraph
            endMacroMarkerStandalone [testsimplemacro] []
            endMacroMarkerStandalone [testnestedmacro] []
            beginMacroMarkerStandalone [testsimplemacro] []
            beginParagraph
            onWord [simplemacro3]
            endParagraph
            endMacroMarkerStandalone [testsimplemacro] []
            beginMacroMarkerStandalone [testtwonestedmacros] []
            beginMacroMarkerStandalone [testsimplemacro] []
            beginParagraph
            onWord [simplemacro4]
            endParagraph
            endMacroMarkerStandalone [testsimplemacro] []
            beginMacroMarkerStandalone [testnestedmacro] []
            beginMacroMarkerStandalone [testsimplemacro] []
            beginParagraph
            onWord [simplemacro5]
            endParagraph
            endMacroMarkerStandalone [testsimplemacro] []
            endMacroMarkerStandalone [testnestedmacro] []
            endMacroMarkerStandalone [testtwonestedmacros] []
            beginMacroMarkerStandalone [testsimplemacro] []
            beginParagraph
            onWord [simplemacro6]
            endParagraph
            endMacroMarkerStandalone [testsimplemacro] []
            endDocument""";
        XDOM dom = new XDOM(List.of(
            new MacroBlock("testsimplemacro", Listener.EMPTY_PARAMETERS, false),
            new MacroBlock("testprioritymacro", Listener.EMPTY_PARAMETERS, false),
            new MacroBlock("testnestedmacro", Listener.EMPTY_PARAMETERS, false),
            new MacroBlock("testsimplemacro", Listener.EMPTY_PARAMETERS, false),
            new MacroBlock("testtwonestedmacros", Listener.EMPTY_PARAMETERS, false),
            new MacroBlock("testsimplemacro", Listener.EMPTY_PARAMETERS, false)
        ));

        this.transformation.transform(dom, new

            TransformationContext(dom, Syntax.XWIKI_2_0));

        WikiPrinter printer = new DefaultWikiPrinter();

        BlockRenderer eventBlockRenderer =
            this.componentManager.getInstance(BlockRenderer.class, Syntax.EVENT_1_0.toIdString());
        eventBlockRenderer.render(dom, printer);

        assertEquals(expected, printer.toString());
    }

    /**
     * Test that macro with same priorities execute in the order in which they are defined.
     */
    @Test
    void macroWithSamePriorityExecuteOnPageOrder() throws Exception
    {
        // Both macros have the same priorities, and thus "testsimplemacro" should be executed first and generate
        // "simplemacro0".
        XDOM dom = new XDOM(Arrays.<Block>asList(
            new MacroBlock("testsimplemacro", Collections.emptyMap(), false),
            new MacroBlock("testcontentmacro", Collections.emptyMap(), "content", false)));

        TransformationContext context = new TransformationContext(dom, Syntax.XWIKI_2_0);
        this.transformation.transform(dom, context);

        WikiPrinter printer = new DefaultWikiPrinter();
        BlockRenderer eventBlockRenderer =
            this.componentManager.getInstance(BlockRenderer.class, Syntax.EVENT_1_0.toIdString());
        eventBlockRenderer.render(dom, printer);

        String expected = """
            beginDocument
            beginMacroMarkerStandalone [testsimplemacro] []
            beginParagraph
            onWord [simplemacro0]
            endParagraph
            endMacroMarkerStandalone [testsimplemacro] []
            beginMacroMarkerStandalone [testcontentmacro] [] [content]
            onWord [content]
            endMacroMarkerStandalone [testcontentmacro] [] [content]
            endDocument""";
        assertEquals(expected, printer.toString());

        // We must also test the other order ("testcontentmacro" before "testsimplemacro"), to ensure, for example that
        // there's no lexical order on Macro class names, for example.
        dom = new XDOM(Arrays.<Block>asList(
            new MacroBlock("testcontentmacro", Collections.emptyMap(), "content", false),
            new MacroBlock("testsimplemacro", Collections.emptyMap(), false)));

        context.setXDOM(dom);
        this.transformation.transform(dom, context);

        printer = new DefaultWikiPrinter();
        eventBlockRenderer.render(dom, printer);

        expected = """
            beginDocument
            beginMacroMarkerStandalone [testcontentmacro] [] [content]
            onWord [content]
            endMacroMarkerStandalone [testcontentmacro] [] [content]
            beginMacroMarkerStandalone [testsimplemacro] []
            beginParagraph
            onWord [simplemacro1]
            endParagraph
            endMacroMarkerStandalone [testsimplemacro] []
            endDocument""";
        assertEquals(expected, printer.toString());
    }

    /**
     * Test that a not existing macro generate an error in the XDOM.
     */
    @Test
    void transformNotExistingMacro() throws Exception
    {
        String expected = """
            beginDocument
            beginMacroMarkerStandalone [notexisting] []
            beginGroup [[class]=[xwikirenderingerror]]
            onWord [Unknown macro: notexisting. Click on this message for details.]
            endGroup [[class]=[xwikirenderingerror]]
            beginGroup [[class]=[xwikirenderingerrordescription hidden]]
            onVerbatim [The [notexisting] macro is not in the list of registered macros. Verify the spelling or contact your administrator.] [false]
            endGroup [[class]=[xwikirenderingerrordescription hidden]]
            endMacroMarkerStandalone [notexisting] []
            endDocument""";

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("notexisting", Collections.emptyMap(), false)));

        assertEquals(expected, transformAndRenderEvents(dom));
    }

    @Test
    void prepareSimpleMacro() throws Exception
    {
        MacroBlock macroBlock = new MacroBlock("testmacro", Collections.emptyMap(), false);

        Macro<?> testMacro = this.componentManager.registerMockComponent(Macro.class, macroBlock.getId());
        doAnswer((Answer<Void>) invocation -> {
            invocation.<MacroBlock>getArgument(0).setAttribute("test", "prepared");

            return null;
        }).when(testMacro).prepare(macroBlock);

        this.transformation.prepare(macroBlock);

        assertEquals(Map.of("test", "prepared"), macroBlock.getAttributes());
    }

    @Test
    void prepareSimpleMacros() throws Exception
    {
        MacroBlock macroBlock1 = new MacroBlock("testmacro1", Collections.emptyMap(), false);
        MacroBlock macroBlock2 = new MacroBlock("testmacro2", Collections.emptyMap(), false);

        Macro<?> testMacro1 = this.componentManager.registerMockComponent(Macro.class, macroBlock1.getId());
        Macro<?> testMacro2 = this.componentManager.registerMockComponent(Macro.class, macroBlock2.getId());
        doAnswer((Answer<Void>) invocation -> {
            MacroBlock block = invocation.getArgument(0);
            block.setAttribute("test", "prepared1");

            return null;
        }).when(testMacro1).prepare(macroBlock1);
        doAnswer((Answer<Void>) invocation -> {
            MacroBlock block = invocation.getArgument(0);
            block.setAttribute("test", "prepared2");

            return null;
        }).when(testMacro2).prepare(macroBlock2);

        this.transformation.prepare(new XDOM(List.of(macroBlock1, macroBlock2)));

        assertEquals(Map.of("test", "prepared1"), macroBlock1.getAttributes());
        assertEquals(Map.of("test", "prepared2"), macroBlock2.getAttributes());
    }

    @Test
    void prepareNotExistingMacro()
    {
        MacroBlock macroBlock = new MacroBlock("notexisting", Collections.emptyMap(), false);
        XDOM dom = new XDOM(List.of(macroBlock));

        assertTrue(macroBlock.getAttributes().isEmpty());

        this.transformation.prepare(dom);

        assertTrue(macroBlock.getAttributes().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void replacementMacro(boolean isIsolated) throws Exception
    {
        // Expect that while the replacement happens, the new macro isn't executed as no new scan of macros happens when
        // the macro is isolated.
        String expected = isIsolated ? """
            beginDocument
            onMacroStandalone [testReplacement] [param1=newValue] [macroContent]
            endDocument"""
            : """
            beginDocument
            beginMacroMarkerStandalone [testReplacement] [param1=newValue] [macroContent]
            onWord [testReplacement]
            onWord [macroContent]
            onWord [newValue]
            endMacroMarkerStandalone [testReplacement] [param1=newValue] [macroContent]
            endDocument""";

        String macroID = "testReplaceMe";
        MacroBlock macroBlock = new MacroBlock(macroID, Map.of("oldParameter", "oldValue"), "macroContent", false);
        XDOM dom = new XDOM(List.of(macroBlock));

        when(this.isolatedExecutionConfiguration.isExecutionIsolated(macroID, false)).thenReturn(isIsolated);

        assertEquals(expected, transformAndRenderEvents(dom));
    }

    @ParameterizedTest
    @CsvSource({
        "false, false",
        "true, false",
        "true, true",
        "false, true"
    })
    void isIsolated(boolean macroIsolated, boolean executionIsolated) throws Exception
    {
        StringBuilder expected = new StringBuilder("""
            beginDocument
            beginMacroMarkerStandalone [isolatedMacro] []
            onWord [isolated]
            endMacroMarkerStandalone [isolatedMacro] []
            """);
        if (executionIsolated) {
            expected.append("onMacroStandalone [testReplacement] [param1=Hello] [inside]\n");
        } else {
            expected.append("""
                beginMacroMarkerStandalone [testReplacement] [param1=Hello] [inside]
                onWord [testReplacement]
                onWord [inside]
                onWord [Hello]
                endMacroMarkerStandalone [testReplacement] [param1=Hello] [inside]
                """);
        }
        expected.append("endDocument");

        // Create a mock macro to be able to dynamically change the return value of isExecutionIsolated.
        String macroId = "isolatedMacro";
        createMockMacro(macroId, 100, macroIsolated, invocation -> {
            MacroTransformationContext context = invocation.getArgument(2);
            context.getXDOM().addChild(new MacroBlock("testReplacement", Map.of("param1", "Hello"), "inside", false));
            return List.of(new WordBlock("isolated"));
        });

        MacroBlock macroBlock = new MacroBlock(macroId, Collections.emptyMap(), false);
        XDOM dom = new XDOM(List.of(macroBlock));

        when(this.isolatedExecutionConfiguration.isExecutionIsolated(eq(macroId), anyBoolean()))
            .thenReturn(executionIsolated);

        assertEquals(expected.toString(), transformAndRenderEvents(dom));

        verify(this.isolatedExecutionConfiguration).isExecutionIsolated(macroId, macroIsolated);
    }

    @Test
    void testNestedPriorities() throws Exception
    {
        // Ensure that a macro nested in a high-priority macro gets inserted at the right position in the PQ - at the
        // position of the parent macro.
        String counterMacro = "testCounter";
        String containerMacro = "testContainer";
        MutableInt executionCounter = new MutableInt(0);

        createMockMacro(counterMacro, 1000, true,
            invocation -> List.of(new WordBlock("counter" + executionCounter.getAndIncrement())));
        createMockMacro(containerMacro, 100, true,
            invocation -> List.of(
                new WordBlock("container" + executionCounter.getAndIncrement()),
                new MacroBlock(counterMacro, Map.of(), false))
        );

        XDOM dom = new XDOM(List.of(
            new MacroBlock(counterMacro, Map.of(), false),
            new MacroBlock(containerMacro, Map.of(), false),
            new MacroBlock(counterMacro, Map.of(), false)
        ));

        String expected = """
            beginDocument
            beginMacroMarkerStandalone [testCounter] []
            onWord [counter1]
            endMacroMarkerStandalone [testCounter] []
            beginMacroMarkerStandalone [testContainer] []
            onWord [container0]
            beginMacroMarkerStandalone [testCounter] []
            onWord [counter2]
            endMacroMarkerStandalone [testCounter] []
            endMacroMarkerStandalone [testContainer] []
            beginMacroMarkerStandalone [testCounter] []
            onWord [counter3]
            endMacroMarkerStandalone [testCounter] []
            endDocument""";

        assertEquals(expected, transformAndRenderEvents(dom));
    }

    private void createMockMacro(String macroId, int priority, boolean macroIsolated, Answer<List<Block>> execute)
        throws Exception
    {
        Macro<Object> macro = this.componentManager.registerMockComponent(Macro.class, macroId);
        when(macro.getPriority()).thenReturn(priority);
        when(macro.execute(any(), any(), any())).thenAnswer(execute);
        when(macro.isExecutionIsolated(any(), any())).thenReturn(macroIsolated);
        MacroDescriptor macroDescriptor = new DefaultMacroDescriptor(new MacroId(macroId), macroId, macroId, null,
            new DefaultBeanDescriptor(Object.class));
        when(macro.getDescriptor()).thenReturn(macroDescriptor);
        when(macro.compareTo(any())).thenAnswer(invocation -> {
            Macro<?> other = invocation.getArgument(0);
            return priority - other.getPriority();
        });
    }

    private String transformAndRenderEvents(XDOM dom) throws TransformationException, ComponentLookupException
    {
        this.transformation.transform(dom, new TransformationContext(dom, Syntax.XWIKI_2_0));

        WikiPrinter printer = new DefaultWikiPrinter();
        BlockRenderer eventBlockRenderer =
            this.componentManager.getInstance(BlockRenderer.class, Syntax.EVENT_1_0.toIdString());
        eventBlockRenderer.render(dom, printer);
        return printer.toString();
    }

    @Test
    void macroExecutionsLimitStopsTheTransformation() throws Exception
    {
        when(this.renderingLimits.isExceeded(RenderingLimitType.MACRO_EXECUTIONS)).thenReturn(true);
        when(this.renderingLimits.getLimit(RenderingLimitType.MACRO_EXECUTIONS)).thenReturn(42L);

        String expected = """
            beginDocument
            beginMacroMarkerStandalone [testsimplemacro] []
            beginGroup [[class]=[xwikirenderingerror]]
            onWord [The [testsimplemacro] macro couldn't be executed as the [macro.executions] limit of [42] for rendering a page has been reached. Click on this message for details.]
            endGroup [[class]=[xwikirenderingerror]]
            beginGroup [[class]=[xwikirenderingerrordescription hidden]]
            onVerbatim [The rendering of a page is limited to protect the server against pages that consume too many resources. A wiki administrator can change these limits in xwiki.properties.] [false]
            endGroup [[class]=[xwikirenderingerrordescription hidden]]
            endMacroMarkerStandalone [testsimplemacro] []
            onMacroStandalone [testsimplemacro] []
            endDocument""";

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("testsimplemacro", Map.of(), false),
            new MacroBlock("testsimplemacro", Map.of(), false)));

        assertEquals(expected, transformAndRenderEvents(dom));
    }

    @Test
    void timeLimitStopsTheTransformation() throws Exception
    {
        when(this.renderingLimits.isExceeded(RenderingLimitType.TIME)).thenReturn(true);
        when(this.renderingLimits.getLimit(RenderingLimitType.TIME)).thenReturn(60_000L);

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("testsimplemacro", Map.of(), false)));

        assertTrue(transformAndRenderEvents(dom).contains("The [testsimplemacro] macro couldn't be executed as the"
            + " [time] limit of [60000] for rendering a page has been reached."));
    }

    @Test
    void sizeLimitDropsWhatAMacroProducedWhenItAloneIsTooLarge() throws Exception
    {
        // What the macro produces is on its own larger than what a whole page may contain.
        when(this.renderingLimits.getLimit(RenderingLimitType.DOCUMENT_SIZE)).thenReturn(1L);

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("testsimplemacro", Map.of(), false)));

        String result = transformAndRenderEvents(dom);

        assertTrue(result.contains("The [testsimplemacro] macro couldn't be executed as the [document.size] limit of"
            + " [1] for rendering a page has been reached."), result);
        // What the macro produced isn't part of the result.
        assertFalse(result.contains("simplemacro0"), result);
    }

    @Test
    void sizeLimitKeepsWhatAMacroProducedWhenItFitsIntoAWholePage() throws Exception
    {
        // The size limit is only exceeded once what the macro produced has been charged.
        MutableBoolean exceeded = new MutableBoolean();
        doAnswer(invocation -> {
            exceeded.setTrue();
            return null;
        }).when(this.renderingLimits).charge(eq(RenderingLimitType.DOCUMENT_SIZE), anyLong());
        when(this.renderingLimits.isExceeded(RenderingLimitType.DOCUMENT_SIZE))
            .thenAnswer(invocation -> exceeded.getValue());
        when(this.renderingLimits.getLimit(RenderingLimitType.DOCUMENT_SIZE)).thenReturn(1024L);

        XDOM dom = new XDOM(List.of(new MacroBlock("testsimplemacro", Map.of(), false),
            new MacroBlock("testsimplemacro", Map.of(), false)));

        String result = transformAndRenderEvents(dom);

        // The result fits into a whole page, so it is content that has been counted before and dropping it would
        // remove content from the document that isn't what made it too large.
        assertTrue(result.contains("onWord [simplemacro0]"), result);
        // The transformation still stops, so the second macro isn't executed and reports the exhausted budget.
        assertTrue(result.contains("The [testsimplemacro] macro couldn't be executed as the [document.size] limit of"
            + " [1024] for rendering a page has been reached."), result);
    }

    @Test
    void exhaustedSizeLimitStopsTheTransformationWithoutExecutingAMacro() throws Exception
    {
        when(this.renderingLimits.isExceeded(RenderingLimitType.DOCUMENT_SIZE)).thenReturn(true);
        when(this.renderingLimits.getLimit(RenderingLimitType.DOCUMENT_SIZE)).thenReturn(1024L);

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("testsimplemacro", Map.of(), false)));

        String result = transformAndRenderEvents(dom);

        assertTrue(result.contains("The [testsimplemacro] macro couldn't be executed as the [document.size] limit of"
            + " [1024] for rendering a page has been reached."), result);
        assertFalse(result.contains("simplemacro0"), result);
        // The macro hasn't been executed at all, so no execution has been charged for it.
        verify(this.renderingLimits, never()).charge(eq(RenderingLimitType.MACRO_EXECUTIONS), anyLong());
    }

    @Test
    void limitExceededByAMacroIsReportedAsALimitError() throws Exception
    {
        String macroId = "testLimitMacro";
        // Wrap the exception as macros usually do when they catch what the macro content parser throws.
        createMockMacro(macroId, 100, true, invocation -> {
            throw new MacroExecutionException("Failed to parse the content",
                new MacroRenderingLimitExceededException(RenderingLimitType.DOCUMENT_SIZE,
                    "The content doesn't fit."));
        });
        when(this.renderingLimits.getLimit(RenderingLimitType.DOCUMENT_SIZE)).thenReturn(1024L);

        XDOM dom = new XDOM(List.of(new MacroBlock(macroId, Map.of(), false),
            new MacroBlock("testsimplemacro", Map.of(), false)));

        String result = transformAndRenderEvents(dom);

        assertTrue(result.contains("The [testLimitMacro] macro couldn't be executed as the [document.size] limit of"
            + " [1024] for rendering a page has been reached."), result);
        assertFalse(result.contains("Failed to execute"), result);
        // A single piece of content that doesn't fit doesn't stop the transformation.
        assertTrue(result.contains("onWord [simplemacro"), result);
    }

    @Test
    void errorMessagesLimitStopsGeneratingErrorMessages() throws Exception
    {
        when(this.renderingLimits.isExceeded(RenderingLimitType.MACRO_EXECUTIONS)).thenReturn(true);
        when(this.renderingLimits.isExceeded(RenderingLimitType.ERROR_MESSAGES)).thenReturn(true);

        String expected = """
            beginDocument
            onMacroStandalone [testsimplemacro] []
            endDocument""";

        XDOM dom = new XDOM(List.of((Block) new MacroBlock("testsimplemacro", Map.of(), false)));

        assertEquals(expected, transformAndRenderEvents(dom));
        // Not logged as a warning: the exhausted budget for the error messages is reported once for the whole
        // rendering, and a rendering that reached a limit stops many macros, each of which would add a line.
        assertEquals(0, this.logCapture.size());
    }
}
