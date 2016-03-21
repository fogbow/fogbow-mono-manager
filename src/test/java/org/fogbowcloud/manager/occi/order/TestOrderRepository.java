package org.fogbowcloud.manager.occi.order;

import java.util.Date;
import java.util.HashMap;

import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderRepository;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestOrderRepository {

	private static final String ID1 = "ID1";
	private static final String ID2 = "ID2";
	private static final String ID3 = "ID3";
	private static final String ID4 = "ID4";
	private static final String ID5 = "ID5";
	private static final String USER = "user";
	
	private OrderRepository orderRepository;
	
	@Before
	public void setUp() {
		orderRepository = new OrderRepository();
		orderRepository.addOrder(USER, createOrder(ID1, USER, true));
		orderRepository.addOrder(USER, createOrder(ID2, USER, true));
		orderRepository.addOrder(USER, createOrder(ID3, USER, true));
		orderRepository.addOrder(USER, createOrder(ID4, USER, false));
		orderRepository.addOrder(USER, createOrder(ID5, USER, false));		
	}

	@Test
	public void testGetLocalOrder() {	
		Assert.assertNotNull(orderRepository.get(ID1));
		Assert.assertNotNull(orderRepository.get(ID2));
		Assert.assertNotNull(orderRepository.get(ID3));
	}
	
	@Test
	public void testTryGetLocalOrder() {	
		Assert.assertNull(orderRepository.get(ID4));
		Assert.assertNull(orderRepository.get(ID5));
	}	

	@Test
	public void testGetServeredOrder() {	
		Assert.assertNotNull(orderRepository.get(ID4, false));
	}

	@Test
	public void testTryGetServeredOrder() {	
		Assert.assertNull(orderRepository.get(ID1, false));
	}	
	
	@Test
	public void testGetLocalOrderByUser() {	
		Assert.assertNotNull(orderRepository.get(USER, ID1));
		Assert.assertNotNull(orderRepository.get(USER, ID2));
		Assert.assertNotNull(orderRepository.get(USER, ID3));
	}

	@Test
	public void testTryGetLocalOrderByUser() {	
		Assert.assertNull(orderRepository.get(USER, ID4));
		Assert.assertNull(orderRepository.get(USER, ID5));
	}	

	@Test
	public void testGetServeredOrderByUser() {	
		Assert.assertNotNull(orderRepository.get(USER, ID4, false));
	}
	
	@Test
	public void testTryGetServeredOrderByUser() {	
		Assert.assertNull(orderRepository.get(USER, ID1, false));
	}		
	
	@Test
	public void testGetByUser() {
		Assert.assertEquals(3, orderRepository.getByUser(USER).size());
	}
	
	@Test
	public void testGetByUserServeredOrder() {
		Assert.assertEquals(2, orderRepository.getByUser(USER, false).size());
	}	
	
	private Order createOrder(String id, String user, boolean isLocal) {
		Token federationToken = new Token("1", user, new Date(), new HashMap<String, String>());
		Order order = new Order(id, federationToken, "", "", "", new Date().getTime(),
				isLocal, OrderState.OPEN, null, null);
		return order;
	}
	
}
