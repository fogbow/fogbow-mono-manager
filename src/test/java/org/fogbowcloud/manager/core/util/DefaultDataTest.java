package org.fogbowcloud.manager.core.util;

import java.util.Date;

public class DefaultDataTest {

	//xmpp data
	protected static final int SERVER_CLIENT_PORT = 5222;
	protected static final int SERVER_COMPONENT_PORT = 5347;
	protected static final String SERVER_HOST = "localhost";
	protected static final String CLIENT_ADRESS = "client@test.com";
	protected static final String CLIENT_PASS = "password";
	protected static final String SMACK_ENDING = "/Smack";
	public static final String MANAGER_COMPONENT_URL = "manager.test.com";
	public static final String MANAGER_COMPONENT_PASS = "password";
	public static final String WHOISALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/whoisalive";
	public static final String IAMALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/iamalive";
	
	//manager controller data
	public static final String CONFIG_PATH = "src/test/resources/manager.conf.test";
	
	//time data
	public static final int TEST_DEFAULT_TIMEOUT = 10000;
	public static final int TIMEOUT_GRACE = 500;	
	public static final Long SCHEDULER_PERIOD = 500L;
	public static final Long GRACE_TIME = 30L;
	public static final long LONG_TIME = 1 * 24 * 60 * 60 * 1000; //one day
	public static final Date TOKEN_FUTURE_EXPIRATION = new Date(System.currentTimeMillis() + LONG_TIME);
	
	//test default values
	public static final String USER_NAME = "user";
	public static final String USER_PASS = "password";
	public static final String ACCESS_TOKEN_ID = "HgjhgYUDFTGBgrbelihBDFGBÇuyrb";
	public static final String TENANT_NAME = "tenantName";
	public static final String INSTANCE_ID = "b122f3ad-503c-4abb-8a55-ba8d90cfce9f";
}