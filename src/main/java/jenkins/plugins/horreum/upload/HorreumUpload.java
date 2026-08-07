package jenkins.plugins.horreum.upload;

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

public class HorreumUpload extends HorreumBaseBuilder<HorreumUploadConfig> {

    @DataBoundConstructor
    public HorreumUpload(@Nonnull String authenticationType,
                         @Nonnull String credentials,
                         @Nonnull String folder,
                         String jsonFile,
                         String files,
                         String path,
                         boolean addBuildInfo) {
        super(new HorreumUploadConfig(authenticationType, credentials, folder, jsonFile, files, path, addBuildInfo));
    }

    public String getFolder() {
        return config.getFolder();
    }

    @DataBoundSetter
    public void setFolder(String folder) {
        this.config.setFolder(folder);
    }

    public String getJsonFile() {
        return config.getJsonFile();
    }

    @DataBoundSetter
    public void setJsonFile(String jsonFile) {
        this.config.setJsonFile(jsonFile);
    }

    public String getFiles() {
        return config.getFiles();
    }

    @DataBoundSetter
    public void setFiles(String files) {
        this.config.setFiles(files);
    }

    public String getPath() {
        return config.getPath();
    }

    @DataBoundSetter
    public void setPath(String path) {
        this.config.setPath(path);
    }

    public boolean getAddBuildInfo() {
        return config.getAddBuildInfo();
    }

    @DataBoundSetter
    public void setAddBuildInfo(boolean add) {
        config.setAddBuildInfo(add);
    }

    public boolean getAwaitProcessing() {
        return config.getAwaitProcessing();
    }

    @DataBoundSetter
    public void setAwaitProcessing(boolean awaitProcessing) {
        config.setAwaitProcessing(awaitProcessing);
    }

    public long getProcessingTimeout() {
        return config.getProcessingTimeout();
    }

    @DataBoundSetter
    public void setProcessingTimeout(long timeout) {
        config.setProcessingTimeout(timeout);
    }

    public boolean getFailOnChanges() {
        return config.getFailOnChanges();
    }

    @DataBoundSetter
    public void setFailOnChanges(boolean failOnChanges) {
        config.setFailOnChanges(failOnChanges);
    }

    @Override
    protected HorreumUploadExecutionContext createExecutionContext(AbstractBuild<?, ?> build, BuildListener listener, EnvVars envVars) {
        return HorreumUploadExecutionContext.from(config, envVars, build, listener,
                () -> build.getWorkspace() == null ? null : build.getWorkspace().getRemote(),
                () -> this.config.resolveUploadFiles(envVars, build));
    }

    @Extension
    public static final class DescriptorImpl extends HorreumBaseDescriptor {

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
    }
}
