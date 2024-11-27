package jenkins.plugins.horreum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static jenkins.plugins.horreum.junit.HorreumTestExtension.*;
import static jenkins.plugins.horreum.junit.HorreumTestClientExtension.getHorreumClient;

import java.util.List;
import java.util.stream.Collectors;

import io.hyperfoil.tools.horreum.api.alerting.RunExpectation;
import org.junit.jupiter.api.Test;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import jenkins.plugins.horreum.expect.HorreumExpect;
import org.junit.jupiter.api.TestInfo;

public class HorreumExpectTest extends HorreumPluginTestBase {
	@Test
	public void testExpectRun(TestInfo info) throws Exception {
		io.hyperfoil.tools.horreum.api.data.Test dummyTest = createTest(info.getTestClass() + "-expect", "dev-team");
		HorreumExpect horreumExpect = new HorreumExpect(
			AuthenticationType.OIDC.name(), HORREUM_UPLOAD_CREDENTIALS, dummyTest.name, 60L, "Jenkins CI", "$BUILD_URL"
		);

		// Run build
		FreeStyleProject project = j.createFreeStyleProject("Horreum-Expect-Freestyle");
		project.getBuildersList().add(horreumExpect);
		FreeStyleBuild build = project.scheduleBuild2(0).get();

		// Check expectations
		j.assertBuildStatusSuccess(build);

		List<RunExpectation> expectations = getHorreumClient().alertingService.expectations();
		expectations = expectations.stream().filter(e -> e.testId == dummyTest.id).collect(Collectors.toList());
		assertEquals(1, expectations.size());
		RunExpectation runExpectation = expectations.get(0);
		assertEquals("Jenkins CI", runExpectation.expectedBy);
		assertTrue(runExpectation.backlink.contains("jenkins/job/Horreum-Expect-Freestyle"));
	}
}
