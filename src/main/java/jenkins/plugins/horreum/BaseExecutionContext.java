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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.CloseProofOutputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.security.ACL;
import io.hyperfoil.tools.HorreumClient;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

public abstract class BaseExecutionContext<R> extends MasterToSlaveCallable<R, RuntimeException> {
   protected final String url;
   protected final AuthenticationType authenticationType;
   protected final String credentialsID;
   protected final List<Long> retries;
   protected final OutputStream remoteLogger;
   protected final Credentials credentials;
   protected transient PrintStream localLogger;

   public BaseExecutionContext(String url, String authenticationType, String credentials, PrintStream logger) {
      this.url = url;
      this.authenticationType = authenticationType == null ? AuthenticationType.BASIC : AuthenticationType.valueOf(authenticationType);
      this.credentialsID = credentials;
      HorreumGlobalConfig globalConfig = HorreumGlobalConfig.get();
      retries = globalConfig.retries();
      this.remoteLogger = new RemoteOutputStream(new CloseProofOutputStream(logger));
      this.localLogger = logger;

      List<StandardCredentials> credentialsList = CredentialsProvider.lookupCredentials(
            StandardCredentials.class,
            Jenkins.get(),
            ACL.SYSTEM,
            Collections.emptyList()
      );
      this.credentials = CredentialsMatchers.firstOrNull(
            credentialsList,
            CredentialsMatchers.both(CredentialsMatchers.withId(credentialsID), CredentialsMatchers.instanceOf(this.authenticationType.credentialsClass()))
      );
      if (this.credentials == null) {
         throw new IllegalStateException("Could not retrieve Horreum Credentials. Please check the Horreum plugin configuration");
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
   @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "Undeclared exception can be thrown from response.getEntity()")
   public R call() {
      HorreumClient client = createClient();
      RETRY: for (int retry = 0;; ++retry) {
         try {
            return invoke(client);
         } catch (Exception e) {
            Throwable cause = e;
            for (; ; ) {
               if (cause instanceof WebApplicationException) {
                  Response response = ((WebApplicationException) cause).getResponse();
                  Object entity = null;
                  try {
                     entity = response.getEntity();
                     if (entity instanceof InputStream) {
                        entity = toByteArrayOutputStream((InputStream) entity).toString(StandardCharsets.UTF_8.name());
                     }
                  } catch (Exception e2) {
                     // ignore e.g. IllegalStateException: RESTEASY003765: Response is closed.
                  }
                  logger().printf("Request failed with status %d, message: %s%n", response.getStatus(), entity);
               } else if (cause instanceof SocketException) {
                  // these errors are usually temporary, let's try later
                  if (retry < retries.size()) {
                     logger().printf("Request failed with socket exception, retrying in %d seconds: %s%n", retries.get(retry), cause);
                     try {
                        Thread.sleep(retries.get(retry) * 1000);
                        logger().println("Slept well, retrying now");
                     } catch (InterruptedException ie) {
                        logger().println("Interrupted waiting for another retry, retrying now!");
                     }
                     continue RETRY;
                  } else {
                     logger().printf("Request failed with socket exception and all retry attempts failed, aborting: %s", cause);
                     throw e;
                  }
               }
               if (cause.getCause() != null && cause.getCause() != cause) {
                  cause = cause.getCause();
               } else {
                  break;
               }
            }
            throw e;
         }
      }
   }

   private static ByteArrayOutputStream toByteArrayOutputStream(InputStream stream) throws IOException {
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int length;
      while ((length = stream.read(buffer)) != -1) {
         result.write(buffer, 0, length);
      }
      stream.close();
      return result;
   }

   protected abstract R invoke(HorreumClient client);

   protected HorreumClient createClient() {
      HorreumClient.Builder clientBuilder = new HorreumClient.Builder().horreumUrl(url);
      switch (authenticationType) {
         case BASIC:
         case OIDC:
            clientBuilder.horreumUser(((UsernamePasswordCredentials) credentials).getUsername())
                         .horreumPassword(((UsernamePasswordCredentials) credentials).getPassword().getPlainText());
            break;
         case API_KEY:
            clientBuilder.horreumApiKey(((StringCredentials) credentials).getSecret().getPlainText());
            break;
         default:
      }
      return clientBuilder.build();
   }
}
