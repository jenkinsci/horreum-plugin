package jenkins.plugins.horreum;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thin REST client for communicating with a Horreum server.
 * Uses {@link java.net.http.HttpClient} — no external HTTP library dependencies.
 */
public class HorreumClient implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey; // null when auth is disabled

    public HorreumClient(String baseUrl, String apiKey) {
        // strip trailing slash
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
    }

    /**
     * Find a folder by name.
     *
     * @return the folder JSON ({id, name, groupId}) if it exists, or null if not found
     */
    public JsonNode getFolder(String name) throws HorreumClientException {
        HttpResponse<String> response = doGet("/api/folder/find?name=" + encode(name));
        if (response.statusCode() == 404 || response.statusCode() == 204) {
            return null;
        }
        checkResponse(response, "GET folder");
        String body = response.body();
        if (body == null || body.isBlank()) {
            return null;
        }
        return parseJson(body);
    }

    /**
     * Create a new folder. Returns the folder as JSON ({id, name, groupId}).
     */
    public JsonNode createFolder(String name) throws HorreumClientException {
        HttpResponse<String> response = doPost("/api/folder?name=" + encode(name), "");
        checkResponse(response, "create folder");
        return parseJson(response.body());
    }

    /**
     * Get the folder ID for a given folder name. Returns -1 if not found.
     */
    public long getFolderId(String name) throws HorreumClientException {
        JsonNode folder = getFolder(name);
        if (folder == null) {
            return -1;
        }
        return folder.get("id").asLong();
    }

    /**
     * Upload JSON data to a folder by ID. Returns the upload ID (root value ID).
     *
     * @param folderId folder ID (from {@link #createFolder} or {@link #getFolderId})
     * @param path optional path within the folder (may be null)
     * @param jsonData the raw JSON string to upload
     * @return the upload ID
     */
    public long upload(long folderId, String path, String jsonData) throws HorreumClientException {
        String uri = "/api/folder/" + folderId + "/upload";
        if (path != null && !path.isEmpty()) {
            uri += "?path=" + encode(path);
        }
        HttpResponse<String> response = doPost(uri, jsonData);
        checkResponse(response, "upload");
        return Long.parseLong(response.body().trim());
    }

    /**
     * Upload JSON data to a folder by name. Looks up the folder ID first.
     * If the folder does not exist, throws an exception.
     *
     * @param folderName folder name
     * @param path optional path within the folder (may be null)
     * @param jsonData the raw JSON string to upload
     * @return the upload ID
     */
    public long uploadToFolder(String folderName, String path, String jsonData) throws HorreumClientException {
        long folderId = getFolderId(folderName);
        if (folderId < 0) {
            throw new HorreumClientException("Folder '" + folderName + "' not found", 404);
        }
        return upload(folderId, path, jsonData);
    }

    /**
     * Get the processing status of an upload.
     *
     * @param uploadId the upload ID returned from {@link #upload}
     * @return JSON with fields: id (long), state (PROCESSING|COMPLETED|FAILED), error (string or null).
     *         Returns null if the upload was not found (404).
     */
    public JsonNode getProcessingStatus(long uploadId) throws HorreumClientException {
        HttpResponse<String> response = doGet("/api/processing/" + uploadId);
        if (response.statusCode() == 404) {
            return null;
        }
        checkResponse(response, "get processing status");
        return parseJson(response.body());
    }

    /**
     * Get detection changes descended from an upload.
     * Calls {@code GET /api/value/{uploadId}/descendants?detection=true}.
     *
     * @param uploadId the upload ID
     * @return JSON array of Value objects. Each has fields: id, data, node ({id, name, type, ...}), folder.
     *         Empty array if no changes were detected.
     */
    public JsonNode getDetectionChanges(long uploadId) throws HorreumClientException {
        HttpResponse<String> response = doGet("/api/value/" + uploadId + "/descendants?detection=true");
        checkResponse(response, "get detection changes");
        return parseJson(response.body());
    }

    /**
     * Poll until upload processing completes or times out.
     *
     * @param uploadId the upload ID
     * @param timeoutSeconds maximum time to wait
     * @param pollIntervalMs time between polls in milliseconds
     * @return the final processing status JSON (state will be COMPLETED or FAILED)
     * @throws HorreumClientException if processing fails, times out, or upload is not found
     */
    public JsonNode awaitProcessingComplete(long uploadId, long timeoutSeconds, long pollIntervalMs)
            throws HorreumClientException {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000);

        while (System.currentTimeMillis() < deadline) {
            JsonNode status = getProcessingStatus(uploadId);
            if (status == null) {
                throw new HorreumClientException("Upload " + uploadId + " not found", 404);
            }

            String state = status.get("state").asText();
            if ("COMPLETED".equals(state)) {
                return status;
            } else if ("FAILED".equals(state)) {
                String error = status.has("error") && !status.get("error").isNull()
                        ? status.get("error").asText() : "unknown error";
                throw new HorreumClientException(
                        "Upload " + uploadId + " processing failed: " + error, 500);
            }

            // Still PROCESSING — wait and retry
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HorreumClientException("Interrupted while waiting for processing", e);
            }
        }

        throw new HorreumClientException(
                "Timed out waiting for upload " + uploadId + " processing to complete after " + timeoutSeconds + "s",
                408);
    }

    private HttpResponse<String> doGet(String path) throws HorreumClientException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            addAuthHeader(builder);
            return newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new HorreumClientException("GET " + path + " failed: " + e.getMessage(), e);
        }
    }

    private HttpResponse<String> doPost(String path, String body) throws HorreumClientException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            addAuthHeader(builder);
            return newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new HorreumClientException("POST " + path + " failed: " + e.getMessage(), e);
        }
    }

    private void addAuthHeader(HttpRequest.Builder builder) {
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
    }

    private HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static void checkResponse(HttpResponse<String> response, String operation) throws HorreumClientException {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        throw new HorreumClientException(
                operation + " failed with status " + status + ": " + response.body(),
                status);
    }

    private static JsonNode parseJson(String json) throws HorreumClientException {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new HorreumClientException("Failed to parse JSON response: " + e.getMessage(), e);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Exception thrown when a Horreum REST call fails.
     */
    public static class HorreumClientException extends RuntimeException {
        private final int statusCode;

        public HorreumClientException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public HorreumClientException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = -1;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    /**
     * Exception thrown when changes are detected after upload and failOnChanges is enabled.
     * This is caught by the build step to mark the build as UNSTABLE.
     */
    public static class ChangesDetectedException extends RuntimeException {
        private final int changeCount;
        private final long uploadId;

        public ChangesDetectedException(long uploadId, int changeCount, String details) {
            super("Upload " + uploadId + ": " + changeCount + " change(s) detected. " + details);
            this.uploadId = uploadId;
            this.changeCount = changeCount;
        }

        public int getChangeCount() {
            return changeCount;
        }

        public long getUploadId() {
            return uploadId;
        }
    }
}
