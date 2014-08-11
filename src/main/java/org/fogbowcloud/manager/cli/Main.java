package org.fogbowcloud.manager.cli;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestType;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

public class Main {

	protected static final String DEFAULT_URL = "http://localhost:8182";
	protected static final int DEFAULT_INTANCE_COUNT = 1;
	protected static final String DEFAULT_TYPE = RequestType.ONE_TIME.getValue();
	protected static final String DEFAULT_FLAVOR = RequestConstants.SMALL_TERM;
	protected static final String DEFAULT_IMAGE = "fogbow-linux-x86";

	private static HttpClient client;

	public static void main(String[] args) throws Exception {
		configureLog4j();

		JCommander jc = new JCommander();

		MemberCommand member = new MemberCommand();
		jc.addCommand("member", member);
		RequestCommand request = new RequestCommand();
		jc.addCommand("request", request);
		InstanceCommand instance = new InstanceCommand();
		jc.addCommand("instance", instance);
		TokenCommand token = new TokenCommand();
		jc.addCommand("token", token);
		ResourceCommand resource = new ResourceCommand();
		jc.addCommand("resource", resource);

		jc.setProgramName("fogbow-cli");
		jc.parse(args);

		String parsedCommand = jc.getParsedCommand();

		if (parsedCommand == null) {
			jc.usage();
			return;
		}

		if (parsedCommand.equals("member")) {
			String url = member.url;
			doRequest("get", url + "/members", null);
		} else if (parsedCommand.equals("request")) {
			String url = request.url;
			request.authToken = normalizeToken(request.authToken);
			if (request.get) {
				if (request.create || request.delete) {
					jc.usage();
					return;
				}
				if (request.requestId != null) {
					doRequest("get", url + "/" + RequestConstants.TERM + "/" + request.requestId,
							request.authToken);
				} else {
					doRequest("get", url + "/" + RequestConstants.TERM, request.authToken);
				}
			} else if (request.delete) {
				if (request.create || request.get || request.requestId == null) {
					jc.usage();
					return;
				}
				doRequest("delete", url + "/" + RequestConstants.TERM + "/" + request.requestId,
						request.authToken);
			} else if (request.create) {
				if (request.delete || request.get || request.requestId != null) {
					jc.usage();
					return;
				}

				if (!request.type.equals("one-time") && !request.type.equals("persistent")) {
					jc.usage();
					return;
				}
				
				Set<Header> headers = new HashSet<Header>();
				headers.add(new BasicHeader("Category", RequestConstants.TERM + "; scheme=\""
						+ RequestConstants.SCHEME + "\"; class=\"" + RequestConstants.KIND_CLASS
						+ "\""));
				headers.add(new BasicHeader("X-OCCI-Attribute",
						"org.fogbowcloud.request.instance-count=" + request.instanceCount));
				headers.add(new BasicHeader("X-OCCI-Attribute", "org.fogbowcloud.request.type="
						+ request.type));
				headers.add(new BasicHeader("Category", request.flavor + "; scheme=\""
						+ RequestConstants.TEMPLATE_RESOURCE_SCHEME + "\"; class=\""
						+ RequestConstants.MIXIN_CLASS + "\""));
				headers.add(new BasicHeader("Category", request.image + "; scheme=\""
						+ RequestConstants.TEMPLATE_OS_SCHEME + "\"; class=\""
						+ RequestConstants.MIXIN_CLASS + "\""));
				doRequest("post", url + "/" + RequestConstants.TERM, request.authToken, headers);
			}
		} else if (parsedCommand.equals("instance")) {
			String url = instance.url;
			request.authToken = normalizeToken(request.authToken);
			if (instance.delete && instance.get) {
				jc.usage();
				return;
			}
			if (instance.get) {
				if (instance.instanceId != null) {
					doRequest("get", url + "/compute/" + instance.instanceId, instance.authToken);
				} else {
					doRequest("get", url + "/compute/", instance.authToken);
				}
			} else if (instance.delete) {
				if (instance.instanceId == null) {
					jc.usage();
					return;
				}
				
				
				doRequest("delete", url + "/compute/" + instance.instanceId, instance.authToken);
			}
		} else if (parsedCommand.equals("token")) {
			String url = token.url;

			Set<Header> headers = getCredentialHeaders(token.credentials);

			doRequest("get", url + "/token", null, headers);
		} else if (parsedCommand.equals("resource")) {
			String url = resource.url;

			doRequest("get", url + "/-/", null);
		}
	}

