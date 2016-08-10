package org.fogbowcloud.manager.core.plugins.network.azure;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.windowsazure.management.network.models.NetworkListResponse;
import com.microsoft.windowsazure.management.network.models.NetworkListResponse.Subnet;
import com.microsoft.windowsazure.management.network.models.NetworkListResponse.VirtualNetworkSite;

public class NetworkConfigModel {
	
	private final String KEY_VIRTUAL_SITES = ":VIRTUAL_SITES";
	
	private final String KEY_NETWORK_NAME = ":NETWORK_NAME";
	private final String KEY_SUBNETWORK_NAME = ":SUBNETWORK_NAME";
	private final String KEY_NETWORK_ADDRESS =":NETWORK_ADDRESS";
	private final String KEY_NETWORK_REGION =":NETWORK_REGION";
	private final String KEY_ADDRESS_PREFIX =":ADDRESS_PREFIX";
	private final String KEY_SUBNETWORKS =":SUBNETWORKS";
	
	private final String TAG_ADRESS_PREFIX ="<AddressPrefix>"+KEY_NETWORK_ADDRESS+"</AddressPrefix>";
	private final String TAG_SUBNET = "<Subnet name=\""+KEY_SUBNETWORK_NAME+"\">"+TAG_ADRESS_PREFIX+"</Subnet>";
	
	private List<VirtualNetworkSite> sirtualNetworkSites = new ArrayList<VirtualNetworkSite>();
	
	private StringBuilder virtualNetworkSiteModel = new StringBuilder();
	private StringBuilder networkConfigurationModel = new StringBuilder();
	
	public NetworkConfigModel(NetworkListResponse networkListResponse){
		
		buildVirtualNetworkSiteModel();
		buildNetworkConfigurationModel();
		
		sirtualNetworkSites = networkListResponse.getVirtualNetworkSites();
	}
	
	public void addVirtualNetworkSite(VirtualNetworkSite virtualNetworkSite){
		sirtualNetworkSites.add(virtualNetworkSite);
	}
	
	public boolean removeVirtualNetworkSite(String virtualNetworkName){
		
		VirtualNetworkSite virtualNetworkSiteRemove = null;
		
		for (VirtualNetworkSite virtualNetworkSite : sirtualNetworkSites) {
			
			if(virtualNetworkSite.getName().equalsIgnoreCase(virtualNetworkName)){
				virtualNetworkSiteRemove = virtualNetworkSite;
				break;
			}
			
		}
		
		if(virtualNetworkSiteRemove != null){
			sirtualNetworkSites.remove(virtualNetworkSiteRemove);
			return true;
		}
		return false;
		
	}
	
	public String toString(){
		
		StringBuilder virtualNetworkSitesTags = new StringBuilder();
		
		for (VirtualNetworkSite virtualNetworkSite : sirtualNetworkSites) {
			
			String networkSiteModel = virtualNetworkSiteModel.toString();
			
			networkSiteModel = networkSiteModel.replaceAll(KEY_NETWORK_NAME, virtualNetworkSite.getName());
			networkSiteModel = networkSiteModel.replaceAll(KEY_NETWORK_REGION, virtualNetworkSite.getLocation());
			
			//Making address spaces TAG
			ArrayList<String> addressPrefixes = virtualNetworkSite.getAddressSpace().getAddressPrefixes();
			StringBuilder addressesTags = new StringBuilder();
			for(String address : addressPrefixes){
				if(addressesTags.length() < 1){
					addressesTags.append("\n");	
				}
				addressesTags.append(TAG_ADRESS_PREFIX.replaceAll(KEY_NETWORK_ADDRESS, address));
			}
			if(addressesTags.length() > 0){
				networkSiteModel = networkSiteModel.replaceAll(KEY_ADDRESS_PREFIX, addressesTags.toString());
			}
			
			//Making Subnet TAG
			ArrayList<Subnet> subnets = virtualNetworkSite.getSubnets();
			StringBuilder subnetsTags = new StringBuilder();
			for(Subnet subnet : subnets){
				if(subnetsTags.length() < 1){
					subnetsTags.append("\n");	
				}
				subnetsTags.append(TAG_SUBNET
						.replaceAll(KEY_NETWORK_ADDRESS, subnet.getAddressPrefix())
						.replaceAll(KEY_SUBNETWORK_NAME, subnet.getName()));
				
			}
			if(subnetsTags.length() > 0){
				networkSiteModel = networkSiteModel.replaceAll(KEY_SUBNETWORKS, subnetsTags.toString());
			}
			
			virtualNetworkSitesTags.append(networkSiteModel);
			
		}
		
		return networkConfigurationModel.toString().replaceAll(KEY_VIRTUAL_SITES, virtualNetworkSitesTags.toString());
		
	}
	
	private void buildNetworkConfigurationModel() {
		
		networkConfigurationModel.append("<NetworkConfiguration");
		networkConfigurationModel.append(" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"");
		networkConfigurationModel.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
		networkConfigurationModel.append(" xmlns=\"http://schemas.microsoft.com/ServiceHosting/2011/07/NetworkConfiguration\">\n");
		networkConfigurationModel.append(" <VirtualNetworkConfiguration>\n");
		networkConfigurationModel.append(" 	<Dns/>\n");
		networkConfigurationModel.append(" 	<VirtualNetworkSites>\n");
		networkConfigurationModel.append(KEY_VIRTUAL_SITES+"\n");
		networkConfigurationModel.append(" 	</VirtualNetworkSites>\n");
		networkConfigurationModel.append(" </VirtualNetworkConfiguration>\n");
		networkConfigurationModel.append("</NetworkConfiguration>\n");
	}
	
	private void buildVirtualNetworkSiteModel() {
	
		virtualNetworkSiteModel.append(" 	 <VirtualNetworkSite name=\""+KEY_NETWORK_NAME+"\" Location=\""+KEY_NETWORK_REGION+"\">\n");
		virtualNetworkSiteModel.append(" 	  <AddressSpace>\n");
		virtualNetworkSiteModel.append(" 	  	"+KEY_ADDRESS_PREFIX+"\n");
		virtualNetworkSiteModel.append(" 	  </AddressSpace>\n");
		virtualNetworkSiteModel.append(" 	  <Subnets>\n");
		virtualNetworkSiteModel.append(" 	  	"+KEY_SUBNETWORKS+"\n");
		virtualNetworkSiteModel.append(" 	  </Subnets>\n");
		virtualNetworkSiteModel.append(" 	 </VirtualNetworkSite>\n");
	}

}
