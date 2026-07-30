package com.org.orchestrator.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calls a Gemini model through the Google ADK REST endpoint using only
 * the built-in Java HTTP client — no SDK dependency.
 *
 * Reads the API key from the GOOGLE_API_KEY environment variable.
 * Never logs the key. Sets a timeout so a slow model surfaces as
 * a failure the fallback can catch, rather than a run that hangs.
 */
public final class GoogleAdkLlmClient implements LlmClient {

    private static final String ENV_KEY = "GOOGLE_API_KEY";
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // Endpoint pattern: key is appended as a query parameter.
    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;

    public GoogleAdkLlmClient() {
        this(System.getenv(ENV_KEY), DEFAULT_MODEL);
    }

    public GoogleAdkLlmClient(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    ENV_KEY + " environment variable is not set or blank");
        }
        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    @Override
    public String call(String prompt) throws LlmException {
        if (prompt == null || prompt.isBlank()) {
            throw new LlmException("Prompt must not be blank");
        }

        try {
            String body = buildRequestBody(prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(ENDPOINT_TEMPLATE, model, apiKey)))
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmException("Google ADK returned HTTP " + response.statusCode()
                        + ": " + response.body());
            }

            return extractText(response.body());

        } catch (LlmException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Request interrupted", e);
        } catch (IOException e) {
            throw new LlmException("Network error calling Google ADK", e);
        } catch (Exception e) {
            throw new LlmException("Unexpected error calling Google ADK", e);
        }
    }

    @Override
    public String name() {
        return "google-adk (" + model + ")";
    }

    /**
     * Builds the Gemini generateContent JSON body.
     * Hand-built to avoid a JSON library dependency.
     */
    private static String buildRequestBody(String prompt) {
        // Escape characters that would break the JSON string.
        String escaped = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        return """
                {
                  "contents": [
                    {
                      "parts": [
                        { "text": "%s" }
                      ]
                    }
                  ]
                }
                """.formatted(escaped);
    }

    /**
     * Pulls the generated text from the response JSON.
     * Minimal parsing — avoids a JSON library dependency.
     */
    private static String extractText(String json) throws LlmException {
        // Look for the "text" field inside candidates[0].content.parts[0].
        String marker = "\"text\"";
        int idx = json.indexOf(marker);
        if (idx < 0) {
            throw new LlmException("No text field in Google ADK response: " + json);
        }

        // Walk past "text" : "
        int colon = json.indexOf(':', idx + marker.length());
        if (colon < 0) {
            throw new LlmException("Malformed response: no colon after text field");
        }
        int openQuote = json.indexOf('"', colon + 1);
        if (openQuote < 0) {
            throw new LlmException("Malformed response: no opening quote for text value");
        }

        // Find the matching close quote, respecting escapes.
        StringBuilder result = new StringBuilder();
        for (int i = openQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"'  -> result.append('"');
                    case '\\' -> result.append('\\');
                    case 'n'  -> result.append('\n');
                    case 'r'  -> result.append('\r');
                    case 't'  -> result.append('\t');
                    default   -> { result.append('\\'); result.append(next); }
                }
                i++;
            } else if (c == '"') {
                return result.toString();
            } else {
                result.append(c);
            }
        }
        throw new LlmException("Malformed response: unterminated text value");
    }
}
