package jenkins.plugins.horreum.junit;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.util.Secret;
import io.hyperfoil.tools.HorreumClient;
import io.hyperfoil.tools.horreum.api.services.UserService;
import io.hyperfoil.tools.horreum.infra.common.SelfSignedCert;
import jenkins.plugins.horreum.HorreumGlobalConfig;
import jenkins.plugins.horreum.JenkinsExtension;
import org.jboss.logging.Logger;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.extension.*;

import java.io.InputStream;
import java.util.*;

import static com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL;
import static io.hyperfoil.tools.horreum.api.services.UserService.KeyType.USER;
import static io.hyperfoil.tools.horreum.infra.common.Const.*;
import static io.hyperfoil.tools.horreum.infra.common.HorreumResources.startContainers;
import static jenkins.plugins.horreum.it.JenkinsResources.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HorreumTestExtension {

    private static final Logger log = Logger.getLogger(HorreumTestExtension.class);
    private static boolean started = false;
    public static String horreumHost;
    public static String horreumPort = "8080";
    public static String horreumApiKey;
    private static final Map<Domain, List<Credentials>> credentials = new HashMap<>();
    public static final String HORREUM_UPLOAD_CREDENTIALS = "horreum-creds";
    public static final String HORREUM_API_KEY_CREDENTIALS = "horreum-key";
    public static final String DEFAULT_BOOTSTRAP_USERNAME = "horreum.bootstrap";
    public static final String DEFAULT_BOOTSTRAP_PASSWORD = "secret";

    @RegisterExtension
    public static JenkinsExtension j = new JenkinsExtension();

    public static void beforeAll() throws Exception {
        synchronized (HorreumTestExtension.class) {
            if (!started) {
                log.info("Starting Jenkins IT resources");

                try {
                    String keycloakImage, postgresImage, amqpImage, horreumImage;
                    try (InputStream is = HorreumTestExtension.class.getClassLoader().getResourceAsStream("META-INF/horreum.dev.properties")){
                        Properties properties = new Properties();
                        properties.load(is);
                        keycloakImage = properties.getProperty ("dev.images.keycloak");
                        postgresImage = properties.getProperty ("dev.images.postgres");
                        amqpImage = properties.getProperty ("dev.images.amq");
                        horreumImage = properties.getProperty ("dev.images.horreum");
                    }

                    if ( keycloakImage == null || postgresImage == null || amqpImage == null || horreumImage == null){
                        throw new RuntimeException("Test container images are not defined as system properties");
                    }

                    SelfSignedCert postgresSelfSignedCert = new SelfSignedCert("RSA", "SHA256withRSA", "localhost", 123);
                    Map<String, String> containerArgs = new HashMap<>();
                    containerArgs.putAll(
                        Map.ofEntries(
                        Map.entry(HORREUM_DEV_KEYCLOAK_ENABLED, "true"),
                        Map.entry(HORREUM_DEV_KEYCLOAK_IMAGE, keycloakImage),
                        Map.entry(HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS, DEFAULT_KEYCLOAK_NETWORK_ALIAS),
                        Map.entry(HORREUM_DEV_POSTGRES_ENABLED, "true"),
                        Map.entry(HORREUM_DEV_POSTGRES_IMAGE, postgresImage),
                        Map.entry(HORREUM_DEV_POSTGRES_NETWORK_ALIAS, DEFAULT_POSTGRES_NETWORK_ALIAS),
                        Map.entry(HORREUM_DEV_POSTGRES_SSL_CERTIFICATE, postgresSelfSignedCert.getCertString()),
                        Map.entry(HORREUM_DEV_POSTGRES_SSL_CERTIFICATE_KEY, postgresSelfSignedCert.getKeyString()),
                        Map.entry(HORREUM_DEV_KEYCLOAK_DB_USERNAME, DEFAULT_KC_DB_USERNAME),
                        Map.entry(HORREUM_DEV_KEYCLOAK_DB_PASSWORD, DEFAULT_KC_DB_PASSWORD),
                        Map.entry(HORREUM_DEV_KEYCLOAK_ADMIN_USERNAME, DEFAULT_KC_ADMIN_USERNAME),
                        Map.entry(HORREUM_DEV_KEYCLOAK_ADMIN_PASSWORD, DEFAULT_KC_ADMIN_PASSWORD),
                        Map.entry(HORREUM_DEV_HORREUM_HORREUM_IMAGE, horreumImage),
                        Map.entry("horreum.roles.provider", "database"),
                        Map.entry("quarkus.keycloak.admin-client.client-id", "horreum-client"),
                        Map.entry("quarkus.keycloak.admin-client.realm", "horreum"),
                        Map.entry("quarkus.keycloak.admin-client.client-secret", "secret"),
                        Map.entry("quarkus.keycloak.admin-client.grant-type", "client_credentials"),
                        Map.entry("keycloak.use.https", "false"),
                        Map.entry("keycloak.service.client", "horreum-client"),
                        Map.entry("keycloak.realm", "horreum"),
                        Map.entry("keycloak.token.admin-roles", "admin,manager,tester,viewer,uploader"),
                        Map.entry(HORREUM_DEV_AMQP_ENABLED, "true"),
                        Map.entry(HORREUM_DEV_AMQP_IMAGE, amqpImage),
                        Map.entry(HORREUM_DEV_AMQP_NETWORK_ALIAS, DEFAULT_AMQP_NETWORK_ALIAS),
                        Map.entry("amqp-username", DEFAULT_AMQP_USERNAME),
                        Map.entry("amqp-password", DEFAULT_AMQP_PASSWORD),
                        Map.entry("inContainer", "true"),
                        Map.entry("quarkus.http.host", DEFAULT_HORREUM_NETWORK_ALIAS),
                        Map.entry("quarkus.http.port", "8080"),
                        Map.entry("quarkus.profile", "dev"),
                        Map.entry(HORREUM_DEV_HORREUM_NETWORK_ALIAS, DEFAULT_HORREUM_NETWORK_ALIAS)
                    ));
                    containerArgs.putAll(startContainers(containerArgs));
                    containerArgs.putAll(startAMQPContainer(containerArgs));
                    containerArgs.putAll(startHorreumContainer(containerArgs));
                    try {
                        j.before();
                        started = true;
                    } catch (Throwable e){
                        log.fatal("Could not start Jenkins service", e);
                        throw new Exception(e);
                    }
                    horreumHost = "localhost";
                    horreumPort = containerArgs.get("horreum.container.port");
                } catch (Exception e){
                    log.fatal("Could not start services", e);
                    throw new RuntimeException("Could not start Jenkins/Horreum services", e);
                }
                try {
                    HorreumGlobalConfig globalConfig = HorreumGlobalConfig.get();
                    if (globalConfig != null) {
                        globalConfig.setBaseUrl(getHorreumURL());
                    } else {
                        System.out.println("Can not find Horreum Global Config");
                    }
                    credentials.put(Domain.global(), new ArrayList<>());
                    registerBasicCredential(HORREUM_UPLOAD_CREDENTIALS, DEFAULT_BOOTSTRAP_USERNAME, DEFAULT_BOOTSTRAP_PASSWORD);
                } catch (Throwable throwable) {
                    log.fatal("Could not configure basic credentials re-configure Keycloak", throwable);
                    throw new RuntimeException(throwable);
                }
                try (HorreumClient horreumClient = new HorreumClient.Builder()
                        .horreumUrl(getHorreumURL())
                        .horreumUser(DEFAULT_BOOTSTRAP_USERNAME)
                        .horreumPassword(DEFAULT_BOOTSTRAP_PASSWORD)
                        .build()) {

                    horreumApiKey = horreumClient.userService.newApiKey(new UserService.ApiKeyRequest("Jenkins plugin", USER));
                    registerApiKeyCredential(HORREUM_API_KEY_CREDENTIALS, horreumApiKey);

                } catch (Throwable throwable) {
                    log.fatal("Could not configure API key credentials", throwable);
                    throw new RuntimeException(throwable);
                }
            }
        }
    }

    static void registerBasicCredential(String id, String username, String password) {
        Credentials userCredentials = new UsernamePasswordCredentialsImpl(GLOBAL, id, "", username, password);

        credentials.get(Domain.global()).add(userCredentials);
        SystemCredentialsProvider scp = SystemCredentialsProvider.getInstance();
        assertNotNull(scp);
        scp.setDomainCredentialsMap(credentials);
    }

    static void registerApiKeyCredential(String id, String key) {
        Credentials keyCredentials = new StringCredentialsImpl(GLOBAL, id, "", Secret.fromString(key));

        credentials.get(Domain.global()).add(keyCredentials);
        SystemCredentialsProvider scp = SystemCredentialsProvider.getInstance();
        assertNotNull(scp);
        scp.setDomainCredentialsMap(credentials);
    }

    public static String getHorreumURL() {
        return "http://" + horreumHost + ":" + horreumPort + "/";
    }
}
