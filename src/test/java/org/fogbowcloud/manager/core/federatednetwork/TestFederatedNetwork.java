package org.fogbowcloud.manager.core.federatednetwork;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * Created by arnett on 05/02/18.
 */
public class TestFederatedNetwork {

    public static final String FIRST_IP_ADDRESS = "10.0.0.2";
    public static final String SECOND_IP_ADDRESS = "10.0.0.3";

    @Test
    public void testNextIp() throws SubnetAddressesCapacityReachedException {
        FederatedNetwork m = new FederatedNetwork("fake-id", "10.0.0.0/30", "label", null);
        String orderId = "fake-orderId";

        assertEquals(FIRST_IP_ADDRESS, m.nextFreeIp(orderId));

        try {
            m.nextFreeIp(orderId + "2");
            fail("There should not be any other valid ip address for this subnet");
        } catch (SubnetAddressesCapacityReachedException e) {

        }
    }

    @Test
    public void testFreeIp() throws SubnetAddressesCapacityReachedException {
        FederatedNetwork m = new FederatedNetwork("fake-id", "10.0.0.0/30", "label", null);
        String orderId = "fake-orderId";
        assertTrue(m.isIpAddressFree(FIRST_IP_ADDRESS));
        assertEquals(FIRST_IP_ADDRESS, m.nextFreeIp(orderId));
        assertFalse(m.isIpAddressFree(FIRST_IP_ADDRESS));

        m.freeIp(FIRST_IP_ADDRESS);
        assertTrue(m.isIpAddressFree(FIRST_IP_ADDRESS));
    }

    @Test
    public void testGapBetweenFreeIps() throws SubnetAddressesCapacityReachedException {
    	String orderId = "fake-orderId";
        FederatedNetwork m = new FederatedNetwork("fake-id", "10.0.0.0/29", "label", null);

        assertTrue(m.isIpAddressFree(FIRST_IP_ADDRESS));
        assertEquals(FIRST_IP_ADDRESS, m.nextFreeIp(orderId));
        assertFalse(m.isIpAddressFree(FIRST_IP_ADDRESS));

        assertTrue(m.isIpAddressFree(SECOND_IP_ADDRESS));
        assertEquals(SECOND_IP_ADDRESS, m.nextFreeIp(orderId + "1"));
        assertFalse(m.isIpAddressFree(SECOND_IP_ADDRESS));

        m.freeIp(FIRST_IP_ADDRESS);
        assertTrue(m.isIpAddressFree(FIRST_IP_ADDRESS));

        assertEquals(FIRST_IP_ADDRESS, m.nextFreeIp(orderId + "2"));
    }
    
    @Test
    public void testGetFreeIpToAlreadyExistingOrder( ) throws SubnetAddressesCapacityReachedException {
    	String orderId = "fake-orderId";
    	FederatedNetwork m = new FederatedNetwork("fake-id", "10.0.0.0/29", "label", null);
    	assertTrue(m.isIpAddressFree(FIRST_IP_ADDRESS));
    	assertEquals(FIRST_IP_ADDRESS, m.nextFreeIp(orderId));
    	assertEquals(FIRST_IP_ADDRESS, m.nextFreeIp(orderId));
    }
}
