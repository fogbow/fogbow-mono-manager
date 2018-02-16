package org.fogbowcloud.manager.core.federatednetwork;

import org.apache.commons.net.util.SubnetUtils;
import org.fogbowcloud.manager.core.model.FederationMember;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Created by arnett on 05/02/18.
 */
public class FederatedNetwork {

	private String id;
    private final String cidrNotation;
    private String label;
    private Set<FederationMember> allowedMembers;

    private int ipsServed;
    private Queue<String> freedIps;
    
    private Map<String, String> orderIpMap;
    
    public static final String NO_FREE_IPS_MESSAGE = "Subnet Addresses Capacity Reached, there isn't free IPs to attach";

    public FederatedNetwork(String id, String cidrNotation, String label, Set<FederationMember> allowedMembers) {
        // the reason for this to start at '1' is because the first ip is allocated
        // as the virtual ip address
        this.ipsServed = 1;
        this.freedIps = new LinkedList<String>();
        this.orderIpMap = new HashMap<String, String>();

        this.id = id;
        this.cidrNotation = cidrNotation;
        this.label = label;
        this.allowedMembers = allowedMembers;
    }

	public String nextFreeIp(String orderId) throws SubnetAddressesCapacityReachedException {
		if (this.orderIpMap.containsKey(orderId)) {
			return this.orderIpMap.get(orderId);
		}
		String freeIp = null;
		if (freedIps.isEmpty()) {
			SubnetUtils.SubnetInfo subnetInfo = this.getSubnetInfo();
			int lowAddress = subnetInfo.asInteger(subnetInfo.getLowAddress());
			int candidateIpAddress = lowAddress + ipsServed;
			if (!subnetInfo.isInRange(candidateIpAddress)) {
				throw new SubnetAddressesCapacityReachedException(
						FederatedNetwork.NO_FREE_IPS_MESSAGE);
			} else {
				ipsServed++;
				freeIp = toIpAddress(candidateIpAddress);
			}
		} else {
			freeIp = freedIps.poll();
		}
		this.orderIpMap.put(orderId, freeIp);
		return freeIp;
	}

    private SubnetUtils.SubnetInfo getSubnetInfo() {
        return new SubnetUtils(cidrNotation).getInfo();
    }

    public boolean isIpAddressFree(String address) {
    	SubnetUtils.SubnetInfo subnetInfo = this.getSubnetInfo();
        if (subnetInfo.isInRange(address)) {
            if (freedIps.contains(address)) {
                return true;
            } else {
                int lowAddress = subnetInfo.asInteger(subnetInfo.getLowAddress());
                if (subnetInfo.asInteger(address) >= lowAddress + ipsServed) {
                    return true;
                }
            }
        }

        return false;
    }

    public void freeIp(String ipAddress) {
        if (isIpAddressFree(ipAddress)) {
            // TODO Signal the caller that it tried to free an already free address
            return;
        }

        freedIps.add(ipAddress);
    }

    private String toIpAddress(int value) {
        byte[] bytes = BigInteger.valueOf(value).toByteArray();
        try {
            InetAddress address = InetAddress.getByAddress(bytes);
            return address.toString().replaceAll("/", "");
        } catch (UnknownHostException e) {
            return null;
        }
    }
    
    public void addFederationNetworkMember(FederationMember member) {
    	this.allowedMembers.add(member);
    }
    
    public String getLabel() {
        return label;
    }

    public String getId() {
        return id;
    }

    public String getCidr() {
        return getSubnetInfo().getCidrSignature();
    }

    public Set<FederationMember> getAllowedMembers() {
        return allowedMembers;
    }
    
    @Override
	public String toString() {
		return "FederatedNetwork [id=" + id + ", cidrNotation=" + cidrNotation + ", label=" + label
				+ ", allowedMembers=" + allowedMembers + ", ipsServed=" + ipsServed + ", freedIps="
				+ freedIps + ", orderIpMap=" + orderIpMap + "]";
	}
    
    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FederatedNetwork other = (FederatedNetwork) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
}
