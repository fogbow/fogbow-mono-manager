package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError.Condition;

public class GetInstanceHandler extends AbstractQueryHandler {

	private ManagerController facade;

	public GetInstanceHandler(ManagerController facade) {
		super(ManagerXmppComponent.GETINSTANCE_NAMESPACE);
		this.facade = facade;
	}

	@Override
	public IQ handle(IQ query) {
		String instanceId = query.getElement().element("query")
				.element("instance").elementText("id");
		
		Instance instance = null;
		IQ response = IQ.createResultIQ(query);
		
		try {
			instance = facade.getInstanceForRemoteMember(instanceId);
		} catch (OCCIException e) {
			response.setError(ManagerPacketHelper.getCondition(e));
			return response;
		}
		
		if (instance == null) {
			response.setError(Condition.item_not_found);
			return response;
		}
		
		Element queryEl = response.getElement().addElement("query", 
				ManagerXmppComponent.GETINSTANCE_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("state").setText(instance.getState() != null ? instance.getState().toString() : "null");
		instanceEl.addElement("id").setText(instanceId);
		
		List<Link> links = instance.getLinks();
		for (Link link : links) {
			Element linkEl = instanceEl.addElement("link");
			linkEl.addElement("link").setText(link.getName());
			for (Entry<String, String> linkAtt : link.getAttributes().entrySet()) {
				Element attributeEl = linkEl.addElement("attribute");
				attributeEl.addAttribute("val", linkAtt.getKey());
				attributeEl.setText(linkAtt.getValue());
			}
		}
		
		List<Resource> resources = instance.getResources();
		for (Resource resource : resources != null ? resources : new ArrayList<Resource>()) {
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
