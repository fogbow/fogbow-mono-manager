package org.fogbowcloud.manager;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.CapacityControllerPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberAuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberPickerPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.NetworkPlugin;
import org.fogbowcloud.manager.core.plugins.PrioritizationPlugin;
import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.core.plugins.accounting.FCUAccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.SimpleStorageAccountingPlugin;
import org.fogbowcloud.manager.core.plugins.benchmarking.VanillaBenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.capacitycontroller.satisfactiondriven.SatisfactionDrivenCapacityControllerPlugin;
import org.fogbowcloud.manager.core.plugins.imagestorage.http.HTTPDownloadImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.localcredentails.SingleMapperPlugin;
import org.fogbowcloud.manager.core.plugins.memberauthorization.DefaultMemberAuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.memberpicker.RoundRobinMemberPickerPlugin;
import org.fogbowcloud.manager.core.plugins.prioritization.TwoFoldPrioritizationPlugin;
import org.fogbowcloud.manager.core.util.HttpRequestUtil;
import org.fogbowcloud.manager.occi.DataStoreHelper;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.xmpp.ManagerXmppComponent;
import org.restlet.Component;
import org.restlet.Server;
import org.restlet.data.Parameter;
import org.restlet.data.Protocol;
import org.restlet.engine.Engine;
import org.restlet.ext.slf4j.Slf4jLoggerFacade;
import org.restlet.util.Series;
import org.xmpp.component.ComponentException;

public class Main {

	private static final Logger LOGGER = Logger.getLogger(Main.class);

