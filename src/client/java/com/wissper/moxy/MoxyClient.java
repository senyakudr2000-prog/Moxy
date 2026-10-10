package com.wissper.moxy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moxy stage 2: remember surface data of every chunk the player has seen and
 * compress it into coarse LOD levels (2x2, 4x4, 8x8, 16x16 blocks per cell).
 * Nothing is rendered yet, stats are only written to the log.
 */
public class MoxyClient implements ClientModInitializer {
    public static final String MOD_ID = "moxy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** dimension id -> (chunk position as long -> compressed column) */
    private static final Map<String, Map<Long, LodColumn>> STORE = new ConcurrentHashMap<>();

    private static int tickCounter = 0;
    private static int lastLoggedCount = -1;
    private static String lastInfo = "none";

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Moxy] stage 2 loaded");

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            try {
                onChunkLoad(world, chunk);
            } catch (Throwable t) {
                LOGGER.error("[Moxy] failed to process chunk", t);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            STORE.clear();
            lastLoggedCount = -1;
            LOGGER.info("[Moxy] disconnected, LOD store cleared");
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            if (tickCounter % 600 != 0) {
                return;
            }
            int count = 0;
            long bytes = 0;
            for (Map<Long, LodColumn> dim : STORE.values()) {
                count += dim.size();
                for (LodColumn col : dim.values()) {
                    bytes += col.approxBytes();
                }
            }
            if (count != lastLoggedCount) {
                lastLoggedCount = count;
                LOGGER.info("[Moxy] stored chunks={}, approx memory={} KB, last: {}",
                        count, bytes / 1024, lastInfo);
            }
        });
    }

    private static void onChunkLoad(ClientWorld world, WorldChunk chunk) {
        int bottom = world.getBottomY();
        int baseX = chunk.getPos().getStartX();
        int baseZ = chunk.getPos().getStartZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        int[] colors = new int[256];
        int[] heights = new int[256];

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z);
                int y = Math.max(bottom, top - 1);
                pos.set(baseX + x, y, baseZ + z);
                BlockState state = chunk.getBlockState(pos);
                colors[z * 16 + x] = state.getMapColor(world, pos).color;
                heights[z * 16 + x] = y;
            }
        }

        LodColumn column = LodColumn.build(colors, heights);
        String dim = world.getRegistryKey().getValue().toString();
        STORE.computeIfAbsent(dim, k -> new ConcurrentHashMap<>())
                .put(chunk.getPos().toLong(), column);

        lastInfo = String.format("chunk (%d,%d) avg color #%06X avg height %d",
                chunk.getPos().x, chunk.getPos().z,
                column.colors[4][0] & 0xFFFFFF, column.heights[4][0]);
    }

    /** Surface data of one 16x16 chunk column compressed into levels 1..4. */
    static final class LodColumn {
        /** index = level (1..4), level n has (16 >> n)^2 cells, each cell covers 2^n x 2^n blocks */
        final int[][] colors = new int[5][];
        final short[][] heights = new short[5][];

        static LodColumn build(int[] srcColors, int[] srcHeights) {
            LodColumn col = new LodColumn();
            for (int level = 1; level <= 4; level++) {
                int cells = 16 >> level;
                int size = 1 << level;
                int[] c = new int[cells * cells];
                short[] h = new short[cells * cells];
                for (int cz = 0; cz < cells; cz++) {
                    for (int cx = 0; cx < cells; cx++) {
                        long r = 0, g = 0, b = 0, hs = 0;
                        for (int dz = 0; dz < size; dz++) {
                            for (int dx = 0; dx < size; dx++) {
                                int idx = (cz * size + dz) * 16 + (cx * size + dx);
                                int rgb = srcColors[idx];
                                r += (rgb >> 16) & 0xFF;
                                g += (rgb >> 8) & 0xFF;
                                b += rgb & 0xFF;
                                hs += srcHeights[idx];
                            }
                        }
                        int n = size * size;
                        c[cz * cells + cx] = (int) ((r / n) << 16 | (g / n) << 8 | (b / n));
                        h[cz * cells + cx] = (short) (hs / n);
                    }
                }
                col.colors[level] = c;
                col.heights[level] = h;
            }
            return col;
        }

        long approxBytes() {
            long total = 0;
            for (int level = 1; level <= 4; level++) {
                total += colors[level].length * 4L + heights[level].length * 2L;
            }
            return total;
        }
    }
}
