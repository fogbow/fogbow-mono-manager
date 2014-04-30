package org.fogbowcloud.manager.cli;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

public class Main {

	public static void main(String[] args) throws Exception {
		JCommander jc = new JCommander();
		
		MemberCommand member = new MemberCommand();
		jc.addCommand("member", member);
		RequestCommand request = new RequestCommand();
		jc.addCommand("request", request);
		InstanceCommand instance = new InstanceCommand();
		jc.addCommand("instance", instance);
		TokenCommand token = new TokenCommand();
		jc.addCommand("token", token);		
		
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
			if (request.get) {
				if (request.create || request.delete) {
					jc.usage();
					return;
				}
				if (request.requestId != null) {
					doRequest("get", url + "/request/" + request.requestId, 
							request.authToken);
				} else {
					doRequest("get", url + "/request", request.authToken);
				}
			} else if (request.delete) {
				if (request.create || request.get || request.requestId == null) {
					jc.usage();
					return;
				}
				doRequest("delete", url + "/request/" + request.requestId, 
						request.authToken);
			} else if (request.create) {
				if (request.delete || request.get || request.requestId != null) {
					jc.usage();
					return;
				}
				Set<String> headers = new HashSet<String>();
				headers.add("Category: fogbow-request; scheme=\"http://schemas.fogbowcloud.org/request#\"; class=\"kind\"");
				headers.add("X-OCCI-Attribute: org.fogbowcloud.request.instance-count = " + request.instanceCount);
				headers.add("X-OCCI-Attribute: org.fogbowcloud.request.type = one-time");
				headers.add("Category: " + request.flavor + "; scheme=\"http://schemas.fogbowcloud.org/template/resource#\"; class=\"mixin\"");
				headers.add("Category: " + request.image + "; scheme=\"http://schemas.fogbowcloud.org/template/os#\"; class=\"mixin\"");
				doRequest("post", url + "/request", request.authToken, headers);
			}
		} else if (parsedCommand.equals("instance")) {
			String url = instance.url;
			if (instance.delete && instance.get) {
				jc.usage();
				return;
			}
			if (instance.get) {
				if (instance.instanceId != null) {
					doRequest("get", url + "/compute/" + instance.instanceId, 
							instance.authToken);
				} else {
					doRequest("get", url + "/compute", instance.authToken);
				}
			} else if (instance.delete) {
				if (instance.instanceId == null) {
					jc.usage();
					return;
				}
				doRequest("delete", url + "/compute/" + instance.instanceId, 
						instance.authToken);
			}
		} else if (parsedCommand.equals("token")) {
			String url = token.url;
			Set<String> headers = new HashSet<String>();
			headers.add("password: " + token.password);
			headers.add("username: " + token.username);	
			
			doRequest("get", url + "/token", null, headers);
		}
	}

	private static void doRequest(String method, String endpoint, String authToken) 
			throws URISyntaxException, HttpException, IOException {
		doRequest(method, endpoint, authToken, new HashSet<String>());
	}
	
	private static void doRequest(String method, String endpoint, String authToken, 
			Set<String> additionalHeaders)
			throws URISyntaxException, HttpException, IOException {
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
		for (String header : additionalHeaders) {
			String[] splitHeader = header.split(": ");
			request.addHeader(splitHeader[0].trim(), splitHeader[1].trim());
		}
		
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(request);
		
		System.out.println(EntityUtils.toString(response.getEntity()));
	}
	
	private static class Command {
		@Parameter(names = "--url", required = true, description = "fogbow manager url")
		String url = null;
	}
	
	private static class AuthedCommand extends Command {
		@Parameter(names = "--auth-token", required = true, description = "auth token")
		String authToken = null;
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
		int instanceCount = 0;
		
		@Parameter(names = "--image", description = "Instance image")
		String image = null;
		
		@Parameter(names = "--flavor", description = "Instance flavor")
		String flavor = null;
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
		
		@Parameter(names = "--password", description = "Password")
		String password = null;
		
		@Parameter(names = "--username", description = "Username")
		String username = null;		
	}	
}
