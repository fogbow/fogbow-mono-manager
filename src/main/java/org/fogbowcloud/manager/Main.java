package org.fogbowcloud.manager;

import java.io.FileInputStream;
import java.util.Properties;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberAuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberPickerPlugin;
import org.fogbowcloud.manager.core.plugins.LocalCredentialsPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.PrioritizationPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.FCUAccountingPlugin;
import org.fogbowcloud.manager.core.plugins.benchmarking.VanillaBenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.imagestorage.http.HTTPDownloadImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.localcredentails.SingleLocalCrendentialsPlugin;
import org.fogbowcloud.manager.core.plugins.memberauthorization.DefaultMemberAuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.memberpicker.RoundRobinMemberPickerPlugin;
import org.fogbowcloud.manager.core.plugins.prioritization.TwoFoldPrioritizationPlugin;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.xmpp.ManagerXmppComponent;
import org.restlet.Component;
import org.restlet.data.Protocol;
import org.restlet.engine.Engine;
import org.restlet.ext.slf4j.Slf4jLoggerFacade;
import org.xmpp.component.ComponentException;

public class Main {

	private static final Logger LOGGER = Logger.getLogger(Main.class);
	private static final int EXIT_ERROR_CODE = 128;
	private static final int DEFAULT_HTTP_PORT = 8182;
	
