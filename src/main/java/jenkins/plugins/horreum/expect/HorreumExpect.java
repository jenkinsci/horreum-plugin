package jenkins.plugins.horreum.expect;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import jenkins.plugins.horreum.HorreumBaseBuilder;
import jenkins.plugins.horreum.HorreumBaseDescriptor;

public class HorreumExpect extends HorreumBaseBuilder<HorreumExpectConfig> {

	@DataBoundConstructor
	public HorreumExpect(       @Nonnull String authenticationType,
								@Nonnull String credentials,
								@Nonnull String test,
								@Nonnull long timeout,
								@Nonnull String expectedBy,
								@Nonnull String backlink) {
		super(new HorreumExpectConfig(authenticationType, credentials, test, timeout, expectedBy, backlink));
	}

	public String getTest() {
		return config.getTest();
	}

	@DataBoundSetter
	public void setTest(String test) {
		this.config.setTest(test);
	}

	public long getTimeout() {
		return config.getTimeout();
	}

	@DataBoundSetter
	public void setTimeout(long timeout) {
		this.config.setTimeout(timeout);
	}

	public String getExpectedBy() {
		return config.getExpectedBy();
	}

	@DataBoundSetter
	public void setExpectedBy(String expectedBy) {
		this.config.setExpectedBy(expectedBy);
	}

	public String getBacklink() {
		return config.getBacklink();
	}

	@DataBoundSetter
	public void setBacklink(String backlink) {
		this.config.setBacklink(backlink);
	}

	@Override
	protected HorreumExpectExecutionContext createExecutionContext(AbstractBuild<?, ?> build, BuildListener listener, EnvVars envVars) {
		return HorreumExpectExecutionContext.from(config, envVars, listener);
	}

	@Extension
	public static final class DescriptorImpl extends HorreumBaseDescriptor {
		public static final boolean abortOnFailure = true;
		public static final Boolean quiet = false;

		public DescriptorImpl() {
			load();
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Horreum Expect Run";
		}
	}
}
