package org.fogbowcloud.manager.occi.model;

import java.util.Map;
import java.util.UUID;

import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;

public class ComputeApplication extends Application {

	public static final String TARGET = "/compute";

	private Map<String, String> userToInstanceId;
	private Map<String, InstanceState> instanceIdToState;
	private static InstanceIdGenerator idGenerator;

	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach(TARGET, ComputeServer.class);
		router.attach(TARGET + "/{instanceid}", ComputeServer.class);
		return router;
	}

	public void getAllInstanceIds(String user) {
		// TODO Auto-generated method stub

	}

	protected void setIdGenerator(InstanceIdGenerator idGenerator) {
		this.idGenerator = this.idGenerator;
	}

	public static class ComputeServer extends ServerResource {

		@Get
		public String fetch() {
			ComputeApplication computeApplication = (ComputeApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();
			String token = req.getHeaders().getValues(OCCIHeaders.X_AUTH_TOKEN);

			// TODO getUserFrom keystone
			String user = token;

			computeApplication.getAllInstanceIds(user);
			return "";
		}

		@Post
		public String post() {
			String instanceId = String.valueOf(idGenerator.generateId());
			return "";
		}

		@Delete
		public String remove() {
			return "";
		}

	}

	public class InstanceIdGenerator {
		public String generateId() {
			return String.valueOf(UUID.randomUUID());
		}
	}
}
