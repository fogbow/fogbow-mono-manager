package org.fogbowcloud.manager.occi.request;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class RequestRepository {

	private Map<String, List<Request>> requests = new HashMap<String, List<Request>>();
	
	public void addRequest(String user, Request request) {
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
					return request;
				}
			}
		}
		return null;
	}

	public Request get(String user, String requestId) {
		List<Request> userRequests = requests.get(user);
		if (userRequests == null) {
			return null;
		}
		for (Request request : userRequests) {
			if (request.getId().equals(requestId)) {
				return request;
			}
		}
		return null;
	}
	
	public List<Request> getByUser(String user) {
		List<Request> userRequests = requests.get(user);
		return userRequests == null ? new LinkedList<Request>()
				: new LinkedList<Request>(userRequests);
	}
	
	public void removeByUser(String user) {
		requests.remove(user);
	}
	
	public void remove(String requestId) {
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
}
