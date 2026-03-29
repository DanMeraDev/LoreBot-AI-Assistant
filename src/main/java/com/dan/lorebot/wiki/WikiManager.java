package com.dan.lorebot.wiki;

import com.dan.lorebot.LoreBot;
import net.minecraftforge.fml.common.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class WikiManager {

    private static File wikiDir;
    private static final List<WikiChunk> chunkCache = new ArrayList<>();
    private static long lastLoadTime = 0;

    public static void init() {
        wikiDir = new File(Loader.instance().getConfigDir().getParentFile(), "lorebot_wiki");
        if (!wikiDir.exists()) {
            wikiDir.mkdirs();
            createExampleFile();
        }
        reloadIndex();
    }

    public static File getWikiDir() {
        return wikiDir;
    }

    // Indexa todos los archivos en fragmentos pequeños para una búsqueda rápida
    public static synchronized void reloadIndex() {
        chunkCache.clear();
        File[] files = wikiDir.listFiles((dir, name) -> 
            name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".pdf") || name.endsWith(".docx")
        );

        if (files == null) return;

        for (File file : files) {
            try {
                String fullText = extractText(file);
                if (fullText == null || fullText.isEmpty()) continue;

                // Dividimos el texto en "chunks" de ~600 caracteres para precisión
                String[] paragraphs = fullText.split("(?<=\\.)\\s+");
                for (String p : paragraphs) {
                    if (p.trim().length() > 20) {
                        chunkCache.add(new WikiChunk(file.getName(), p.trim()));
                    }
                }
            } catch (Exception e) {
                LoreBot.logger.error("Failed to index file: " + file.getName(), e);
            }
        }
        lastLoadTime = System.currentTimeMillis();
        LoreBot.logger.info("Wiki Index updated: " + chunkCache.size() + " fragments loaded.");
    }

    public static String getContextForQuery(String query) {
        // Si han pasado más de 5 min, verificamos si hay archivos nuevos (opcional)
        if (System.currentTimeMillis() - lastLoadTime > 300000) reloadIndex();

        String[] queryTerms = query.toLowerCase().split("\\s+");
        
        // Puntuamos cada fragmento según cuántas palabras de la consulta contiene
        List<WikiChunk> relevantChunks = chunkCache.stream()
            .map(chunk -> {
                int score = 0;
                String lowerContent = chunk.content.toLowerCase();
                for (String term : queryTerms) {
                    if (term.length() > 3 && lowerContent.contains(term)) score++;
                }
                chunk.lastScore = score;
                return chunk;
            })
            .filter(chunk -> chunk.lastScore > 0)
            .sorted((c1, c2) -> Integer.compare(c2.lastScore, c1.lastScore))
            .limit(5) // Solo devolvemos los 5 fragmentos más importantes
            .collect(Collectors.toList());

        if (relevantChunks.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (WikiChunk chunk : relevantChunks) {
            sb.append("\n[Source: ").append(chunk.source).append("]\n");
            sb.append(chunk.content).append("\n");
        }
        return sb.toString();
    }

    private static String extractText(File file) throws Exception {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".txt") || name.endsWith(".md")) {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } else if (name.endsWith(".pdf")) {
            try (PDDocument doc = PDDocument.load(file)) {
                return new PDFTextStripper().getText(doc);
            }
        } else if (name.endsWith(".docx")) {
            return extractDocxText(file);
        }
        return null;
    }

    private static final Pattern W_T_PATTERN = Pattern.compile("<w:t[^>]*>([^<]+)</w:t>");
    private static final Pattern W_P_PATTERN = Pattern.compile("</w:p>");

    private static String extractDocxText(File file) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    byte[] data = readAllBytes(zis);
                    String xml = new String(data, StandardCharsets.UTF_8);
                    // Replace paragraph endings with newlines, then extract text from <w:t> tags
                    xml = W_P_PATTERN.matcher(xml).replaceAll("</w:p>\n");
                    Matcher matcher = W_T_PATTERN.matcher(xml);
                    StringBuilder sb = new StringBuilder();
                    while (matcher.find()) {
                        sb.append(matcher.group(1));
                    }
                    return sb.toString();
                }
            }
        }
        return null;
    }

    private static byte[] readAllBytes(java.io.InputStream is) throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }

    private static void createExampleFile() {
        File example = new File(wikiDir, "example_wiki.txt");
        try {
            String content = "Welcome to the LoreBot Wiki!\nAdd documents here to enhance LoreBot's knowledge.";
            Files.write(example.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    // Clase interna para representar un fragmento de información
    private static class WikiChunk {
        String source;
        String content;
        int lastScore;

        WikiChunk(String source, String content) {
            this.source = source;
            this.content = content;
        }
    }
}