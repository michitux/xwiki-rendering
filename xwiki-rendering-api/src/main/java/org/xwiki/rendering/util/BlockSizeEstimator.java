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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.xwiki.rendering.block.AbstractMacroBlock;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.IdBlock;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.RawBlock;
import org.xwiki.rendering.block.VerbatimBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.stability.Unstable;

/**
 * Estimates how much content a tree of blocks amounts to, so that it can be counted against a size limit.
 * <p>
 * The result is deliberately a rough estimate and not a measure of the actual memory usage: every block costs a fixed
 * amount plus the length of the strings it carries. It is meant to be compared to a limit that is set high enough to
 * accommodate that imprecision.
 *
 * @version $Id$
 * @since 18.7.0RC1
 */
@Unstable
public final class BlockSizeEstimator
{
    /**
     * What every block costs, regardless of the content it carries, to account both for the object itself and for the
     * markup a renderer will produce for it.
     */
    private static final int BLOCK_SIZE = 32;

    private BlockSizeEstimator()
    {
        // Utility class.
    }

    /**
     * @param blocks the blocks to estimate the size of, including their children
     * @return the estimated size of the blocks, in bytes
     */
    public static long estimateSize(List<Block> blocks)
    {
        long size = 0;
        Deque<Block> queue = new ArrayDeque<>(blocks);

        while (!queue.isEmpty()) {
            Block block = queue.pop();

            size += estimateBlockSize(block);
            // Traverse iteratively as a block tree can be deep enough to overflow the stack.
            queue.addAll(block.getChildren());
        }

        return size;
    }

    /**
     * @param block the block to estimate the size of, excluding its children
     * @return the estimated size of the block, in bytes
     */
    private static long estimateBlockSize(Block block)
    {
        long size = BLOCK_SIZE;

        for (Map.Entry<String, String> parameter : block.getParameters().entrySet()) {
            size += length(parameter.getKey()) + length(parameter.getValue());
        }

        size += switch (block) {
            case WordBlock wordBlock -> length(wordBlock.getWord());
            case VerbatimBlock verbatimBlock -> length(verbatimBlock.getProtectedString());
            case RawBlock rawBlock -> length(rawBlock.getRawContent());
            case AbstractMacroBlock macroBlock -> length(macroBlock.getId()) + length(macroBlock.getContent());
            case LinkBlock linkBlock -> length(linkBlock.getReference());
            case ImageBlock imageBlock -> length(imageBlock.getReference());
            case IdBlock idBlock -> length(idBlock.getName());
            default -> 0;
        };

        return size;
    }

    private static int length(ResourceReference reference)
    {
        return reference == null ? 0 : length(reference.getReference());
    }

    private static int length(String value)
    {
        return value == null ? 0 : value.length();
    }
}
