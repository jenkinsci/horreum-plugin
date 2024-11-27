package jenkins.plugins.horreum;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public abstract class HorreumBaseConfig implements Serializable {
   static Boolean DEFAULT_QUIET = false;
   static Boolean DEFAULT_ABORT_ON_FAILURE = true;

   private Boolean quiet = DEFAULT_QUIET;
   private Boolean abortOnFailure = DEFAULT_ABORT_ON_FAILURE;
   private String authenticationType;
   private String credentials;

   public boolean getAbortOnFailure() {
      return abortOnFailure;
   }

   public void setAbortOnFailure(boolean abortOnFailure) {
      this.abortOnFailure = abortOnFailure;
   }

   public Boolean getQuiet() {
      return quiet;
   }

   public void setQuiet(@Nonnull Boolean quiet) {
      this.quiet = quiet;
   }

   public void setAuthenticationType(String authenticationType) {
      this.authenticationType = authenticationType;
   }

   public String getAuthenticationType() {
      return this.authenticationType;
   }

   public String getCredentials() {
      return credentials;
   }

   public void setCredentials(String credentials) {
      this.credentials = Objects.requireNonNull(credentials);
   }

   @Nonnull
   protected static String orEmpty(String value) {
      return value == null ? "" : value;
   }
}
