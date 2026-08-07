package jenkins.plugins.horreum;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.CloseProofOutputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * Base class for executing Horreum operations on Jenkins agents.
 * Handles client creation, authentication, retry logic, and remote logging.
 */
public abstract class BaseExecutionContext<R> extends MasterToSlaveCallable<R, RuntimeException> {
    protected final String url;
    protected final AuthenticationType authenticationType;
    protected final String credentialsID;
    protected final List<Long> retries;
    protected final OutputStream remoteLogger;
    protected final String apiKey;
    protected transient PrintStream localLogger;

    public BaseExecutionContext(String url, String authenticationType, String credentials, PrintStream logger) {
        this.url = url;
        this.authenticationType = authenticationType == null ? AuthenticationType.NONE : AuthenticationType.valueOf(authenticationType);
        this.credentialsID = credentials;
        HorreumGlobalConfig globalConfig = HorreumGlobalConfig.get();
        retries = globalConfig.retries();
        this.remoteLogger = new RemoteOutputStream(new CloseProofOutputStream(logger));
        this.localLogger = logger;

        // Resolve API key from credentials
        if (this.authenticationType == AuthenticationType.API_KEY) {
            List<StandardCredentials> credentialsList = CredentialsProvider.lookupCredentials(
                    StandardCredentials.class,
                    Jenkins.get(),
                    ACL.SYSTEM,
                    Collections.emptyList()
            );
            StringCredentials found = (StringCredentials) CredentialsMatchers.firstOrNull(
                    credentialsList,
                    CredentialsMatchers.both(
                            CredentialsMatchers.withId(credentialsID),
                            CredentialsMatchers.instanceOf(StringCredentials.class))
            );
            if (found == null) {
                throw new IllegalStateException("Could not retrieve Horreum API key credentials. Please check the Horreum plugin configuration.");
            }
            this.apiKey = found.getSecret().getPlainText();
        } else {
            this.apiKey = null;
        }
    }

    protected PrintStream logger() {
        if (localLogger == null) {
            try {
                localLogger = new PrintStream(remoteLogger, true, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        return localLogger;
    }

    @Override
    @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "Undeclared exception can be thrown")
    public R call() {
        HorreumClient client = createClient();
        for (int retry = 0; ; ++retry) {
            try {
                return invoke(client);
            } catch (Exception e) {
                Throwable cause = e;
                for (; ; ) {
                    if (cause instanceof HorreumClient.HorreumClientException hce) {
                        logger().printf("Request failed with status %d: %s%n", hce.getStatusCode(), hce.getMessage());
                    } else if (cause instanceof SocketException) {
                        if (retry < retries.size()) {
                            logger().printf("Request failed with socket exception, retrying in %d seconds: %s%n", retries.get(retry), cause);
                            try {
                                Thread.sleep(retries.get(retry) * 1000);
                                logger().println("Slept well, retrying now");
                            } catch (InterruptedException ie) {
                                logger().println("Interrupted waiting for another retry, retrying now!");
                            }
                            break; // break inner loop, continue outer retry loop
                        } else {
                            logger().printf("Request failed with socket exception and all retry attempts failed, aborting: %s%n", cause);
                            throw e;
                        }
                    }
                    if (cause.getCause() != null && cause.getCause() != cause) {
                        cause = cause.getCause();
                    } else {
                        throw e;
                    }
                }
                // If we get here, we broke out of the inner loop to retry
                if (!(cause instanceof SocketException)) {
                    throw e;
                }
            }
        }
    }

    protected abstract R invoke(HorreumClient client);

    protected HorreumClient createClient() {
        return new HorreumClient(url, apiKey);
    }
}
