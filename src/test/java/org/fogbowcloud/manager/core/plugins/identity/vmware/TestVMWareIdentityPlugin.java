package org.fogbowcloud.manager.core.plugins.identity.vmware;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class TestVMWareIdentityPlugin {

    @Test
    public void testCreateToken() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(VMWareIdentityPlugin.IDENTITY_URL, "localhost:9000");

        String fakeUser = "fakeUser";
        String fakePassword = "fakePassword";
        String fakeToken = fakeUser+"-"+fakePassword;

        JsonNode node = new JsonNode("{ 'value': '"+fakeToken+"' }");

        HttpResponse<JsonNode> response = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(200).when(response).getStatus();
        Mockito.doReturn(node).when(response).getBody();

        VMWareIdentityPlugin vmidentity = Mockito.spy(new VMWareIdentityPlugin(properties));
        Mockito.doReturn(response).when(vmidentity).postRequest(Mockito.anyString(), Mockito.anyString());

        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", fakeUser);
        credentials.put("password", fakePassword);

        Token token = vmidentity.createToken(credentials);

        Assert.assertEquals(fakeToken, token.getAccessId());
        Assert.assertEquals(fakeUser, token.getUser().getId());
        Assert.assertEquals(fakeUser, token.getUser().getName());
    }

    @Test
    public void testUnauthorizedCreateToken() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(VMWareIdentityPlugin.IDENTITY_URL, "localhost:9000");

        String fakeUser = "fakeUser";
        String fakePassword = "fakePassword";

        HttpResponse<JsonNode> response = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(401).when(response).getStatus();

        VMWareIdentityPlugin vmidentity = Mockito.spy(new VMWareIdentityPlugin(properties));
        Mockito.doReturn(response).when(vmidentity).postRequest(Mockito.anyString(), Mockito.anyString());

        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", fakeUser);
        credentials.put("password", fakePassword);

        try {
            vmidentity.createToken(credentials);
        } catch (OCCIException e) {
            Assert.assertEquals(ErrorType.UNAUTHORIZED, e.getType());
        }
    }

    @Test
    public void testInternalErrorCreateToken() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(VMWareIdentityPlugin.IDENTITY_URL, "localhost:9000");

        String fakeUser = "fakeUser";
        String fakePassword = "fakePassword";

        HttpResponse<JsonNode> response = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(503).when(response).getStatus();

        VMWareIdentityPlugin vmidentity = Mockito.spy(new VMWareIdentityPlugin(properties));
        Mockito.doReturn(response).when(vmidentity).postRequest(Mockito.anyString(), Mockito.anyString());

        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", fakeUser);
        credentials.put("password", fakePassword);

        try {
            vmidentity.createToken(credentials);
        } catch (OCCIException e) {
            Assert.assertEquals(ErrorType.INTERNAL_SERVER_ERROR, e.getType());
        }
    }

    @Test
    public void testUnknownErrorCreateToken() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(VMWareIdentityPlugin.IDENTITY_URL, "localhost:9000");

        String fakeUser = "fakeUser";
        String fakePassword = "fakePassword";

        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", fakeUser);
        credentials.put("password", fakePassword);

        for (int i = 400; i < 600; i++) {
            if (i == 401 || i == 503) continue;

            HttpResponse<JsonNode> response = Mockito.mock(HttpResponse.class);
            Mockito.doReturn(i).when(response).getStatus();

            VMWareIdentityPlugin vmidentity = Mockito.spy(new VMWareIdentityPlugin(properties));
            Mockito.doReturn(response).when(vmidentity).postRequest(Mockito.anyString(), Mockito.anyString());

            try {
                vmidentity.createToken(credentials);
            } catch (OCCIException e) {
                Assert.assertEquals(ErrorType.NO_VALID_HOST_FOUND, e.getType());
            }
        }
    }

    @Test
    public void testUnirestErrorCreateToken() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(VMWareIdentityPlugin.IDENTITY_URL, "localhost:9000");

        String fakeUser = "fakeUser";
        String fakePassword = "fakePassword";

        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", fakeUser);
        credentials.put("password", fakePassword);

        VMWareIdentityPlugin vmidentity = Mockito.spy(new VMWareIdentityPlugin(properties));
        Mockito.doThrow(UnirestException.class).when(vmidentity).postRequest(Mockito.anyString(), Mockito.anyString());

        try {
            vmidentity.createToken(credentials);
        } catch (OCCIException e) {
            Assert.assertEquals(ErrorType.BAD_REQUEST, e.getType());
        }
    }

    @Test
    public void testJsonErrorCreateToken() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(VMWareIdentityPlugin.IDENTITY_URL, "localhost:9000");

        String fakeUser = "fakeUser";
        String fakePassword = "fakePassword";

        JsonNode node = new JsonNode("{ }");

        HttpResponse<JsonNode> response = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(200).when(response).getStatus();
        Mockito.doReturn(node).when(response).getBody();

        VMWareIdentityPlugin vmidentity = Mockito.spy(new VMWareIdentityPlugin(properties));
        Mockito.doReturn(response).when(vmidentity).postRequest(Mockito.anyString(), Mockito.anyString());

        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", fakeUser);
        credentials.put("password", fakePassword);

        try {
            vmidentity.createToken(credentials);
        } catch (OCCIException e) {
            Assert.assertEquals(ErrorType.BAD_REQUEST, e.getType());
        }
    }
}
