package jenkins.plugins.horreum;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

public abstract class HorreumBaseStep<C extends HorreumBaseConfig> extends Step {
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

    public Boolean getQuiet() {
        return config.getQuiet();
    }

    @DataBoundSetter
    public void setQuiet(Boolean quiet) {
        this.config.setQuiet(quiet);
    }

    /**
     * Required context for all Horreum steps: Run, TaskListener, Launcher, EnvVars, FilePath.
     */
    public static Set<Class<?>> requiredContext() {
        Set<Class<?>> context = new HashSet<>();
        context.add(Run.class);
        context.add(TaskListener.class);
        context.add(Launcher.class);
        context.add(EnvVars.class);
        context.add(FilePath.class);
        return context;
    }

    @SuppressWarnings("unchecked")
    public abstract static class Execution<R, S extends HorreumBaseStep<?>> extends SynchronousNonBlockingStepExecution<R> {
        private final transient S step;

        protected Execution(S step, StepContext context) {
            super(context);
            this.step = step;
        }

        protected S getStep() {
            return step;
        }

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
                // Return the upload ID even when UNSTABLE so pipeline scripts can use it
                return (R) String.valueOf(e.getUploadId());
            }
        }

        protected abstract BaseExecutionContext<R> createExecutionContext() throws Exception;
    }
}
