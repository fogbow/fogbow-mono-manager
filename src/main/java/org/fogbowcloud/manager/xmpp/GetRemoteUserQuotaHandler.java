package org.fogbowcloud.manager.xmpp;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError.Condition;

public class GetRemoteUserQuotaHandler extends AbstractQueryHandler {

	private ManagerController facade;

	public GetRemoteUserQuotaHandler(ManagerController facade) {
		super(ManagerXmppComponent.GETREMOTEUSERQUOTA_NAMESPACE);
		this.facade = facade;
	}

	@Override
	public IQ handle(IQ query) {
	
		Element tokenEl = query.getElement().element("query").element("token");
		String accessId = tokenEl.elementText("accessId");
		Element userEl = tokenEl.element("user");
		String userId = userEl.elementText(ManagerPacketHelper.ID_EL); 

		IQ response = IQ.createResultIQ(query);
		ResourcesInfo resourcesInfo = facade.getResourceInfoForRemoteMember(accessId, userId);
		
		if (resourcesInfo == null) {
			response.setError(Condition.item_not_found);
			return response;
		}

		Element queryEl = response.getElement().addElement("query", ManagerXmppComponent.GETREMOTEUSERQUOTA_NAMESPACE);
		Element resourceEl = queryEl.addElement("resourcesInfo");

		resourceEl.addElement(ManagerPacketHelper.ID_EL).setText(resourcesInfo.getId());
		resourceEl.addElement(ManagerPacketHelper.CPU_IDLE).setText(resourcesInfo.getCpuIdle());
		resourceEl.addElement(ManagerPacketHelper.CPU_IN_USE).setText(resourcesInfo.getCpuInUse());
		resourceEl.addElement(ManagerPacketHelper.CPU_IN_USE_BY_USER).setText(resourcesInfo.getCpuInUseByUser());
		resourceEl.addElement(ManagerPacketHelper.INSTANCES_IDLE).setText(resourcesInfo.getInstancesIdle());
		resourceEl.addElement(ManagerPacketHelper.INSTANCES_IN_USE).setText(resourcesInfo.getInstancesInUse());
		resourceEl.addElement(ManagerPacketHelper.INSTANCES_IN_USE_BY_USER).setText(resourcesInfo.getInstancesInUseByUser());
		resourceEl.addElement(ManagerPacketHelper.MEM_IDLE).setText(resourcesInfo.getMemIdle());
		resourceEl.addElement(ManagerPacketHelper.MEM_IN_USE).setText(resourcesInfo.getMemInUse());
		resourceEl.addElement(ManagerPacketHelper.MEM_IN_USE_BY_USER).setText(resourcesInfo.getMemInUseByUser());
		return response;
	}

}
