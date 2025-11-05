package com.zanable.marketmaking.bot.tools;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.TelegramGetUpdatesResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class TelegramBot {
    private final String token;
    private final HttpClient http = HttpClient.newHttpClient();

    public TelegramBot(String token) throws InterruptedException {
        this.token = token;
        try {
            getUpdates();
        } catch (Exception e) {
            throw new InterruptedException("Can't use API key");
        }
    }

    /**
     * Send a plain text message.
     *
     * @param chatId  Can be a user/group/channel id (e.g., -1001234567890) or "@channelusername"
     * @param text    Message text (1–4096 chars)
     * @return        The raw Telegram API response body (JSON)
     * @throws IOException
     * @throws InterruptedException
     */
    public String sendMessage(String chatId, String text) throws IOException, InterruptedException {
        return sendMessage(chatId, text, null, true);
    }

    /**
     * Send a message with optional formatting options.
     *
     * @param chatId                 e.g., "@mychannel" or "-1001234567890"
     * @param text                   Message text
     * @param parseMode              "MarkdownV2", "Markdown", "HTML", or null for plain text
     * @param disableWebPagePreview  true to suppress link previews
     * @return                       Raw JSON response
     */
    public String sendMessage(String chatId, String text, String parseMode, boolean disableWebPagePreview)
            throws IOException, InterruptedException {

        String url = "https://api.telegram.org/bot" + token + "/sendMessage";

        // Build a small JSON payload manually (no external JSON lib).
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"chat_id\":").append(formatChatId(chatId)).append(",")
                .append("\"text\":").append(escapeJson(text)).append(",")
                .append("\"disable_web_page_preview\":").append(disableWebPagePreview);
        if (parseMode != null && !parseMode.isBlank()) {
            json.append(",\"parse_mode\":").append(escapeJson(parseMode));
        }
        json.append("}");

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (resp.statusCode() != 200) {
            throw new IOException("Telegram API HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    public TelegramGetUpdatesResponse getMyChatUpdates(String offset)
            throws IOException, InterruptedException {

        String url = "https://api.telegram.org/bot" + token + "/getUpdates?allowed_updates=%5B%22my_chat_member%22%5D";
        if (offset != null && !offset.isEmpty()) {
            url = "https://api.telegram.org/bot" + token + "/getUpdates?allowed_updates=%5B%22my_chat_member%22%5D&offset=" + offset;
        }

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(url))
                .GET().build();

        HttpResponse<String> respHttp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = respHttp.body();
        if (respHttp.statusCode() != 200) {
            throw new IOException("Telegram API HTTP " + respHttp.statusCode() + ": " + respHttp.body());
        }
        TelegramGetUpdatesResponse resp =
                new Gson().fromJson(body, TelegramGetUpdatesResponse.class);

        return resp;

    }

    public TelegramGetUpdatesResponse getUpdates()
            throws IOException, InterruptedException {

        String url = "https://api.telegram.org/bot" + token + "/getUpdates";

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(url))
                .GET().build();

        HttpResponse<String> respHttp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = respHttp.body();
        if (respHttp.statusCode() != 200) {
            throw new IOException("Telegram API HTTP " + respHttp.statusCode() + ": " + respHttp.body());
        }
        TelegramGetUpdatesResponse resp =
                new Gson().fromJson(body, TelegramGetUpdatesResponse.class);

        return resp;

    }

    // --- Helpers ---

    // Formats chat_id as JSON string or number depending on input.
    private static String formatChatId(String chatId) {
        // If it looks numeric (e.g., -100123...), send as number; otherwise send as JSON string (for @username).
        try {
            long id = Long.parseLong(chatId.trim());
            return String.valueOf(id);
        } catch (NumberFormatException e) {
            return escapeJson(chatId);
        }
    }

    // Minimal JSON string escaper: wraps in quotes and escapes backslash/quote/newlines.
    private static String escapeJson(String s) {
        String out = s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + out + "\"";
    }
}