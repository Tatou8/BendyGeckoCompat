package fr.tatou8.bendygeckocompat.forge;

import fr.tatou8.bendygeckocompat.GeckoLibCompat;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("bendygeckocompat")
public class BendyGeckoForge {

    public static final Logger LOGGER = LoggerFactory.getLogger("bendygeckocompat");

    public BendyGeckoForge() {
        if (!FMLLoader.getDist().isClient()) {
            LOGGER.warn("[bendygeckocompat] Loading a client-only mod on server-side");
        }
    }

    @Mod.EventBusSubscriber(modid = "bendygeckocompat", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Initializing BendyGeckoCompat for Forge");
            GeckoLibCompat.init(LOGGER);
        }
    }
}
