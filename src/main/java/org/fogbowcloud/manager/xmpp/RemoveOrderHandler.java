package org.fogbowcloud.manager.xmpp;

import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;

public class RemoveOrderHandler extends AbstractQueryHandler {

	private ManagerController facade;

	public RemoveOrderHandler(ManagerController facade) {
		super(ManagerXmppComponent.REMOVEORDER_NAMESPACE);
		this.facade = facade;
	}

	@Override
	public IQ handle(IQ query) {
		String orderId = query.getElement().element("query").element(ManagerPacketHelper.ORDER_EL)
				.elementText("id");
		String accessId = query.getElement().element("query").element("token").elementText("accessId");
		IQ response = IQ.createResultIQ(query);
		try {
			facade.removeOrderForRemoteMember(accessId, orderId);
		} catch (OCCIException e) {
			response.setError(ManagerPacketHelper.getCondition(e));
		}
		return response;
	}
}
