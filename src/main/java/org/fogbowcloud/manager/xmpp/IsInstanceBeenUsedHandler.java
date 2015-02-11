package org.fogbowcloud.manager.xmpp;

import org.fogbowcloud.manager.core.ManagerController;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError.Condition;

public class IsInstanceBeenUsedHandler extends AbstractQueryHandler {

	private ManagerController facade;

	public IsInstanceBeenUsedHandler(ManagerController facade) {
		super(ManagerXmppComponent.ISINSTANCEBEENUSED_NAMESPACE);
		this.facade = facade;
	}

	@Override
	public IQ handle(IQ query) {
		String instanceId = query.getElement().element("query").element("instance")
				.elementText("id");
		IQ response = IQ.createResultIQ(query);
		if (!facade.isInstanceBeenUsed(instanceId)) {
			response.setError(Condition.item_not_found);
		}
		return response;
	}

}
