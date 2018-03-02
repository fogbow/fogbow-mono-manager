package org.fogbowcloud.manager.occi.model;

import org.apache.http.HttpStatus;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

public class OCCIException extends ResourceException {
	
	private static final long serialVersionUID = 6258329809337522269L;
	private ErrorType type;
	
	public OCCIException(ErrorType type, String description) {
		super(new Status(getStatusCode(type)), description);
		this.type = type;
	}
	
	public ErrorType getType() {
		return type;
	}

	private static int getStatusCode(ErrorType type) {
		switch (type) {
		    case UNAUTHORIZED:
			    return HttpStatus.SC_UNAUTHORIZED;
		    case FORBIDDEN:
			    return HttpStatus.SC_FORBIDDEN;
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
            case SERVICE_UNAVAILABLE:
                return HttpStatus.SC_SERVICE_UNAVAILABLE;
            case INTERNAL_SERVER_ERROR:
				return HttpStatus.SC_INTERNAL_SERVER_ERROR;
		default:
			break;
		}
		return 0;
	}
}
