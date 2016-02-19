package org.fogbowcloud.manager.occi;

import java.util.List;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
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
		
//		List<String> listAccept = HeaderUtils.getAccept(headers);
//		String acceptType = getAccept(listAccept);
				

		return generateResponse(accountingInfo);
	}

	private StringRepresentation generateResponse(List<AccountingInfo> accountingInfo) {
		// TODO Auto-generated method stub
		return null;
	}
}
