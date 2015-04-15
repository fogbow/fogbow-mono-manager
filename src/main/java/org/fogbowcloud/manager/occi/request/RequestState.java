package org.fogbowcloud.manager.occi.request;

public enum RequestState {

	/**
	 * Open: The request is not fulfilled.
	 * 
	 * Failed: The request failed because bad parameters were specified.
	 * 
	 * Spawning: The request received an instance but it is not ready to be used yet.
	 * 
	 * Fulfilled: The request is currently active (fulfilled) and has an
	 * associated Instance.
	 * 
	 * Deleted: The request was deleted, but it still has a instance related to it.
	 * 
	 * Closed: The request either completed (a Instance was launched and
	 * subsequently was interrupted or terminated), or was not fulfilled within
	 * the period specified.
	 */
	OPEN("open"), FAILED("failed"), SPAWNING("spawning"), FULFILLED("fulfilled"), DELETED("deleted"), CLOSED("closed");

	private String value;

	private RequestState(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}
	
	public boolean in(RequestState... requestStates) {
		for (RequestState requestState : requestStates) {
			if (requestState.equals(this)){
				return true;
			}
		}
		return false;
	}
	
	public boolean notIn(RequestState... requestStates) {
		for (RequestState requestState : requestStates) {
			if (requestState.equals(this)){
				return false;
			}
		}
		return true;
	}
}
