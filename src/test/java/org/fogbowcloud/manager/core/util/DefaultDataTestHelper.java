package org.fogbowcloud.manager.core.util;

import java.util.Date;

public class DefaultDataTestHelper {

	//xmpp data
	protected static final int SERVER_CLIENT_PORT = 5222;
	protected static final int SERVER_COMPONENT_PORT = 5347;
	public static final int TOKEN_SERVER_HTTP_PORT = 2223;
	protected static final String CLIENT_ADRESS = "client@test.com";
	protected static final String CLIENT_PASS = "password";
	protected static final String SMACK_ENDING = "/Smack";
	public static final String SERVER_HOST = "localhost";
	public static final String LOCAL_MANAGER_COMPONENT_URL = "manager.test.com";
	public static final String REMOTE_MANAGER_COMPONENT_URL = "remote-manager.test.com";
	public static final String REMOTE_MANAGER_TWO_COMPONENT_URL = "remote-manager-two.test.com";
	public static final String REMOTE_MANAGER_THREE_COMPONENT_URL = "remote-manager-three.test.com";
	public static final String MANAGER_COMPONENT_PASS = "password";
	public static final String WHOISALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/whoisalive";
	public static final String IAMALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/iamalive";
	
	//manager controller data
	public static final String CONFIG_PATH = "src/test/resources/manager.conf.test";
	public static final String LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH = "src/test/resources/server.id_rsa.pub";
	
	//time data
	public static final Long SCHEDULER_PERIOD = 500L;
	public static final Long GRACE_TIME = 250L;
	public static final Long SERVED_REQUEST_MONITORING_PERIOD = 300L;
	public static final long LONG_TIME = 1 * 24 * 60 * 60 * 1000; //one day
	public static final Date TOKEN_FUTURE_EXPIRATION = new Date(System.currentTimeMillis() + LONG_TIME);
	
	//test default values
	public static final String USER_NAME = "user";
	public static final String USER_PASS = "password";
	public static final String TENANT_NAME = "tenantName";
    public static final String INSTANCE_ID = "instanceid";
    
    public static final String FED_USER_NAME = "fed_user";
	public static final String FED_USER_PASS = "fed_password";
	public static final String FED_ACCESS_TOKEN_ID = "fed_accesstoken";
	
	//Green Sitter
	public static final String GREEN_SITTER_JID = "green.server.com";
}
