
package ru.selteck.ghostlyserver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import ru.selteck.ghostlyserver.GhostlyServerMod;

@Mod.EventBusSubscriber(modid = GhostlyServerMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GhostlyServerClient {
    private GhostlyServerClient() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pauseScreen)) {
            return;
        }

        // On normal-sized GUI layouts, sit to the right of the vanilla Open to LAN row.
        // On narrower layouts, use the free upper-right corner.
        final int buttonWidth = 100;
        final int x;
        final int y;
        if (pauseScreen.width >= 410) {
            x = Math.min(pauseScreen.width - buttonWidth - 6, pauseScreen.width / 2 + 102);
            y = pauseScreen.height / 4 + 120;
        } else {
            x = pauseScreen.width - buttonWidth - 6;
            y = 8;
        }

        Button serverButton = Button.builder(Component.literal("Сервер"), button ->
                Minecraft.getInstance().setScreen(new GhostlyServerScreen(pauseScreen)))
            .bounds(x, y, buttonWidth, 20)
            .build();
        event.addListener(serverButton);
    }

    static boolean isPlayitCompanionLoaded() {
        return ModList.get().isLoaded("playit_companion");
    }
}
