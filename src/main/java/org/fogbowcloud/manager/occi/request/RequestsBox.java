package org.fogbowcloud.manager.occi.request;

import java.util.ArrayList;
import java.util.List;

public class RequestsBox {

	private List<String> open;
	private List<String> fulfilled;
	private List<String> failed;
	private List<String> canceled;
	private List<String> closed;

	public RequestsBox() {
		// TODO concurrency problems?
		open = new ArrayList<String>();
		fulfilled = new ArrayList<String>();
		failed = new ArrayList<String>();
		canceled = new ArrayList<String>();
		closed = new ArrayList<String>();
	}

	public void addOpen(String requestId) {
		open.add(requestId);
	}

	public List<String> getAllRequestIds() {
		List<String> allRequestIds = new ArrayList<String>();
		allRequestIds.addAll(open);
		allRequestIds.addAll(fulfilled);
		allRequestIds.addAll(failed);
		allRequestIds.addAll(canceled);
		allRequestIds.addAll(closed);
		return allRequestIds;
	}

	public void remove(String requestId) {
		open.remove(requestId);
		fulfilled.remove(requestId);
		failed.remove(requestId);
		canceled.remove(requestId);
		closed.remove(requestId);
	}

	public boolean contains(String requestId) {
		if (open.contains(requestId) || fulfilled.contains(requestId)
				|| fulfilled.contains(requestId) || failed.contains(requestId)
				|| canceled.contains(requestId) || closed.contains(requestId)) {
			return true;
		}
		return false;
	}

	public void openToFulfilled(String requestId) {
		open.remove(requestId);
		fulfilled.add(requestId);
	}

	public List<String> getOpenIds() {
		return open;
	}

	public void openToFailed(String requestId) {
		open.remove(requestId);
		failed.add(requestId);
	}
}
