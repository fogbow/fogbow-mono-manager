package org.fogbowcloud.manager.xmpp;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;

public class GetRemoteUserQuotaHandler extends AbstractQueryHandler {

	private ManagerController facade;

	public GetRemoteUserQuotaHandler(ManagerController facade) {
		super(ManagerXmppComponent.GETREMOTEUSERQUOTA_NAMESPACE);
		this.facade = facade;
	}

	@Override
	public IQ handle(IQ query) {

		String accessId = query.getElement().element("query").element("token").elementText("accessId");

		IQ response = IQ.createResultIQ(query);
		ResourcesInfo resourcesInfo = facade.getResourceInfoForRemoteMember(accessId);
		if (resourcesInfo == null) {
			return response;
		}

		Element queryEl = response.getElement().addElement("query", ManagerXmppComponent.GETREMOTEUSERQUOTA_NAMESPACE);
		Element resourceEl = queryEl.addElement("resourcesInfo");

		resourceEl.addElement("id").setText(resourcesInfo.getId());
		resourceEl.addElement("cpuIdle").setText(resourcesInfo.getCpuIdle());
		resourceEl.addElement("cpuInUse").setText(resourcesInfo.getCpuInUse());
		resourceEl.addElement("instancesIdle").setText(resourcesInfo.getInstancesIdle());
		resourceEl.addElement("instancesInUse").setText(resourcesInfo.getInstancesInUse());
		resourceEl.addElement("memIdle").setText(resourcesInfo.getMemIdle());
		resourceEl.addElement("memInUse").setText(resourcesInfo.getMemInUse());
		return response;
	}

}