	public static void main(String[] args) throws Exception {
		MainHelper.configureLog4j();

		Properties properties = new Properties();

		String managerConfgFilePath = args[0];
		String infrastructureConfgFilePath = args[1];
		String federationConfgFilePath = args[2];
		
		File managerConfigFile = new File(managerConfgFilePath);
		File infrastructureConfgFile = new File(infrastructureConfgFilePath);
		File federationConfgFile = new File(federationConfgFilePath);
		
		if(!managerConfigFile.exists()){
			LOGGER.warn("Informed path to manager.conf file must be valid.");
			System.exit(MainHelper.EXIT_ERROR_CODE);
		}
		if(!infrastructureConfgFile.exists()){
			LOGGER.warn("Informed path to infrastructure.conf file must be valid.");
			System.exit(MainHelper.EXIT_ERROR_CODE);
		}
		if(!federationConfgFile.exists()){
			LOGGER.warn("Informed path to federation.conf file must be valid.");
			System.exit(MainHelper.EXIT_ERROR_CODE);
		}
		
		FileInputStream input = new FileInputStream(managerConfigFile);
		properties.load(input);
		
		Properties infraProperties = new Properties();
		FileInputStream infraInput = new FileInputStream(infrastructureConfgFile);
		infraProperties.load(infraInput);
		properties.putAll(infraProperties);

		Properties fedProperties = new Properties();
		FileInputStream fedInput = new FileInputStream(federationConfgFile);
		fedProperties.load(fedInput);
		properties.putAll(fedProperties);

		ResourceRepository.init(properties);
		HttpRequestUtil.init(properties);
		
		//folder name default to datastores
		DataStoreHelper.setDataStoreFolderExecution(DataStoreHelper.DATASTORES_FOLDER);
		
		ComputePlugin computePlugin = null;
		try {
			computePlugin = (ComputePlugin) MainHelper.createInstance(
					ConfigurationConstants.COMPUTE_CLASS_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Compute Plugin not especified in the properties.", e);
			System.exit(MainHelper.EXIT_ERROR_CODE);
		}

		AuthorizationPlugin authorizationPlugin = null;
		try {
			authorizationPlugin = (AuthorizationPlugin) MainHelper.createInstance(
					ConfigurationConstants.AUTHORIZATION_CLASS_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Authorization Plugin not especified in the properties.", e);
			System.exit(MainHelper.EXIT_ERROR_CODE);
		}
		
		IdentityPlugin localIdentityPlugin = null;
		try {
			localIdentityPlugin = (IdentityPlugin) MainHelper.getIdentityPluginByPrefix(properties,
					ConfigurationConstants.LOCAL_PREFIX);
		} catch (Exception e) {
			LOGGER.warn("Local Identity Plugin not especified in the properties.", e);
			System.exit(MainHelper.EXIT_ERROR_CODE);
		}
		
		IdentityPlugin federationIdentityPlugin = null;
		try {
			federationIdentityPlugin = (IdentityPlugin) MainHelper.getIdentityPluginByPrefix(properties,
					ConfigurationConstants.FEDERATION_PREFIX);
		} catch (Exception e) {
			LOGGER.warn("Federation Identity Plugin not especified in the properties.", e);
			System.exit(MainHelper.EXIT_ERROR_CODE);
		}

		FederationMemberAuthorizationPlugin validator = new DefaultMemberAuthorizationPlugin(properties);
		try {
			validator = (FederationMemberAuthorizationPlugin) MainHelper.createInstance(
					ConfigurationConstants.MEMBER_VALIDATOR_CLASS_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Member Validator not especified in the properties.");
			System.exit(MainHelper.EXIT_ERROR_CODE);
		}
		
		if (properties.get(ConfigurationConstants.RENDEZVOUS_JID_KEY) == null
				|| properties.get(ConfigurationConstants.RENDEZVOUS_JID_KEY).toString().isEmpty()) {
			LOGGER.warn("Rendezvous (" + ConfigurationConstants.RENDEZVOUS_JID_KEY
					+ ") not especified in the properties.");
		}
		
		ImageStoragePlugin imageStoragePlugin = null;
		try {
			imageStoragePlugin = (ImageStoragePlugin) MainHelper.createInstanceWithComputePlugin(
					ConfigurationConstants.IMAGE_STORAGE_PLUGIN_CLASS, properties, computePlugin);
		} catch (Exception e) {
			imageStoragePlugin = new HTTPDownloadImageStoragePlugin(properties, computePlugin);
			LOGGER.warn("Image Storage plugin not specified in properties. Using the default one.", e);
		}
				
		BenchmarkingPlugin benchmarkingPlugin = null;
		try {
			benchmarkingPlugin = (BenchmarkingPlugin) MainHelper.createInstance(
					ConfigurationConstants.BENCHMARKING_PLUGIN_CLASS_KEY, properties);
		} catch (Exception e) {
			benchmarkingPlugin = new VanillaBenchmarkingPlugin(properties);
			LOGGER.warn("Benchmarking plugin not specified in properties. Using the default one.", e);
		}
				
		AccountingPlugin computeAccountingPlugin = null;
		try {
			computeAccountingPlugin = (AccountingPlugin) MainHelper.createInstanceWithBenchmarkingPlugin(
					ConfigurationConstants.COMPUTE_ACCOUNTING_PLUGIN_CLASS_KEY, properties, benchmarkingPlugin);
		} catch (Exception e) {
			computeAccountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin);
			LOGGER.warn("Accounting plugin (compute) not specified in properties. Using the default one.", e);
		}
		
		AccountingPlugin storageccountingPlugin = null;
		try {
			storageccountingPlugin = (AccountingPlugin) MainHelper.createInstance(
					ConfigurationConstants.STORAGE_ACCOUNTING_PLUGIN_CLASS_KEY, properties);
		} catch (Exception e) {
			storageccountingPlugin = new SimpleStorageAccountingPlugin(properties);
			LOGGER.warn("Accounting plugin (storage) not specified in properties. Using the default one.", e);
		}		
		
		FederationMemberPickerPlugin memberPickerPlugin = null;
		try {
			memberPickerPlugin = (FederationMemberPickerPlugin) MainHelper.createInstanceWithAccountingPlugin(
					ConfigurationConstants.MEMBER_PICKER_PLUGIN_CLASS_KEY, properties,
					computeAccountingPlugin);
		} catch (Exception e) {
			memberPickerPlugin = new RoundRobinMemberPickerPlugin(properties, computeAccountingPlugin);
			LOGGER.warn("Member picker plugin not specified in properties. Using the default one.", e);
		}
		
		MapperPlugin mapperPlugin = null;
		try {
			mapperPlugin = (MapperPlugin) MainHelper.createInstance(
					ConfigurationConstants.LOCAL_CREDENTIALS_CLASS_KEY, properties);
		} catch (Exception e) {
			mapperPlugin = new SingleMapperPlugin(properties);
			LOGGER.warn("Federation user crendetail plugin not specified in properties. Using the default one.", e);
		}		
		
		StoragePlugin storagePlugin = null;
		try {
			storagePlugin = (StoragePlugin) MainHelper.createInstance(
					ConfigurationConstants.STORAGE_CLASS_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Storage Plugin not especified in the properties.", e);
			System.exit(MainHelper.EXIT_ERROR_CODE);
		}
			
		
		NetworkPlugin networkPlugin = null;
		try {
			networkPlugin = (NetworkPlugin) MainHelper.createInstance(
					ConfigurationConstants.NETWORK_CLASS_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Network Plugin not especified in the properties.", e);
			System.exit(MainHelper.EXIT_ERROR_CODE);
		}		

		
		String occiExtraResourcesPath = properties
				.getProperty(ConfigurationConstants.OCCI_EXTRA_RESOURCES_KEY_PATH);
		if (occiExtraResourcesPath != null && !occiExtraResourcesPath.isEmpty()) {
			if (properties.getProperty(ConfigurationConstants.INSTANCE_DATA_STORE_URL) == null) {
				LOGGER.error("If OCCI extra resources was set for supporting post-compute, you must also set instance datastore property ("
						+ ConfigurationConstants.INSTANCE_DATA_STORE_URL + ").");
				System.exit(MainHelper.EXIT_ERROR_CODE);
			}
		}
		
		PrioritizationPlugin prioritizationPlugin = new TwoFoldPrioritizationPlugin(properties,	computeAccountingPlugin);
		
		CapacityControllerPlugin capacityControllerPlugin = null;
		try {
			capacityControllerPlugin = (CapacityControllerPlugin) MainHelper.createInstanceWithAccountingPlugin(
					ConfigurationConstants.CAPACITY_CONTROLLER_PLUGIN_CLASS, properties, computeAccountingPlugin);
		} catch (Exception e) {
			capacityControllerPlugin = new SatisfactionDrivenCapacityControllerPlugin();
			LOGGER.warn("Capacity Controller plugin not specified in properties. Using the default one.", e);
		}

		ManagerController facade = new ManagerController(properties);
		facade.setComputePlugin(computePlugin);
		facade.setAuthorizationPlugin(authorizationPlugin);
		facade.setLocalIdentityPlugin(localIdentityPlugin);
		facade.setFederationIdentityPlugin(federationIdentityPlugin);
		facade.setImageStoragePlugin(imageStoragePlugin);
		facade.setValidator(validator);
		facade.setBenchmarkingPlugin(benchmarkingPlugin);
		facade.setComputeAccountingPlugin(computeAccountingPlugin);
		facade.setStorageAccountingPlugin(storageccountingPlugin);
		facade.setMemberPickerPlugin(memberPickerPlugin);
		facade.setPrioritizationPlugin(prioritizationPlugin);
		facade.setLocalCredentailsPlugin(mapperPlugin);
		facade.setStoragePlugin(storagePlugin);
		facade.setCapacityControllerPlugin(capacityControllerPlugin);
		facade.setNetworkPlugin(networkPlugin);
		
		String xmppHost = properties.getProperty(ConfigurationConstants.XMPP_HOST_KEY);
		String xmppJid = properties.getProperty(ConfigurationConstants.XMPP_JID_KEY);
		
		if (xmppHost != null && xmppJid != null) {
			long timeout = MainHelper.getXMPPTimeout(properties);
			ManagerXmppComponent xmpp = new ManagerXmppComponent(
					xmppJid,
					properties.getProperty(ConfigurationConstants.XMPP_PASS_KEY),
					xmppHost,
					Integer.parseInt(properties.getProperty(ConfigurationConstants.XMPP_PORT_KEY)),
					facade, 
					timeout);
			xmpp.setRendezvousAddress(properties.getProperty(ConfigurationConstants.RENDEZVOUS_JID_KEY));
			try {
				xmpp.connect();			
			} catch (ComponentException e) {
				LOGGER.error("Conflict in the initialization of xmpp component.", e);
				System.exit(MainHelper.EXIT_ERROR_CODE);
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
			String httpPort = properties.getProperty(ConfigurationConstants.HTTP_PORT_KEY, 
					String.valueOf(MainHelper.DEFAULT_HTTP_PORT));
			String httpsPort = properties.getProperty(ConfigurationConstants.HTTPS_PORT_KEY, 
					String.valueOf(MainHelper.DEFAULT_HTTPS_PORT));
			String httpsKeystorePath = properties.getProperty(ConfigurationConstants.HTTPS_KEYSTORE_PATH);
			String httpsKeystorePassword = properties.getProperty(ConfigurationConstants.HTTPS_KEYSTORE_PASSWORD);
			String httpsKeyPassword = properties.getProperty(ConfigurationConstants.HTTPS_KEY_PASSWORD);
			String httpsKeystoreType = properties.getProperty(ConfigurationConstants.HTTPS_KEYSTORE_TYPE, "JKS");
			Boolean httpsEnabled = Boolean.valueOf(properties.getProperty(ConfigurationConstants.HTTPS_ENABLED, 
					String.valueOf(MainHelper.DEFAULT_HTTPS_ENABLED)));
			String requestHeaderSize = String.valueOf(Integer.parseInt(
					properties.getProperty(ConfigurationConstants.HTTP_REQUEST_HEADER_SIZE_KEY, 
					String.valueOf(MainHelper.DEFAULT_REQUEST_HEADER_SIZE))));
			String responseHeaderSize = String.valueOf(Integer.parseInt(properties.getProperty(
					ConfigurationConstants.HTTP_RESPONSE_HEADER_SIZE_KEY, 
					String.valueOf(MainHelper.DEFAULT_RESPONSE_HEADER_SIZE))));
			
			//Adding HTTP server
			Server httpServer = http.getServers().add(Protocol.HTTP, Integer.parseInt(httpPort));
			Series<Parameter> httpParameters = httpServer.getContext().getParameters();
			httpParameters.add("http.requestHeaderSize", requestHeaderSize);
			httpParameters.add("http.responseHeaderSize", responseHeaderSize);
			
			if (httpsEnabled) {
				//Adding HTTPS server
				Server httpsServer = http.getServers().add(Protocol.HTTPS, Integer.parseInt(httpsPort));
				
				@SuppressWarnings("rawtypes")
				Series parameters = httpsServer.getContext().getParameters();
				parameters.add("sslContextFactory", "org.restlet.engine.ssl.DefaultSslContextFactory");
				if (httpsKeystorePath != null) {
					parameters.add("keyStorePath", httpsKeystorePath);
				}
				if (httpsKeystorePassword != null) {
					parameters.add("keyStorePassword", httpsKeystorePassword);
				}
				if (httpsKeyPassword != null) {
					parameters.add("keyPassword", httpsKeyPassword);
				}
				if (httpsKeystoreType != null) {
					parameters.add("keyStoreType", httpsKeystoreType);
				}
				parameters.add("http.requestHeaderSize", requestHeaderSize);
				parameters.add("http.responseHeaderSize", responseHeaderSize);
			}
			
			http.getDefaultHost().attach(application);
			http.start();
		} catch (Exception e) {
			LOGGER.error("Conflict in the initialization of the HTTP component.", e);
			System.exit(MainHelper.EXIT_ERROR_CODE);
		}
	}
	
}
