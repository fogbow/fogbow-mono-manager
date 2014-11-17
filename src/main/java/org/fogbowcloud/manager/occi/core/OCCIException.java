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
		case QUOTA_EXCEEDED:
			return HttpStatus.SC_INSUFFICIENT_SPACE_ON_RESOURCE;
		case NOT_ACCEPTABLE:
			return HttpStatus.SC_NOT_ACCEPTABLE;			
		case METHOD_NOT_ALLOWED:
			return HttpStatus.SC_METHOD_NOT_ALLOWED;
		default:
			break;
		}
		return 0;
	}
}
