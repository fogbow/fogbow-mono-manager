package org.fogbowcloud.manager.xmpp;

import java.util.HashMap;
import java.util.Map;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.storage.StorageAttribute;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;

public class StorageLinkHandler extends AbstractQueryHandler {

	public static final String ID = "id";
	public static final String ATTACHMENT = "attachment";
	private ManagerController facade;

	public StorageLinkHandler(ManagerController facade) {
		super(ManagerXmppComponent.STORAGE_LINK_NAMESPACE);
		this.facade = facade;
	}

	@Override
	public IQ handle(IQ iq) {
		Element queryEl = iq.getElement().element("query");
		
		Element storageLinkEl = queryEl.element(ManagerPacketHelper.STORAGE_LINK_EL); 
		String source = storageLinkEl.element(ManagerPacketHelper.SOURCE_EL).getText();
		String deviceId = storageLinkEl.element(ManagerPacketHelper.DEVICE_ID_EL).getText();
		String target = storageLinkEl.element(ManagerPacketHelper.TARGET_EL).getText();				

		Element tokenEl = queryEl.element(ManagerPacketHelper.TOKEN_EL);
		Token userToken = null;
		if (tokenEl != null) {
			userToken = new Token(tokenEl.elementText(ManagerPacketHelper.ACCESS_ID_EL),
					tokenEl.elementText(ManagerPacketHelper.USER_EL), null, new HashMap<String, String>());
		}
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(StorageAttribute.TARGET.getValue(), target);
		xOCCIAtt.put(StorageAttribute.SOURCE.getValue(), source);
		xOCCIAtt.put(StorageAttribute.DEVICE_ID.getValue(), deviceId);
		
		IQ response = IQ.createResultIQ(iq);
		String attachmentId = null;
		try {
			attachmentId = facade.attachStorage(null, xOCCIAtt, facade.getLocalIdentityPlugin().createToken(
					facade.getMapperPlugin().getLocalCredentials(userToken.getAccessId())));
		} catch (OCCIException e) {
			response.setError(ManagerPacketHelper.getCondition(e));
			return response;
		}		

		Element queryResponseEl = response.getElement().addElement("query", 
				ManagerXmppComponent.STORAGE_LINK_NAMESPACE);
		Element attachmentEl = queryResponseEl.addElement(ATTACHMENT);
		attachmentEl.addElement("id").setText(attachmentId);		
		
		return response;
	}
}
