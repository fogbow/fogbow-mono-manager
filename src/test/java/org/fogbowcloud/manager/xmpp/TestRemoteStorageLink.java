package org.fogbowcloud.manager.xmpp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.storage.StorageAttribute;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository.StorageLink;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.jivesoftware.smack.XMPPException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestRemoteStorageLink {

	private ManagerTestHelper managerTestHelper;

	@Before
	public void setUp() throws XMPPException {
		this.managerTestHelper = new ManagerTestHelper();
	}

	@After
	public void tearDown() throws Exception {
		this.managerTestHelper.shutdown();
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testRemoteStorageLink() throws Exception {
		managerTestHelper.initializeXMPPManagerComponent(false);
				
		String target = "target";
		String source = "source";
		String deviceId = "deviceId";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(StorageAttribute.TARGET.getValue(), target);
		xOCCIAtt.put(StorageAttribute.SOURCE.getValue(), source);
		xOCCIAtt.put(StorageAttribute.DEVICE_ID.getValue(), deviceId);
		
		Mockito.doNothing().when(managerTestHelper.getStoragePlugin()).attach(
				Mockito.any(Token.class), Mockito.any(List.class), Mockito.eq(xOCCIAtt));

		Token token = new Token("accessId", OCCITestHelper.USER_MOCK,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>());
		
		StorageLink storageLink = new StorageLink("id", source, target, deviceId);
				
		ManagerPacketHelper.remoteStorageLink(storageLink, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				token, managerTestHelper.createPacketSender());				
	}

	@SuppressWarnings("unchecked")
	@Test(expected=OCCIException.class)
	public void testRemoteStorageLinkWrong() throws Exception {
		managerTestHelper.initializeXMPPManagerComponent(false);
		
		String target = "target";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(StorageAttribute.TARGET.getValue(), target);
		
		Mockito.doThrow(new OCCIException(ErrorType.BAD_REQUEST, "bad"))
				.when(managerTestHelper.getStoragePlugin())
				.attach(Mockito.any(Token.class), Mockito.any(List.class),
						Mockito.anyMap());

		Token token = new Token("accessId", OCCITestHelper.USER_MOCK,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>());
		
		StorageLink storageLink = new StorageLink("id", "source", target, "deviceId");
				
		ManagerPacketHelper.remoteStorageLink(storageLink, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				token, managerTestHelper.createPacketSender());					
	}
	
}
