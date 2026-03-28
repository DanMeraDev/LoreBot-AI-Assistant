package com.dan.lorebot.commands;

import com.dan.lorebot.LoreBot;
import com.dan.lorebot.config.ModConfig;
import com.dan.lorebot.wiki.WikiManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CommandAsk extends CommandBase {

    @Override
    public String getName() {
        return "ask";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ask <question>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString("§cYou need to write a question! Example: /ask how do I craft a backpack"));
            return;
        }

        String prompt = String.join(" ", args);
        sender.sendMessage(new TextComponentString("§7[" + sender.getName() + "] " + prompt));
        sender.sendMessage(new TextComponentString("§e[LoreBot] Asking " + ModConfig.aiProvider.name() + "..."));

        new Thread(() -> {
            try {
                String aiReply = "";
                String finalPrompt = prompt;

                // RAG: Buscamos contexto en la wiki local si está habilitado
                if (ModConfig.enableWiki) {
                    String context = WikiManager.getContextForQuery(prompt);
                    if (!context.isEmpty()) {
                        finalPrompt = "CRITICAL INSTRUCTION: Use the following LOCAL WIKI CONTEXT to answer the user's question. " +
                                     "If the information is in the context, prioritize it and be as detailed as possible. " +
                                     "If you find specific enchantment lists or technical guides, reproduce them faithfully.\n\n" +
                                     "LOCAL WIKI CONTEXT:\n" + context + "\n\n" +
                                     "USER QUESTION: " + prompt;
                        LoreBot.logger.info("Wiki context found and added to prompt.");
                    }
                }

                // Combinamos el system prompt base con la instrucción del idioma
                String finalSystemPrompt = ModConfig.systemPrompt + "\n\nCRITICAL: Respond ONLY in " + ModConfig.language + ".";

                switch (ModConfig.aiProvider) {
                    case OLLAMA:
                        aiReply = askOllama(finalPrompt, finalSystemPrompt);
                        break;
                    case OPENAI:
                        aiReply = askOpenAI(finalPrompt, finalSystemPrompt);
                        break;
                    case GEMINI:
                        aiReply = askGemini(finalPrompt, finalSystemPrompt);
                        break;
                    case CLAUDE:
                        aiReply = askClaude(finalPrompt, finalSystemPrompt);
                        break;
                    case MISTRAL:
                        aiReply = askMistral(finalPrompt, finalSystemPrompt);
                        break;
                    default:
                        aiReply = "§cProvider " + ModConfig.aiProvider.name() + " is not implemented yet.";
                }

                final String finalReply = aiReply;
                server.addScheduledTask(() -> {
                    sender.sendMessage(new TextComponentString("§b[LoreBot] §f" + finalReply));
                });

            } catch (Exception e) {
                e.printStackTrace();
                server.addScheduledTask(() -> {
                    sender.sendMessage(new TextComponentString("§c[LoreBot] Connection error: " + e.getMessage()));
                });
            }
        }).start();
    }

    private String askOllama(String userPrompt, String systemPrompt) throws Exception {
        URL url = new URL(ModConfig.apiUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setDoOutput(true);

        JsonObject jsonPayload = new JsonObject();
        jsonPayload.addProperty("model", "llama3");
        jsonPayload.addProperty("prompt", userPrompt);
        jsonPayload.addProperty("system", systemPrompt);
        jsonPayload.addProperty("stream", false);

        sendPostData(con, jsonPayload);

        if (con.getResponseCode() == 200) {
            String result = readResponse(con);
            JsonObject jsonResponse = new JsonParser().parse(result).getAsJsonObject();
            return jsonResponse.has("response") ? jsonResponse.get("response").getAsString() : "Error parsing Ollama response.";
        }
        return "HTTP Error: " + con.getResponseCode();
    }

    private String askOpenAI(String userPrompt, String systemPrompt) throws Exception {
        // Si el usuario puso una URL personalizada (no la de Ollama por defecto), la usamos.
        String urlStr = ModConfig.apiUrl.contains("11434") ? "https://api.openai.com/v1/chat/completions" : ModConfig.apiUrl;
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Authorization", "Bearer " + ModConfig.apiKey);
        con.setDoOutput(true);

        JsonObject jsonPayload = new JsonObject();
        jsonPayload.addProperty("model", "gpt-3.5-turbo");
        JsonArray messages = new JsonArray();
        
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        
        messages.add(systemMsg);
        messages.add(userMsg);
        jsonPayload.add("messages", messages);

        sendPostData(con, jsonPayload);

        if (con.getResponseCode() == 200) {
            String result = readResponse(con);
            JsonObject jsonResponse = new JsonParser().parse(result).getAsJsonObject();
            return jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
        }
        return "OpenAI Error: " + con.getResponseCode();
    }

    private String askClaude(String userPrompt, String systemPrompt) throws Exception {
        URL url = new URL("https://api.anthropic.com/v1/messages");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("x-api-key", ModConfig.apiKey);
        con.setRequestProperty("anthropic-version", "2023-06-01");
        con.setDoOutput(true);

        JsonObject jsonPayload = new JsonObject();
        jsonPayload.addProperty("model", "claude-3-haiku-20240307");
        jsonPayload.addProperty("max_tokens", 1024);
        jsonPayload.addProperty("system", systemPrompt);
        
        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);
        jsonPayload.add("messages", messages);

        sendPostData(con, jsonPayload);

        if (con.getResponseCode() == 200) {
            String result = readResponse(con);
            JsonObject jsonResponse = new JsonParser().parse(result).getAsJsonObject();
            return jsonResponse.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
        }
        return "Claude Error: " + con.getResponseCode();
    }

    private String askMistral(String userPrompt, String systemPrompt) throws Exception {
        URL url = new URL("https://api.mistral.ai/v1/chat/completions");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Authorization", "Bearer " + ModConfig.apiKey);
        con.setDoOutput(true);

        JsonObject jsonPayload = new JsonObject();
        jsonPayload.addProperty("model", "open-mistral-7b");
        JsonArray messages = new JsonArray();
        
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        
        messages.add(systemMsg);
        messages.add(userMsg);
        jsonPayload.add("messages", messages);

        sendPostData(con, jsonPayload);

        if (con.getResponseCode() == 200) {
            String result = readResponse(con);
            JsonObject jsonResponse = new JsonParser().parse(result).getAsJsonObject();
            return jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
        }
        return "Mistral Error: " + con.getResponseCode();
    }

    private String askGemini(String userPrompt, String systemPrompt) throws Exception {
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + ModConfig.apiKey);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setDoOutput(true);

        JsonObject jsonPayload = new JsonObject();
        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("text", systemPrompt);
        systemParts.add(systemPart);
        systemInstruction.add("parts", systemParts);
        jsonPayload.add("system_instruction", systemInstruction);
        
        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", userPrompt);
        parts.add(part);
        content.add("parts", parts);
        
        JsonArray contents = new JsonArray();
        contents.add(content);
        jsonPayload.add("contents", contents);

        sendPostData(con, jsonPayload);

        int code = con.getResponseCode();
        if (code == 200) {
            String result = readResponse(con);
            JsonObject jsonResponse = new JsonParser().parse(result).getAsJsonObject();
            return jsonResponse.getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
        } else {
            String errorMsg = "Gemini Error: " + code;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);
                LoreBot.logger.error("Gemini API Error details: " + response.toString());
            } catch (Exception ignored) {}
            return errorMsg + " (Check console for details)";
        }
    }

    private void sendPostData(HttpURLConnection con, JsonObject payload) throws Exception {
        try (OutputStream os = con.getOutputStream()) {
            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
    }

    private String readResponse(HttpURLConnection con) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }
}