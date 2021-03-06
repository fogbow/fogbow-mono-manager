package org.fogbowcloud.manager.xmpp;

import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;

public class RemoveInstanceHandler extends AbstractQueryHandler {

	private ManagerController facade;

	public RemoveInstanceHandler(ManagerController facade) {
		super(ManagerXmppComponent.REMOVEINSTANCE_NAMESPACE);
		this.facade = facade;
	}

	@Override
	public IQ handle(IQ query) {
		String instanceId = query.getElement().element("query")
				.element("instance").elementText("id");
		IQ response = IQ.createResultIQ(query);
		try {
			facade.removeInstanceForRemoteMember(instanceId);
		} catch (OCCIException e) {
			response.setError(ManagerPacketHelper.getCondition(e));
		}
		return response;
	}
}
