package com.cutedifficult;

import com.cutedifficult.command.CdCommand;
import com.cutedifficult.entity.ModEntities;
import com.cutedifficult.event.BestiaryHandler;
import com.cutedifficult.event.ChatCommandHandler;
import com.cutedifficult.event.FoxAbilityHandler;
import com.cutedifficult.event.FoxAggressionHandler;
import com.cutedifficult.event.FoxAuraHandler;
import com.cutedifficult.event.FoxBehaviorHandler;
import com.cutedifficult.event.FoxDeathHandler;
import com.cutedifficult.event.FoxFlightHandler;
import com.cutedifficult.event.FoxOfferingHandler;
import com.cutedifficult.event.FoxSpawnHandler;
import com.cutedifficult.event.GreedyLootHandler;
import com.cutedifficult.event.MobSpawnHandler;
import com.cutedifficult.event.PhantomDroneHandler;
import com.cutedifficult.event.PlayerJoinHandler;
import com.cutedifficult.event.PlayerTickHandler;
import com.cutedifficult.event.ResonanceBlessingHandler;
import com.cutedifficult.event.SkeletonMeleeHandler;
import com.cutedifficult.event.SmartMobsHandler;
import com.cutedifficult.event.SniperProjectileHandler;
import com.cutedifficult.event.SpiderAbilitiesHandler;
import com.cutedifficult.event.TotemEffectHandler;
import com.cutedifficult.item.ModItems;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CuteDifficult implements ModInitializer {
    public static final String MOD_ID = "cutedifficult";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static DifficultyMode currentMode = DifficultyMode.CRUEL;

    @Override
    public void onInitialize() {
        LOGGER.info("[CuteDifficult] Cruel Universe Tortures Everyone — initializing.");
        LOGGER.info("[CuteDifficult] The kitsune are watching. The dragon stirs in the void.");

        ModEntities.init();
        ModItems.init();

        PlayerJoinHandler.register();
        PlayerTickHandler.register();
        MobSpawnHandler.register();
        SmartMobsHandler.register();
        ChatCommandHandler.register();
        PhantomDroneHandler.register();
        TotemEffectHandler.register();
        BestiaryHandler.register();
        GreedyLootHandler.register();
        SpiderAbilitiesHandler.register();
        SkeletonMeleeHandler.register();
        SniperProjectileHandler.register();
        FoxSpawnHandler.register();
        FoxOfferingHandler.register();
        FoxAuraHandler.register();
        FoxBehaviorHandler.register();
        FoxAbilityHandler.register();
        FoxAggressionHandler.register();
        FoxFlightHandler.register();
        FoxDeathHandler.register();
        ResonanceBlessingHandler.register();
        CdCommand.register();

        LOGGER.info("[CuteDifficult] All systems online. May Inari have mercy on the players.");
    }
}
