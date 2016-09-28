package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.italiangrid.voms.VOMSAttribute;

public class VOBasedMapperPlugin implements MapperPlugin {

	private Properties properties;
	private VomsIdentityPlugin vomsIdentityPlugin;
	
	public VOBasedMapperPlugin(Properties properties) {
		this.properties = properties;
		this.vomsIdentityPlugin = new VomsIdentityPlugin(properties);
	}

	@Override
	public Map<String, String> getLocalCredentials(Order order) {
		if (order == null) {
			return MapperHelper.getCredentialsPerRelatedLocalName(
					this.properties, MapperHelper.FOGBOW_DEFAULTS);			
		}
		
		String member = getVO(order);
		Map<String, String> credentialsPerMember = MapperHelper
				.getCredentialsPerRelatedLocalName(this.properties, member);
		if (!credentialsPerMember.isEmpty()) {
			return credentialsPerMember;
		}
		return MapperHelper.getCredentialsPerRelatedLocalName(
				this.properties, MapperHelper.FOGBOW_DEFAULTS);
	}

	@Override
	public Map<String, Map<String, String>> getAllLocalCredentials() {
		return MapperHelper.getLocalCredentials(properties, null);
	}
	
	protected String getVO(Order order) {
		String accessId = order.getFederationToken().getAccessId();
		if (!vomsIdentityPlugin.isValid(accessId)) {
			return MapperHelper.FOGBOW_DEFAULTS;
		}	

		X509Certificate[] theChain = null;
		try {
			theChain = CertificateUtils.extractCertificates(
					CertificateUtils.parseChain(accessId)).toArray(new X509Certificate[] {});
			for (VOMSAttribute vomsAttribute : vomsIdentityPlugin.getVOMSValidator().validate(theChain)) {
				return vomsAttribute.getVO();
			}
		} catch (Exception e) {}		
		
		return MapperHelper.FOGBOW_DEFAULTS;
	}

	@Override
	public Map<String, String> getLocalCredentials(String accessId) {
		Token token = new Token(accessId, new Token.User("", ""), new Date(), null);
		return getLocalCredentials(new Order("", token, "", "", "", 
				new Date().getTime(), false, null, null, null));
	}
}
