package org.fogbowcloud.manager.occi.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.Resource;

public class Instance {

	private String PREFIX_DEFAULT_INSTANCE = "X-OCCI-Location: ";
	
	private String id;
	private String details;
	private List<Resource> resources;
	private Map<String, String> attributes;

	public Instance() {
	}
	
	public Instance parseIds(String textResponse) {
		Instance instance = new Instance();
		instance.setId(textResponse.replace(PREFIX_DEFAULT_INSTANCE , "").trim());
		return instance;
	}
	
	public Instance parseDetails(String textResponse) {
		
		String[] lines = textResponse.split("/n");
		for (String line : lines) {
			
			if (line.contains("Category:")) {
				String[] BlockLine = line.split(";");
				
				String[] blockValues;
				String term = ""; 
				String scheme = "";
				String catClass = "";
				String title = "";
				String rel = "";
				String location = "";
				List<String> attributes = new ArrayList<String>();
				List<String> actions = new ArrayList<String>(); 
				
				for (String block: BlockLine) {

					if(block.contains("Category:")) {
						blockValues = block.split(":");
						term = blockValues[1].trim();
					}else if(block.contains("Link:")) {
						//TODO create new class Link
					}
					else {
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
								attributes.add(attribute);
							}
						}else if (blockValues[0].contains("actions")) {
							String[] attributesValues = blockValues[1].replace("\"", "").split(" ");
							for (String action : attributesValues) {
								actions.add(action);
							}
						}
					}
				}				
				Category category = new Category(term, scheme, catClass);
				this.resources.add(new Resource(category, attributes, actions, location, title, rel));
//				new Resource(category, supportedAtt, actions, location, title, rel);
			}else if (line.contains("Link:")){
				
			}else if (line.contains("X-OCCI-Attribute:")){
				
			}
		}
	
		Instance instance = new Instance();
		instance.setDetails(textResponse);
		return instance;
	}	
	
	public String toOCCIMassageFormatLocation(){
		return PREFIX_DEFAULT_INSTANCE + this.id;
	}
	
	public String toOCCIMassageFormatDetails(){
		//TODO temporary
		return this.details;
	}	
	
	public void setId(String id) {
		this.id = id;
	}
	
	public void setDetails(String details) {
		this.details = details;
	}
	
	public String getDetails(){
		return this.details;
	}
}
