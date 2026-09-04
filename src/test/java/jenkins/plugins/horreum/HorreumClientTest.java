package jenkins.plugins.horreum;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class HorreumClientTest {

    private WireMockServer wireMock;
    private HorreumClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
        client = new HorreumClient("http://localhost:" + wireMock.port(), "test-api-key");
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // --- Folder tests ---

    @Test
    void getFolder_exists() {
        stubFor(get(urlPathEqualTo("/api/folder/find"))
                .withQueryParam("name", equalTo("my-test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 1, \"name\": \"my-test\", \"groupId\": 10}")));

        JsonNode folder = client.getFolder("my-test");
        assertNotNull(folder);
        assertEquals(1, folder.get("id").asInt());
        assertEquals("my-test", folder.get("name").asText());

        verify(getRequestedFor(urlPathEqualTo("/api/folder/find"))
                .withQueryParam("name", equalTo("my-test"))
                .withHeader("Authorization", equalTo("Bearer test-api-key")));
    }

    @Test
    void getFolder_notFound_returns204() {
        stubFor(get(urlPathEqualTo("/api/folder/find"))
                .withQueryParam("name", equalTo("missing"))
                .willReturn(aResponse().withStatus(204)));

        JsonNode folder = client.getFolder("missing");
        assertNull(folder);
    }

    @Test
    void getFolder_notFound_returns404() {
        stubFor(get(urlPathEqualTo("/api/folder/find"))
                .withQueryParam("name", equalTo("missing"))
                .willReturn(aResponse().withStatus(404)));

        JsonNode folder = client.getFolder("missing");
        assertNull(folder);
    }

    @Test
    void createFolder_success() {
        stubFor(post(urlEqualTo("/api/folder"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 42, \"name\": \"new-test\", \"groupId\": 100}")));

        JsonNode folder = client.createFolder("new-test");
        assertEquals(42, folder.get("id").asInt());
        assertEquals("new-test", folder.get("name").asText());

        verify(postRequestedFor(urlEqualTo("/api/folder"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer test-api-key"))
                .withRequestBody(equalToJson("{\"name\": \"new-test\"}")));
    }

    @Test
    void createFolder_serverError() {
        stubFor(post(urlEqualTo("/api/folder"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        HorreumClient.HorreumClientException ex = assertThrows(
                HorreumClient.HorreumClientException.class,
                () -> client.createFolder("bad"));
        assertEquals(500, ex.getStatusCode());
    }

    @Test
    void getFolderId_exists() {
        stubFor(get(urlPathEqualTo("/api/folder/find"))
                .withQueryParam("name", equalTo("my-test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 7, \"name\": \"my-test\", \"groupId\": 10}")));

        assertEquals(7, client.getFolderId("my-test"));
    }

    @Test
    void getFolderId_notFound() {
        stubFor(get(urlPathEqualTo("/api/folder/find"))
                .withQueryParam("name", equalTo("missing"))
                .willReturn(aResponse().withStatus(204)));

        assertEquals(-1, client.getFolderId("missing"));
    }

    // --- Upload tests ---

    @Test
    void upload_byFolderId() {
        stubFor(post(urlEqualTo("/api/folder/1/upload"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("123")));

        long uploadId = client.upload(1, "{\"cpu\": 42.5}");
        assertEquals(123, uploadId);

        verify(postRequestedFor(urlEqualTo("/api/folder/1/upload"))
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withHeader("Authorization", equalTo("Bearer test-api-key"))
                .withRequestBody(containing("\"cpu\": 42.5")));
    }

    @Test
    void upload_multipart_containsRawField() {
        stubFor(post(urlEqualTo("/api/folder/1/upload"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("456")));

        long uploadId = client.upload(1, "{\"data\": true}");
        assertEquals(456, uploadId);

        verify(postRequestedFor(urlEqualTo("/api/folder/1/upload"))
                .withRequestBody(containing("Content-Disposition: form-data; name=\"raw\""))
                .withRequestBody(containing("{\"data\": true}")));
    }

    @Test
    void uploadToFolder_resolvesNameToId() {
        // Stub getFolder
        stubFor(get(urlPathEqualTo("/api/folder/find"))
                .withQueryParam("name", equalTo("my-test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 5, \"name\": \"my-test\", \"groupId\": 10}")));

        // Stub upload by ID
        stubFor(post(urlEqualTo("/api/folder/5/upload"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("789")));

        long uploadId = client.uploadToFolder("my-test", "{\"data\": 1}");
        assertEquals(789, uploadId);
    }

    @Test
    void uploadToFolder_folderNotFound() {
        stubFor(get(urlPathEqualTo("/api/folder/find"))
                .withQueryParam("name", equalTo("missing"))
                .willReturn(aResponse().withStatus(204)));

        HorreumClient.HorreumClientException ex = assertThrows(
                HorreumClient.HorreumClientException.class,
                () -> client.uploadToFolder("missing", "{}"));
        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void upload_unauthorized() {
        stubFor(post(urlPathEqualTo("/api/folder/1/upload"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("Unauthorized")));

        HorreumClient.HorreumClientException ex = assertThrows(
                HorreumClient.HorreumClientException.class,
                () -> client.upload(1, "{}"));
        assertEquals(401, ex.getStatusCode());
    }

    @Test
    void noAuth_whenApiKeyIsNull() {
        HorreumClient noAuthClient = new HorreumClient("http://localhost:" + wireMock.port(), null);

        stubFor(get(urlPathEqualTo("/api/folder/find"))
                .withQueryParam("name", equalTo("open"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 1, \"name\": \"open\", \"groupId\": 1}")));

        JsonNode folder = noAuthClient.getFolder("open");
        assertNotNull(folder);

        verify(getRequestedFor(urlPathEqualTo("/api/folder/find"))
                .withoutHeader("Authorization"));
    }

    // --- Processing status tests ---

    @Test
    void getProcessingStatus_completed() {
        stubFor(get(urlEqualTo("/api/processing/upload/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"nodeId\": 1, \"state\": \"COMPLETED\", \"error\": null}")));

        JsonNode status = client.getProcessingStatus(123);
        assertNotNull(status);
        assertEquals("COMPLETED", status.get("state").asText());
    }

    @Test
    void getProcessingStatus_running() {
        stubFor(get(urlEqualTo("/api/processing/upload/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"nodeId\": 1, \"state\": \"RUNNING\", \"error\": null}")));

        JsonNode status = client.getProcessingStatus(123);
        assertEquals("RUNNING", status.get("state").asText());
    }

    @Test
    void getProcessingStatus_failed() {
        stubFor(get(urlEqualTo("/api/processing/upload/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"nodeId\": 1, \"state\": \"FAILED\", \"error\": \"Node calculation error\"}")));

        JsonNode status = client.getProcessingStatus(123);
        assertEquals("FAILED", status.get("state").asText());
        assertEquals("Node calculation error", status.get("error").asText());
    }

    @Test
    void getProcessingStatus_notFound() {
        stubFor(get(urlEqualTo("/api/processing/upload/999"))
                .willReturn(aResponse().withStatus(404)));

        assertNull(client.getProcessingStatus(999));
    }

    // --- Detection changes tests ---

    @Test
    void getDetectionChanges_empty() {
        stubFor(get(urlEqualTo("/api/value/123/descendants?detection=true"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        JsonNode changes = client.getDetectionChanges(123);
        assertTrue(changes.isArray());
        assertEquals(0, changes.size());
    }

    @Test
    void getDetectionChanges_withResults() {
        String responseBody = """
                [
                  {
                    "id": 500,
                    "data": {"value": 95.5, "bound": 90.0, "direction": "UPPER"},
                    "node": {"id": 10, "name": "cpu-threshold", "type": "FIXED_THRESHOLD", "fqdn": "cpu-threshold"},
                    "folder": {"id": 1, "name": "perf-test"}
                  }
                ]
                """;
        stubFor(get(urlEqualTo("/api/value/123/descendants?detection=true"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        JsonNode changes = client.getDetectionChanges(123);
        assertEquals(1, changes.size());
        assertEquals("cpu-threshold", changes.get(0).get("node").get("name").asText());
        assertEquals("FIXED_THRESHOLD", changes.get(0).get("node").get("type").asText());
    }

    // --- awaitProcessingComplete tests ---

    @Test
    void awaitProcessingComplete_immediatelyCompleted() {
        stubFor(get(urlEqualTo("/api/processing/upload/100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"nodeId\": 1, \"state\": \"COMPLETED\", \"error\": null}")));

        JsonNode result = client.awaitProcessingComplete(100, 10, 100);
        assertEquals("COMPLETED", result.get("state").asText());
    }

    @Test
    void awaitProcessingComplete_runningThenCompleted() {
        stubFor(get(urlEqualTo("/api/processing/upload/100"))
                .inScenario("poll")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"nodeId\": 1, \"state\": \"RUNNING\", \"error\": null}"))
                .willSetStateTo("second-call"));

        stubFor(get(urlEqualTo("/api/processing/upload/100"))
                .inScenario("poll")
                .whenScenarioStateIs("second-call")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"nodeId\": 1, \"state\": \"COMPLETED\", \"error\": null}")));

        JsonNode result = client.awaitProcessingComplete(100, 10, 100);
        assertEquals("COMPLETED", result.get("state").asText());
        verify(2, getRequestedFor(urlEqualTo("/api/processing/upload/100")));
    }

    @Test
    void awaitProcessingComplete_failed() {
        stubFor(get(urlEqualTo("/api/processing/upload/100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"nodeId\": 1, \"state\": \"FAILED\", \"error\": \"out of memory\"}")));

        HorreumClient.HorreumClientException ex = assertThrows(
                HorreumClient.HorreumClientException.class,
                () -> client.awaitProcessingComplete(100, 10, 100));
        assertTrue(ex.getMessage().contains("out of memory"));
        assertEquals(500, ex.getStatusCode());
    }

    @Test
    void awaitProcessingComplete_timeout() {
        stubFor(get(urlEqualTo("/api/processing/upload/100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"nodeId\": 1, \"state\": \"RUNNING\", \"error\": null}")));

        HorreumClient.HorreumClientException ex = assertThrows(
                HorreumClient.HorreumClientException.class,
                () -> client.awaitProcessingComplete(100, 1, 200));
        assertTrue(ex.getMessage().contains("Timed out"));
        assertEquals(408, ex.getStatusCode());
    }
}
