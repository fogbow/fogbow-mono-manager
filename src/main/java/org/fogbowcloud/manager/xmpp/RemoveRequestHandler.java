package org.fogbowcloud.manager.xmpp;

import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;

public class RemoveRequestHandler extends AbstractQueryHandler {

	private ManagerController facade;

	public RemoveRequestHandler(ManagerController facade) {
		super(ManagerXmppComponent.REMOVEREQUEST_NAMESPACE);
		this.facade = facade;
	}

	@Override
	public IQ handle(IQ query) {
		String requestId = query.getElement().element("query").element("request").elementText("id");
		String accessId = query.getElement().element("query").element("token").elementText("accessId");
		IQ response = IQ.createResultIQ(query);
		try {
			facade.removeRequestForRemoteMember(accessId, requestId);
		} catch (OCCIException e) {
			response.setError(ManagerPacketHelper.getCondition(e));
		}
		return response;
	}
}
