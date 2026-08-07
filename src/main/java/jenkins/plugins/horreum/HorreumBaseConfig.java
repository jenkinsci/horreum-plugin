package jenkins.plugins.horreum;

import java.io.Serializable;

import javax.annotation.Nonnull;

public abstract class HorreumBaseConfig implements Serializable {

    private Boolean quiet = false;
    private String authenticationType;
    private String credentials;

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
        this.credentials = credentials;
    }

    @Nonnull
    protected static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
