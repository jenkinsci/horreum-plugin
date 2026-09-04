package jenkins.plugins.horreum;

import java.io.IOException;
import java.util.Map;

import org.kohsuke.stapler.DataBoundSetter;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.remoting.VirtualChannel;
import hudson.tasks.Builder;

public abstract class HorreumBaseBuilder<C extends HorreumBaseConfig> extends Builder {
    protected final C config;

    public HorreumBaseBuilder(C config) {
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

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        EnvVars envVars = build.getEnvironment(listener);
        for (Map.Entry<String, String> e : build.getBuildVariables().entrySet()) {
            envVars.put(e.getKey(), e.getValue());
        }

        BaseExecutionContext<?> exec = createExecutionContext(build, listener, envVars);

        VirtualChannel channel = launcher.getChannel();
        if (channel == null) {
            throw new IllegalStateException("Launcher doesn't support remoting but it is required");
        }

        try {
            channel.call(exec);
        } catch (HorreumClient.ChangesDetectedException e) {
            listener.getLogger().println(e.getMessage());
            build.setResult(Result.UNSTABLE);
        }

        return true;
    }

    protected abstract BaseExecutionContext<?> createExecutionContext(AbstractBuild<?, ?> build, BuildListener listener, EnvVars envVars);
}
