package jenkins.plugins.horreum.folder;

import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import jenkins.plugins.horreum.BaseExecutionContext;
import jenkins.plugins.horreum.HorreumBaseDescriptor;
import jenkins.plugins.horreum.HorreumBaseStep;

public final class HorreumFolderEnsureStep extends HorreumBaseStep<HorreumFolderEnsureConfig> {

    @DataBoundConstructor
    public HorreumFolderEnsureStep(String authenticationType,
                                   String credentials,
                                   String folder) {
        super(new HorreumFolderEnsureConfig(authenticationType, credentials, folder));
    }

    public String getFolder() {
        return config.getFolder();
    }

    @DataBoundSetter
    public void setFolder(String folder) {
        this.config.setFolder(folder);
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
            return "horreumFolderEnsure";
        }

        @Override
        public String getDisplayName() {
            return "Ensure a Horreum folder exists";
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

    public static final class Execution extends HorreumBaseStep.Execution<Void> {
        @Inject
        private transient HorreumFolderEnsureStep step;

        @Override
        protected BaseExecutionContext<Void> createExecutionContext() throws Exception {
            return HorreumFolderEnsureExecutionContext.from(step.config, null, getContext().get(TaskListener.class));
        }

        private static final long serialVersionUID = 1L;
    }
}
