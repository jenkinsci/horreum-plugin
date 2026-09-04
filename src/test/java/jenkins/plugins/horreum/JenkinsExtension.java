package jenkins.plugins.horreum;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * JUnit 5 adapter for JenkinsRule (which is a JUnit 4 Rule).
 * Creates and tears down an embedded Jenkins instance per test method.
 *
 * <p>Usage:
 * <pre>
 * {@literal @}RegisterExtension
 * JenkinsExtension j = new JenkinsExtension();
 * </pre>
 *
 * @see <a href="https://issues.jenkins.io/browse/JENKINS-48466">JENKINS-48466</a>
 */
public class JenkinsExtension extends JenkinsRule
        implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        String className = context.getRequiredTestClass().getName();
        String methodName = context.getRequiredTestMethod().getName();
        Description description = Description.createTestDescription(className, methodName);
        this.testDescription = description;
        try {
            this.before();
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("JenkinsRule.before() failed", t);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        this.after();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        // No-op: JUnit 5 manages the lifecycle via beforeEach/afterEach
        return base;
    }
}
