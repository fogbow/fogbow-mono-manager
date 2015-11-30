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

	/*
	 * The get and remove operation in RequestRepository consider only local
	 * requests to allow these operations only from manager where the request
	 * was created.
	 */
	
	protected boolean requestExists(List<Request> requests, Request userRequest) {
		for (Request request : requests) {
			if (request.getId().equals(userRequest.getId())) {
				return true;
			}
		}
		return false;
	}
	
	public void addRequest(String user, Request request) {
		LOGGER.debug("Adding request " + request.getId() + " to user " + user);
		List<Request> userRequests = requests.get(user);
		if (userRequests == null) {
			userRequests = new LinkedList<Request>();
			requests.put(user, userRequests);
		}
		if (requestExists(userRequests, request)) {
			return;
		}
		userRequests.add(request);
	}

	public List<Request> getRequestsIn(RequestState... states) {
		List<Request> allRequestsInState = new LinkedList<Request>();
		for (List<Request> userRequests : requests.values()) {
			for (Request request : userRequests) {
				if (request.getState().in(states)) {
					allRequestsInState.add(request);
				}
			}
		}
		return allRequestsInState;
	}

	public Request get(String requestId) {
		return get(requestId, true);
	}
	
	public Request get(String requestId, boolean findLocalRequest) {
		for (List<Request> userRequests : requests.values()) {
			for (Request request : userRequests) {
				if (request.getId().equals(requestId)) {
					if (findLocalRequest && request.isLocal() 
							|| !findLocalRequest && !request.isLocal()) {
						LOGGER.debug("Getting request id " + request);
						return request;						
					}
				}
			}
		}
		LOGGER.debug("Request id " + requestId + " was not found.");
		return null;
	}

	public Request get(String user, String requestId) {
		return get(user, requestId, true);
	}
	
	public Request get(String user, String requestId, boolean findLocalRequest) {
		List<Request> userRequests = requests.get(user);
		if (userRequests == null) {
			LOGGER.debug("User " + user + " does not have requests.");
			return null;
		}
		for (Request request : userRequests) {
			if (request.getId().equals(requestId)) {
				if (findLocalRequest && request.isLocal() 
						|| !findLocalRequest && !request.isLocal()) {
					LOGGER.debug("Getting request " + request + " owner by user " + user);
					return request;					
				}
			}
		}
		LOGGER.debug("Request " + requestId + " owner by user " + user + " was not found.");
		return null;
	}

	public List<Request> getByUser(String user) {
		return getByUser(user, true);
	}
	
	public List<Request> getByUser(String user, boolean findLocalRequest) {
		LOGGER.debug("Getting local requests by user " + user);
		List<Request> userRequests = requests.get(user);
		if (userRequests == null) {
			return new LinkedList<Request>();
		}		
		LinkedList<Request> userLocalRequests = new LinkedList<Request>();
		for (Request request : userRequests) {
			if (findLocalRequest && request.isLocal()) {
				userLocalRequests.add(request);
			} else if (!findLocalRequest && !request.isLocal()) {
				userLocalRequests.add(request);
			}
		}
		return userLocalRequests;
	}

	public void removeByUser(String user) {
		List<Request> requestsByUser = requests.get(user);
		if (requestsByUser != null) {
			for (Request request : requestsByUser) {
				if (request.isLocal()) {
					remove(request.getId());
				}
			}
		}
	}

	public void remove(String requestId) {
		LOGGER.debug("Removing requestId " + requestId);

		for (List<Request> userRequests : requests.values()) {
			Iterator<Request> iterator = userRequests.iterator();
			while (iterator.hasNext()) {
				Request request = (Request) iterator.next();
				if (request.getId().equals(requestId) && request.isLocal()) {
					if (request.getState().equals(RequestState.CLOSED)) { 
						LOGGER.debug("Request " + requestId + " does not have an instance. Excluding request.");
						iterator.remove();
					} else {
						request.setState(RequestState.DELETED);
					}
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

	public List<Request> getAllRequests() {
		List<Request> allRequests = new LinkedList<Request>();
		for (List<Request> userRequests : requests.values()) {
			for (Request request : userRequests) {
				allRequests.add(request);
			}
		}
		return allRequests;
	}
	
	public List<Request> getAllLocalRequests() {
		List<Request> allLocalRequests = new LinkedList<Request>();
		for (List<Request> userRequests : requests.values()) {
			for (Request request : userRequests) {
				if (request.isLocal()){
					allLocalRequests.add(request);
				}
			}
		}
		return allLocalRequests;
	}
	
	public List<Request> getAllServedRequests() {
		List<Request> allRemoteRequests = new LinkedList<Request>();
		for (List<Request> userRequests : requests.values()) {
			for (Request request : userRequests) {
				if (!request.isLocal()){
					allRemoteRequests.add(request);
				}
			}
		}
		return allRemoteRequests;
	}
	
	public Request getRequestByInstance(String instanceId) {
		for (List<Request> userRequests : requests.values()) {
			for (Request request : userRequests) {
				if (request.getState().in(RequestState.FULFILLED, 
						RequestState.SPAWNING, RequestState.DELETED)
						&& instanceId.equals(request.getInstanceId())) {
					return request;
				}
			}
		}
		return null;
	}
}
