import movethese.FederatedNetwork;
import movethese.SubnetAddressesCapacityReachedException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * Created by arnett on 05/02/18.
 */
public class FederatedNetworkTest {

    public static final String FIRST_IP_ADDRESS = "10.0.0.1";
    public static final String SECOND_IP_ADDRESS = "10.0.0.2";

    @Test
    public void testNextIp() throws SubnetAddressesCapacityReachedException {
        FederatedNetwork m = new FederatedNetwork("10.0.0.0/30");

        assertEquals(FIRST_IP_ADDRESS, m.nextFreeIp());
        assertEquals(SECOND_IP_ADDRESS, m.nextFreeIp());

        try {
            m.nextFreeIp();
            fail("There should not be any other valid ip address for this subnet");
        } catch (SubnetAddressesCapacityReachedException e) {

        }

    }

    @Test
    public void testFreeIp() throws SubnetAddressesCapacityReachedException {
        FederatedNetwork m = new FederatedNetwork("10.0.0.0/30");
        assertTrue(m.isIpAddressFree(FIRST_IP_ADDRESS));
        assertEquals(FIRST_IP_ADDRESS, m.nextFreeIp());
        assertFalse(m.isIpAddressFree(FIRST_IP_ADDRESS));

        m.freeIp(FIRST_IP_ADDRESS);
        assertTrue(m.isIpAddressFree(FIRST_IP_ADDRESS));
    }

    @Test
    public void testGapBetweenFreeIps() throws SubnetAddressesCapacityReachedException {
        FederatedNetwork m = new FederatedNetwork("10.0.0.0/30");

        assertTrue(m.isIpAddressFree(FIRST_IP_ADDRESS));
        assertEquals(FIRST_IP_ADDRESS, m.nextFreeIp());
        assertFalse(m.isIpAddressFree(FIRST_IP_ADDRESS));

        assertTrue(m.isIpAddressFree(SECOND_IP_ADDRESS));
        assertEquals(SECOND_IP_ADDRESS, m.nextFreeIp());
        assertFalse(m.isIpAddressFree(SECOND_IP_ADDRESS));

        m.freeIp(FIRST_IP_ADDRESS);
        assertTrue(m.isIpAddressFree(FIRST_IP_ADDRESS));

        assertEquals(FIRST_IP_ADDRESS, m.nextFreeIp());
    }
}
