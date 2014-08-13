package org.fogbowcloud.manager.core.plugins.opennebula;

import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;

public class OpenNebulaClientFactory {

	public Client createClient(String accessId, String openNebulaEndpoint)
			throws ClientConfigurationException {
		return new Client(accessId, openNebulaEndpoint);
	}

}
