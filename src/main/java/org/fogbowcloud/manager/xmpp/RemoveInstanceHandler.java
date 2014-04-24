package org.fogbowcloud.manager.xmpp;

import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.core.ManagerFacade;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError.Condition;

public class RemoveInstanceHandler extends AbstractQueryHandler {

	private ManagerFacade facade;

	public RemoveInstanceHandler(ManagerFacade facade) {
		super(ManagerXmppComponent.REMOVEINSTANCE_NAMESPACE);
		this.facade = facade;
	}

	@Override
	public IQ handle(IQ query) {
		String instanceId = query.getElement().element("instance")
				.elementText("id");
		IQ response = IQ.createResultIQ(query);
		try {
			facade.removeInstanceForRemoteMember(instanceId);
		} catch (OCCIException e) {
			response.setError(getCondition(e));
		}
		return response;
	}

	private Condition getCondition(OCCIException e) {
		switch (e.getStatus().getCode()) {
		case HttpStatus.SC_NOT_FOUND:
			return Condition.item_not_found;
		case HttpStatus.SC_UNAUTHORIZED:
			return Condition.not_authorized;
		case HttpStatus.SC_BAD_REQUEST:
			return Condition.bad_request;
		default:
			return Condition.internal_server_error;
		}
	}
}
