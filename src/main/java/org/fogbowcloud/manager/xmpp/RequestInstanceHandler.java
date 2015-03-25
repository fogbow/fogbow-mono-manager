package org.fogbowcloud.manager.xmpp;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.Token;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError.Condition;

public class RequestInstanceHandler extends AbstractQueryHandler {

	private ManagerController facade;

	public RequestInstanceHandler(ManagerController facade) {
		super(ManagerXmppComponent.REQUEST_NAMESPACE);
		this.facade = facade;
	}

	@SuppressWarnings("unchecked")
	@Override
	public IQ handle(IQ query) {
		Element queryEl = query.getElement().element("query");
		List<Category> categories = new LinkedList<Category>();
		List<Element> categoriesEl = queryEl.elements("category");
		for (Element categoryEl : categoriesEl) {
			Category category = new Category(categoryEl.elementText("term"),
					categoryEl.elementText("scheme"),
					categoryEl.elementText("class"));
			categories.add(category);
		}
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		List<Element> attributesEl = queryEl.elements("attribute");
		for (Element attributeEl : attributesEl) {
			xOCCIAtt.put(attributeEl.attributeValue("var"), attributeEl
					.element("value").getText());
		}
		Element tokenEl = queryEl.element("token");
		Token userToken = null;
		if (tokenEl != null) {
			userToken = new Token(tokenEl.elementText("accessId"), 
					tokenEl.elementText("user"), 
					null, new HashMap<String, String>());
		}

		IQ response = IQ.createResultIQ(query);
		try {
			String instanceId = facade.createInstanceWithFederationUser(query
					.getFrom().toBareJID(), categories, xOCCIAtt, null, userToken);
			if (instanceId == null) {
				response.setError(Condition.item_not_found);
			} else {
				Element queryResponseEl = response.getElement().addElement(
						"query", ManagerXmppComponent.REQUEST_NAMESPACE);
				queryResponseEl.addElement("instance").addElement("id")
						.setText(instanceId);
			}
		} catch (OCCIException e) {
			response.setError(ManagerPacketHelper.getCondition(e));
		}
		return response;
	}
}
