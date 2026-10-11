/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils.accessor;

import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.world.level.chunk.LevelChunk;

public interface IChunkArray {
    void copyFrom(IChunkArray other);

    /**
     * Insert a chunk directly if it's within range of this array's current center,
     * bypassing {@link #copyFrom}'s assumption that the source array shares layout
     * with this one. Used to backfill from chunk sources (such as mods that replace
     * the vanilla backing array, e.g. Immersive Portals) where the normal array-copy
     * approach would otherwise silently yield no data.
     */
    void putIfInRange(LevelChunk chunk);

    /**
     * Explicitly set this array's view center, independent of {@link #copyFrom}. Needed
     * because some chunk sources never report an up-to-date center (see {@link #putIfInRange}).
     */
    void setCenter(int x, int z);

    AtomicReferenceArray<LevelChunk> getChunks();

    int centerX();

    int centerZ();

    int viewDistance();
}
