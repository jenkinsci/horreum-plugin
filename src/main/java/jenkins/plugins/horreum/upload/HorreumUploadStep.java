package jenkins.plugins.horreum.upload;

import java.io.IOException;

import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import jenkins.plugins.horreum.BaseExecutionContext;
import jenkins.plugins.horreum.HorreumBaseDescriptor;
import jenkins.plugins.horreum.HorreumBaseStep;

public final class HorreumUploadStep extends HorreumBaseStep<HorreumUploadConfig> {

    @DataBoundConstructor
    public HorreumUploadStep(String authenticationType,
                             String credentials,
                             String folder,
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
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "horreumUpload";
        }

        @Override
        public String getDisplayName() {
            return "Upload JSON data to a Horreum folder";
        }

        public ListBoxModel doFillCredentialsItems(
                @AncestorInPath Item item, @QueryParameter String credentials) {
            return new HorreumBaseDescriptor() {
                @Override
                public boolean isApplicable(Class aClass) {
                    return true;
                }

                @Override
                public String getDisplayName() {
                    return "";
                }
            }.doFillCredentialsItems(item, credentials);
        }
    }

    public static final class Execution extends HorreumBaseStep.Execution<String> {
        @Inject
        private transient HorreumUploadStep step;

        @Override
        protected BaseExecutionContext<String> createExecutionContext() throws Exception {
            StepContext context = getContext();
            return HorreumUploadExecutionContext.from(step.config, null,
                    context.get(Run.class), context.get(TaskListener.class),
                    this::resolveWorkspacePath, this::resolveUploadFiles);
        }

        private String resolveWorkspacePath() {
            try {
                FilePath workspace = getContext().get(FilePath.class);
                return workspace == null ? null : workspace.getRemote();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private FilePath[] resolveUploadFiles() {
            try {
                FilePath workspace = getContext().get(FilePath.class);
                if (workspace == null) {
                    throw new IllegalStateException("Could not find workspace.");
                }
                String jsonFile = step.getJsonFile();
                String files = step.getFiles();
                if (jsonFile != null && !jsonFile.trim().isEmpty()) {
                    FilePath uploadFilePath = workspace.child(jsonFile);
                    if (!uploadFilePath.exists()) {
                        throw new IllegalStateException("Could not find upload file: " + jsonFile);
                    }
                    return new FilePath[]{uploadFilePath};
                } else if (files != null && !files.trim().isEmpty()) {
                    return workspace.list(files);
                } else {
                    throw new IllegalStateException("Neither 'jsonFile' nor 'files' is set.");
                }
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        private static final long serialVersionUID = 1L;
    }
}
