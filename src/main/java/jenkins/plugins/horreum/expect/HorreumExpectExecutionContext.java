package jenkins.plugins.horreum.expect;

import java.io.PrintStream;

import hudson.EnvVars;
import hudson.model.TaskListener;
import io.hyperfoil.tools.HorreumClient;
import jenkins.plugins.horreum.BaseExecutionContext;
import jenkins.plugins.horreum.HorreumGlobalConfig;

public class HorreumExpectExecutionContext extends BaseExecutionContext<Void> {

	private static final long serialVersionUID = -2066857816168989599L;
	private final HorreumExpectConfig config;
	private final String backlink;

	static HorreumExpectExecutionContext from(HorreumExpectConfig config,
															EnvVars envVars, TaskListener listener) {
		String url = envVars != null ? envVars.expand(HorreumGlobalConfig.get().getBaseUrl()) : HorreumGlobalConfig.get().getBaseUrl(); //http.resolveUrl(envVars, build, taskListener);
		TaskListener taskListener = config.getQuiet() ? TaskListener.NULL : listener;

		String backlink = config.resolveBacklink(envVars);
		HorreumExpectExecutionContext context = new HorreumExpectExecutionContext(
				url,
				config,
				backlink,
				taskListener.getLogger());
		return context;
	}

	private HorreumExpectExecutionContext(
			String url,
			HorreumExpectConfig config,
			String backlink,
			PrintStream logger
	) {
		super(url, config.getAuthenticationType(), config.getCredentials(), logger);
		this.config = config;
		this.backlink = backlink;
	}

	@Override
	protected Void invoke(HorreumClient client) {
		client.alertingService.expectRun(config.getTest(), config.getTimeout(), config.getExpectedBy(), backlink);
		return null;
	}
}
