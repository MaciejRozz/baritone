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

package baritone.launch.mixins;

import baritone.utils.accessor.IChunkArray;
import baritone.utils.accessor.IClientChunkProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

@Mixin(ClientChunkCache.class)
public class MixinClientChunkProvider implements IClientChunkProvider {

    @Final
    @Shadow
    ClientLevel level;

    @Override
    public ClientChunkCache createThreadSafeCopy() {
        IChunkArray arr = extractReferenceArray();
        int viewDistance = arr.viewDistance();
        // Some mods (e.g. Immersive Portals) replace the client's chunk cache with their
        // own implementation that overrides getChunk/hasChunk but constructs the vanilla
        // superclass (whose backing array we read via reflection above) with a tiny/unused
        // view distance. In that case the reflection-based array copy below silently
        // produces an empty result. Detect that and fall back to a real view distance so
        // the manual backfill pass further down has a sane radius to search.
        int effectiveViewDistance = viewDistance;
        if (effectiveViewDistance <= 2) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null) {
                effectiveViewDistance = mc.options.renderDistance().get();
            }
        }
        ClientChunkCache result = new ClientChunkCache(level, effectiveViewDistance); // no -3 fudge; see manual backfill below for why this matters when the source view distance is unreliable
        IChunkArray copyArr = ((IClientChunkProvider) result).extractReferenceArray();

        // Center the copy on the player's real position rather than trusting the source
        // array's center (which mods like Immersive Portals never update, since they
        // override updateViewCenter as a no-op). Must happen before copyFrom/putIfInRange,
        // since inRange()/getIndex() on the copy depend on its own center being correct.
        Player player = mcPlayerOrNull();
        int centerX = player != null ? (player.getBlockX() >> 4) : arr.centerX();
        int centerZ = player != null ? (player.getBlockZ() >> 4) : arr.centerZ();
        copyArr.setCenter(centerX, centerZ);

        copyArr.copyFrom(arr);

        // Backfill directly from the live provider's public getChunk, independent of
        // whatever internal storage it actually uses. This is a no-op (every lookup
        // already present) for vanilla, and is what actually makes the copy correct
        // when the live provider's backing array doesn't reflect its real chunk set.
        ClientChunkCache live = (ClientChunkCache) (Object) this;
        for (int dx = -effectiveViewDistance; dx <= effectiveViewDistance; dx++) {
            for (int dz = -effectiveViewDistance; dz <= effectiveViewDistance; dz++) {
                int cx = centerX + dx;
                int cz = centerZ + dz;
                LevelChunk chunk = live.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk != null && !chunk.isEmpty()) {
                    copyArr.putIfInRange(chunk);
                }
            }
        }
        return result;
    }

    private static Player mcPlayerOrNull() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.player;
    }

    @Override
    public IChunkArray extractReferenceArray() {
        for (Field f : ClientChunkCache.class.getDeclaredFields()) {
            if (IChunkArray.class.isAssignableFrom(f.getType())) {
                try {
                    return (IChunkArray) f.get(this);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        throw new RuntimeException(Arrays.toString(ClientChunkCache.class.getDeclaredFields()));
    }
}
