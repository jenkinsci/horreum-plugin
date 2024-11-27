package jenkins.plugins.horreum.expect;

import java.util.Objects;

import javax.annotation.Nonnull;

import hudson.EnvVars;
import jenkins.plugins.horreum.HorreumBaseConfig;

public class HorreumExpectConfig extends HorreumBaseConfig {

	private @Nonnull String test;
	private long timeout;
	private @Nonnull String expectedBy;
	private @Nonnull String backlink; // = "$BUILD_URL"

	public HorreumExpectConfig(String authenticationType, String credentials, String test, long timeout, String expectedBy, String backlink) {
		this.setAuthenticationType(authenticationType);
		this.setCredentials(credentials);
		this.test = Objects.requireNonNull(test);
		this.timeout = timeout;
		this.expectedBy = orEmpty(expectedBy);
		this.backlink = orEmpty(backlink);
	}

	@Nonnull
	public String getTest() {
		return test;
	}

	public void setTest(@Nonnull String test) {
		this.test = Objects.requireNonNull(test);
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	@Nonnull
	public String getExpectedBy() {
		return expectedBy;
	}

	public void setExpectedBy(@Nonnull String expectedBy) {
		this.expectedBy = orEmpty(expectedBy);
	}

	@Nonnull
	public String getBacklink() {
		return backlink;
	}

	public void setBacklink(@Nonnull String backlink) {
		this.backlink = orEmpty(backlink);
	}

	public String resolveBacklink(EnvVars envVars) {
		String backlink = this.backlink.isEmpty() ? "$BUILD_URL" : this.backlink;
		return envVars != null ? envVars.expand(backlink) : backlink;
	}
}
