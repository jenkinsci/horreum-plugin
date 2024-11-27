package jenkins.plugins.horreum;

import java.io.IOException;

import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Run;
import hudson.remoting.VirtualChannel;

public abstract class HorreumBaseStep<C extends HorreumBaseConfig> extends AbstractStepImpl {
   protected final C config;

   protected HorreumBaseStep(C config) {
      this.config = config;

      // Populate step config from Global state HorreumGlobalConfig.get();
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
         if (launcher != null) {
            VirtualChannel channel = launcher.getChannel();
            if (channel == null) {
               throw new IllegalStateException("Launcher doesn't support remoting but it is required");
            }
            // Fix loading class by name from TCL in org.jboss.resteasy.client.jaxrs.ProxyBuilder
            Thread thread = Thread.currentThread();
            ClassLoader originalClassLoader = thread.getContextClassLoader();
            try {
               thread.setContextClassLoader(getClass().getClassLoader());
               return channel.call(exec);
            } finally {
               thread.setContextClassLoader(originalClassLoader);
            }
         }

         return exec.call();
      }

      protected abstract BaseExecutionContext<R> createExecutionContext() throws Exception;

      public Item getProject() throws IOException, InterruptedException {
         return getContext().get(Run.class).getParent();
      }
   }
}
