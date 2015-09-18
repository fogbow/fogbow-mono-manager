package org.fogbowcloud.manager.core.plugins.compute.azure;

public class AzureTestInstanceConfigurationSet {

	private String id;
	private String label;
	private String sizeName;

	public AzureTestInstanceConfigurationSet(String id) {
		this(id, AzureComputePlugin.AZURE_VM_DEFAULT_LABEL,
				TestAzureComputePlugin.FLAVOR_NAME_EXTRA_SMALL);
	}

	public AzureTestInstanceConfigurationSet(String id, String label,
			String sizeName) {
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
