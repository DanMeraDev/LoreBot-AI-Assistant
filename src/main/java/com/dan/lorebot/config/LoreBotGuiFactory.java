package com.dan.lorebot.config;

import com.dan.lorebot.LoreBot;
import com.dan.lorebot.wiki.WikiManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;

import java.awt.Desktop;
import java.io.IOException;
import java.util.Set;

public class LoreBotGuiFactory implements IModGuiFactory {

    public LoreBotGuiFactory() {
        // Constructor vacío obligatorio para Forge
    }

    @Override
    public void initialize(Minecraft minecraftInstance) {}

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return new LoreBotConfigGui(parentScreen);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    // Clase interna para personalizar la pantalla de configuración
    public static class LoreBotConfigGui extends GuiConfig {
        public LoreBotConfigGui(GuiScreen parentScreen) {
            super(parentScreen,
                    ConfigElement.from(ModConfig.class).getChildElements(),
                    LoreBot.MODID,
                    false,
                    false,
                    "LoreBot AI Assistant Configuration"
            );
        }

        @Override
        public void initGui() {
            super.initGui();
            // Añadimos un botón personalizado para abrir la carpeta de la wiki
            // Lo colocamos en la parte inferior, desplazado un poco del botón "Done"
            this.buttonList.add(new GuiButton(999, this.width / 2 - 100, this.height - 52, 200, 20, "§6Open Wiki Folder (RAG)"));
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button.id == 999) {
                try {
                    // Abrimos la carpeta lorebot_wiki usando el explorador del sistema
                    Desktop.getDesktop().open(WikiManager.getWikiDir());
                } catch (IOException e) {
                    LoreBot.logger.error("Could not open wiki folder", e);
                }
            }
            super.actionPerformed(button);
        }
    }
}