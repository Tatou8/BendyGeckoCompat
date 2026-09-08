package fr.tatou8.bendygeckocompat.fabric;

import fr.tatou8.bendygeckocompat.GeckoLibCompat;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Init implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("bendygeckocompat");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing BendyGeckoCompat for Fabric");
        GeckoLibCompat.init(LOGGER);
    }
}
