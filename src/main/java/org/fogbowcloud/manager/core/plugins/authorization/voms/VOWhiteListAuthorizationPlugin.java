package org.fogbowcloud.manager.core.plugins.authorization.voms;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.italiangrid.voms.VOMSAttribute;

public class VOWhiteListAuthorizationPlugin implements AuthorizationPlugin {

	protected static final String AUTHORIZATION_VOMS_WHITELIST = "authorization_vo_whitelist";
	private static final Logger LOGGER = Logger.getLogger(VOWhiteListAuthorizationPlugin.class);
	private VomsIdentityPlugin vomsIdentityPlugin;
	private List<String> VOWhiteList;
	
	public VOWhiteListAuthorizationPlugin(Properties properties) {
		if (properties == null) {
			throw new IllegalArgumentException("Properties can not come null.");			
		}
		String whiteListStr = properties.getProperty(AUTHORIZATION_VOMS_WHITELIST);
		if (whiteListStr == null) {
			LOGGER.error("Property " + AUTHORIZATION_VOMS_WHITELIST + " must be set.");
			throw new IllegalArgumentException(
					"Property " + AUTHORIZATION_VOMS_WHITELIST + " must be set.");
		}
		this.VOWhiteList = Arrays.asList(whiteListStr.split(","));
		this.vomsIdentityPlugin = new VomsIdentityPlugin(properties);
	}
	
	@Override
	public boolean isAuthorized(Token token) {
		String accessId = token.getAccessId();
		if (!vomsIdentityPlugin.isValid(accessId)) {
			return false;
		}	

		String vomsName = null;
		X509Certificate[] theChain = null;
		try {
			theChain = CertificateUtils.extractCertificates(CertificateUtils.parseChain(accessId))
					.toArray(new X509Certificate[] {});
			for (VOMSAttribute vomsAttribute : vomsIdentityPlugin.getVOMSValidator().validate(
					theChain)) {
				vomsName = vomsAttribute.getVO();
			}
		} catch (Exception e) {
		}
					
		return vomsName != null && VOWhiteList.contains(vomsName) ? true : false;
	}
}
