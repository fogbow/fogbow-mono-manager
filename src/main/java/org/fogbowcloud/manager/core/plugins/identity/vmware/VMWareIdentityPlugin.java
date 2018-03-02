package org.fogbowcloud.manager.core.plugins.identity.vmware;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.core.util.HttpRequestUtil;
import org.fogbowcloud.manager.occi.model.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class VMWareIdentityPlugin implements IdentityPlugin {

    private final static Logger LOGGER = Logger.getLogger(VMWareIdentityPlugin.class);

    private static final String VCENTER_AUTH_ENDPOINT = "/rest/com/vmware/cis/session";

    static final String IDENTITY_URL = "identity_url";
    static final String AUTH_URL = "authUrl";

    private HttpClient client;
    private String vcenterUrl;
    private String vcenterTokenEndpoint;

    public VMWareIdentityPlugin(Properties properties) throws Exception {
        readProperties(properties);
        this.vcenterTokenEndpoint = vcenterUrl + VCENTER_AUTH_ENDPOINT;
    }

    private void readProperties(Properties properties) throws Exception {
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
            HttpResponse response = postRequest(username, password);
            if (response.getStatusLine().getStatusCode() >= 200 && response.getStatusLine().getStatusCode() < 300) {
                JSONObject json = extractJsonFromResponse(response);
                String tk = (String) json.get("value");
                return new Token(tk, new Token.User(username, username), new Date(), new HashMap<>());
            } else if (response.getStatusLine().getStatusCode() == 401) {
                LOGGER.error("VMWare Unauthorized");
                throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.INVALID_USER_OR_PASSWORD);
            } else if (response.getStatusLine().getStatusCode() == 503) {
                LOGGER.error("VMWare Failed to respond");
                throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.INTERNAL_ERROR);
            } else {
                LOGGER.error("VMWare Respondend with unknown code");
                throw new OCCIException(ErrorType.NO_VALID_HOST_FOUND, ResponseConstants.INTERNAL_ERROR);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to generate token.", e);
            throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
        } catch (JSONException e) {
            LOGGER.error("Response body had a malformed JSON.", e);
            throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
        }
    }

    @NotNull
    JSONObject extractJsonFromResponse(HttpResponse response) throws IOException, JSONException {
        InputStream body = response.getEntity().getContent();
        byte[] bodyData = new byte[body.available()];
        int read = body.read(bodyData);
        if (read != bodyData.length) {
            LOGGER.warn("Amount of data read from request response differs from how much it said was available");
        }
        return new JSONObject(new String(bodyData));
    }

    HttpResponse postRequest(String username, String password) throws IOException {
        HttpPost request = new HttpPost(vcenterTokenEndpoint);
        request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
        request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
        request.addHeader(OCCIHeaders.AUTHORIZATION, "BASIC " + makeBasicAuth(username, password));
        return getClient().execute(request);
    }

    private String makeBasicAuth(String username, String password) {
        return new String(
                Base64.encodeBase64((username+":"+password).getBytes(Charsets.UTF_8))
        );
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

    private HttpClient getClient() {
        if (client == null) {
            client = HttpRequestUtil.createHttpClient();
        }
        return client;
    }
}
