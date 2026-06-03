package com.cutedifficult.item;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.world.ModDimensions;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Interstellar — a relic that tears a hole between worlds. Right-click to
 * travel to {@link ModDimensions#HORIZON the Horizon}, an empty void dimension
 * where you can build anything. Right-click again to leave.
 *
 * <h2>Travel rules</h2>
 * <ul>
 *   <li><b>Entering</b> (from ANY dimension) drops you at YOUR personal island
 *       — a unique coordinate derived from your UUID, so every player gets
 *       their own corner of the void. We remember where you came from.</li>
 *   <li><b>Leaving normally</b> returns you to the exact dimension + spot you
 *       entered from.</li>
 *   <li><b>Leaving while sneaking (Shift)</b> drops you in the SAME dimension
 *       you entered from, but at the coordinates matching your current Horizon
 *       position scaled up — 1 Horizon block = {@value #SCALE_DEFAULT} blocks
 *       in the Overworld/End, or {@value #SCALE_NETHER} in the Nether. The
 *       saved return point is discarded.</li>
 * </ul>
 *
 * <p>Fall damage is cancelled in the Horizon (see HorizonHandler). The item is
 * not consumed — it's an expensive endgame key meant to be kept.
 */
public class InterstellarItem extends Item {

    /** Coordinate scale: 1 Horizon block = this many blocks in Overworld/End. */
    private static final int SCALE_DEFAULT = 40;
    /** 1 Horizon block = this many Nether blocks. */
    private static final int SCALE_NETHER = 5;
    /** Y level of personal arrival platforms. */
    private static final int ARRIVAL_Y = 64;
    /** Spacing between players' personal islands, in Horizon blocks. */
    private static final int ISLAND_SPACING = 1024;

    private static final Map<UUID, ReturnPoint> RETURN_POINTS = new ConcurrentHashMap<>();

    private record ReturnPoint(RegistryKey<World> dimension,
                               double x, double y, double z, float yaw, float pitch) {}

    public InterstellarItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        if (!(user instanceof ServerPlayerEntity player)) return TypedActionResult.pass(stack);
        MinecraftServer server = player.getServer();
        if (server == null) return TypedActionResult.pass(stack);

        user.getItemCooldownManager().set(this, 60);

        boolean inHorizon = world.getRegistryKey() == ModDimensions.HORIZON;
        if (inHorizon) {
            if (player.isSneaking()) {
                exitInPlace(server, player);
            } else {
                exitToReturnPoint(server, player);
            }
        } else {
            enterHorizon(server, player);
        }
        return TypedActionResult.success(stack);
    }

    // ===== Entering =====

    private void enterHorizon(MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld horizon = server.getWorld(ModDimensions.HORIZON);
        if (horizon == null) {
            player.sendMessage(Text.literal("The Horizon does not answer. (dimension not loaded)")
                    .formatted(Formatting.RED), false);
            CuteDifficult.LOGGER.warn("[CuteDifficult] Horizon world is null — datapack dimension missing?");
            return;
        }

        // Remember where we came from for a normal exit.
        RETURN_POINTS.put(player.getUuid(), new ReturnPoint(
                player.getWorld().getRegistryKey(),
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch()));

        // Personal island coordinate, unique per player.
        BlockPos island = personalIsland(player.getUuid());
        buildPlatform(horizon, island.down());

        player.teleport(horizon,
                island.getX() + 0.5, island.getY(), island.getZ() + 0.5,
                Set.of(), player.getYaw(), player.getPitch());

        horizon.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                island.getX() + 0.5, island.getY() + 1, island.getZ() + 0.5,
                40, 0.5, 1.0, 0.5, 0.2);
        horizon.playSound(null, island,
                SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.6f, 1.2f);
        player.sendMessage(Text.literal("You step beyond the edge of everything. Welcome to the Horizon.")
                .formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC), false);
        player.sendMessage(Text.literal("(Sneak + use to exit at your current position, scaled to the world.)")
                .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), true);
    }

    /** A unique, stable island position for a player, spread far apart but
     *  kept within sane coordinates — even after the ×40 exit scale, the
     *  result must stay well inside the world border. */
    private static BlockPos personalIsland(UUID uuid) {
        long h = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        // Constrain to a small grid: roughly ±24 cells each axis. With
        // ISLAND_SPACING = 1024 that's about ±24,576 Horizon blocks, which
        // becomes ±983,040 after the ×40 Overworld scale — comfortably inside
        // the ±29,999,984 world border.
        int gx = (int) Math.floorMod(h, 49) - 24;
        int gz = (int) Math.floorMod(h >>> 16, 49) - 24;
        return new BlockPos(gx * ISLAND_SPACING, ARRIVAL_Y, gz * ISLAND_SPACING);
    }

    // ===== Normal exit (to saved return point) =====

    private void exitToReturnPoint(MinecraftServer server, ServerPlayerEntity player) {
        ReturnPoint rp = RETURN_POINTS.remove(player.getUuid());
        ServerWorld targetWorld = (rp != null) ? server.getWorld(rp.dimension()) : null;

        double tx, ty, tz;
        float tyaw, tpitch;

        if (targetWorld != null) {
            tx = rp.x(); ty = rp.y(); tz = rp.z();
            tyaw = rp.yaw(); tpitch = rp.pitch();
        } else {
            ServerWorld overworld = server.getOverworld();
            if (overworld == null) return;
            BlockPos spawn = player.getSpawnPointPosition() != null
                    ? player.getSpawnPointPosition() : overworld.getSpawnPos();
            targetWorld = overworld;
            tx = spawn.getX() + 0.5; ty = spawn.getY(); tz = spawn.getZ() + 0.5;
            tyaw = player.getYaw(); tpitch = player.getPitch();
        }

        player.teleport(targetWorld, tx, ty, tz, Set.of(), tyaw, tpitch);
        targetWorld.playSound(null, BlockPos.ofFloored(tx, ty, tz),
                SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.6f, 0.8f);
        player.sendMessage(Text.literal("The Horizon releases you. You return to where you tore the veil.")
                .formatted(Formatting.AQUA, Formatting.ITALIC), false);
    }

    // ===== Sneak exit (scaled, in place) =====

    private void exitInPlace(MinecraftServer server, ServerPlayerEntity player) {
        ReturnPoint rp = RETURN_POINTS.remove(player.getUuid());
        RegistryKey<World> destKey = (rp != null) ? rp.dimension() : World.OVERWORLD;
        ServerWorld dest = server.getWorld(destKey);
        if (dest == null) {
            dest = server.getOverworld();
            if (dest == null) return;
        }

        int scale = scaleFor(dest.getRegistryKey());
        double tx = player.getX() * scale;
        double tz = player.getZ() * scale;

        // Clamp to just inside the destination's world border so we never
        // teleport into the "far lands" / invalid space.
        double limit = dest.getWorldBorder().getSize() / 2.0 - 16;
        double center = dest.getWorldBorder().getCenterX();
        double centerZ = dest.getWorldBorder().getCenterZ();
        tx = Math.max(center - limit, Math.min(center + limit, tx));
        tz = Math.max(centerZ - limit, Math.min(centerZ + limit, tz));

        // Find safe ground at the scaled spot via the heightmap (same call used
        // elsewhere in the mod). If the column is void, keep the player's Y.
        BlockPos column = BlockPos.ofFloored(tx, player.getY(), tz);
        BlockPos top = dest.getTopPosition(
                net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, column);
        BlockPos safe = (top.getY() > dest.getBottomY()) ? top : column;

        player.teleport(dest, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                Set.of(), player.getYaw(), player.getPitch());
        dest.playSound(null, safe,
                SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.7f, 0.6f);
        player.sendMessage(Text.literal(String.format(
                        "You fold space and step out at %d, %d, %d — the Horizon's distances writ large.",
                        safe.getX(), safe.getY(), safe.getZ()))
                .formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC), false);
    }

    private static int scaleFor(RegistryKey<World> dim) {
        if (dim == World.NETHER) return SCALE_NETHER;
        return SCALE_DEFAULT; // Overworld, End, and everything else
    }

    // ===== Helpers =====

    private void buildPlatform(ServerWorld world, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p = center.add(dx, 0, dz);
                if (world.getBlockState(p).isAir()) {
                    world.setBlockState(p, Blocks.OBSIDIAN.getDefaultState());
                }
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              java.util.List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
        tooltip.add(Text.literal("Tears a path to the Horizon — and back.")
                .formatted(Formatting.GRAY, Formatting.ITALIC));
        tooltip.add(Text.literal("Sneak + use in the Horizon to exit in place, scaled to the world.")
                .formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        tooltip.add(Text.literal("1 Horizon block = 40 Overworld blocks (5 in the Nether).")
                .formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }
}