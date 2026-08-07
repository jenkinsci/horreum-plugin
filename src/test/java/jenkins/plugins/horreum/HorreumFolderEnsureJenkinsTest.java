package jenkins.plugins.horreum;

import com.fasterxml.jackson.databind.JsonNode;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import jenkins.plugins.horreum.folder.HorreumFolderEnsure;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jenkins integration tests for the Horreum folder ensure step.
 * Tests both freestyle and pipeline projects against a real h5m instance.
 */
class HorreumFolderEnsureJenkinsTest extends HorreumJenkinsTestBase {

    // --- Freestyle tests ---

    @Test
    void freestyleFolderEnsure_apiKey_createsFolder() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("freestyle-ensure-test");
        project.getBuildersList().add(new HorreumFolderEnsure(
                "API_KEY",
                API_KEY_CREDENTIALS_ID,
                "freestyle-ensure"
        ));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(build);

        // Verify the folder was created in h5m
        JsonNode folder = client.getFolder("freestyle-ensure");
        assertNotNull(folder, "Folder should exist after ensure step");
        assertEquals("freestyle-ensure", folder.get("name").asText());
    }

    @Test
    void freestyleFolderEnsure_noAuth_fails() throws Exception {
        // NONE auth should fail when h5m security is enabled
        FreeStyleProject project = j.createFreeStyleProject("freestyle-ensure-noauth-test");
        project.getBuildersList().add(new HorreumFolderEnsure(
                "NONE",
                API_KEY_CREDENTIALS_ID,
                "freestyle-ensure-noauth"
        ));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        // Folder creation requires authentication — this should fail
        j.assertBuildStatus(Result.FAILURE, build);
    }

    // --- Pipeline tests ---

    @Test
    void pipelineFolderEnsure_createsNewFolder() throws Exception {
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "pipeline-ensure-test");
        proj.setDefinition(new CpsFlowDefinition("""
                node {
                    horreumFolderEnsure(
                        authenticationType: 'API_KEY',
                        credentials: '%s',
                        folder: 'pipeline-ensure'
                    )
                }
                """.formatted(API_KEY_CREDENTIALS_ID), true));

        WorkflowRun run = proj.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Created folder", run);

        // Verify in h5m
        JsonNode folder = client.getFolder("pipeline-ensure");
        assertNotNull(folder, "Folder should exist after pipeline ensure step");
    }

    @Test
    void pipelineFolderEnsure_existingFolder_succeeds() throws Exception {
        // Pre-create the folder
        client.createFolder("pipeline-ensure-existing");

        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "pipeline-ensure-existing-test");
        proj.setDefinition(new CpsFlowDefinition("""
                node {
                    horreumFolderEnsure(
                        authenticationType: 'API_KEY',
                        credentials: '%s',
                        folder: 'pipeline-ensure-existing'
                    )
                }
                """.formatted(API_KEY_CREDENTIALS_ID), true));

        WorkflowRun run = proj.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("already exists", run);
    }

    @Test
    void pipelineFolderEnsure_thenUpload() throws Exception {
        // Test the typical workflow: ensure folder exists, then upload
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "pipeline-ensure-upload-test");
        proj.setDefinition(new CpsFlowDefinition("""
                node {
                    horreumFolderEnsure(
                        authenticationType: 'API_KEY',
                        credentials: '%s',
                        folder: 'ensure-then-upload'
                    )
                    writeFile file: 'data.json', text: '{"value": 99}'
                    horreumUpload(
                        authenticationType: 'API_KEY',
                        credentials: '%s',
                        folder: 'ensure-then-upload',
                        jsonFile: 'data.json',
                        awaitProcessing: true,
                        processingTimeout: 30
                    )
                }
                """.formatted(API_KEY_CREDENTIALS_ID, API_KEY_CREDENTIALS_ID), true));

        WorkflowRun run = proj.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Created folder", run);
        j.assertLogContains("Uploaded to folder", run);
        j.assertLogContains("Processing completed", run);
    }
}
