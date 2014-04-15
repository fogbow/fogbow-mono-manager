package org.fogbowcloud.manager.occi.core;

import org.apache.http.HttpStatus;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

public class OCCIException extends ResourceException {
	
	private static final long serialVersionUID = 6258329809337522269L;
	
	public OCCIException(ErrorType type, String description) {
		super(new Status(getStatusCode(type)), description);
	}

	private static int getStatusCode(ErrorType type) {
		switch (type) {
		case UNAUTHORIZED:
			return HttpStatus.SC_UNAUTHORIZED;
		case NOT_FOUND:
			return HttpStatus.SC_NOT_FOUND;
		case BAD_REQUEST:
			return HttpStatus.SC_BAD_REQUEST;
		default:
			break;
		}
		return 0;
	}
}
