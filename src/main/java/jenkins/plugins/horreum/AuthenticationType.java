package jenkins.plugins.horreum;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

public enum AuthenticationType {
    BASIC, OIDC, API_KEY;

    Class<? extends Credentials> credentialsClass() {
         switch (this) {
             case BASIC:
             case OIDC:
                 return UsernamePasswordCredentials.class;
             case API_KEY:
                 return StringCredentials.class;
             default:
                 return StandardCredentials.class;
         }
    }
}
