package org.fogbowcloud.manager.core.federatednetwork;

import org.apache.commons.net.util.SubnetUtils;
import org.fogbowcloud.manager.core.model.FederationMember;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by arnett on 05/02/18.
 */
public class FederatedNetwork {

    private String label;
    private SubnetUtils.SubnetInfo subnetInfo;
    private Queue<String> freedIps;
    private int ipsServed;
    private Collection<FederationMember> allowedMembers;

    public FederatedNetwork(String label, String cidrNotation, Collection<FederationMember> allowedMembers) {
        this.freedIps = new LinkedList<String>();
        this.ipsServed = 0;

        this.label = label;
        this.subnetInfo = new SubnetUtils(cidrNotation).getInfo();
        this.allowedMembers = allowedMembers;
    }

    public String nextFreeIp() throws SubnetAddressesCapacityReachedException {
        if (freedIps.isEmpty()) {
            int lowAddress = subnetInfo.asInteger(subnetInfo.getLowAddress());
            int candidateIpAddress = lowAddress + ipsServed;
            if (!subnetInfo.isInRange(candidateIpAddress)) {
                throw new SubnetAddressesCapacityReachedException();
            } else {
                ipsServed++;
                return toIpAddress(candidateIpAddress);
            }
        } else {
            return freedIps.poll();
        }
    }

    public boolean isIpAddressFree(String address) {
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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

}
