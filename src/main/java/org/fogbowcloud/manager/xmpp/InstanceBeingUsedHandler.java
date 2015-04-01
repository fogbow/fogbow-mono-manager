package org.fogbowcloud.manager.xmpp;

import org.fogbowcloud.manager.core.ManagerController;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError.Condition;

public class InstanceBeingUsedHandler extends AbstractQueryHandler {

	private ManagerController facade;

	public InstanceBeingUsedHandler(ManagerController facade) {
		super(ManagerXmppComponent.INSTANCEBEINGUSED_NAMESPACE);
		this.facade = facade;
	}

	@Override
	public IQ handle(IQ query) {
		String requestId = query.getElement().element("query").element("request")
				.elementText("id");
		String instanceId = query.getElement().element("query").element("instance")
				.elementText("id");
		IQ response = IQ.createResultIQ(query);
		if (!facade.instanceHasRequestRelatedTo(requestId, instanceId)) {
			response.setError(Condition.item_not_found);
		}
		return response;
	}

}
