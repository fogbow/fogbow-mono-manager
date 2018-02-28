package org.fogbowcloud.manager.core.plugins.identity.vmware;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONException;

import java.util.*;

public class VMWareIdentityPlugin implements IdentityPlugin {

    private final static Logger LOGGER = Logger.getLogger(VMWareIdentityPlugin.class);

    private static final String VCENTER_AUTH_ENDPOINT = "/rest/com/vmware/cis/session";

    static final String IDENTITY_URL = "identity_url";
    static final String AUTH_URL = "authUrl";

    private String vcenterUrl;
    private String vcenterTokenEndpoint;

    public VMWareIdentityPlugin(Properties properties) throws Exception {
        readProperties(properties);
        this.vcenterTokenEndpoint = vcenterUrl + VCENTER_AUTH_ENDPOINT;
    }

    void readProperties(Properties properties) throws Exception {
        String identityUrl = properties.getProperty(IDENTITY_URL);
        if (identityUrl == null) {
            String authUrl = properties.getProperty(AUTH_URL);
            if (authUrl == null) {
                throw new Exception("Neither [" + IDENTITY_URL + "] nor [" + AUTH_URL + "] were set.");
            } else {
                this.vcenterUrl = authUrl;
            }
        } else {
            this.vcenterUrl = identityUrl;
        }
    }

    @Override
    public Token createToken(Map<String, String> userCredentials) {
        try {
            String username = userCredentials.get("username");
            String password = userCredentials.get("password");
            HttpResponse<JsonNode> response = postRequest(username, password);
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                String tk = (String) response.getBody().getObject().get("value");;
                return new Token(tk, new Token.User(username, username), new Date(), new HashMap<>());
            } else if (response.getStatus() == 401) {
                LOGGER.error("VMWare Unauthorized");
                throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.INVALID_USER_OR_PASSWORD);
            } else if (response.getStatus() == 503) {
                LOGGER.error("VMWare Failed to respond");
                throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.INTERNAL_ERROR);
            } else {
                LOGGER.error("VMWare Respondend with unknown code");
                throw new OCCIException(ErrorType.NO_VALID_HOST_FOUND, ResponseConstants.INTERNAL_ERROR);
            }
        } catch (UnirestException e) {
            LOGGER.error("Failed to generate token.", e);
            throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
        } catch (JSONException e) {
            LOGGER.error("Response body had a malformed JSON.", e);
            throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
        }
    }

    HttpResponse<JsonNode> postRequest(String username, String password) throws UnirestException {
        return Unirest.post(vcenterTokenEndpoint)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .basicAuth(username, password)
                .asJson();
    }

    @Override
    public Token reIssueToken(Token token) {
        return token;
    }

    @Override
    public Token getToken(String accessId) {
        return null;
    }

    @Override
    public boolean isValid(String accessId) {
        return false;
    }

    @Override
    public Credential[] getCredentials() {
        return new Credential[] {
                new Credential("username", true, null),
                new Credential("password", true, null)
        };
    }

    @Override
    public String getAuthenticationURI() {
        return null;
    }

    @Override
    public Token getForwardableToken(Token originalToken) {
        return null;
    }
}