	public static void main(String[] args) throws Exception {
		configureLog4j();

		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(args[0]);
		properties.load(input);
		ResourceRepository.init(properties);
		
		ComputePlugin computePlugin = null;
		try {
			computePlugin = (ComputePlugin) createInstance(
					ConfigurationConstants.COMPUTE_CLASS_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Compute Plugin not especified in the properties.", e);
			System.exit(EXIT_ERROR_CODE);
		}

		AuthorizationPlugin authorizationPlugin = null;
		try {
			authorizationPlugin = (AuthorizationPlugin) createInstance(
					ConfigurationConstants.AUTHORIZATION_CLASS_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Authorization Plugin not especified in the properties.", e);
			System.exit(EXIT_ERROR_CODE);
		}
		
		IdentityPlugin localIdentityPlugin = null;
		try {
			localIdentityPlugin = (IdentityPlugin) getIdentityPluginByPrefix(properties,
					ConfigurationConstants.LOCAL_PREFIX);
		} catch (Exception e) {
			LOGGER.warn("Local Identity Plugin not especified in the properties.", e);
			System.exit(EXIT_ERROR_CODE);
		}
		
		IdentityPlugin federationIdentityPlugin = null;
		try {
			federationIdentityPlugin = (IdentityPlugin) getIdentityPluginByPrefix(properties,
					ConfigurationConstants.FEDERATION_PREFIX);
		} catch (Exception e) {
			LOGGER.warn("Federation Identity Plugin not especified in the properties.", e);
			System.exit(EXIT_ERROR_CODE);
		}

		FederationMemberAuthorizationPlugin validator = new DefaultMemberAuthorizationPlugin(properties);
		try {
			validator = (FederationMemberAuthorizationPlugin) createInstance(
					ConfigurationConstants.MEMBER_VALIDATOR_CLASS_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Member Validator not especified in the properties.");
			System.exit(EXIT_ERROR_CODE);
		}
		
		if (properties.get(ConfigurationConstants.RENDEZVOUS_JID_KEY) == null
				|| properties.get(ConfigurationConstants.RENDEZVOUS_JID_KEY).toString().isEmpty()) {
			LOGGER.warn("Rendezvous (" + ConfigurationConstants.RENDEZVOUS_JID_KEY
					+ ") not especified in the properties.");
		}
		
		ImageStoragePlugin imageStoragePlugin = null;
		try {
			imageStoragePlugin = (ImageStoragePlugin) createInstanceWithComputePlugin(
					ConfigurationConstants.IMAGE_STORAGE_PLUGIN_CLASS, properties, computePlugin);
		} catch (Exception e) {
			imageStoragePlugin = new HTTPDownloadImageStoragePlugin(properties, computePlugin);
			LOGGER.warn("Image Storage plugin not specified in properties. Using the default one.", e);
		}
				
		BenchmarkingPlugin benchmarkingPlugin = null;
		try {
			benchmarkingPlugin = (BenchmarkingPlugin) createInstance(
					ConfigurationConstants.BENCHMARKING_PLUGIN_CLASS_KEY, properties);
		} catch (Exception e) {
			benchmarkingPlugin = new VanillaBenchmarkingPlugin(properties);
			LOGGER.warn("Benchmarking plugin not specified in properties. Using the default one.", e);
		}
				
		AccountingPlugin accountingPlugin = null;
		try {
			accountingPlugin = (AccountingPlugin) createInstanceWithBenchmarkingPlugin(
					ConfigurationConstants.ACCOUNTING_PLUGIN_CLASS_KEY, properties, benchmarkingPlugin);
		} catch (Exception e) {
			accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin);
			LOGGER.warn("Accounting plugin not specified in properties. Using the default one.", e);
		}
		
		FederationMemberPickerPlugin memberPickerPlugin = null;
		try {
			memberPickerPlugin = (FederationMemberPickerPlugin) createInstanceWithAccoutingPlugin(
					ConfigurationConstants.MEMBER_PICKER_PLUGIN_CLASS_KEY, properties,
					accountingPlugin);
		} catch (Exception e) {
			memberPickerPlugin = new RoundRobinMemberPickerPlugin(properties, accountingPlugin);
			LOGGER.warn("Member picker plugin not specified in properties. Using the default one.", e);
		}
		
		LocalCredentialsPlugin localCredentialsPlugin = null;
		try {
			localCredentialsPlugin = (LocalCredentialsPlugin) createInstance(
					ConfigurationConstants.LOCAL_CREDENTIALS_CLASS_KEY, properties);
		} catch (Exception e) {
			localCredentialsPlugin = new SingleLocalCrendentialsPlugin(properties);
			LOGGER.warn("Federation user crendetail plugin not specified in properties. Using the default one.", e);
		}		
		
		String occiExtraResourcesPath = properties
				.getProperty(ConfigurationConstants.OCCI_EXTRA_RESOURCES_KEY_PATH);
		if (occiExtraResourcesPath != null && !occiExtraResourcesPath.isEmpty()) {
			if (properties.getProperty(ConfigurationConstants.INSTANCE_DATA_STORE_URL) == null) {
				LOGGER.error("If OCCI extra resources was set for supporting post-compute, you must also set instance datastore property ("
						+ ConfigurationConstants.INSTANCE_DATA_STORE_URL + ").");
				System.exit(EXIT_ERROR_CODE);
			}
		}
		
		PrioritizationPlugin prioritizationPlugin = new TwoFoldPrioritizationPlugin(properties,
				accountingPlugin);

		ManagerController facade = new ManagerController(properties);
		facade.setComputePlugin(computePlugin);
		facade.setAuthorizationPlugin(authorizationPlugin);
		facade.setLocalIdentityPlugin(localIdentityPlugin);
		facade.setFederationIdentityPlugin(federationIdentityPlugin);
		facade.setImageStoragePlugin(imageStoragePlugin);
		facade.setValidator(validator);
		facade.setBenchmarkingPlugin(benchmarkingPlugin);
		facade.setAccountingPlugin(accountingPlugin);
		facade.setMemberPickerPlugin(memberPickerPlugin);
		facade.setPrioritizationPlugin(prioritizationPlugin);
		facade.setFederationUserCredentailsPlugin(localCredentialsPlugin);
		
		String xmppHost = properties.getProperty(ConfigurationConstants.XMPP_HOST_KEY);
		String xmppJid = properties.getProperty(ConfigurationConstants.XMPP_JID_KEY);
		
		if (xmppHost != null && xmppJid != null) {
			ManagerXmppComponent xmpp = new ManagerXmppComponent(
					xmppJid,
					properties.getProperty(ConfigurationConstants.XMPP_PASS_KEY),
					xmppHost,
					Integer.parseInt(properties.getProperty(ConfigurationConstants.XMPP_PORT_KEY)),
					facade);
			xmpp.setRendezvousAddress(properties.getProperty(ConfigurationConstants.RENDEZVOUS_JID_KEY));
			try {
				xmpp.connect();			
			} catch (ComponentException e) {
				LOGGER.error("Conflict in the initialization of xmpp component.", e);
				System.exit(EXIT_ERROR_CODE);
			}
			xmpp.process(false);
			xmpp.init();
			facade.setPacketSender(xmpp);
		}

		OCCIApplication application = new OCCIApplication(facade);

		Slf4jLoggerFacade loggerFacade = new Slf4jLoggerFacade();
		Engine.getInstance().setLoggerFacade(loggerFacade);
		
		try {
			Component http = new Component();
			String httpPort = properties.getProperty(ConfigurationConstants.HTTP_PORT_KEY);			
			http.getServers().add(Protocol.HTTP, httpPort == null ? DEFAULT_HTTP_PORT : Integer.parseInt(httpPort));
			http.getDefaultHost().attach(application);
			http.start();
		} catch (Exception e) {
			LOGGER.error("Conflict in the initialization of the HTTP component.", e);
			System.exit(EXIT_ERROR_CODE);
		}
	}

	private static Object getIdentityPluginByPrefix(Properties properties, String prefix)
			throws Exception {
		Properties pluginProperties = new Properties();
		for (Object keyObj : properties.keySet()) {
			String key = keyObj.toString();
			pluginProperties.put(key, properties.get(key));
			if (key.startsWith(prefix)) {
				String newKey = key.replace(prefix, "");
				pluginProperties.put(newKey, properties.get(key));
			}
		}
		return createInstance(prefix + ConfigurationConstants.IDENTITY_CLASS_KEY, pluginProperties);
	}

	private static Object createInstance(String propName, Properties properties) throws Exception {
		return Class.forName(properties.getProperty(propName)).getConstructor(Properties.class)
				.newInstance(properties);
	}
	
	private static Object createInstanceWithComputePlugin(String propName, 
			Properties properties, ComputePlugin computePlugin) throws Exception {
		return Class.forName(properties.getProperty(propName)).getConstructor(Properties.class, ComputePlugin.class)
				.newInstance(properties, computePlugin);
	}
	
	private static Object createInstanceWithBenchmarkingPlugin(
			String propName, Properties properties,
			BenchmarkingPlugin benchmarkingPlugin) throws Exception {
		return Class.forName(properties.getProperty(propName)).getConstructor(Properties.class, BenchmarkingPlugin.class)
				.newInstance(properties, benchmarkingPlugin);
	}
	
	private static Object createInstanceWithAccoutingPlugin(
			String propName, Properties properties,
			AccountingPlugin accoutingPlugin) throws Exception {
		return Class.forName(properties.getProperty(propName)).getConstructor(Properties.class, AccountingPlugin.class)
				.newInstance(properties, accoutingPlugin);
	}

	private static void configureLog4j() {
		ConsoleAppender console = new ConsoleAppender();
		console.setThreshold(org.apache.log4j.Level.OFF);
		console.activateOptions();
		Logger.getRootLogger().addAppender(console);
	}
}