	private static String normalizeToken(String token) {
		if (token == null) {
			return null;
		}		
		return token.replace(Token.BREAK_LINE_REPLACE, "");
	}
	
	private static Set<Header> getCredentialHeaders(Map<String, String> mapCredentials) {
		Set<Header> headers = new HashSet<Header>();
		
		for (String keyMapCredentials : mapCredentials.keySet()) {
			headers.add(new BasicHeader(keyMapCredentials, mapCredentials.get(keyMapCredentials)));
		}

		return headers;
	}

	private static void configureLog4j() {
		ConsoleAppender console = new ConsoleAppender();
		console.setThreshold(Level.OFF);
		console.activateOptions();
		Logger.getRootLogger().addAppender(console);
	}

	private static void doRequest(String method, String endpoint, String authToken)
			throws URISyntaxException, HttpException, IOException {
		doRequest(method, endpoint, authToken, new HashSet<Header>());
	}

	private static void doRequest(String method, String endpoint, String authToken,
			Set<Header> additionalHeaders) throws URISyntaxException, HttpException, IOException {
		HttpUriRequest request = null;
		if (method.equals("get")) {
			request = new HttpGet(endpoint);
		} else if (method.equals("delete")) {
			request = new HttpDelete(endpoint);
		} else if (method.equals("post")) {
			request = new HttpPost(endpoint);
		}
		request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		if (authToken != null) {
			request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
		}
		for (Header header : additionalHeaders) {
			request.addHeader(header);
		}
		
		if (client == null) {
			client = new DefaultHttpClient();
			HttpParams params = new BasicHttpParams();
			params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
			client = new DefaultHttpClient(new ThreadSafeClientConnManager(params, client
					.getConnectionManager().getSchemeRegistry()), params);
		}
		
		HttpResponse response = null;
		try {
			response = client.execute(request);			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
			System.out.println(EntityUtils.toString(response.getEntity()));
		} else {
			System.out.println(response.getStatusLine().toString());
		}
	}

	protected static void setClient(HttpClient client) {
		Main.client = client;
	}

	private static class Command {
		@Parameter(names = "--url", description = "fogbow manager url")
		String url = System.getenv("FOGBOW_URL") == null ? Main.DEFAULT_URL : System
				.getenv("FOGBOW_URL");
	}

	private static class AuthedCommand extends Command {
		@Parameter(names = "--auth-token", description = "auth token")
		String authToken = System.getenv("FOGBOW_AUTH_TOKEN");
	}

	@Parameters(separators = "=", commandDescription = "Members operations")
	private static class MemberCommand extends Command {
		@Parameter(names = "--get", description = "List federation members")
		Boolean get = true;
	}

	@Parameters(separators = "=", commandDescription = "Request operations")
	private static class RequestCommand extends AuthedCommand {
		@Parameter(names = "--get", description = "Get request")
		Boolean get = false;

		@Parameter(names = "--create", description = "Create request")
		Boolean create = false;

		@Parameter(names = "--delete", description = "Delete request")
		Boolean delete = false;

		@Parameter(names = "--id", description = "Request id")
		String requestId = null;

		@Parameter(names = "--n", description = "Instance count")
		int instanceCount = Main.DEFAULT_INTANCE_COUNT;

		@Parameter(names = "--image", description = "Instance image")
		String image = Main.DEFAULT_IMAGE;

		@Parameter(names = "--flavor", description = "Instance flavor")
		String flavor = Main.DEFAULT_FLAVOR;

		@Parameter(names = "--type", description = "Request type (one-time|persistent)")
		String type = Main.DEFAULT_TYPE;
	}

	@Parameters(separators = "=", commandDescription = "Instance operations")
	private static class InstanceCommand extends AuthedCommand {
		@Parameter(names = "--get", description = "Get instance data")
		Boolean get = false;

		@Parameter(names = "--delete", description = "Delete instance")
		Boolean delete = false;

		@Parameter(names = "--id", description = "Instance id")
		String instanceId = null;
	}

	@Parameters(separators = "=", commandDescription = "Token operations")
	private static class TokenCommand extends Command {
		@Parameter(names = "--get", description = "Get token")
		Boolean get = false;

		@DynamicParameter(names = "-D", description = "Dynamic parameters")
		private Map<String, String> credentials = new HashMap<String, String>();
	}

	@Parameters(separators = "=", commandDescription = "Resources Fogbow")
	private static class ResourceCommand extends Command {
		@Parameter(names = "--get", description = "Get all resources")
		Boolean get = false;
	}
}