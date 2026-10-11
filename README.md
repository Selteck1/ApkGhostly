
# Ghostly Server — Minecraft 1.20.1 Forge

Ghostly Server adds a **Сервер** button to the pause menu (Esc). From its screen, **Открыть доступ** publishes a singleplayer world to LAN. The official Playit Companion mod detects the integrated-server listener and opens a public tunnel.

## Important networking note

A random public IP cannot be manufactured locally. The public address is allocated by Playit.gg. This avoids router port-forwarding in typical home-network setups. On first use, Playit Companion may print a claim URL in Minecraft chat; open it and complete the one-time agent claim. After that, the public address appears in chat and can be shared with friends in other countries.

## Setup

- Minecraft Java Edition 1.20.1
- Forge 1.20.1 (47.4.x)
- Java 17
- Install both Ghostly Server and Playit Companion into the same profile's mods folder. The included Windows installer downloads both.
- Friends need to use the same Minecraft version and the same mods/modpack if the world uses modded blocks or items.

## Build from source

Requires JDK 17 and Gradle 8.8. Run:

    gradle build --no-daemon

The reobfuscated JAR is written to build/libs/ghostly-server-1.0.0.jar. GitHub Actions builds the JAR and publishes a GitHub release after a commit to main.

## Attribution

The public tunnel is provided by Playit.gg. The installer fetches the official MIT-licensed Playit Companion Forge mod by playit-cloud. Its code is not embedded in this project's JAR.
