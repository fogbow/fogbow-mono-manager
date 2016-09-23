package org.fogbowcloud.manager.xmpp;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.jamppa.component.handler.AsyncQueryHandler;
import org.xmpp.packet.IQ;

public class OrderInstanceHandler extends AsyncQueryHandler {

	private ManagerController facade;

	public OrderInstanceHandler(ManagerController facade) {
		super(ManagerXmppComponent.ORDER_NAMESPACE);
		this.facade = facade;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void handleAsync(IQ iq) {
		Element queryEl = iq.getElement().element("query");
		List<Category> categories = new LinkedList<Category>();
		List<Element> categoriesEl = queryEl.elements("category");
		for (Element categoryEl : categoriesEl) {
			Category category = new Category(categoryEl.elementText("term"),
					categoryEl.elementText("scheme"), categoryEl.elementText("class"));
			categories.add(category);
		}
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		List<Element> attributesEl = queryEl.elements("attribute");
		for (Element attributeEl : attributesEl) {
			xOCCIAtt.put(attributeEl.attributeValue("var"), attributeEl.element("value").getText());
		}	
		
		String orderId = queryEl.element("request").element("id").getText();

		Element tokenEl = queryEl.element("token");
		Token userToken = null;
		if (tokenEl != null) {
			Element userEl = tokenEl.element("user");
			userToken = new Token(tokenEl.elementText("accessId"), new Token.User(userEl.elementText("id"), 
					userEl.elementText("name")), null, new HashMap<String, String>());
		}
		facade.queueServedOrder(iq.getFrom().toBareJID(), categories, xOCCIAtt, orderId,
				userToken);
	}
}
