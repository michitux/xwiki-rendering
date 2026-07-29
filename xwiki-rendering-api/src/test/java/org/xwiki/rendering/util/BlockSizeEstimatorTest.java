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
package org.xwiki.rendering.util;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.RawBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.VerbatimBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.syntax.Syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BlockSizeEstimator}.
 *
 * @version $Id$
 */
class BlockSizeEstimatorTest
{
    private static final int BLOCK_SIZE = 32;

    @Test
    void estimateSizeOfNoBlock()
    {
        assertEquals(0, BlockSizeEstimator.estimateSize(List.of()));
    }

    @Test
    void estimateSizeOfABlockWithoutContent()
    {
        assertEquals(BLOCK_SIZE, BlockSizeEstimator.estimateSize(List.of(new SpaceBlock())));
    }

    @Test
    void estimateSizeCountsTheContentOfTheBlocks()
    {
        List<Block> blocks = List.of(new WordBlock("word"), new VerbatimBlock("verbatim", true),
            new RawBlock("raw", Syntax.HTML_5_0));

        assertEquals(3 * BLOCK_SIZE + "word".length() + "verbatim".length() + "raw".length(),
            BlockSizeEstimator.estimateSize(blocks));
    }

    @Test
    void estimateSizeCountsTheParametersOfTheBlocks()
    {
        Block block = new ParagraphBlock(List.of(), Map.of("key", "value"));

        assertEquals(BLOCK_SIZE + "key".length() + "value".length(), BlockSizeEstimator.estimateSize(List.of(block)));
    }

    @Test
    void estimateSizeCountsTheIdAndContentOfAMacro()
    {
        Block block = new MacroBlock("macroid", Map.of(), "macrocontent", false);

        assertEquals(BLOCK_SIZE + "macroid".length() + "macrocontent".length(),
            BlockSizeEstimator.estimateSize(List.of(block)));
    }

    @Test
    void estimateSizeCountsTheChildren()
    {
        Block block = new ParagraphBlock(List.of(new WordBlock("word"), new SpaceBlock(), new WordBlock("word")));

        assertEquals(4 * BLOCK_SIZE + 2 * "word".length(), BlockSizeEstimator.estimateSize(List.of(block)));
    }

    @Test
    void estimateSizeOfADeeplyNestedTree()
    {
        // Deep enough to overflow the stack if the traversal was recursive.
        Block block = new WordBlock("word");
        for (int i = 0; i < 100_000; ++i) {
            block = new ParagraphBlock(List.of(block));
        }

        assertTrue(BlockSizeEstimator.estimateSize(List.of(block)) > 100_000L * BLOCK_SIZE);
    }
}
