package org.fogbowcloud.manager.occi;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class OCCIConstants {

	public static final String ID = "occi.core.id";
	public static final String TITLE = "occi.core.title";
	
	public static final String NETWORK_VLAN = "occi.network.vlan";
	public static final String NETWORK_LABEL = "occi.network.label";
	public static final String NETWORK_STATE = "occi.network.state";
	public static final String NETWORK_ADDRESS = "occi.network.address";
	public static final String NETWORK_GATEWAY = "occi.network.gateway";
	public static final String NETWORK_ALLOCATION = "occi.network.allocation";
	
	public static final String NETWORK_INTERFACE_MAC = "occi.networkinterface.mac";
	public static final String NETWORK_INTERFACE_INTERFACE = "occi.networkinterface.interface";
	public static final String NETWORK_INTERFACE_STATE = "occi.networkinterface.state";
	
	public static final String NETWORK_TYPE = "network";
	
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
	
	public static List<String> getValues() {	
		List<String> values = new ArrayList<String>();		
		
		Field[] fields = OCCIConstants.class.getDeclaredFields();
	    for(Field f: fields) {
	    	f.setAccessible(true);
	    	try {
	    		values.add((String) f.get(null));
	    	} catch (Exception e) {
	    		continue;
	    	}
	    }
	    return values;
		
	}
}
