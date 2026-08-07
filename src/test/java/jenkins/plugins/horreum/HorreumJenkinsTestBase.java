package jenkins.plugins.horreum;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.fasterxml.jackson.databind.ObjectMapper;

import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for Jenkins integration tests that run against a real h5m instance.
 *
 * <p>Provides:
 * <ul>
 *   <li>Testcontainers: PostgreSQL + h5m (shared across all tests in the class)</li>
 *   <li>JenkinsRule via JUnit 5 adapter (fresh Jenkins per test method)</li>
 *   <li>API key credentials registered in Jenkins</li>
 *   <li>HorreumGlobalConfig configured with h5m base URL</li>
 *   <li>HorreumClient for direct h5m API calls in test setup/verification</li>
 * </ul>
 */
@Testcontainers
@Tag("jenkins-e2e")
public abstract class HorreumJenkinsTestBase {

    static final String BOOTSTRAP_API_KEY = "H5M_JENKINS_INTEGRATION_TEST_KEY";
    static final String API_KEY_CREDENTIALS_ID = "horreum-api-key";
    static final ObjectMapper MAPPER = new ObjectMapper();

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

    @RegisterExtension
    JenkinsExtension j = new JenkinsExtension();

    /** Direct client for test setup and verification (bypasses Jenkins) */
    HorreumClient client;

    /** Base URL of the h5m instance */
    String h5mBaseUrl;

    @BeforeEach
    void configureJenkins() {
        h5mBaseUrl = "http://localhost:" + h5m.getMappedPort(8080);
        client = new HorreumClient(h5mBaseUrl, BOOTSTRAP_API_KEY);

        // Configure global Horreum settings
        HorreumGlobalConfig globalConfig = HorreumGlobalConfig.get();
        if (globalConfig != null) {
            globalConfig.setBaseUrl(h5mBaseUrl);
        }

        // Register API key credentials
        registerApiKeyCredential(API_KEY_CREDENTIALS_ID, BOOTSTRAP_API_KEY);
    }

    /**
     * Register a secret text credential (API key) in Jenkins.
     */
    void registerApiKeyCredential(String id, String apiKey) {
        Credentials cred = new StringCredentialsImpl(
                CredentialsScope.GLOBAL, id, "h5m API key for testing", Secret.fromString(apiKey));
        SystemCredentialsProvider provider = SystemCredentialsProvider.getInstance();
        provider.getDomainCredentialsMap()
                .computeIfAbsent(Domain.global(), d -> new java.util.ArrayList<>())
                .add(cred);
    }

    // --- h5m setup helpers (direct REST calls, not via Jenkins) ---

    /**
     * Create a folder and configure a FixedThreshold detection node on it.
     * Returns [folderId, groupId, ftNodeId].
     */
    long[] createFolderWithFixedThreshold(String folderName, double min, double max) throws Exception {
        com.fasterxml.jackson.databind.JsonNode folder = client.createFolder(folderName);
        long folderId = folder.get("id").asLong();
        long groupId = folder.get("groupId").asLong();

        // Get root node ID
        long rootNodeId = getRootNodeId(groupId);

        // Create JQ extractor nodes
        long rangeNodeId = createNode(groupId, "range", "JQ", ".value");
        long fpExtractorId = createNode(groupId, "fp-extractor", "JQ", ".env");

        // Create FixedThreshold: sources = [fingerprint, groupBy(root), range]
        String config = String.format(
                "{\"min\": %f, \"max\": %f, \"minInclusive\": true, \"maxInclusive\": true}", min, max);
        long ftNodeId = createConfiguredNode(groupId, "threshold", "FIXED_THRESHOLD",
                new long[]{fpExtractorId, rootNodeId, rangeNodeId}, config);

        return new long[]{folderId, groupId, ftNodeId};
    }

    long getRootNodeId(long groupId) throws Exception {
        HttpResponse<String> response = doGet("/api/group/" + groupId);
        return MAPPER.readTree(response.body()).get("root").get("id").asLong();
    }

    long createNode(long groupId, String name, String type, String operation) throws Exception {
        String url = h5mBaseUrl + "/api/node?name=" + encode(name)
                + "&groupId=" + groupId + "&type=" + type
                + "&operation=" + encode(operation);
        HttpResponse<String> response = doPost(url, "");
        if (response.statusCode() != 200) {
            throw new RuntimeException("Create node '" + name + "' failed: " + response.body());
        }
        return MAPPER.readTree(response.body()).get("id").asLong();
    }

    long createConfiguredNode(long groupId, String name, String type, long[] sourceIds, String configBody) throws Exception {
        StringBuilder url = new StringBuilder(h5mBaseUrl + "/api/node/configured?name=" + encode(name)
                + "&groupId=" + groupId + "&type=" + type);
        for (long id : sourceIds) {
            url.append("&sources=").append(id);
        }
        HttpResponse<String> response = doPost(url.toString(), configBody != null ? configBody : "");
        if (response.statusCode() != 200) {
            throw new RuntimeException("Create configured node '" + name + "' failed: " + response.body());
        }
        return MAPPER.readTree(response.body()).get("id").asLong();
    }

    private HttpResponse<String> doGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(h5mBaseUrl + path))
                .header("Authorization", "Bearer " + BOOTSTRAP_API_KEY)
                .GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> doPost(String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + BOOTSTRAP_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
