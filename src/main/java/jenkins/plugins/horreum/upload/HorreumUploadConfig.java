package jenkins.plugins.horreum.upload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import jenkins.plugins.horreum.AuthenticationType;
import jenkins.plugins.horreum.HorreumBaseConfig;
import jenkins.plugins.horreum.util.HttpRequestNameValuePair;

public class HorreumUploadConfig extends HorreumBaseConfig {

	private @Nonnull String test;
	private @Nonnull String owner;
	private @Nonnull String access;
	private @Nonnull String start;
	private @Nonnull String stop;
	private @Nonnull String schema;
	private String jsonFile;
	private String files;
	private boolean addBuildInfo;

	public HorreumUploadConfig(String authenticationType, String credentials, String test, String owner, String access, String start, String stop, String schema, String jsonFile, String files, boolean addBuildInfo) {
		this.setAuthenticationType(authenticationType);
		this.setCredentials(credentials);
		if (test == null || test.isEmpty()) {
			throw new IllegalArgumentException("Test name (or ID) must be set.");
		}
		owner = orEmpty(owner);
		access = orEmpty(access);
		if (start == null || start.isEmpty()) {
			throw new IllegalArgumentException("Start timestamp must be set.");
		}
		if (stop == null || stop.isEmpty()) {
			throw new IllegalArgumentException("Stop timestamp must be set.");
		}
		schema = orEmpty(schema);
		if ((jsonFile == null || jsonFile.isEmpty()) && (files == null || files.isEmpty())) {
			throw new IllegalArgumentException("Either 'jsonFile' or 'files' must be set.");
		} else if (jsonFile != null && !jsonFile.isEmpty() && files != null && !files.isEmpty()) {
			throw new IllegalArgumentException("You can set 'jsonFile' or 'files' but not both.");
		}
		this.test = Objects.requireNonNull(test);
		this.owner = Objects.requireNonNull(owner);
		this.access = Objects.requireNonNull(access);
		this.start = Objects.requireNonNull(start);
		this.stop = Objects.requireNonNull(stop);
		this.schema = Objects.requireNonNull(schema);
		this.jsonFile = jsonFile;
		this.files = files;
		this.addBuildInfo = addBuildInfo;
	}

	@Nonnull
	public String getTest() {
		return test;
	}

	public void setTest(@Nonnull String test) {
		this.test = test;
	}

	@Nonnull
	public String getOwner() {
		return owner;
	}

	public void setOwner(@Nonnull String owner) {
		this.owner = owner;
	}

	@Nonnull
	public String getAccess() {
		return access;
	}

	public void setAccess(@Nonnull String access) {
		this.access = access;
	}

	@Nonnull
	public String getStart() {
		return start;
	}

	public void setStart(@Nonnull String start) {
		this.start = start;
	}

	@Nonnull
	public String getStop() {
		return stop;
	}

	public void setStop(@Nonnull String stop) {
		this.stop = stop;
	}

	@Nonnull
	public String getSchema() {
		return schema;
	}

	public void setSchema(@Nonnull String schema) {
		this.schema = schema;
	}

	public String getJsonFile() {
		return jsonFile;
	}

	public void setJsonFile(@Nonnull String jsonFile) {
		this.jsonFile = jsonFile;
	}

	public String getFiles() {
		return files;
	}

	public void setFiles(@Nonnull String files) {
		this.files = files;
	}

	public boolean getAddBuildInfo() {
		return addBuildInfo;
	}

	public HorreumUploadConfig setAddBuildInfo(boolean addBuildInfo) {
		this.addBuildInfo = addBuildInfo;
		return this;
	}

	FilePath[] resolveUploadFiles(EnvVars envVars, AbstractBuild<?,?> build) {
		try {
			FilePath workspace = build.getWorkspace();
			if (workspace == null) {
				throw new IllegalStateException("Could not find workspace to check existence of upload file: " + jsonFile +
						". You should use it inside a 'node' block");
			}
			if (jsonFile != null && !jsonFile.isEmpty()) {
				FilePath uploadFilePath = workspace.child(envVars.expand(jsonFile));
				if (!uploadFilePath.exists()) {
					throw new IllegalStateException("Could not find upload file: " + jsonFile);
				}
				return new FilePath[] { uploadFilePath };
			} else if (files != null && !files.isEmpty()) {
				return workspace.list(envVars.expand(files));
			} else {
				throw new IllegalStateException();
			}
		} catch (IOException | InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	public List<HttpRequestNameValuePair> resolveParams() {
		List<HttpRequestNameValuePair> params = new ArrayList<>();
		params.add(new HttpRequestNameValuePair("test",this.test));
		params.add(new HttpRequestNameValuePair("owner",this.owner));
		params.add(new HttpRequestNameValuePair("access",this.access));
		params.add(new HttpRequestNameValuePair("start",this.start));
		params.add(new HttpRequestNameValuePair("stop",this.stop));
		params.add(new HttpRequestNameValuePair("schema", this.schema));
		params.add(new HttpRequestNameValuePair("addBuildInfo", String.valueOf(this.addBuildInfo)));
		return params;
	}
}
