package jenkins.plugins.horreum;

import com.fasterxml.jackson.databind.JsonNode;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import jenkins.plugins.horreum.upload.HorreumUpload;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CreateFileBuilder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jenkins integration tests for the Horreum upload step.
 * Tests both freestyle and pipeline projects against a real h5m instance.
 */
class HorreumUploadJenkinsTest extends HorreumJenkinsTestBase {

    private static final String TEST_JSON = "{\"value\": 42, \"env\": {\"type\": \"ci-test\"}}";

    // --- Freestyle tests ---

    @Test
    void freestyleUpload_apiKey() throws Exception {
        // Setup: create folder in h5m
        client.createFolder("freestyle-upload");

        // Create freestyle project with a file and upload step
        FreeStyleProject project = j.createFreeStyleProject("freestyle-upload-test");
        project.getBuildersList().add(new CreateFileBuilder("data.json", TEST_JSON));
        project.getBuildersList().add(new HorreumUpload(
                "API_KEY",
                API_KEY_CREDENTIALS_ID,
                "freestyle-upload",
                "data.json",  // jsonFile
                null,         // files (glob)
                null,         // path
                false         // addBuildInfo
        ));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(build);
    }

    @Test
    void freestyleUpload_noAuth() throws Exception {
        // h5m has security enabled, so NONE auth should fail
        client.createFolder("freestyle-noauth");

        FreeStyleProject project = j.createFreeStyleProject("freestyle-noauth-test");
        project.getBuildersList().add(new CreateFileBuilder("data.json", TEST_JSON));
        project.getBuildersList().add(new HorreumUpload(
                "NONE",
                API_KEY_CREDENTIALS_ID, // credentials ignored when NONE
                "freestyle-noauth",
                "data.json",
                null, null, false
        ));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        // Should fail because h5m has security enabled and NONE sends no auth header
        j.assertBuildStatus(Result.FAILURE, build);
    }

    // --- Pipeline tests ---

    @Test
    void pipelineUpload_apiKey() throws Exception {
        client.createFolder("pipeline-upload");

        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "pipeline-upload-test");
        proj.setDefinition(new CpsFlowDefinition("""
                node {
                    writeFile file: 'result.json', text: '%s'
                    def uploadId = horreumUpload(
                        authenticationType: 'API_KEY',
                        credentials: '%s',
                        folder: 'pipeline-upload',
                        jsonFile: 'result.json',
                        awaitProcessing: true,
                        processingTimeout: 30
                    )
                    echo "Upload ID: ${uploadId}"
                }
                """.formatted(TEST_JSON, API_KEY_CREDENTIALS_ID), true));

        WorkflowRun run = proj.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Upload ID:", run);
    }

    @Test
    void pipelineUpload_multipleFiles() throws Exception {
        client.createFolder("pipeline-multi");

        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "pipeline-multi-test");
        proj.setDefinition(new CpsFlowDefinition("""
                node {
                    writeFile file: 'run/file1.json', text: '{"cpu": 42}'
                    writeFile file: 'run/file2.json', text: '{"memory": 1024}'
                    horreumUpload(
                        authenticationType: 'API_KEY',
                        credentials: '%s',
                        folder: 'pipeline-multi',
                        files: 'run/**/*.json',
                        awaitProcessing: true,
                        processingTimeout: 30
                    )
                }
                """.formatted(API_KEY_CREDENTIALS_ID), true));

        WorkflowRun run = proj.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Uploaded to folder", run);
    }

    @Test
    void pipelineUpload_awaitProcessing() throws Exception {
        client.createFolder("pipeline-await");

        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "pipeline-await-test");
        proj.setDefinition(new CpsFlowDefinition("""
                node {
                    writeFile file: 'data.json', text: '{"value": 50}'
                    horreumUpload(
                        authenticationType: 'API_KEY',
                        credentials: '%s',
                        folder: 'pipeline-await',
                        jsonFile: 'data.json',
                        awaitProcessing: true,
                        processingTimeout: 30
                    )
                }
                """.formatted(API_KEY_CREDENTIALS_ID), true));

        WorkflowRun run = proj.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Processing completed", run);
        j.assertLogContains("no changes detected", run);
    }

    @Test
    void pipelineUpload_failOnChanges_marksUnstable() throws Exception {
        // Create folder with FixedThreshold: min=10, max=100
        createFolderWithFixedThreshold("pipeline-unstable", 10.0, 100.0);

        // Upload value=5 (below min=10) with failOnChanges=true
        String violatingJson = "{\"value\": 5, \"env\": {\"type\": \"ci-test\"}}";

        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "pipeline-unstable-test");
        proj.setDefinition(new CpsFlowDefinition("""
                node {
                    writeFile file: 'data.json', text: '%s'
                    horreumUpload(
                        authenticationType: 'API_KEY',
                        credentials: '%s',
                        folder: 'pipeline-unstable',
                        jsonFile: 'data.json',
                        awaitProcessing: true,
                        processingTimeout: 60,
                        failOnChanges: true
                    )
                }
                """.formatted(violatingJson, API_KEY_CREDENTIALS_ID), true));

        WorkflowRun run = proj.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.UNSTABLE, run);
        j.assertLogContains("change(s) detected", run);
        j.assertLogContains("threshold", run);
    }

    @Test
    void pipelineUpload_withinThreshold_success() throws Exception {
        // Same threshold but value within range — should be SUCCESS
        createFolderWithFixedThreshold("pipeline-within", 10.0, 100.0);

        String withinJson = "{\"value\": 50, \"env\": {\"type\": \"ci-test\"}}";

        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "pipeline-within-test");
        proj.setDefinition(new CpsFlowDefinition("""
                node {
                    writeFile file: 'data.json', text: '%s'
                    horreumUpload(
                        authenticationType: 'API_KEY',
                        credentials: '%s',
                        folder: 'pipeline-within',
                        jsonFile: 'data.json',
                        awaitProcessing: true,
                        processingTimeout: 60,
                        failOnChanges: true
                    )
                }
                """.formatted(withinJson, API_KEY_CREDENTIALS_ID), true));

        WorkflowRun run = proj.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("no changes detected", run);
    }

    @Test
    void pipelineUpload_wrongCredentials_fails() throws Exception {
        client.createFolder("pipeline-badcreds");

        // Register a wrong API key
        registerApiKeyCredential("wrong-key", "H5M_DEFINITELY_WRONG_KEY");

        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "pipeline-badcreds-test");
        proj.setDefinition(new CpsFlowDefinition("""
                node {
                    writeFile file: 'data.json', text: '{"value": 1}'
                    horreumUpload(
                        authenticationType: 'API_KEY',
                        credentials: 'wrong-key',
                        folder: 'pipeline-badcreds',
                        jsonFile: 'data.json'
                    )
                }
                """, true));

        WorkflowRun run = proj.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, run);
    }
}
