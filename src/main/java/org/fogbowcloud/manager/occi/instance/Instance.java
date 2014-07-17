package org.fogbowcloud.manager.occi.instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

	public Instance(String id) {
		this.id = id;
	}

	public Instance(String id, List<Resource> resources, Map<String, String> attributes, Link link) {
		this(id);
		this.resources = resources;
		this.attributes = attributes;
		this.link = link;
	}

	public static Instance parseInstance(String textResponse) {
		return new Instance(textResponse.replace(PREFIX_DEFAULT_INSTANCE, "").trim());
	}

	public static Instance parseInstance(String id, String textResponse) {
		List<Resource> resources = new ArrayList<Resource>();
		Map<String, String> attributes = new LinkedHashMap<String, String>();
		Link link = null;

		String[] lines = textResponse.split("\n");
		for (String line : lines) {
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

				for (String block : blockLine) {
					if (block.contains(CATEGORY)) {
						blockValues = block.split(":");
						term = blockValues[1].trim();
					} else {
						blockValues = block.split("=");
						if (blockValues[0].contains("scheme")) {
							scheme = blockValues[1].replace("\"", "").trim();
						} else if (blockValues[0].contains("class")) {
							catClass = blockValues[1].replace("\"", "").trim();
						} else if (blockValues[0].contains("title")) {
							title = blockValues[1].replace("\"", "").trim();
						} else if (blockValues[0].contains("rel")) {
							rel = blockValues[1].replace("\"", "").trim();
						} else if (blockValues[0].contains("location")) {
							location = blockValues[1].replace("\"", "").trim();
						} else if (blockValues[0].contains("attributes")) {
							String[] attributesValues = blockValues[1].replace("\"", "").split(" ");
							for (String attribute : attributesValues) {
								attributesResource.add(attribute);
							}
						} else if (blockValues[0].contains("actions")) {
							String[] actionsValues = blockValues[1].replace("\"", "").split(" ");
							for (String action : actionsValues) {
								actionsResource.add(action);
							}
						}
					}
				}
				Category category = new Category(term, scheme, catClass);
				resources.add(new Resource(category, attributesResource, actionsResource, location,
						title, rel));
			} else if (line.contains("Link")) {
				link = Link.parseLink(line);
			} else if (line.contains("X-OCCI-Attribute: ")) {

				String[] blockLine = line.replace(PREFIX_DEFAULT_ATTRIBUTE, "").split("=");
				attributes.put(blockLine[0], blockLine[1].replace("\"", "").trim());
			}
		}
		return new Instance(id, resources, attributes, link);
	}

	public String toOCCIMessageFormatLocation() {
		return PREFIX_DEFAULT_INSTANCE + this.id;
	}

	public String toOCCIMessageFormatDetails() {
		String messageFormat = "";
		for (Resource resource : this.resources) {
			messageFormat += CATEGORY + " " + resource.toHeader() + "\n";
		}
		if (link != null) {
			messageFormat += this.link.toOCCIMessageFormatLink() + "\n";
		}
		for (String key : this.attributes.keySet()) {
			messageFormat += PREFIX_DEFAULT_ATTRIBUTE + key + "=\"" + attributes.get(key) + "\"\n";
		}

		return messageFormat.trim();
	}

	public List<Resource> getResources() {
		return resources;
	}

	public Map<String, String> getAttributes() {
		return attributes;
	}
	
	public void addAttribute(String key, String value) {
		if (attributes == null) {
			attributes = new HashMap<String, String>();
		}
		attributes.put(key, value);
	}

	public String getId() {
		return id;
	}

	public Link getLink() {
		return link;
	}

	public static class Link {

		private static final String NAME_LINK = "Link:";

		private String name;
		private Map<String, String> attributes;

		public Link() {
		}

		public Link(String name, Map<String, String> attributes) {
			this.name = name;
			this.attributes = attributes;
		}

		public static Link parseLink(String line) {
			Link link = new Link();
			Map<String, String> itens = new LinkedHashMap<String, String>();

			String[] blockLine = line.split(";");
			for (String block : blockLine) {
				if (block.contains(NAME_LINK)) {
					String[] blockValues = block.split(":");
					link.setName(blockValues[1].replace("\"", "").trim());
				} else {
					String[] blockValues = block.split("=");
					if (blockValues.length == 2){
						itens.put(blockValues[0].replace("\"", "").trim(),
								blockValues[1].replace("\"", "").trim());
					}
				}
			}
			link.setAttributes(itens);
			return link;
		}

		public String toOCCIMessageFormatLink() {
			String itensMessageFormat = "";
			int cont = 0;
			for (String key : this.attributes.keySet()) {
				itensMessageFormat += " " + key + "=\"" + this.attributes.get(key) + "\"";				
				if(cont < this.attributes.keySet().size() - 1){
					itensMessageFormat += ";";						
				}	
				cont++;
			}
			return NAME_LINK + " " + this.name + ";" + itensMessageFormat;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setAttributes(Map<String, String> attributes) {
			this.attributes = attributes;
		}

		public Map<String, String> getAttributes() {
			return attributes;
		}
	}
}
