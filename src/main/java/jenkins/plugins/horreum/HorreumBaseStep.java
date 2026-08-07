package jenkins.plugins.horreum;

import java.io.IOException;

import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

public abstract class HorreumBaseStep<C extends HorreumBaseConfig> extends AbstractStepImpl {
    protected final C config;

    protected HorreumBaseStep(C config) {
        this.config = config;
    }

    public String getAuthenticationType() {
        return config.getAuthenticationType();
    }

    @DataBoundSetter
    public void setAuthenticationType(String authenticationType) {
        config.setAuthenticationType(authenticationType);
    }

    public String getCredentials() {
        return config.getCredentials();
    }

    @DataBoundSetter
    public void setCredentials(String credentials) {
        config.setCredentials(credentials);
    }

    public boolean getAbortOnFailure() {
        return config.getAbortOnFailure();
    }

    @DataBoundSetter
    public void setAbortOnFailure(boolean abortOnFailure) {
        this.config.setAbortOnFailure(abortOnFailure);
    }

    public Boolean getQuiet() {
        return config.getQuiet();
    }

    @DataBoundSetter
    public void setQuiet(Boolean quiet) {
        this.config.setQuiet(quiet);
    }

    public abstract static class Execution<R> extends AbstractSynchronousNonBlockingStepExecution<R> {
        @Override
        protected R run() throws Exception {
            BaseExecutionContext<R> exec = createExecutionContext();

            Launcher launcher = getContext().get(Launcher.class);
            try {
                if (launcher != null) {
                    VirtualChannel channel = launcher.getChannel();
                    if (channel == null) {
                        throw new IllegalStateException("Launcher doesn't support remoting but it is required");
                    }
                    return channel.call(exec);
                }
                return exec.call();
            } catch (HorreumClient.ChangesDetectedException e) {
                TaskListener listener = getContext().get(TaskListener.class);
                if (listener != null) {
                    listener.getLogger().println(e.getMessage());
                }
                Run<?, ?> run = getContext().get(Run.class);
                if (run != null) {
                    run.setResult(Result.UNSTABLE);
                }
                // Return null for Void steps, or the upload ID if available
                return null;
            }
        }

        protected abstract BaseExecutionContext<R> createExecutionContext() throws Exception;

        public Item getProject() throws IOException, InterruptedException {
            return getContext().get(Run.class).getParent();
        }
    }
}
