package org.fogbowcloud.manager.occi.request;

import java.util.Date;
import java.util.HashMap;

import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestRequestRepository {

	private static final String ID1 = "ID1";
	private static final String ID2 = "ID2";
	private static final String ID3 = "ID3";
	private static final String ID4 = "ID4";
	private static final String ID5 = "ID5";
	private static final String USER = "user";
	
	private RequestRepository requestRepository;
	
	@Before
	public void setUp() {
		requestRepository = new RequestRepository();
		requestRepository.addRequest(USER, createRequest(ID1, USER, true));
		requestRepository.addRequest(USER, createRequest(ID2, USER, true));
		requestRepository.addRequest(USER, createRequest(ID3, USER, true));
		requestRepository.addRequest(USER, createRequest(ID4, USER, false));
		requestRepository.addRequest(USER, createRequest(ID5, USER, false));		
	}

	@Test
	public void testGetLocalRequest() {	
		Assert.assertNotNull(requestRepository.get(ID1));
		Assert.assertNotNull(requestRepository.get(ID2));
		Assert.assertNotNull(requestRepository.get(ID3));
	}
	
	@Test
	public void testTryGetLocalRequest() {	
		Assert.assertNull(requestRepository.get(ID4));
		Assert.assertNull(requestRepository.get(ID5));
	}	

	@Test
	public void testGetServeredRequest() {	
		Assert.assertNotNull(requestRepository.get(ID4, false));
	}

	@Test
	public void testTryGetServeredRequest() {	
		Assert.assertNull(requestRepository.get(ID1, false));
	}	
	
	@Test
	public void testGetLocalRequestByUser() {	
		Assert.assertNotNull(requestRepository.get(USER, ID1));
		Assert.assertNotNull(requestRepository.get(USER, ID2));
		Assert.assertNotNull(requestRepository.get(USER, ID3));
	}

	@Test
	public void testTryGetLocalRequestByUser() {	
		Assert.assertNull(requestRepository.get(USER, ID4));
		Assert.assertNull(requestRepository.get(USER, ID5));
	}	

	@Test
	public void testGetServeredRequestByUser() {	
		Assert.assertNotNull(requestRepository.get(USER, ID4, false));
	}
	
	@Test
	public void testTryGetServeredRequestByUser() {	
		Assert.assertNull(requestRepository.get(USER, ID1, false));
	}		
	
	@Test
	public void testGetByUser() {
		Assert.assertEquals(3, requestRepository.getByUser(USER).size());
	}
	
	@Test
	public void testGetByUserServeredRequest() {
		Assert.assertEquals(2, requestRepository.getByUser(USER, false).size());
	}	
	
	private Request createRequest(String id, String user, boolean isLocal) {
		Token federationToken = new Token("1", user, new Date(), new HashMap<String, String>());
		Request request = new Request(id, federationToken, "", "", "", new Date().getTime(),
				isLocal, RequestState.OPEN, null, null);
		return request;
	}
	
}
