
package ru.selteck.ghostlyserver;

import net.minecraftforge.fml.common.Mod;

@Mod(GhostlyServerMod.MOD_ID)
public final class GhostlyServerMod {
    public static final String MOD_ID = "ghostlyserver";

    public GhostlyServerMod() {
        // The pause-menu integration is client-only and is registered by GhostlyServerClient.
    }
}
