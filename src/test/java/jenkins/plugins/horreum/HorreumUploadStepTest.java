package jenkins.plugins.horreum;

import static jenkins.plugins.horreum.junit.HorreumTestExtension.*;
import static org.junit.jupiter.api.Assertions.*;
import static jenkins.plugins.horreum.junit.HorreumTestClientExtension.getApiKeyClient;
import static jenkins.plugins.horreum.junit.HorreumTestClientExtension.getHorreumClient;

import java.net.URL;

import com.fasterxml.jackson.databind.node.ObjectNode;
import hudson.FilePath;
import io.hyperfoil.tools.horreum.api.services.RunService;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class HorreumUploadStepTest extends HorreumPluginTestBase {
   @Test
   public void testUpload() throws Exception {
      URL jsonResource = Thread.currentThread().getContextClassLoader().getResource("data/config-quickstart.jvm.json");
      WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "Horreum-Upload-Pipeline-testUpload");
      io.hyperfoil.tools.horreum.api.data.Test dummyTest = createTest(getHorreumClient(), "upload-single", "dev-team");
      proj.setDefinition(new CpsFlowDefinition(
          "node {\n" +
              "def id = horreumUpload(\n" +
              "authenticationType: '" + AuthenticationType.OIDC.name() + "',\n" +
              "credentials: '" + HORREUM_UPLOAD_CREDENTIALS + "',\n" +
              "test: '" + dummyTest.name + "',\n" +
              "owner: '" + dummyTest.owner + "',\n" +
              "access: 'PUBLIC',\n" +
              "start: '$.build-timestamp',\n" +
              "stop: '$.build-timestamp',\n" +
              "jsonFile: '" + jsonResource.getPath() + "',\n" +
              "addBuildInfo: true\n" +
              ")\n" +
              "println(id)\n" +
              "}\n",
          true));

      WorkflowRun run = proj.scheduleBuild2(0).get();

      j.assertBuildStatusSuccess(run);

      RunService.RunsSummary summary = getHorreumClient().runService.listTestRuns(dummyTest.id, false, null, null, "", null);
      assertEquals(1, summary.total);
      assertEquals(1, summary.runs.size());
   }

   @Test
   public void testUploadApiKey() throws Exception {
      URL jsonResource = Thread.currentThread().getContextClassLoader().getResource("data/config-quickstart.jvm.json");
      WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "Horreum-Upload-ApiKey-Pipeline-testUpload");
      io.hyperfoil.tools.horreum.api.data.Test dummyTest = createTest(getHorreumClient(), "upload-apikey-single", "dev-team");
      proj.setDefinition(new CpsFlowDefinition(
              "node {\n" +
              "def id = horreumUpload(\n" +
              "authenticationType: '" + AuthenticationType.API_KEY.name() + "',\n" +
              "credentials: '" + HORREUM_API_KEY_CREDENTIALS + "',\n" +
              "test: '" + dummyTest.name + "',\n" +
              "owner: '" + dummyTest.owner + "',\n" +
              "access: 'PUBLIC',\n" +
              "start: '$.build-timestamp',\n" +
              "stop: '$.build-timestamp',\n" +
              "jsonFile: '" + jsonResource.getPath() + "',\n" +
              "addBuildInfo: true\n" +
              ")\n" +
              "println(id)\n" +
              "}\n",
              true));

      WorkflowRun run = proj.scheduleBuild2(0).get();

      j.assertBuildStatusSuccess(run);

      RunService.RunsSummary summary = getApiKeyClient().runService.listTestRuns(dummyTest.id, false, null, null, "", null);
      assertEquals(1, summary.total);
      assertEquals(1, summary.runs.size());
   }

   @Test
   public void testUploadMultiple(TestInfo info) throws Exception {
      URL jsonResource1 = Thread.currentThread().getContextClassLoader().getResource("data/config-quickstart.jvm.json");
      URL jsonResource2 = Thread.currentThread().getContextClassLoader().getResource("data/another-file.json");
      assertNotNull(j);
      assertNotNull(j.jenkins);
      WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "Horreum-Upload-Pipeline-testUploadMultiple");
      FilePath folder = j.jenkins.getWorkspaceFor(proj).child("run");
      folder.child("config-quickstart.jvm.json").copyFrom(jsonResource1);
      folder.child("another-file.json").copyFrom(jsonResource2);
      io.hyperfoil.tools.horreum.api.data.Test dummyTest = createTest(getHorreumClient(), info.getTestClass() + "-upload-multiple", "dev-team");
      proj.setDefinition(new CpsFlowDefinition(
          "node {\n" +
              "def id = horreumUpload(\n" +
              "authenticationType: '" + AuthenticationType.OIDC.name() + "',\n" +
              "credentials: '" + HORREUM_UPLOAD_CREDENTIALS + "',\n" +
              "test: '" + dummyTest.name + "',\n" +
              "owner: '" + dummyTest.owner + "',\n" +
              "access: 'PUBLIC',\n" +
              "start: '1970-01-01T00:00:00.00Z',\n" +
              "stop: '1970-01-01T00:00:01.00Z',\n" +
              "files: '**/*.json',\n"+
              "addBuildInfo: true\n" +
              ")\n" +
              "println(id)\n" +
              "}\n",
          true));
      WorkflowRun run = proj.scheduleBuild2(0).get();
      j.assertBuildStatusSuccess(run);
      RunService.RunsSummary summary = getHorreumClient().runService.listTestRuns(dummyTest.id, false, null, null, "", null);
      assertEquals(1, summary.total);
      assertEquals(1, summary.runs.size());
      RunService.RunExtended runObject = getHorreumClient().runService.getRun(summary.runs.get(0).id);
      assertNotNull(runObject);
      assertInstanceOf(RunService.RunExtended.class, runObject,"run should return a RunService.RunExtended");
      Object data = runObject.data;
      assertNotNull(data);
      assertInstanceOf(ObjectNode.class, data,"data should be a ObjectNode");
      assertEquals(2,((ObjectNode) data).size(),"data should have an entry for each file");
   }
}
