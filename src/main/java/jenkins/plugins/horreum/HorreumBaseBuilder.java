package jenkins.plugins.horreum;

import java.io.IOException;
import java.util.Map;

import org.kohsuke.stapler.DataBoundSetter;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Items;
import hudson.remoting.VirtualChannel;
import hudson.tasks.Builder;
import jenkins.plugins.horreum.util.HttpRequestNameValuePair;

public abstract class HorreumBaseBuilder<C extends HorreumBaseConfig> extends Builder {
   protected final C config;

   public HorreumBaseBuilder(C config) {
      this.config = config;
   }

   @Initializer(before = InitMilestone.PLUGINS_STARTED)
   public static void xStreamCompatibility() {
      Items.XSTREAM2.alias("pair", HttpRequestNameValuePair.class);
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
      // Fix loading class by name from TCL in org.jboss.resteasy.client.jaxrs.ProxyBuilder
      Thread thread = Thread.currentThread();
      ClassLoader originalClassLoader = thread.getContextClassLoader();
      try {
         thread.setContextClassLoader(getClass().getClassLoader());
         channel.call(exec);
      } finally {
         thread.setContextClassLoader(originalClassLoader);
      }

      return true;
   }

   protected abstract BaseExecutionContext<?> createExecutionContext(AbstractBuild<?, ?> build, BuildListener listener, EnvVars envVars);
}
