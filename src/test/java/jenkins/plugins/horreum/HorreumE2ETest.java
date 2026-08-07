package jenkins.plugins.horreum;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests against a real h5m instance running in Docker.
 * Uses Testcontainers to spin up PostgreSQL + h5m.
 *
 * <p>These tests require Docker to be available. They are tagged with "e2e"
 * so they can be included/excluded from the test suite.</p>
 */
@Testcontainers
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HorreumE2ETest {

    private static final String BOOTSTRAP_API_KEY = "H5M_TEST_KEY_FOR_JENKINS_E2E";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withNetwork(network)
            .withNetworkAliases("db")
            .withDatabaseName("h5m")
            .withUsername("h5m")
            .withPassword("h5m");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> h5m = new GenericContainer<>("ghcr.io/hyperfoil/h5m:latest")
            .withNetwork(network)
            .withExposedPorts(8080)
            .withEnv("QUARKUS_DATASOURCE_DB_KIND", "postgresql")
            .withEnv("QUARKUS_DATASOURCE_JDBC_URL", "jdbc:postgresql://db:5432/h5m")
            .withEnv("QUARKUS_DATASOURCE_USERNAME", "h5m")
            .withEnv("QUARKUS_DATASOURCE_PASSWORD", "h5m")
            .withEnv("H5M_SECURITY_ENABLED", "true")
            .withEnv("H5M_BOOTSTRAP_API_KEY", BOOTSTRAP_API_KEY)
            .dependsOn(postgres)
            .waitingFor(Wait.forHttp("/q/health/ready").forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(2));

    private HorreumClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + h5m.getMappedPort(8080);
        client = new HorreumClient(baseUrl, BOOTSTRAP_API_KEY);
    }

    // --- Basic folder operations ---

    @Test
    @Order(1)
    void createFolder_andVerify() {
        JsonNode folder = client.createFolder("e2e-test");
        assertTrue(folder.get("id").asLong() > 0, "Folder ID should be positive");
        assertEquals("e2e-test", folder.get("name").asText());

        JsonNode found = client.getFolder("e2e-test");
        assertNotNull(found);
        assertEquals("e2e-test", found.get("name").asText());
    }

    @Test
    @Order(2)
    void folderEnsure_idempotent() {
        client.createFolder("ensure-test");

        // Getting it should work
        JsonNode folder = client.getFolder("ensure-test");
        assertNotNull(folder);

        // The folder ensure step checks existence first, so this is valid
        JsonNode existing = client.getFolder("ensure-test");
        assertNotNull(existing, "Folder should still exist");
    }

    // --- Upload and processing ---

    @Test
    @Order(3)
    void upload_andAwaitCompletion() {
        JsonNode folder = client.createFolder("upload-test");
        long folderId = folder.get("id").asLong();

        long uploadId = client.upload(folderId, null, "{\"cpu\": 42.5, \"memory\": 1024}");
        assertTrue(uploadId > 0, "Upload ID should be positive");

        JsonNode status = client.awaitProcessingComplete(uploadId, 30, 500);
        assertEquals("COMPLETED", status.get("state").asText());
    }

    @Test
    @Order(4)
    void upload_noDetectionNodes_noChanges() {
        JsonNode folder = client.createFolder("no-detection-test");
        long folderId = folder.get("id").asLong();

        long uploadId = client.upload(folderId, null, "{\"value\": 50}");
        client.awaitProcessingComplete(uploadId, 30, 500);

        JsonNode changes = client.getDetectionChanges(uploadId);
        assertNotNull(changes);
        assertTrue(changes.isArray());
        assertEquals(0, changes.size(), "Should have no detection changes without detection nodes");
    }

    // --- Authentication ---

    @Test
    @Order(5)
    void authenticatedUpload_withBootstrapKey() {
        JsonNode folder = client.createFolder("auth-test");
        long folderId = folder.get("id").asLong();

        long uploadId = client.upload(folderId, null, "{\"data\": true}");
        assertTrue(uploadId > 0, "Authenticated upload should succeed");
    }

    @Test
    @Order(6)
    void upload_withBadKey_fails() {
        HorreumClient badClient = new HorreumClient(baseUrl, "H5M_WRONG_KEY_12345");

        HorreumClient.HorreumClientException ex = assertThrows(
                HorreumClient.HorreumClientException.class,
                () -> badClient.createFolder("should-fail"));
        assertEquals(401, ex.getStatusCode(), "Should get 401 Unauthorized with wrong API key");
    }

    // --- Change detection end-to-end ---

    @Test
    @Order(10)
    void upload_withFixedThreshold_detectsViolation() throws Exception {
        // 1. Create folder
        JsonNode folder = client.createFolder("ft-e2e-test");
        long folderId = folder.get("id").asLong();
        long groupId = folder.get("groupId").asLong();

        // 2. Create JQ extractor nodes
        long rangeNodeId = createNode(groupId, "range", "JQ", ".value");
        long fpExtractorId = createNode(groupId, "fp-extractor", "JQ", ".env");

        // 3. Get root node ID from folder's group
        long rootNodeId = getRootNodeId(groupId);

        // 4. Create FixedThreshold detection node
        //    Sources order: [fingerprint(0), groupBy(1), range(2)]
        //    Config: min=10, max=100
        long ftNodeId = createConfiguredNode(groupId, "cpu-threshold", "FIXED_THRESHOLD",
                new long[]{fpExtractorId, rootNodeId, rangeNodeId},
                "{\"min\": 10.0, \"max\": 100.0, \"minInclusive\": true, \"maxInclusive\": true}");
        assertTrue(ftNodeId > 0, "FixedThreshold node should be created");

        // 5. Upload data with value=5 (below min=10, should trigger violation)
        long uploadId = client.upload(folderId, null,
                "{\"value\": 5, \"env\": {\"type\": \"perf-test\"}}");

        // 6. Wait for processing
        client.awaitProcessingComplete(uploadId, 60, 1000);

        // 7. Check detection changes
        JsonNode changes = client.getDetectionChanges(uploadId);
        assertNotNull(changes);
        assertTrue(changes.isArray());
        assertTrue(changes.size() > 0, "Should detect threshold violation (value=5 < min=10)");

        // Verify the detection result structure
        JsonNode change = changes.get(0);
        assertEquals("cpu-threshold", change.get("node").get("name").asText());
        assertEquals("FIXED_THRESHOLD", change.get("node").get("type").asText());
    }

    @Test
    @Order(11)
    void upload_withinThreshold_noChanges() throws Exception {
        // Same setup but with a value within the threshold range
        JsonNode folder = client.createFolder("ft-nochange-test");
        long folderId = folder.get("id").asLong();
        long groupId = folder.get("groupId").asLong();

        long rangeNodeId = createNode(groupId, "range", "JQ", ".value");
        long fpExtractorId = createNode(groupId, "fp-extractor", "JQ", ".env");
        long rootNodeId = getRootNodeId(groupId);

        createConfiguredNode(groupId, "cpu-threshold", "FIXED_THRESHOLD",
                new long[]{fpExtractorId, rootNodeId, rangeNodeId},
                "{\"min\": 10.0, \"max\": 100.0, \"minInclusive\": true, \"maxInclusive\": true}");

        // Upload data with value=50 (within [10, 100])
        long uploadId = client.upload(folderId, null,
                "{\"value\": 50, \"env\": {\"type\": \"perf-test\"}}");

        client.awaitProcessingComplete(uploadId, 60, 1000);

        JsonNode changes = client.getDetectionChanges(uploadId);
        assertEquals(0, changes.size(), "Value=50 is within [10,100] — no changes expected");
    }

    // --- Helper methods for h5m node setup (direct HTTP, not part of plugin API) ---

    private long getRootNodeId(long groupId) throws Exception {
        HttpResponse<String> response = doGet("/api/group/" + groupId);
        assertEquals(200, response.statusCode(), "Group lookup should succeed");
        JsonNode group = MAPPER.readTree(response.body());
        return group.get("root").get("id").asLong();
    }

    private long createNode(long groupId, String name, String type, String operation) throws Exception {
        String url = baseUrl + "/api/node?name=" + encode(name) + "&groupId=" + groupId + "&type=" + type;
        if (operation != null) {
            url += "&operation=" + encode(operation);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + BOOTSTRAP_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(),
                "Create node '" + name + "' failed: " + response.body());
        return MAPPER.readTree(response.body()).get("id").asLong();
    }

    private long createConfiguredNode(long groupId, String name, String type, long[] sourceIds, String configBody) throws Exception {
        StringBuilder url = new StringBuilder(baseUrl + "/api/node/configured?name=" + encode(name)
                + "&groupId=" + groupId + "&type=" + type);
        for (long sourceId : sourceIds) {
            url.append("&sources=").append(sourceId);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + BOOTSTRAP_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(configBody != null ? configBody : ""))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(),
                "Create configured node '" + name + "' failed: " + response.body());
        return MAPPER.readTree(response.body()).get("id").asLong();
    }

    private HttpResponse<String> doGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + BOOTSTRAP_API_KEY)
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
