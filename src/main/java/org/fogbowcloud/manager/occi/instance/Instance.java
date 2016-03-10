package org.fogbowcloud.manager.occi.instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.JSONHelper;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Resource;
import org.json.JSONException;
import org.json.JSONObject;

public class Instance {

	public static final String PREFIX_DEFAULT_INSTANCE = "X-OCCI-Location: ";
	public static final String SSH_PUBLIC_ADDRESS_ATT = "org.fogbowcloud.request.ssh-public-address";
	public static final String LOCAL_IP_ADDRESS_ATT = "org.fogbowcloud.request.local-ip-address";
	public static final String SSH_USERNAME_ATT = "org.fogbowcloud.request.ssh-username";
	public static final String EXTRA_PORTS_ATT = "org.fogbowcloud.request.extra-ports";

	private static final String PREFIX_DEFAULT_ATTRIBUTE = "X-OCCI-Attribute: ";
	private static final String CATEGORY = "Category:";

	private String id;
	private List<Resource> resources;
	private List<Link> links;
	private InstanceState state = InstanceState.PENDING;
	/**
	 * Attributes: - occi.core.id - occi.compute.state - occi.compute.speed -
	 * occi.compute.cores - occi.compute.hostname - occi.compute.memory
	 */
	private Map<String, String> attributes;

	public Instance(String id) {
		this.id = id;
	}

	public Instance(String id, List<Resource> resources, Map<String, String> attributes,
			List<Link> links, InstanceState instanceState) {
		this(id);
		this.resources = resources;
		this.attributes = attributes;
		this.links = links;
		this.state = instanceState;
	}

	public InstanceState getState() {
		return state;
	}

	public static Instance parseInstance(String textResponse) {
		return new Instance(textResponse.replace(PREFIX_DEFAULT_INSTANCE, "").trim());
	}

	// TODO refactor it
	public static Instance parseInstance(String id, String textResponse) {
		List<Resource> resources = new ArrayList<Resource>();
		Map<String, String> attributes = new LinkedHashMap<String, String>();
		List<Link> links = new ArrayList<Link>();

		String[] lines = textResponse.split("\n");
		for (String line : lines) {
			if (line.contains("Category:")) {
				String[] blockLine = line.split(";");
				Map<String, String> blocks = new HashMap<String, String>();

				for (String block : blockLine) {
					if (block.contains(CATEGORY)) {
						String[] blockValues = block.split(":");
						blocks.put("term", blockValues[1].trim());
					} else {
						String[] blockValues = block.split("=");
						blocks.put(blockValues[0].trim(), blockValues[1].replace("\"", "").trim());
					}
				}
				List<String> attributesResource = new ArrayList<String>();
				List<String> actionsResource = new ArrayList<String>();
				String scheme = emptyIfNull(blocks.get("scheme"));
				String catClass = emptyIfNull(blocks.get("class"));
				String title = emptyIfNull(blocks.get("title"));
				String rel = emptyIfNull(blocks.get("rel"));
				String location = emptyIfNull(blocks.get("location"));
				String term = emptyIfNull(blocks.get("term"));

				String attributeBlocks = blocks.get("attributes");
				if (attributeBlocks != null) {
					for (String attribute : attributeBlocks.split(" ")) {
						attributesResource.add(attribute);
					}
				}

				String actionBlocks = blocks.get("actions");
				if (actionBlocks != null) {
					for (String action : actionBlocks.split(" ")) {
						actionsResource.add(action);
					}
				}

				Category category = new Category(term, scheme, catClass);
				resources.add(new Resource(category, attributesResource, actionsResource, location,
						title, rel));
			} else if (line.contains("Link")) {
				links.add(Link.parseLink(line));
			} else if (line.contains("X-OCCI-Attribute: ")) {
				String[] blockLine = line.replace(PREFIX_DEFAULT_ATTRIBUTE, "").split("=");
				attributes.put(blockLine[0], blockLine[1].replace("\"", "").trim());
			}
		}
		return new Instance(id, resources, attributes, links,
				InstanceState.fromOCCIState(attributes.get("occi.compute.state")));
	}

	private static String emptyIfNull(String input) {
		return input == null ? new String() : input;
	}

	public String toOCCIMessageFormatLocation() {
		return PREFIX_DEFAULT_INSTANCE + this.id;
	}

	public String toOCCIMessageFormatDetails() {
		String messageFormat = "";
		if (resources != null) {
			for (Resource resource : this.resources) {
				messageFormat += CATEGORY + " " + resource.toHeader() + "\n";
			}
		}
		if (links != null) {
			for (Link link : links) {
				messageFormat += link.toOCCIMessageFormatLink() + "\n";
			}
		}
		if (attributes != null) {
			for (String key : this.attributes.keySet()) {
				messageFormat += PREFIX_DEFAULT_ATTRIBUTE + key + "=\"" + attributes.get(key)
						+ "\"\n";
			}
		}

		return messageFormat.trim();
	}

	public List<Resource> getResources() {
		return resources;
	}

	public void addResource(Resource resource) {
		resources.add(resource);
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

	public List<Link> getLinks() {
		return links;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Instance) {
			Instance otherInst = (Instance) obj;
			return getId().equals(otherInst.getId());
		}
		return false;
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
					if (blockValues.length == 2) {
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
			HashMap<String, String> attrsCopy = new HashMap<String, String>(attributes);
			int numberOfAttrs = attrsCopy.size();
			String relAttr = attrsCopy.remove("rel");
			if (relAttr != null) {
				itensMessageFormat += " rel=\"" + relAttr + "\";";
				cont++;
			}

			String selfAttr = attrsCopy.remove("self");
			if (selfAttr != null) {
				itensMessageFormat += " self=\"" + selfAttr + "\";";
				cont++;
			}

			String categoryAttr = attrsCopy.remove("category");
			if (categoryAttr != null) {
				itensMessageFormat += " category=\"" + categoryAttr + "\";";
				cont++;
			}

			for (String key : attrsCopy.keySet()) {
				itensMessageFormat += " " + key + "=\"" + attrsCopy.get(key) + "\"";
				if (cont < numberOfAttrs - 1) {
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

		public JSONObject toJSON() throws JSONException {
			return new JSONObject().put("name", name).put("attributes", attributes.toString());
		}

		public static Link fromJSON(String linkJSON) throws JSONException {
			JSONObject jsonObject = new JSONObject(linkJSON);
			return new Link(jsonObject.optString("name"), JSONHelper.toMap(jsonObject
					.optString("attributes")));
		}
	}

}
