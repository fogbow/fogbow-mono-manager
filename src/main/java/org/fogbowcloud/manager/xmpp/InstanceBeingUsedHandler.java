package org.fogbowcloud.manager.xmpp;

import org.dom4j.Element;
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
		String orderId = query.getElement().element("query").element(
				ManagerPacketHelper.ORDER_EL).elementText("id");
		String instanceId = null;
		Element instanceEl = query.getElement().element("query").element("instance");
		if (instanceEl != null) {
			instanceId = instanceEl.elementText("id");
		}
		IQ response = IQ.createResultIQ(query);
		if (!facade.instanceHasOrderRelatedTo(orderId, instanceId)) {
			response.setError(Condition.item_not_found);
		}
		return response;
	}

}
