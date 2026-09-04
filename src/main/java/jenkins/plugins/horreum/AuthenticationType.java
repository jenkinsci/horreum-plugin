package jenkins.plugins.horreum;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * Authentication modes supported by the Horreum plugin.
 * <ul>
 *   <li>{@link #API_KEY} - Bearer token authentication using an API key (stored as Jenkins secret text)</li>
 *   <li>{@link #NONE} - No authentication (for Horreum instances with security disabled)</li>
 * </ul>
 */
public enum AuthenticationType {
    API_KEY, NONE;

    Class<? extends Credentials> credentialsClass() {
        return switch (this) {
            case API_KEY -> StringCredentials.class;
            case NONE -> StandardCredentials.class;
        };
    }
}
