package jenkins.plugins.horreum.upload;

import java.io.IOException;
import java.util.Objects;

import javax.annotation.Nonnull;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import jenkins.plugins.horreum.HorreumBaseConfig;

public class HorreumUploadConfig extends HorreumBaseConfig {

    private @Nonnull String folder;
    private String jsonFile;
    private String files;
    private String path;
    private boolean addBuildInfo;
    private boolean awaitProcessing = true;
    private long processingTimeout = 300; // seconds
    private boolean failOnChanges = false;

    public HorreumUploadConfig(String authenticationType, String credentials, String folder,
                               String jsonFile, String files, String path, boolean addBuildInfo) {
        this.setAuthenticationType(authenticationType);
        this.setCredentials(credentials);
        if (folder == null || folder.isEmpty()) {
            throw new IllegalArgumentException("Folder name must be set.");
        }
        if ((jsonFile == null || jsonFile.isEmpty()) && (files == null || files.isEmpty())) {
            throw new IllegalArgumentException("Either 'jsonFile' or 'files' must be set.");
        } else if (jsonFile != null && !jsonFile.isEmpty() && files != null && !files.isEmpty()) {
            throw new IllegalArgumentException("You can set 'jsonFile' or 'files' but not both.");
        }
        this.folder = Objects.requireNonNull(folder);
        this.jsonFile = jsonFile;
        this.files = files;
        this.path = orEmpty(path);
        this.addBuildInfo = addBuildInfo;
    }

    @Nonnull
    public String getFolder() {
        return folder;
    }

    public void setFolder(@Nonnull String folder) {
        this.folder = folder;
    }

    public String getJsonFile() {
        return jsonFile;
    }

    public void setJsonFile(String jsonFile) {
        this.jsonFile = jsonFile;
    }

    public String getFiles() {
        return files;
    }

    public void setFiles(String files) {
        this.files = files;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = orEmpty(path);
    }

    public boolean getAddBuildInfo() {
        return addBuildInfo;
    }

    public HorreumUploadConfig setAddBuildInfo(boolean addBuildInfo) {
        this.addBuildInfo = addBuildInfo;
        return this;
    }

    public boolean getAwaitProcessing() {
        return awaitProcessing;
    }

    public void setAwaitProcessing(boolean awaitProcessing) {
        this.awaitProcessing = awaitProcessing;
    }

    public long getProcessingTimeout() {
        return processingTimeout;
    }

    public void setProcessingTimeout(long processingTimeout) {
        if (processingTimeout <= 0) {
            throw new IllegalArgumentException("Processing timeout must be positive, got: " + processingTimeout);
        }
        this.processingTimeout = processingTimeout;
    }

    public boolean getFailOnChanges() {
        return failOnChanges;
    }

    public void setFailOnChanges(boolean failOnChanges) {
        this.failOnChanges = failOnChanges;
    }

    FilePath[] resolveUploadFiles(EnvVars envVars, AbstractBuild<?, ?> build) {
        try {
            FilePath workspace = build.getWorkspace();
            if (workspace == null) {
                throw new IllegalStateException("Could not find workspace to check existence of upload file: " + jsonFile +
                        ". You should use it inside a 'node' block");
            }
            if (jsonFile != null && !jsonFile.isEmpty()) {
                FilePath uploadFilePath = workspace.child(envVars.expand(jsonFile));
                if (!uploadFilePath.exists()) {
                    throw new IllegalStateException("Could not find upload file: " + jsonFile);
                }
                return new FilePath[]{uploadFilePath};
            } else if (files != null && !files.isEmpty()) {
                return workspace.list(envVars.expand(files));
            } else {
                throw new IllegalStateException("Neither 'jsonFile' nor 'files' is set.");
            }
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
