package jenkins.plugins.horreum.upload;

import java.io.IOException;
import java.io.PrintStream;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.plugins.horreum.BaseExecutionContext;
import jenkins.plugins.horreum.HorreumClient;
import jenkins.plugins.horreum.HorreumGlobalConfig;

public class HorreumUploadExecutionContext extends BaseExecutionContext<String> {

    private static final long serialVersionUID = 2L;
    private static final String HORREUM_JENKINS_SCHEMA = "urn:horreum:jenkins-plugin:0.1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long POLL_INTERVAL_MS = 2000; // 2 seconds between polls

    private final String folder;
    private final String path;
    private final String workspacePath;
    private final FilePath[] uploadFiles;
    private final String buildInfoJson; // serialized JSON string (ObjectNode is NOT Serializable)
    private final boolean awaitProcessing;
    private final long processingTimeout;
    private final boolean failOnChanges;

    static HorreumUploadExecutionContext from(HorreumUploadConfig config,
                                              EnvVars envVars,
                                              Run<?, ?> run,
                                              TaskListener listener,
                                              Supplier<String> workspacePathSupplier,
                                              Supplier<FilePath[]> uploadFilesSupplier) {
        HorreumGlobalConfig globalConfig = HorreumGlobalConfig.get();
        String baseUrl = globalConfig != null ? globalConfig.getBaseUrl() : null;
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Horreum base URL is not configured. Please set it in Jenkins global configuration (Manage Jenkins > Horreum Configuration).");
        }
        String url = envVars != null ? envVars.expand(baseUrl) : baseUrl;
        FilePath[] uploadFiles = uploadFilesSupplier.get();
        TaskListener taskListener = config.getQuiet() ? TaskListener.NULL : listener;

        String buildInfoJson = null;
        if (config.getAddBuildInfo()) {
            ObjectNode buildInfo = JsonNodeFactory.instance.objectNode();
            buildInfo.put("$schema", HORREUM_JENKINS_SCHEMA);
            String rootUrl = Jenkins.get().getRootUrl();
            buildInfo.put("buildUrl", (rootUrl != null ? rootUrl : "") + run.getUrl());
            buildInfo.put("buildNumber", run.getNumber());
            buildInfo.put("buildDisplayName", run.getDisplayName());
            buildInfo.put("jobName", run.getParent().getName());
            buildInfo.put("jobDisplayName", run.getParent().getDisplayName());
            buildInfo.put("jobFullName", run.getParent().getFullName());
            buildInfo.put("scheduleTime", run.getTimeInMillis());
            buildInfo.put("startTime", run.getStartTimeInMillis());
            buildInfo.put("uploadTime", System.currentTimeMillis());
            buildInfoJson = buildInfo.toString(); // serialize to String for remoting
        }

        String folder = envVars != null ? envVars.expand(config.getFolder()) : config.getFolder();
        String path = config.getPath();
        if (path != null && !path.isEmpty() && envVars != null) {
            path = envVars.expand(path);
        }

        return new HorreumUploadExecutionContext(
                url,
                config.getAuthenticationType(),
                config.getCredentials(),
                folder,
                path,
                workspacePathSupplier.get(),
                uploadFiles,
                buildInfoJson,
                config.getAwaitProcessing(),
                config.getProcessingTimeout(),
                config.getFailOnChanges(),
                taskListener.getLogger());
    }

    private HorreumUploadExecutionContext(
            String url,
            String authenticationType,
            String credentials,
            String folder,
            String path,
            String workspacePath,
            FilePath[] uploadFiles,
            String buildInfoJson,
            boolean awaitProcessing,
            long processingTimeout,
            boolean failOnChanges,
            PrintStream logger
    ) {
        super(url, authenticationType, credentials, logger);
        this.folder = folder;
        this.path = path;
        this.workspacePath = workspacePath;
        this.uploadFiles = uploadFiles;
        this.buildInfoJson = buildInfoJson;
        this.awaitProcessing = awaitProcessing;
        this.processingTimeout = processingTimeout;
        this.failOnChanges = failOnChanges;
    }

    @Override
    protected String invoke(HorreumClient client) {
        String jsonData = loadUploadData();

        // Wrap with build info if enabled
        if (buildInfoJson != null) {
            try {
                JsonNode data = MAPPER.readTree(jsonData);
                JsonNode buildInfo = MAPPER.readTree(buildInfoJson);
                ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
                wrapper.set("data", data);
                wrapper.set("buildInfo", buildInfo);
                jsonData = wrapper.toString();
            } catch (IOException e) {
                throw new RuntimeException("Failed to wrap data with build info", e);
            }
        }

        // Upload (resolve folder name to ID)
        long uploadId = client.uploadToFolder(folder, path, jsonData);
        logger().printf("Uploaded to folder '%s', upload ID: %d%n", folder, uploadId);

        // Await processing completion
        if (awaitProcessing) {
            logger().printf("Waiting for processing to complete (timeout: %ds)...%n", processingTimeout);
            client.awaitProcessingComplete(uploadId, processingTimeout, POLL_INTERVAL_MS);
            logger().printf("Processing completed for upload %d%n", uploadId);

            // Check for detection changes
            checkForChanges(client, uploadId);
        }

        return String.valueOf(uploadId);
    }

    private void checkForChanges(HorreumClient client, long uploadId) {
        JsonNode changes = client.getDetectionChanges(uploadId);
        int changeCount = changes.isArray() ? changes.size() : 0;

        if (changeCount == 0) {
            logger().printf("Upload %d: no changes detected%n", uploadId);
            return;
        }

        // Log each change
        StringBuilder details = new StringBuilder();
        for (int i = 0; i < changeCount; i++) {
            JsonNode change = changes.get(i);
            JsonNode node = change.get("node");
            String nodeName = node != null && node.has("name") ? node.get("name").asText() : "unknown";
            String nodeType = node != null && node.has("type") ? node.get("type").asText() : "unknown";
            logger().printf("  Change detected by node '%s' (type: %s)%n", nodeName, nodeType);
            if (details.length() > 0) details.append(", ");
            details.append(nodeName).append(" (").append(nodeType).append(")");
        }

        logger().printf("Upload %d: %d change(s) detected%n", uploadId, changeCount);

        if (failOnChanges) {
            throw new HorreumClient.ChangesDetectedException(uploadId, changeCount, details.toString());
        }
    }

    private String loadUploadData() {
        if (uploadFiles == null || uploadFiles.length == 0) {
            throw new IllegalStateException("There are no files to upload!");
        } else if (uploadFiles.length == 1) {
            return readFileContent(uploadFiles[0]);
        } else {
            // Multiple files: merge into a JSON object keyed by relative path
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            for (FilePath uploadFile : uploadFiles) {
                String filePath = uploadFile.getRemote();
                if (workspacePath != null && filePath.startsWith(workspacePath)) {
                    filePath = filePath.substring(workspacePath.length());
                    if (filePath.startsWith("/")) {
                        filePath = filePath.substring(1);
                    }
                }
                try {
                    root.set(filePath, MAPPER.readTree(readFileContent(uploadFile)));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to parse JSON from file: " + uploadFile.getRemote(), e);
                }
            }
            return root.toString();
        }
    }

    /**
     * Read file content using the FilePath API, which correctly handles
     * remoting (files on remote agents) via the Jenkins channel.
     */
    private String readFileContent(FilePath file) {
        try {
            return file.readToString();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("File for upload cannot be read: " + file.getRemote(), e);
        }
    }
}
