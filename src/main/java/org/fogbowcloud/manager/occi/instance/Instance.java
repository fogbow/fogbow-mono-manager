package org.fogbowcloud.manager.occi.instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.Resource;

public class Instance {

	public static String PREFIX_DEFAULT_INSTANCE = "X-OCCI-Location: ";
	private static String PREFIX_DEFAULT_ATTRIBUTE = "X-OCCI-Attribute: ";
	private static String CATEGORY = "Category:";
	
	private String id;
	private List<Resource> resources;
	private Link link;
	private Map<String, String> attributes;

	public Instance() {
	}
	
	public Instance(String id) {
		this.id = id;
	}	
	
	public Instance(List<Resource> resources, Map<String, String> attributes, Link link) {
		this.resources = resources;
		this.attributes = attributes;
		this.link = link;
	}
	
	public static Instance parseInstanceId(String textResponse) {
		return new Instance(textResponse.replace(PREFIX_DEFAULT_INSTANCE , "").trim());
	}
	
	public static Instance parseInstanceDetails(String textResponse) {
		List<Resource> resources = new ArrayList<Resource>();
		Map<String, String> attributes = new HashMap<String, String>();
		Link link = null;
		
		String[] lines = textResponse.split("\n");
		for (String line : lines) {
			System.out.println("Line = " + line);
			if (line.contains("Category:")) {
				String[] blockLine = line.split(";");
				
				String[] blockValues;
				String term = ""; 
				String scheme = "";
				String catClass = "";
				String title = "";
				String rel = "";
				String location = "";
				List<String> attributesResource = new ArrayList<String>();
				List<String> actionsResource = new ArrayList<String>(); 
				
				for (String block: blockLine) {
					if(block.contains(CATEGORY)) {
						blockValues = block.split(":");
						term = blockValues[1].trim();
					} else {
						blockValues = block.split("=");
						if (blockValues[0].contains("scheme")) {
							scheme = blockValues[1].replace("\"", "").trim();
						}else if (blockValues[0].contains("class")) {
							catClass = blockValues[1].replace("\"", "").trim();
						}else if (blockValues[0].contains("title")) {
							title = blockValues[1].replace("\"", "").trim();
						}else if (blockValues[0].contains("rel")) {
							rel = blockValues[1].replace("\"", "").trim();
						}else if (blockValues[0].contains("location")) {
							location = blockValues[1].replace("\"", "").trim();
						}else if (blockValues[0].contains("attributes")) {
							String[] attributesValues = blockValues[1].replace("\"", "").split(" ");
							for (String attribute : attributesValues) {
								attributesResource.add(attribute);
							}
						}else if (blockValues[0].contains("actions")) {
							String[] actionsValues = blockValues[1].replace("\"", "").split(" ");
							for (String action : actionsValues) {
								actionsResource.add(action);
							}
						}
					}
				}
				Category category = new Category(term, scheme, catClass);
				resources.add(new Resource(category, attributesResource, actionsResource, location, title, rel));
			}else if (line.contains("Link")){
				link = Link.parseLink(line);
			}else if (line.contains("X-OCCI-Attribute: ")){
				
				String[] blockLine = line.replace(PREFIX_DEFAULT_ATTRIBUTE, "").split("=");
				attributes.put(blockLine[0], blockLine[1].replace("\"", "").trim());
			}
		}		
		return new Instance(resources, attributes, link);
	}	
	
	public String toOCCIMassageFormatLocation(){
		return PREFIX_DEFAULT_INSTANCE + this.id;
	}
	
	public String toOCCIMassageFormatDetails(){
		String messageFormat = "";
		for (Resource resource : this.resources) {
			messageFormat += CATEGORY + " " + resource.toHeader() + "\n";
		}
		if(link != null){
			messageFormat += this.link.toOCCIMessageFormatLink() + "\n";
		}
		for (String key : this.attributes.keySet()) {
			messageFormat += PREFIX_DEFAULT_ATTRIBUTE + key + "=\"" + attributes.get(key) + "\"\n"; 
		}
		
		return messageFormat;
	}	
	
	public void setId(String id) {
		this.id = id;
	}
	
	public List<Resource> getResources() {
		return resources;
	}

	public void setResources(List<Resource> resources) {
		this.resources = resources;
	}

	public Map<String, String> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, String> attributes) {
		this.attributes = attributes;
	}

	public String getId() {
		return id;
	}	
	
	public Link getLink() {
		return link;
	}

	public void setLink(Link link) {
		this.link = link;
	}	
	
	public static class Link {
		
		private static final String NAME_LINK = "Link:";
		
		private String link;
		private Map<String, String> itens; 
		
		public static Link parseLink(String line){
			Link link = new Link();
			Map<String, String> itens = new HashMap<String, String>();
			
			String[] blockLine = line.split(";");
			for (String block : blockLine) {
				if(block.contains(NAME_LINK)){
					String[] blockValues = block.split(":");
					link.setLink(blockValues[1].replace("\"", "").trim()); 
				}else{
					String[] blockValues = block.split("=");
					itens.put(blockValues[0].replace("\"", "").trim(), blockValues[1].replace("\"", "").trim());
				}
			}
			link.setItens(itens);
			return link;
		}
		
		public String toOCCIMessageFormatLink(){
			String itensMessageFormat = "";
			for (String key : this.itens.keySet()) {
				itensMessageFormat += " " + key + "=\"" + this.itens.get(key) + "\";";
			}
			return NAME_LINK + " " + this.link + ";" + itensMessageFormat;
		}
		
		public void setLink(String link) {
			this.link = link;
		}
		
		public void setItens(Map<String,String> itens){
			this.itens = itens;
		}
	}
}