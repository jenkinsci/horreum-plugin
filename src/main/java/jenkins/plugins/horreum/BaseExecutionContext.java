package jenkins.plugins.horreum;

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

    private static final long serialVersionUID = 2L;

    protected final String url;
    protected final AuthenticationType authenticationType;
    protected final String credentialsID;
    protected final List<Long> retries;
    protected final OutputStream remoteLogger;
    protected final String apiKey;
    protected transient PrintStream localLogger;

    public BaseExecutionContext(String url, String authenticationType, String credentials, PrintStream logger) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "Horreum base URL is not configured. Please set it in Jenkins global configuration (Manage Jenkins > Horreum Configuration).");
        }
        this.url = url;
        this.authenticationType = authenticationType == null ? AuthenticationType.NONE : AuthenticationType.valueOf(authenticationType);
        this.credentialsID = credentials;
        HorreumGlobalConfig globalConfig = HorreumGlobalConfig.get();
        retries = globalConfig != null ? globalConfig.retries() : Collections.emptyList();
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
                throw new IllegalStateException("Could not retrieve Horreum API key credentials with ID '" + credentialsID
                        + "'. Please check the Horreum plugin configuration.");
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
                if (shouldRetry(e, retry)) {
                    long delay = retries.get(retry);
                    logger().printf("Request failed, retrying in %d seconds: %s%n", delay, e.getMessage());
                    try {
                        Thread.sleep(delay * 1000);
                        logger().println("Retrying now");
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    if (e instanceof HorreumClient.HorreumClientException hce) {
                        logger().printf("Request failed with status %d: %s%n", hce.getStatusCode(), hce.getMessage());
                    }
                    throw e;
                }
            }
        }
    }

    /**
     * Determine if an exception is retryable and we haven't exhausted retries.
     */
    private boolean shouldRetry(Exception e, int retryCount) {
        if (retryCount >= retries.size()) {
            return false;
        }
        // Retry on socket errors (connection refused, reset, timeout)
        if (findCause(e, SocketException.class) != null) {
            return true;
        }
        // Retry on 5xx server errors and 429 rate limiting
        if (e instanceof HorreumClient.HorreumClientException hce) {
            return HorreumClient.isRetryableStatus(hce.getStatusCode());
        }
        return false;
    }

    /**
     * Walk the cause chain looking for a specific exception type.
     */
    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        Throwable current = t;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return null;
    }

    protected abstract R invoke(HorreumClient client);

    protected HorreumClient createClient() {
        return new HorreumClient(url, apiKey);
    }
}
