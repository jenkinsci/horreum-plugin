package jenkins.plugins.horreum;

import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;

import hudson.model.Item;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public abstract class HorreumBaseDescriptor extends BuildStepDescriptor<Builder> {

    public ListBoxModel doFillAuthenticationTypeItems() {
        ListBoxModel items = new ListBoxModel();
        for (AuthenticationType type : AuthenticationType.values()) {
            items.add(type.name(), type.name());
        }
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
                .includeAs(ACL.SYSTEM, Jenkins.get(), StringCredentialsImpl.class)
                .includeCurrentValue(credentials);
    }
}
