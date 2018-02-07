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

    private String id;
    private int ipsServed;
    private Queue<String> freedIps;
    private String label;
    private Collection<FederationMember> allowedMembers;

    private SubnetUtils.SubnetInfo subnetInfo;

    public FederatedNetwork(String id, String cidrNotation, String label, Collection<FederationMember> allowedMembers) {
        this.subnetInfo = new SubnetUtils(cidrNotation).getInfo();

        // the reason for this to start at '1' is because the first ip is allocated
        // as the virtual ip address
        this.ipsServed = 1;
        this.freedIps = new LinkedList<String>();

        this.id = id;
        this.label = label;
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

    public String getId() {
        return id;
    }

    public String getCidr() {
        return subnetInfo.getCidrSignature();
    }

    public Collection<FederationMember> getAllowedMembers() {
        return allowedMembers;
    }
}
