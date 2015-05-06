package org.fogbowcloud.manager.core.plugins.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import net.schmizz.sshj.SSHClient;

import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.util.SshClientPool.SSHClientFactory;
import org.fogbowcloud.manager.core.plugins.util.SshClientPool.SSHConnection;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestSshClientPool {
	
	private static final int EXTRA_TIME = 1000;
	private SshClientPool sshClientPool; 
	private Semaphore semaphore;
	
	@Before
	public void setUp() {
		sshClientPool = new SshClientPool();
	}
	
	@Test
	public void testRemoveTimedoutConnection() throws Exception {		
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		long now = System.currentTimeMillis();
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Map<String, SSHConnection> pool = new HashMap<String, SSHConnection>();
		pool.put("keyOne", sshClientPool.new SSHConnection(new SSHClient(), now - (sshClientPool.TIMEOUT + EXTRA_TIME)));
		pool.put("keyTwo", sshClientPool.new SSHConnection(new SSHClient(), now + sshClientPool.TIMEOUT));
		pool.put("keyThree", sshClientPool.new SSHConnection(new SSHClient(), now));
		pool.put("keyFour", sshClientPool.new SSHConnection(new SSHClient(), now - (sshClientPool.TIMEOUT + EXTRA_TIME)));
		sshClientPool.setPool(pool);
		
		Assert.assertEquals(4, sshClientPool.getPool().size());
		
		sshClientPool.removeTimedoutSSHConnection();
		
		Assert.assertEquals(2, sshClientPool.getPool().size());
	}
	
	@Test
	public void testTriggerShhClientPollScheduler() throws Exception {
		String address = "localhost:1000";		
		SSHClient sshClient = Mockito.mock(SSHClient.class);
		Mockito.doNothing().when(sshClient).connect(Mockito.anyString(), Mockito.anyInt());
		
		SSHClientFactory clientFactory = Mockito.mock(SSHClientFactory.class);
		Mockito.when(clientFactory.createSshClient()).thenReturn(sshClient);
		
		sshClientPool.setClientFactory(clientFactory);
		
		long now = System.currentTimeMillis();
						
		Assert.assertEquals(0, sshClientPool.getPool().size());
		Assert.assertFalse(sshClientPool.getSshConnectionSchedulerTimer().isScheduled());
		
		sshClientPool.getClient(address, "sshUser", "sshPrivateKeyPath");
		
		Assert.assertEquals(1, sshClientPool.getPool().size());
		Assert.assertTrue(sshClientPool.getSshConnectionSchedulerTimer().isScheduled());
		
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + sshClientPool.TIMEOUT + EXTRA_TIME);
		sshClientPool.setDateUtils(dateUtils);
		
		sshClientPool.removeTimedoutSSHConnection();
		
		Assert.assertEquals(0, sshClientPool.getPool().size());
		Assert.assertFalse(sshClientPool.getSshConnectionSchedulerTimer().isScheduled());	
	}
	
	@Test
	public void testSemaphoreForShhClient() throws Exception {
		semaphore = new Semaphore(ManagerController.DEFAULT_MAX_POOL);
		sshClientPool.setSemaphore(semaphore);
		
		SSHClient sshClient = Mockito.mock(SSHClient.class);
		Mockito.doNothing().when(sshClient).connect(Mockito.anyString(), Mockito.anyInt());
		
		SSHClientFactory clientFactory = Mockito.mock(SSHClientFactory.class);
		Mockito.when(clientFactory.createSshClient()).thenReturn(sshClient);
		
		sshClientPool.setClientFactory(clientFactory);
		
		for (int i = 1; i <= ManagerController.DEFAULT_MAX_POOL ; i++) {
			Random gerador = new Random();			
			String address = UUID.randomUUID() + ":" + gerador.nextInt(1000);
			sshClientPool.getClient(address, "sshUser", "sshPrivateKeyPath");
		}
		
		Assert.assertFalse(semaphore.tryAcquire());	

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		long now = System.currentTimeMillis();
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + sshClientPool.TIMEOUT + EXTRA_TIME);
		sshClientPool.setDateUtils(dateUtils);
		
		sshClientPool.removeTimedoutSSHConnection();

		Assert.assertEquals(0, sshClientPool.getPool().size());
		Assert.assertTrue(semaphore.tryAcquire(ManagerController.DEFAULT_MAX_POOL));
		Assert.assertFalse(semaphore.tryAcquire(ManagerController.DEFAULT_MAX_POOL + 1));
	}
}
