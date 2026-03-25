package com.dan.lorebot.config;

import com.dan.lorebot.LoreBot;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = LoreBot.MODID, name = "lorebot_config")
public class ModConfig {

    @Config.Comment("The AI provider you want to use (OLLAMA, OPENAI, GEMINI, CLAUDE, MISTRAL)")
    @Config.Name("1. AI Provider")
    public static Provider aiProvider = Provider.OLLAMA;

    @Config.Comment("Your API Key (Leave blank if using a local AI like Ollama)")
    @Config.Name("2. API Key")
    public static String apiKey = "";

    @Config.Comment("URL for the API. This is REQUIRED for Ollama (local). For cloud providers, leave as default unless using a proxy or local OpenAI-compatible server.")
    @Config.Name("3. Endpoint URL")
    public static String apiUrl = "http://localhost:11434/api/generate";

    @Config.Comment("Base instructions for the AI. Defines how it should behave.")
    @Config.Name("4. System Prompt")
    public static String systemPrompt = "You are LoreBot, a highly knowledgeable AI assistant. Your goal is to provide detailed, technical, and comprehensive information. When 'Local Wiki' context is provided, you MUST prioritize that information above your general knowledge. If the context contains specific lists (like enchantments or guides), reproduce them exactly and explain them. Use Markdown (bullet points, bold text) for readability.";

    @Config.Comment("The language the AI should use to respond.")
    @Config.Name("5. Response Language")
    public static String language = "English";

    @Config.Comment("If enabled, the mod will search for information in the 'lorebot_wiki' folder before asking the AI.")
    @Config.Name("6. Enable Local Wiki (RAG)")
    public static boolean enableWiki = true;

    public enum Provider {
        OLLAMA, OPENAI, GEMINI, CLAUDE, MISTRAL
    }

    @Mod.EventBusSubscriber(modid = LoreBot.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(LoreBot.MODID)) {
                ConfigManager.sync(LoreBot.MODID, Config.Type.INSTANCE);
                LoreBot.logger.info("LoreBot configuration updated. Current provider: " + aiProvider.name());
            }
        }
    }
}