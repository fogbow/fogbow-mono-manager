package org.fogbowcloud.manager.xmpp;

import java.util.List;
import java.util.Map.Entry;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerFacade;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError.Condition;

public class GetInstanceHandler extends AbstractQueryHandler {

	private ManagerFacade facade;

	public GetInstanceHandler(ManagerFacade facade) {
		super(ManagerXmppComponent.GETINSTANCE_NAMESPACE);
		this.facade = facade;
	}

	@Override
	public IQ handle(IQ query) {
		String instanceId = query.getElement().element("instance")
				.elementText("id");
		Instance instance = facade.getInstanceForRemoteMember(instanceId);
		
		IQ response = IQ.createResultIQ(query);
		if (instance == null) {
			response.setError(Condition.item_not_found);
			return response;
		}
		
		Element queryEl = response.getElement().addElement("query", 
				ManagerXmppComponent.GETINSTANCE_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText(instanceId);
		
		Element linkEl = instanceEl.addElement("link");
		Link link = instance.getLink();
		linkEl.addElement("link").setText(link.getName());
		for (Entry<String, String> linkAtt : link.getAttributes().entrySet()) {
			Element attributeEl = linkEl.addElement("attribute");
			attributeEl.addAttribute("val", linkAtt.getKey());
			attributeEl.setText(linkAtt.getValue());
		}
		
		List<Resource> resources = instance.getResources();
		for (Resource resource : resources) {
			Element resourceEl = instanceEl.addElement("resource");
			Element categoryEl = resourceEl.addElement("category");
			
			Category category = resource.getCategory();
			categoryEl.addElement("term").setText(category.getTerm());
			categoryEl.addElement("scheme").setText(category.getScheme());
			categoryEl.addElement("class").setText(category.getCatClass());
			for (String attribute : resource.getAttributes()) {
				categoryEl.addElement("attribute").setText(attribute);
			}
			for (String action : resource.getActions()) {
				categoryEl.addElement("action").setText(action);
			}
			
			resourceEl.addElement("location").setText(resource.getLocation());
			resourceEl.addElement("title").setText(resource.getTitle());
			resourceEl.addElement("rel").setText(resource.getRel());
		}
		
		for (Entry<String, String> instanceAtt : instance.getAttributes().entrySet()) {
			Element attributeEl = instanceEl.addElement("attribute");
			attributeEl.addAttribute("val", instanceAtt.getKey());
			attributeEl.setText(instanceAtt.getValue());
		}
		
		return response;
	}

}
