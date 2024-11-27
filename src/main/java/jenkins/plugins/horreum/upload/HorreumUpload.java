package jenkins.plugins.horreum.upload;

import javax.annotation.Nonnull;

import jenkins.plugins.horreum.AuthenticationType;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.util.ListBoxModel;
import jenkins.plugins.horreum.HorreumBaseBuilder;
import jenkins.plugins.horreum.HorreumBaseDescriptor;

//TODO: Make safe functionality as upload step
public class HorreumUpload extends HorreumBaseBuilder<HorreumUploadConfig> {
	@DataBoundConstructor
	public HorreumUpload(       @Nonnull String authenticationType,
								@Nonnull String credentials,
								@Nonnull String test,
								@Nonnull String owner,
								@Nonnull String access,
								@Nonnull String start,
								@Nonnull String stop,
								@Nonnull String schema,
								String jsonFile,
								String files,
								boolean addBuildInfo) {
		super(new HorreumUploadConfig(authenticationType, credentials, test, owner, access, start, stop, schema, jsonFile, files, addBuildInfo));
	}

	public String getTest() {
		return config.getTest();
	}

	@DataBoundSetter
	public void setTest(String test) {
		this.config.setTest(test);
	}

	public String getOwner() {
		return config.getOwner();
	}

	@DataBoundSetter
	public void setOwner(String owner) {
		this.config.setOwner(owner);
	}

	public String getAccess() {
		return this.config.getAccess();
	}

	@DataBoundSetter
	public void setAccess(String access) {
		this.config.setAccess(access);
	}

	public String getStart() {
		return config.getStart();
	}

	@DataBoundSetter
	public void setStart(String start) {
		this.config.setStart(start);
	}

	public String getStop() {
		return config.getStop();
	}

	@DataBoundSetter
	public void setStop(String stop) {
		this.config.setStop(stop);
	}

	@DataBoundSetter
	public void setSchema(String schema) {
		this.config.setSchema(schema);
	}

	public String getSchema() {
		return config.getSchema();
	}

	public String getJsonFile() {
		return config.getJsonFile();
	}

	@DataBoundSetter
	public void setJsonFile(String jsonFile) {
		this.config.setJsonFile(jsonFile);
	}

	public boolean getAddBuildInfo() {
		return config.getAddBuildInfo();
	}

	@DataBoundSetter
	public void setAddBuildInfo(boolean add) {
		config.setAddBuildInfo(add);
	}

	@Override
	protected HorreumUploadExecutionContext createExecutionContext(AbstractBuild<?, ?> build, BuildListener listener, EnvVars envVars) {
		return HorreumUploadExecutionContext.from(config, envVars, build,	listener,
				() -> build.getWorkspace() == null ? null : build.getWorkspace().getRemote(),
				() -> this.config.resolveUploadFiles(envVars, build));
	}

	@Extension
	public static final class DescriptorImpl extends HorreumBaseDescriptor {
		public static final String jsonFile = "";

		public DescriptorImpl() {
			load();
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Horreum Upload";
		}

		public ListBoxModel doFillAccessItems(@AncestorInPath Item item, @QueryParameter String credentials) {
			ListBoxModel items = new ListBoxModel();
			items.add("PUBLIC");
			items.add("PROTECTED");
			items.add("PRIVATE");
			return items;
		}
	}
}
