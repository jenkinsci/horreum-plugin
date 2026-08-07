package jenkins.plugins.horreum.folder;

import java.util.Objects;

import javax.annotation.Nonnull;

import jenkins.plugins.horreum.HorreumBaseConfig;

public class HorreumFolderEnsureConfig extends HorreumBaseConfig {

    private @Nonnull String folder;

    public HorreumFolderEnsureConfig(String authenticationType, String credentials, String folder) {
        this.setAuthenticationType(authenticationType);
        this.setCredentials(credentials);
        if (folder == null || folder.isEmpty()) {
            throw new IllegalArgumentException("Folder name must be set.");
        }
        this.folder = Objects.requireNonNull(folder);
    }

    @Nonnull
    public String getFolder() {
        return folder;
    }

    public void setFolder(@Nonnull String folder) {
        this.folder = Objects.requireNonNull(folder);
    }
}
