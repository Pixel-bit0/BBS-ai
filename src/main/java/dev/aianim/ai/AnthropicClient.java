package dev.aianim.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Minimal Anthropic Messages API client using the JDK's built-in HTTP client. */
public final class AnthropicClient
{
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private AnthropicClient() {}

    /** Completes with the model's text, or exceptionally with a readable message. */
    public static CompletableFuture<String> ask(String apiKey, String model, String system, String user, int maxTokens)
    {
        JsonObject body = new JsonObject();

        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("system", system);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();

        message.addProperty("role", "user");
        message.addProperty("content", user);
        messages.add(message);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
            .timeout(Duration.ofSeconds(90))
            .header("content-type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply((response) ->
        {
            if (response.statusCode() != 200)
            {
                throw new IllegalStateException(describeError(response.statusCode(), response.body()));
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            StringBuilder text = new StringBuilder();

            for (int i = 0; i < content.size(); i++)
            {
                JsonObject block = content.get(i).getAsJsonObject();

                if (block.has("text"))
                {
                    text.append(block.get("text").getAsString());
                }
            }

            return text.toString();
        });
    }

    private static String describeError(int status, String body)
    {
        String detail = "";

        try
        {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");

            if (error != null && error.has("message"))
            {
                detail = error.get("message").getAsString();
            }
        }
        catch (Exception ignored)
        {}

        String hint = switch (status)
        {
            case 401 -> "API key rejected. Check it with /aianim key.";
            case 404 -> "Model not found. Change it with /aianim model <name>.";
            case 429 -> "Rate limited. It will retry automatically.";
            default -> "";
        };

        return "HTTP " + status + " " + hint + " " + detail;
    }
}
