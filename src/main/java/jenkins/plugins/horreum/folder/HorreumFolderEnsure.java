package jenkins.plugins.horreum.folder;

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

public class HorreumFolderEnsure extends HorreumBaseBuilder<HorreumFolderEnsureConfig> {

    @DataBoundConstructor
    public HorreumFolderEnsure(@Nonnull String authenticationType,
                               @Nonnull String credentials,
                               @Nonnull String folder) {
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
    protected HorreumFolderEnsureExecutionContext createExecutionContext(AbstractBuild<?, ?> build, BuildListener listener, EnvVars envVars) {
        return HorreumFolderEnsureExecutionContext.from(config, envVars, listener);
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
            return "Horreum Folder Ensure";
        }
    }
}
