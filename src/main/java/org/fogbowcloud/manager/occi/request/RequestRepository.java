package org.fogbowcloud.manager.occi.request;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

public class RequestRepository {

	private static final Logger LOGGER = Logger.getLogger(RequestRepository.class);

	private Map<String, List<Request>> requests = new HashMap<String, List<Request>>();

	public void addRequest(String user, Request request) {
		LOGGER.debug("Adding request " + request.getId() + " to user " + user);
		List<Request> userRequests = requests.get(user);
		if (userRequests == null) {
			userRequests = new LinkedList<Request>();
			requests.put(user, userRequests);
		}
		userRequests.add(request);
	}

	public List<Request> get(RequestState state) {
		List<Request> requestInState = new LinkedList<Request>();
		for (List<Request> userRequests : requests.values()) {
			for (Request request : userRequests) {
				if (request.getState().equals(state)) {
					requestInState.add(request);
				}
			}
		}
		return requestInState;
	}

	public Request get(String requestId) {
		for (List<Request> userRequests : requests.values()) {
			for (Request request : userRequests) {
				if (request.getId().equals(requestId)) {
					LOGGER.debug("Getting request id " + request);
					return request;
				}
			}
		}
		LOGGER.debug("Request id " + requestId + " was not found.");
		return null;
	}

	public Request get(String user, String requestId) {
		List<Request> userRequests = requests.get(user);
		if (userRequests == null) {
			LOGGER.debug("User " + user + " does not have requests.");
			return null;
		}
		for (Request request : userRequests) {
			if (request.getId().equals(requestId)) {
				LOGGER.debug("Getting request " + request + " owner by user " + user);
				return request;
			}
		}
		LOGGER.debug("Request " + requestId + " owner by user " + user + " was not found.");
		return null;
	}

	public List<Request> getByUser(String user) {
		LOGGER.debug("Getting instances by user " + user);
		List<Request> userRequests = requests.get(user);
		return userRequests == null ? new LinkedList<Request>() : new LinkedList<Request>(
				userRequests);
	}

	public void removeByUser(String user) {
		List<Request> requestsByUser = requests.get(user);
		if (requestsByUser != null) {
			for (Request request : requestsByUser) {
				remove(request.getId());
			}
		}
	}

	public void remove(String requestId) {
		LOGGER.debug("Removing requestId " + requestId);

		for (List<Request> userRequests : requests.values()) {
			for (Request request : userRequests) {
				if (request.getId().equals(requestId)) {
					request.setState(RequestState.DELETED);
					return;
				}
			}
		}
	}

	public void exclude(String requestId) {
		LOGGER.debug("Excluing requestId " + requestId);

		for (List<Request> userRequests : requests.values()) {
			Iterator<Request> iterator = userRequests.iterator();
			while (iterator.hasNext()) {
				Request request = (Request) iterator.next();
				if (request.getId().equals(requestId)) {
					iterator.remove();
					return;
				}
			}
		}
	}

	public List<Request> getAll() {
		List<Request> allRequests = new LinkedList<Request>();
		for (List<Request> userRequests : requests.values()) {
			for (Request request : userRequests) {
				allRequests.add(request);
			}
		}
		return allRequests;
	}
}
