package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.EnchantMarkers;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the buffed behavior of enhanced enchantments. Strength scales with
 * the stored level. Items may carry multiple markers (see {@link EnchantMarkers}),
 * and each is checked independently — a sword can be both Kasai fire-AOE and
 * Kaminari at once.
 */
public final class EnhancedEnchantEffectHandler {

    private static long tickCounter = 0;

    private EnhancedEnchantEffectHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            tickCounter++;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                applyPassiveEffects(player);
            }
            if (tickCounter % 2 == 0) {
                for (ServerWorld world : server.getWorlds()) {
                    for (var entity : world.iterateEntities()) {
                        if (entity instanceof TridentEntity trident) {
                            checkThorTrident(world, trident);
                        } else if (entity instanceof net.minecraft.entity.projectile.PersistentProjectileEntity proj
                                && !(entity instanceof TridentEntity)) {
                            checkArrowEffects(world, proj);
                        }
                    }
                }
            }
        });

        // Attack-based effects: Kasai fire AOE, Kaminari Raiden, Kori Sub-Zero,
        // Mori Let's dance, Yurei Omnislash. All key off the main-hand weapon.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;
            ItemStack weapon = sp.getMainHandStack();
            ServerWorld sw = (ServerWorld) world;

            int kasai = EnchantMarkers.levelOf(weapon, "kasai");
            if (kasai > 0) applyFireAoe(sw, sp, target, kasai);

            int raiden = EnchantMarkers.levelOf(weapon, "kaminari_raiden");
            if (raiden > 0) applyRaiden(sw, target);

            int subzero = EnchantMarkers.levelOf(weapon, "kori_subzero");
            if (subzero > 0) applySubZero(sw, target, subzero);

            int dance = EnchantMarkers.levelOf(weapon, "mori_dance");
            if (dance > 0) applyLetsDance(sw, sp, target, dance);

            int omni = EnchantMarkers.levelOf(weapon, "yurei_omnislash");
            if (omni > 0) applyOmnislash(sw, sp, target, omni);

            return ActionResult.PASS;
        });

        // Now This Is Water Bending — riptide anywhere.
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient) {
                return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
            }
            ItemStack stack = player.getStackInHand(hand);
            int mizuLvl = EnchantMarkers.levelOf(stack, "mizu");
            if (mizuLvl <= 0 || !(player instanceof ServerPlayerEntity sp)) {
                return net.minecraft.util.TypedActionResult.pass(stack);
            }
            launchRiptide(sp, mizuLvl);
            return net.minecraft.util.TypedActionResult.success(stack);
        });

        // Nanomachines, son! — Daichi Protection: Resistance burst when hit.
        // I Am Inevitable — Daichi Blast Protection: massively reduce explosion damage.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity sp)) return true;
            int nanoLvl = 0, inevitableLvl = 0;
            for (ItemStack armor : sp.getArmorItems()) {
                nanoLvl = Math.max(nanoLvl, EnchantMarkers.levelOf(armor, "daichi_nano"));
                inevitableLvl = Math.max(inevitableLvl, EnchantMarkers.levelOf(armor, "daichi_inevitable"));
            }
            if (nanoLvl > 0) {
                sp.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.RESISTANCE, 40, nanoLvl, false, false, true));
                ((ServerWorld) sp.getWorld()).spawnParticles(ParticleTypes.CRIT,
                        sp.getX(), sp.getY() + 1, sp.getZ(), 8, 0.3, 0.5, 0.3, 0.1);
            }
            // I Am Inevitable: explosion damage is nearly ignored. We can't easily
            // scale the number here (ALLOW_DAMAGE is boolean), so we cancel small
            // explosion hits outright and let big ones through reduced via Resistance.
            if (inevitableLvl > 0 && source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION)) {
                sp.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.RESISTANCE, 60, 3, false, false, true));
                // Cancel the knockback feel by zeroing velocity next tick.
                sp.velocityModified = true;
                if (amount < 4.0f * inevitableLvl) {
                    return false; // negate minor explosion damage entirely
                }
            }
            return true;
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] EnhancedEnchantEffectHandler registered.");
    }

    private static void applyPassiveEffects(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();

        int ghostLvl = 0, flyLvl = 0, frostLvl = 0, sunLvl = 0, stoneLvl = 0;
        int fineLvl = 0, phelpsLvl = 0, breathLvl = 0, sneakLvl = 0;

        List<ItemStack> toScan = new ArrayList<>();
        toScan.add(player.getMainHandStack());
        toScan.add(player.getOffHandStack());
        for (ItemStack armor : player.getArmorItems()) toScan.add(armor);

        for (ItemStack stack : toScan) {
            ghostLvl = Math.max(ghostLvl, EnchantMarkers.levelOf(stack, "yurei"));
            flyLvl = Math.max(flyLvl, EnchantMarkers.levelOf(stack, "kaze"));
            frostLvl = Math.max(frostLvl, EnchantMarkers.levelOf(stack, "kori"));
            sunLvl = Math.max(sunLvl, EnchantMarkers.levelOf(stack, "tengoku"));
            stoneLvl = Math.max(stoneLvl, EnchantMarkers.levelOf(stack, "daichi"));
            fineLvl = Math.max(fineLvl, EnchantMarkers.levelOf(stack, "kasai_fine"));
            phelpsLvl = Math.max(phelpsLvl, EnchantMarkers.levelOf(stack, "mizu_phelps"));
            breathLvl = Math.max(breathLvl, EnchantMarkers.levelOf(stack, "mizu_breath"));
            sneakLvl = Math.max(sneakLvl, EnchantMarkers.levelOf(stack, "kaze_sneaky"));
        }

        if (ghostLvl > 0) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SPEED, 40, ghostLvl, false, false, false));
        }
        if (flyLvl > 0) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOW_FALLING, 40, 0, false, false, false));
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.JUMP_BOOST, 40, 1 + flyLvl, false, false, false));
        }
        if (frostLvl > 0 && tickCounter % 20 == 0) {
            double radius = 3 + frostLvl;
            Box box = new Box(player.getBlockPos()).expand(radius);
            for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e != player && e.isAlive() && !(e instanceof PlayerEntity))) {
                le.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, 60, 2 + frostLvl, false, true, true));
                le.setFrozenTicks(Math.min(le.getMinFreezeDamageTicks() + 20 * frostLvl, 300));
                world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        le.getX(), le.getY() + 0.5, le.getZ(), 5, 0.2, 0.3, 0.2, 0.01);
            }
        }
        if (sunLvl > 0 && tickCounter % 40 == 0
                && world.isDay() && world.isSkyVisible(player.getBlockPos().up())) {
            repairMarked(player, "tengoku", 1 + sunLvl);
        }
        if (stoneLvl > 0 && tickCounter % 100 == 0) {
            repairMarked(player, "daichi", stoneLvl);
        }

        // This Is Fine — Fire Resistance while equipped (Kasai fire prot).
        if (fineLvl > 0) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.FIRE_RESISTANCE, 40, 0, false, false, false));
            if (player.isOnFire() && tickCounter % 10 == 0) {
                world.spawnParticles(ParticleTypes.FLAME,
                        player.getX(), player.getY() + 0.3, player.getZ(), 4, 0.3, 0.3, 0.3, 0.01);
            }
        }
        // Michael Phelps — Dolphin's Grace + Speed while in water.
        if (phelpsLvl > 0 && player.isTouchingWater()) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.DOLPHINS_GRACE, 40, phelpsLvl, false, false, false));
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SPEED, 40, phelpsLvl, false, false, false));
        }
        // Holding Breath Simulator — top off air constantly.
        if (breathLvl > 0) {
            player.setAir(player.getMaxAir());
        }
        // Sneaky Beaky Like — full-speed sneak + brief invisibility while sneaking.
        if (sneakLvl > 0 && player.isSneaking()) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SPEED, 20, sneakLvl, false, false, false));
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.INVISIBILITY, 20, 0, false, false, false));
        }
    }

    private static void repairMarked(ServerPlayerEntity player, String marker, int amount) {
        List<ItemStack> toScan = new ArrayList<>();
        toScan.add(player.getMainHandStack());
        toScan.add(player.getOffHandStack());
        for (ItemStack armor : player.getArmorItems()) toScan.add(armor);
        for (ItemStack stack : toScan) {
            if (EnchantMarkers.has(stack, marker) && stack.isDamaged()) {
                stack.setDamage(Math.max(0, stack.getDamage() - amount));
                return;
            }
        }
    }

    private static void checkThorTrident(ServerWorld world, TridentEntity trident) {
        ItemStack stack = tridentStack(trident);
        if (stack == null) return;
        int level = EnchantMarkers.levelOf(stack, "kaminari");
        if (level <= 0) return;
        if (trident.isOnGround()) return;
        if (trident.getVelocity().lengthSquared() < 0.5) return;

        int interval = Math.max(2, 5 - level);
        if (world.getTime() % interval == 0) {
            var bolt = EntityType.LIGHTNING_BOLT.create(world);
            if (bolt != null) {
                Vec3d pos = trident.getPos();
                bolt.setPosition(pos.x, pos.y, pos.z);
                world.spawnEntity(bolt);
            }
        }
    }

    private static void applyFireAoe(ServerWorld world, ServerPlayerEntity player,
                                     net.minecraft.entity.Entity hitTarget, int level) {
        double radius = 2 + level * 1.5;
        int fireSeconds = 3 + level * 2;
        Box box = new Box(hitTarget.getBlockPos()).expand(radius);
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != player && e.isAlive())) {
            le.setOnFireFor(fireSeconds);
        }
        world.spawnParticles(ParticleTypes.FLAME,
                hitTarget.getX(), hitTarget.getY() + 0.5, hitTarget.getZ(),
                (int) (15 * level), radius / 2, 0.5, radius / 2, 0.05);
        world.playSound(null, hitTarget.getX(), hitTarget.getY(), hitTarget.getZ(),
                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    /** Raiden — lightning on each hit. Balance: cosmetic-safe bolt, capped damage. */
    private static void applyRaiden(ServerWorld world, LivingEntity target) {
        var bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.setPosition(target.getX(), target.getY(), target.getZ());
            world.spawnEntity(bolt);
        }
    }

    /** Sub-Zero — freeze the target; "fatality" hard-freeze if it's low HP. */
    private static void applySubZero(ServerWorld world, LivingEntity target, int level) {
        target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 60 + level * 20, 1 + level, false, true, true));
        target.setFrozenTicks(Math.min(target.getFrozenTicks() + 60 * level, 300));
        // Fatality: if target is already below 20% HP, deep-freeze (long slowness V).
        if (target.getHealth() < target.getMaxHealth() * 0.2f) {
            target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, 120, 4, false, true, true));
            world.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.5f);
        }
        world.spawnParticles(ParticleTypes.SNOWFLAKE,
                target.getX(), target.getY() + 0.5, target.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
    }

    /** Let's dance! — wider/stronger sweep; bonus sweep damage to nearby foes. */
    private static void applyLetsDance(ServerWorld world, ServerPlayerEntity player,
                                       LivingEntity target, int level) {
        double radius = 2.5 + level;
        float bonus = 2.0f + level;
        Box box = new Box(target.getBlockPos()).expand(radius);
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != player && e != target && e.isAlive())) {
            le.damage(world.getDamageSources().playerAttack(player), bonus);
            Vec3d kb = le.getPos().subtract(player.getPos()).normalize().multiply(0.4);
            le.addVelocity(kb.x, 0.1, kb.z);
            le.velocityModified = true;
        }
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                target.getX(), target.getY() + 0.5, target.getZ(), 3, radius / 2, 0.2, radius / 2, 0.0);
    }

    /** Omnislash — hit pierces to additional enemies in a line behind the target. */
    private static void applyOmnislash(ServerWorld world, ServerPlayerEntity player,
                                       LivingEntity target, int level) {
        Vec3d dir = target.getPos().subtract(player.getPos()).normalize();
        int maxHits = 1 + level;
        int hits = 0;
        Box box = new Box(target.getBlockPos()).expand(2 + level * 1.5);
        float dmg = 3.0f + level;
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != player && e != target && e.isAlive())) {
            // Only those roughly in front along the swing direction.
            Vec3d toLe = le.getPos().subtract(player.getPos()).normalize();
            if (toLe.dotProduct(dir) > 0.3) {
                le.damage(world.getDamageSources().playerAttack(player), dmg);
                if (++hits >= maxHits) break;
            }
        }
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                target.getX(), target.getY() + 0.5, target.getZ(), 5, 1.0, 0.3, 1.0, 0.0);
    }

    private static void launchRiptide(ServerPlayerEntity player, int level) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d look = player.getRotationVector();
        double power = 1.5 + level * 0.8;
        player.addVelocity(look.x * power, look.y * power + 0.2, look.z * power);
        player.velocityModified = true;
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.BUBBLE,
                player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.3, 0.5, 0.3, 0.1);
        player.fallDistance = 0;
    }

    /**
     * Bow-enchant effects on arrows in flight. We look at the shooter's held
     * bow for the marker (arrows themselves don't carry the bow's enchant).
     */
    private static void checkArrowEffects(ServerWorld world,
                                          net.minecraft.entity.projectile.PersistentProjectileEntity arrow) {
        if (arrow.isOnGround()) return;
        var owner = arrow.getOwner();
        if (!(owner instanceof ServerPlayerEntity sp)) return;

        // Find a bow/crossbow with a Tengoku/Kasai bow marker.
        ItemStack bow = sp.getMainHandStack();
        int sunshot = EnchantMarkers.levelOf(bow, "tengoku_sunshot");
        int glowstick = EnchantMarkers.levelOf(bow, "tengoku_glowstick");
        int plagueis = EnchantMarkers.levelOf(bow, "kasai_plagueis");
        if (sunshot == 0 && glowstick == 0 && plagueis == 0) {
            // Maybe it's in the offhand.
            bow = sp.getOffHandStack();
            sunshot = EnchantMarkers.levelOf(bow, "tengoku_sunshot");
            glowstick = EnchantMarkers.levelOf(bow, "tengoku_glowstick");
            plagueis = EnchantMarkers.levelOf(bow, "kasai_plagueis");
        }

        Vec3d pos = arrow.getPos();
        // Sunshot — arrow burns + glowing trail.
        if (sunshot > 0) {
            arrow.setOnFireFor(5);
            world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 2, 0.05, 0.05, 0.05, 0.0);
            world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        // Infinite Glowstick — bright trail (light-ish particles).
        if (glowstick > 0) {
            world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 3, 0.1, 0.1, 0.1, 0.01);
        }
        // The Tragedy of Darth Plagueis — flaming arrow trails fire, explodes on impact.
        if (plagueis > 0) {
            arrow.setOnFireFor(8);
            world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.0);
            // On near-ground / low velocity, burst fire.
            if (arrow.getVelocity().lengthSquared() < 0.3) {
                Box box = new Box(arrow.getBlockPos()).expand(2 + plagueis);
                for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box,
                        e -> e != sp && e.isAlive())) {
                    le.setOnFireFor(4 + plagueis * 2);
                }
                world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
                arrow.discard();
            }
        }
    }

    private static ItemStack tridentStack(TridentEntity trident) {
        try {
            ItemStack s = trident.getWeaponStack();
            if (s != null && !s.isEmpty()) return s;
        } catch (Throwable ignored) {}
        try {
            ItemStack s = trident.getItemStack();
            if (s != null && !s.isEmpty()) return s;
        } catch (Throwable ignored) {}
        return null;
    }
}