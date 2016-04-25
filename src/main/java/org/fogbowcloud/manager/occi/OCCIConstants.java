package org.fogbowcloud.manager.occi;

public class OCCIConstants {

	public static final String ID = "occi.core.id";
	public static final String TITLE = "occi.core.title";
	
	public static final String NETWORK_VLAN = "occi.network.vlan";
	public static final String NETWORK_LABEL = "occi.network.label";
	public static final String NETWORK_STATE = "occi.network.state";
	public static final String NETWORK_ADDRESS = "occi.network.address";
	public static final String NETWORK_GATEWAY = "occi.network.gateway";
	public static final String NETWORK_ALLOCATION = "occi.network.allocation";
	
	public enum NetworkState {
		ACTIVE("active"), INACTIVE("inactive");
		
		private String value;
		
		NetworkState(String value) {
			this.value = value;
		}
		
		public String getValue() {
			return value;
		}
	}
	
	public enum NetworkAllocation {
		DYNAMIC("dynamic"), STATIC("static");
		
		private String value;
		
		NetworkAllocation(String value) {
			this.value = value;
		}
		
		public String getValue() {
			return value;
		}
	}
	
}
