package jenkins.plugins.horreum;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.model.Item;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public abstract class HorreumBaseDescriptor extends BuildStepDescriptor<Builder> {

   public ListBoxModel doFillAuthenticationItems(@AncestorInPath Item project,
                                                 @QueryParameter String url) {
      return fillAuthenticationItems(project, url);
   }

   public static ListBoxModel fillAuthenticationItems(Item project, String url) {
      if (project == null || !project.hasPermission(Item.CONFIGURE)) {
         return new StandardListBoxModel();
      }

      List<ListBoxModel.Option> options = new ArrayList<>();

      AbstractIdCredentialsListBoxModel<StandardListBoxModel, StandardCredentials> items = new StandardListBoxModel()
            .includeEmptyValue()
            .includeAs(ACL.SYSTEM,
                  project, StandardUsernamePasswordCredentials.class,
                  URIRequirementBuilder.fromUri(url).build());
      items.addMissing(options);
      return items;
   }

   public ListBoxModel doFillCredentialsItems(@AncestorInPath Item item, @QueryParameter String credentials) {
      StandardListBoxModel result = new StandardListBoxModel();
      if (item == null) {
         if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return result.includeCurrentValue(credentials);
         }
      } else {
         if (!item.hasPermission(Item.EXTENDED_READ)
               && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
            return result.includeCurrentValue(credentials);
         }
      }
      return result
            .includeEmptyValue()
            .includeAs(ACL.SYSTEM, Jenkins.get(),
                  UsernamePasswordCredentialsImpl.class)
            .includeCurrentValue(credentials);
   }
}
