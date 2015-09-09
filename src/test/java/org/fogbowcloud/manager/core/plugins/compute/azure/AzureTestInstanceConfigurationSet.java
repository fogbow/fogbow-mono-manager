package org.fogbowcloud.manager.core.plugins.compute.azure;

public class AzureTestInstanceConfigurationSet {
	
	public static final String DEFAULT_SIZE_NAME = "Standard_D1";
	
	private String id;
	private String label;
	private String sizeName;
	
	public  AzureTestInstanceConfigurationSet(String id) {
		this(id, AzureComputePlugin.AZURE_VM_DEFAULT_LABEL, DEFAULT_SIZE_NAME);
	}
	
	public  AzureTestInstanceConfigurationSet(String id, String label, String sizeName) {
		this.id = id;
		this.label = label;
		this.sizeName = sizeName;
	}
	
	public String getId() {
		return id;
	}
	
	public String getLabel() {
		return label;
	}
	
	public String getSizeName() {
		return sizeName;
	}
}
