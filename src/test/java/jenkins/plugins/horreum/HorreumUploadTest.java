package jenkins.plugins.horreum;

import static jenkins.plugins.horreum.junit.HorreumTestExtension.*;
import static jenkins.plugins.horreum.junit.HorreumTestClientExtension.getApiKeyClient;
import static jenkins.plugins.horreum.junit.HorreumTestClientExtension.getHorreumClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import io.hyperfoil.tools.HorreumClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.jvnet.hudson.test.CreateFileBuilder;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import io.hyperfoil.tools.horreum.api.services.RunService;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Schema;
import jenkins.plugins.horreum.upload.HorreumUpload;

public class HorreumUploadTest extends HorreumPluginTestBase {
	@Test
	public void testUpload(TestInfo info) throws Exception {
		URL jsonResource = Thread.currentThread().getContextClassLoader().getResource("data/config-quickstart.jvm.json");
		io.hyperfoil.tools.horreum.api.data.Test dummyTest = createTest(getHorreumClient(), info.getTestClass() + "-upload-single", "dev-team");

		// Prepare HttpRequest#
		HorreumUpload horreumUpload = new HorreumUpload(
			AuthenticationType.OIDC.name(),
			HORREUM_UPLOAD_CREDENTIALS,
			dummyTest.name,
			dummyTest.owner,
			"PUBLIC",
			"$.build-timestamp",
			"$.build-timestamp",
			"",
			jsonResource.getPath(),
			null,
			true
		);

		runInFreeStyleProject(getHorreumClient(), "Horreum-Upload-Freestyle", dummyTest, horreumUpload);
	}

	@Test
	public void testApiKeyUpload(TestInfo info) throws Exception {
		URL jsonResource = Thread.currentThread().getContextClassLoader().getResource("data/config-quickstart.jvm.json");
		io.hyperfoil.tools.horreum.api.data.Test dummyTest = createTest(getApiKeyClient(), info.getTestClass() + "-upload-apikey-single", "dev-team");

		// Prepare HttpRequest#
		HorreumUpload horreumUpload = new HorreumUpload(
				AuthenticationType.API_KEY.name(),
				HORREUM_API_KEY_CREDENTIALS,
				dummyTest.name,
				dummyTest.owner,
				"PUBLIC",
				"$.build-timestamp",
				"$.build-timestamp",
				"",
				jsonResource.getPath(),
				null,
				true
		);

		runInFreeStyleProject(getApiKeyClient(), "Horreum-Upload-ApiKey-Freestyle", dummyTest, horreumUpload);
	}

	@Test
	public void testUploadMultiple(TestInfo info) throws Exception {
		String json1 = readFile("data/config-quickstart.jvm.json");
		String json2 = readFile("data/another-file.json");
		io.hyperfoil.tools.horreum.api.data.Test dummyTest = createTest(getHorreumClient(), info.getTestClass() + "-upload-multiple", "dev-team");

		addSchema("Some schema", "urn:some-schema", dummyTest);
		addSchema("Foobar", "urn:foobar", dummyTest);

		// Prepare HttpRequest#
		HorreumUpload horreumUpload = new HorreumUpload(
			AuthenticationType.OIDC.name(),
			HORREUM_UPLOAD_CREDENTIALS,
			dummyTest.name,
			dummyTest.owner,
			"PUBLIC",
			"2022-12-07T01:23:45Z", // cannot use JSONPath with multiple files...
			"2022-12-07T01:23:45Z",
			"urn:some-schema",
			null,
			"**/*.json",
			true
		);

		RunService.RunSummary summary = runInFreeStyleProject(getHorreumClient(), "Horreum-Upload-Multiple", dummyTest,
			new CreateFileBuilder("file1.json", json1),
			new CreateFileBuilder("file2.json", json2),
			horreumUpload);
		assertEquals(2, summary.schemas.size());
	}


	private void addSchema(String name, String uri, io.hyperfoil.tools.horreum.api.data.Test dummyTest) {
		Schema schema = new Schema();
		schema.name = name;
		schema.uri = uri;
		schema.owner = dummyTest.owner;
		schema.access = Access.PUBLIC;
		getHorreumClient().schemaService.add(schema);
	}

	private String readFile(String filename) throws IOException {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(cl.getResourceAsStream(filename))))) {
			return reader.lines().collect(Collectors.joining("\n"));
		}
	}

	private RunService.RunSummary runInFreeStyleProject(HorreumClient horreumClient, String name, io.hyperfoil.tools.horreum.api.data.Test dummyTest, Builder... builders) throws Exception {
		// Run build
		FreeStyleProject project = j.createFreeStyleProject(name);
		assertTrue(builders.length > 0);
		assertTrue(Arrays.stream(builders).anyMatch(b -> b instanceof HorreumUpload));
		project.getBuildersList().addAll(Arrays.asList(builders));
		FreeStyleBuild build = project.scheduleBuild2(0).get();

		// Check expectations
		j.assertBuildStatusSuccess(build);

		RunService.RunsSummary summary = horreumClient.runService.listTestRuns(dummyTest.id, false, null, null, "", null);
		assertEquals(1, summary.total);
		assertEquals(1, summary.runs.size());
		return summary.runs.get(0);
	}
}
