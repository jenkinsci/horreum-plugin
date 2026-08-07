package jenkins.plugins.horreum.folder;

import java.io.PrintStream;

import com.fasterxml.jackson.databind.JsonNode;

import hudson.EnvVars;
import hudson.model.TaskListener;
import jenkins.plugins.horreum.BaseExecutionContext;
import jenkins.plugins.horreum.HorreumClient;
import jenkins.plugins.horreum.HorreumGlobalConfig;

public class HorreumFolderEnsureExecutionContext extends BaseExecutionContext<Void> {

    private static final long serialVersionUID = 1L;
    private final String folder;

    static HorreumFolderEnsureExecutionContext from(HorreumFolderEnsureConfig config,
                                                    EnvVars envVars,
                                                    TaskListener listener) {
        String url = envVars != null
                ? envVars.expand(HorreumGlobalConfig.get().getBaseUrl())
                : HorreumGlobalConfig.get().getBaseUrl();
        TaskListener taskListener = config.getQuiet() ? TaskListener.NULL : listener;

        String folder = config.getFolder();
        if (envVars != null) {
            folder = envVars.expand(folder);
        }

        return new HorreumFolderEnsureExecutionContext(
                url,
                config.getAuthenticationType(),
                config.getCredentials(),
                folder,
                taskListener.getLogger());
    }

    private HorreumFolderEnsureExecutionContext(
            String url,
            String authenticationType,
            String credentials,
            String folder,
            PrintStream logger
    ) {
        super(url, authenticationType, credentials, logger);
        this.folder = folder;
    }

    @Override
    protected Void invoke(HorreumClient client) {
        JsonNode existing = client.getFolder(folder);
        if (existing != null) {
            logger().printf("Folder '%s' already exists (id: %s)%n", folder, existing.path("id"));
        } else {
            JsonNode created = client.createFolder(folder);
            logger().printf("Created folder '%s' (id: %s)%n", folder, created.get("id"));
        }
        return null;
    }
}
