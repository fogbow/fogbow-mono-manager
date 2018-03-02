package org.fogbowcloud.manager.core.plugins.identity.vmware;

import com.amazonaws.util.StringInputStream;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
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

        InputStream node = new StringInputStream("{ 'value': '" + fakeToken + "' }");

        StatusLine statusLine = Mockito.mock(StatusLine.class);
        Mockito.doReturn(200).when(statusLine).getStatusCode();

        HttpEntity entity = Mockito.mock(HttpEntity.class);
        Mockito.doReturn(node).when(entity).getContent();

        HttpResponse response = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(statusLine).when(response).getStatusLine();
        Mockito.doReturn(entity).when(response).getEntity();

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

        StatusLine statusLine = Mockito.mock(StatusLine.class);
        Mockito.doReturn(401).when(statusLine).getStatusCode();

        HttpResponse response = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(statusLine).when(response).getStatusLine();

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

        StatusLine statusLine = Mockito.mock(StatusLine.class);
        Mockito.doReturn(503).when(statusLine).getStatusCode();

        HttpResponse response = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(statusLine).when(response).getStatusLine();

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

            StatusLine statusLine = Mockito.mock(StatusLine.class);
            Mockito.doReturn(i).when(statusLine).getStatusCode();

            HttpResponse response = Mockito.mock(HttpResponse.class);
            Mockito.doReturn(statusLine).when(response).getStatusLine();

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
        Mockito.doThrow(IOException.class).when(vmidentity).postRequest(Mockito.anyString(), Mockito.anyString());

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

        InputStream node = new StringInputStream("{ }");

        StatusLine statusLine = Mockito.mock(StatusLine.class);
        Mockito.doReturn(200).when(statusLine).getStatusCode();

        HttpEntity entity = Mockito.mock(HttpEntity.class);
        Mockito.doReturn(node).when(entity).getContent();

        HttpResponse response = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(statusLine).when(response).getStatusLine();
        Mockito.doReturn(entity).when(response).getEntity();

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
