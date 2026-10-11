
package ru.selteck.ghostlyserver.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import java.net.URI;

public final class GhostlyServerScreen extends Screen {
    private final Screen parent;
    private String status = "Друзья смогут подключиться по адресу Playit.";
    private int statusColor = 0xB7D7FF;

    public GhostlyServerScreen(Screen parent) {
        super(Component.literal("Ghostly Server"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int openY = this.height / 2 - 2;

        this.addRenderableWidget(Button.builder(Component.literal("Открыть доступ"), button -> openAccess())
            .bounds(centerX - 100, openY, 200, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Открыть Playit.gg"), button ->
            Util.getPlatform().openUri(URI.create("https://playit.gg/")))
            .bounds(centerX - 100, openY + 26, 200, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Назад"), button -> onClose())
            .bounds(centerX - 100, openY + 52, 200, 20)
            .build());
    }

    private void openAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();

        if (server == null) {
            setStatus("Эта кнопка работает в одиночном мире.", 0xFF7777);
            return;
        }

        if (!GhostlyServerClient.isPlayitCompanionLoaded()) {
            setStatus("Не найден Playit Companion. Запусти установщик из ZIP.", 0xFF7777);
            return;
        }

        if (server.isPublished()) {
            setStatus("Мир уже открыт. Проверь чат на адрес Playit.", 0x8FE3A0);
            return;
        }

        // Port 0 asks Minecraft to choose an available port automatically.
        // Playit Companion detects the integrated-server listener and opens its public tunnel.
        boolean opened = server.publishServer((GameType) null, false, 0);
        if (opened) {
            setStatus("Доступ открыт. Смотри чат: сначала может появиться ссылка активации Playit.", 0x8FE3A0);
            minecraft.gui.getChat().addMessage(Component.literal(
                "Ghostly Server: локальный мир открыт. Смотри чат — Playit Companion выдаст ссылку активации или адрес сервера."));
        } else {
            setStatus("Не получилось открыть мир. Попробуй ещё раз или проверь журнал игры.", 0xFF7777);
        }
    }

    private void setStatus(String message, int color) {
        this.status = message;
        this.statusColor = color;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 38, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("Открывай мир для друзей через интернет"), this.width / 2, 61, 0xC7CDD8);
        graphics.drawCenteredString(this.font, Component.literal("Адрес выдаёт Playit — его можно передать игрокам из других стран."), this.width / 2, 74, 0xA5AFBD);
        graphics.drawCenteredString(this.font, Component.literal(this.status), this.width / 2, this.height / 2 - 24, this.statusColor);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
