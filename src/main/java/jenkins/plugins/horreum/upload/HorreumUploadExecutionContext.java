package jenkins.plugins.horreum.upload;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.ws.rs.WebApplicationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.hyperfoil.tools.HorreumClient;
import io.hyperfoil.tools.horreum.api.data.Access;
import jakarta.ws.rs.core.Response;
import jenkins.model.Jenkins;
import jenkins.plugins.horreum.BaseExecutionContext;
import jenkins.plugins.horreum.HorreumGlobalConfig;
import jenkins.plugins.horreum.util.HttpRequestNameValuePair;

public class HorreumUploadExecutionContext extends BaseExecutionContext<String> {

	private static final long serialVersionUID = -2066857816168989599L;
	private static final String HORREUM_JENKINS_SCHEMA = "urn:horreum:jenkins-plugin:0.1";
	private final Map<String, HttpRequestNameValuePair> params;
	private final String workspacePath;
	private final FilePath[] uploadFiles;
	private final ObjectNode buildInfo;

	static HorreumUploadExecutionContext from(HorreumUploadConfig config,
															EnvVars envVars,
															Run<?, ?> run,
															TaskListener listener,
															Supplier<String> workspacePathSupplier,
															Supplier<FilePath[]> uploadFilesSupplier) {
		String url = envVars != null ? envVars.expand(HorreumGlobalConfig.get().getBaseUrl()) : HorreumGlobalConfig.get().getBaseUrl(); //http.resolveUrl(envVars, build, taskListener);
		List<HttpRequestNameValuePair> params = config.resolveParams(); //Need to define params in freestyle project
		FilePath[] uploadFiles = uploadFilesSupplier.get();
		TaskListener taskListener = config.getQuiet() ? TaskListener.NULL : listener;

		ObjectNode buildInfo = null;
		if (params.stream().anyMatch(p -> p.getName().equals("addBuildInfo") && "true".equals(p.getValue()))) {
			buildInfo = JsonNodeFactory.instance.objectNode();
			buildInfo.put("$schema", HORREUM_JENKINS_SCHEMA);
			buildInfo.put("buildUrl", Jenkins.get().getRootUrl() + run.getUrl());
			buildInfo.put("buildNumber", run.getNumber());
			buildInfo.put("buildDisplayName", run.getDisplayName());
			buildInfo.put("jobName", run.getParent().getName());
			buildInfo.put("jobDisplayName", run.getParent().getDisplayName());
			buildInfo.put("jobFullName", run.getParent().getFullName());
			buildInfo.put("scheduleTime", run.getTimeInMillis());
			buildInfo.put("startTime", run.getStartTimeInMillis());
			buildInfo.put("uploadTime", System.currentTimeMillis());
		}
		return new HorreumUploadExecutionContext(
				url,
				config.getCredentials(),
				params,
				workspacePathSupplier.get(),
				uploadFiles,
				buildInfo,
				taskListener.getLogger());
	}

	private HorreumUploadExecutionContext(
			String url,
			String credentials,
			List<HttpRequestNameValuePair> params,
			String workspacePath,
			FilePath[] uploadFiles,
			ObjectNode buildInfo, PrintStream logger
	) {
		super(url, credentials, logger);
		this.params = new HashMap<>();
		params.forEach(param -> this.params.put(param.getName(), param));
		this.workspacePath = workspacePath;
		this.uploadFiles = uploadFiles;
		this.buildInfo = buildInfo;
	}

	@Override
	protected String invoke(HorreumClient client) {
		JsonNode data;
		if (uploadFiles == null || uploadFiles.length == 0) {
			throw new IllegalStateException("There are no files to upload!");
		} else if (uploadFiles.length == 1) {
			data = loadFile(uploadFiles[0]);
		} else {
			ObjectNode root = JsonNodeFactory.instance.objectNode();
			for (FilePath uploadFile : uploadFiles) {
				String path = uploadFile.getRemote();
				if (workspacePath != null && path.startsWith(workspacePath)) {
					path = path.substring(workspacePath.length());
					if (path.startsWith("/")) {
						path = path.substring(1);
					}
				}
				root.set(path, loadFile(uploadFile));
			}
			data = root;
		}
		String schema = params.get("schema").getValue();
		if (schema != null && schema.isEmpty()) {
			schema = null;
		}
		String start = params.get("start").getValue();
		String stop = params.get("stop").getValue();
		String test = params.get("test").getValue();
		String owner = params.get("owner").getValue();
		Access access = Access.valueOf(params.get("access").getValue());
		Response response;
		if (buildInfo == null) {
			response = client.runService.addRunFromData(
					start, stop, test, owner, access, schema, null,	data);
		} else {
			response = client.runService.addRunFromData(
					start, stop, test, owner, access, schema, null,	data, buildInfo);
		}
		Object entity = response.getEntity();
		String id;
		if (entity instanceof String) {
			id = (String) entity;
		} else if (entity instanceof InputStream) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader((InputStream) entity, StandardCharsets.UTF_8))) {
				id = reader.readLine();
				if (id == null) {
					throw new WebApplicationException("Missing run ID!");
				}
				String secondLine = reader.readLine();
				if (secondLine != null) {
					throw new WebApplicationException("Run ID shouldn't contain newlines; first line was " + id + ", second line: " + secondLine);
				}
			} catch (IOException e) {
				throw new WebApplicationException("Cannot read run ID", e);
			}
		} else {
			throw new WebApplicationException("Cannot convert response entity to string! Entity: " + entity);
		}
		logger().printf("Uploaded run ID: %s%n", id);
		return id;
	}

	private JsonNode loadFile(FilePath uploadFile) {
		try {
			return new ObjectMapper().readTree(new File(uploadFile.getRemote()));
		} catch (IOException e) {
			throw new RuntimeException("File for upload cannot be read: " + uploadFile.getRemote(), e);
		}
	}
}
