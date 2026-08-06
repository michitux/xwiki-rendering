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
package org.xwiki.rendering.internal.util;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.VerbatimBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DefaultErrorBlockGenerator}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultErrorBlockGeneratorTest
{
    @InjectMockComponents
    private DefaultErrorBlockGenerator generator;

    @Test
    void causeIsAddedToTheMessage()
    {
        List<Block> blocks = this.generator.generateErrorBlocks("message.", new Exception("exception"), false);

        assertEquals("message. Cause: [exception]. Click on this message for details.", getMessage(blocks));
    }

    @Test
    void longCauseIsAbbreviatedInTheMessage()
    {
        String cause = StringUtils.repeat('a', 1000);

        List<Block> blocks = this.generator.generateErrorBlocks("message.", new Exception(cause), false);

        // An exception message can be arbitrarily long, e.g. because it contains the content that couldn't be parsed,
        // and the message is what is displayed in the place of the content that failed.
        assertEquals("message. Cause: [" + StringUtils.repeat('a', 497) + "...]. Click on this message for details.",
            getMessage(blocks));
        // The full message is still in the stack trace of the details.
        assertTrue(((VerbatimBlock) blocks.get(1).getChildren().get(0)).getProtectedString().contains(cause));
    }

    private static String getMessage(List<Block> blocks)
    {
        return ((WordBlock) blocks.get(0).getChildren().get(0)).getWord();
    }
}
