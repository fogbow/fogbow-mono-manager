package org.fogbowcloud.manager.xmpp;

import java.util.HashMap;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository.StorageLink;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;

public class RemoveStorageLinkHandler extends AbstractQueryHandler {

	private ManagerController facade;

	public RemoveStorageLinkHandler(ManagerController facade) {
		super(ManagerXmppComponent.REMOVESTORAGELINK_NAMESPACE);
		this.facade = facade;
	}

	@Override
	public IQ handle(IQ query) {
		Element queryEl = query.getElement().element("query");
		Element storageEl = queryEl.element(ManagerPacketHelper.STORAGE_LINK_EL);
		
		String id = storageEl.elementText(ManagerPacketHelper.ID_EL);
		String storageId = storageEl.elementText(ManagerPacketHelper.TARGET_EL);
		String instanceId = storageEl.elementText(ManagerPacketHelper.SOURCE_EL);

		Element tokenEl = queryEl.element(ManagerPacketHelper.TOKEN_EL);
		Token userToken = null;
		if (tokenEl != null) {
			userToken = new Token(tokenEl.elementText(ManagerPacketHelper.ACCESS_ID_EL),
					tokenEl.elementText(ManagerPacketHelper.USER_EL), null, new HashMap<String, String>());
		}
		
		IQ response = IQ.createResultIQ(query);
		try {
			StorageLink storageLink = new StorageLink(id, instanceId, storageId, "");
			facade.detachStorage(storageLink,facade.getLocalIdentityPlugin().createToken(
							facade.getMapperPlugin().getLocalCredentials(userToken.getAccessId())));	
		} catch (OCCIException e) {
			response.setError(ManagerPacketHelper.getCondition(e));
		}
		return response;
	}
}
