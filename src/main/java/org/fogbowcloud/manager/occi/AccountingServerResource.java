package org.fogbowcloud.manager.occi;

import java.util.List;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.restlet.data.MediaType;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class AccountingServerResource extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(AccountingServerResource.class);

	@Get
	public StringRepresentation fetch() {
		LOGGER.debug("Executing the accounting fetch method");
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
	
		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		
		List<AccountingInfo> accountingInfo = application.getAccountingInfo(authToken);
		
		List<String> listAccept = HeaderUtils.getAccept(req.getHeaders());
		String acceptType = getAccept(listAccept);
		if (acceptType.equals(OCCIHeaders.JSON_CONTENT_TYPE)) {
			return generateJsonResponse(accountingInfo);
		} else if (acceptType.equals(MediaType.TEXT_PLAIN.toString())) {
			return generateTextPlainResponse(accountingInfo);
		} 
		throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
				ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
	}
	
	private String getAccept(List<String> listAccept) {
		if (listAccept.size() > 0 ) {
			if (listAccept.get(0).contains(MediaType.TEXT_PLAIN.toString())) {
				return MediaType.TEXT_PLAIN.toString();			
			} else if (listAccept.get(0).contains(OCCIHeaders.JSON_CONTENT_TYPE)) {
				return OCCIHeaders.JSON_CONTENT_TYPE;				
			} else {
				throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
						ResponseConstants.ACCEPT_NOT_ACCEPTABLE);				
			}
		} else {
			return MediaType.TEXT_PLAIN.toString();
		}
	}

	private StringRepresentation generateTextPlainResponse(List<AccountingInfo> accountingInfo) {
		// TODO Auto-generated method stub
		return null;
	}
	
	private StringRepresentation generateJsonResponse(List<AccountingInfo> accountingInfo) {
		// TODO Auto-generated method stub
		return null;
	}
}

