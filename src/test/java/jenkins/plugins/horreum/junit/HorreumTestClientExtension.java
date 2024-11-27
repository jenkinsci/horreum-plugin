package jenkins.plugins.horreum.junit;

import io.hyperfoil.tools.HorreumClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class HorreumTestClientExtension extends HorreumTestExtension implements BeforeAllCallback {
    private static HorreumClient horreumClient, apiKeyClient;

    public static void instantiateClient() {
        if (horreumClient == null) {
            horreumClient = new HorreumClient.Builder()
                .horreumUrl(getHorreumURL())
                .horreumUser("horreum.bootstrap")
                .horreumPassword("secret")
                .build();
            Assertions.assertNotNull(horreumClient);
        }
    }

    public static void instantiateApiKeyClient() {
        if (apiKeyClient == null) {
            apiKeyClient = new HorreumClient.Builder()
                    .horreumUrl(getHorreumURL())
                    .horreumApiKey(horreumApiKey)
                    .build();
            Assertions.assertNotNull(apiKeyClient);
        }
    }

    public static HorreumClient getHorreumClient() {
        if (horreumClient == null) {
            instantiateClient();
        }
        return horreumClient;
    }

    public static HorreumClient getApiKeyClient() {
        if (apiKeyClient == null) {
            instantiateApiKeyClient();
        }
        return apiKeyClient;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext)  {
    }
}
