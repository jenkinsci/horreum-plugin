package jenkins.plugins.horreum.folder;

import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.EnvVars;
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
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "horreumFolderEnsure";
        }

        @Override
        public String getDisplayName() {
            return "Ensure a Horreum folder exists";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return HorreumBaseStep.requiredContext();
        }

        public ListBoxModel doFillAuthenticationTypeItems() {
            ListBoxModel items = new ListBoxModel();
            for (jenkins.plugins.horreum.AuthenticationType type : jenkins.plugins.horreum.AuthenticationType.values()) {
                items.add(type.name(), type.name());
            }
            return items;
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

    public static final class Execution extends HorreumBaseStep.Execution<Void, HorreumFolderEnsureStep> {

        Execution(HorreumFolderEnsureStep step, StepContext context) {
            super(step, context);
        }

        @Override
        protected BaseExecutionContext<Void> createExecutionContext() throws Exception {
            return HorreumFolderEnsureExecutionContext.from(getStep().config,
                    getContext().get(EnvVars.class), getContext().get(TaskListener.class));
        }

        private static final long serialVersionUID = 1L;
    }
}
