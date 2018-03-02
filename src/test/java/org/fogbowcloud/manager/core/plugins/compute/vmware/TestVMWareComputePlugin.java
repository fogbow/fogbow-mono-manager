package org.fogbowcloud.manager.core.plugins.compute.vmware;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.data.Status;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class TestVMWareComputePlugin {

    private static final String FAKE_INSTANCE_ID = "fake-instance-id";

    @Test
    public void testRequestInstance() throws JSONException, IOException {
        StatusLine status = Mockito.mock(StatusLine.class);
        Mockito.doReturn(200).when(status).getStatusCode();

        HttpResponse response = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(status).when(response).getStatusLine();

        JSONObject respData = Mockito.mock(JSONObject.class);
        Mockito.doReturn(FAKE_INSTANCE_ID).when(respData).get(Mockito.eq("value"));

        VMWareComputePlugin plugin = Mockito.mock(VMWareComputePlugin.class);
        Mockito.doReturn(null).when(plugin).createInstanceRequestData(
                Mockito.anyMap(),
                Mockito.anyString()
        );
        Mockito.doReturn(response).when(plugin).doRequest(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(JSONObject.class)
        );
        Mockito.doReturn(respData).when(plugin).extractJsonFromResponse(Mockito.eq(response));
        Mockito.doCallRealMethod().when(plugin).requestInstance(
                Mockito.any(Token.class),
                Mockito.anyList(),
                Mockito.anyMap(),
                Mockito.anyString()
        );

        Token token = new Token("", null, new Date(), new HashMap<>());
        Assert.assertEquals(
                FAKE_INSTANCE_ID,
                plugin.requestInstance(token, new ArrayList<>(), new HashMap<>(), "")
        );
    }

    @Test
    public void testRequestInstanceFailureCreateRequest() throws JSONException {
        VMWareComputePlugin plugin = Mockito.mock(VMWareComputePlugin.class);
        Mockito.doThrow(JSONException.class).when(plugin).createInstanceRequestData(
                Mockito.anyMap(),
                Mockito.anyString()
        );
        Mockito.doCallRealMethod().when(plugin).requestInstance(
                Mockito.any(Token.class),
                Mockito.anyList(),
                Mockito.anyMap(),
                Mockito.anyString()
        );

        Token token = new Token("", null, new Date(), new HashMap<>());
        try {
            plugin.requestInstance(token, new ArrayList<>(), new HashMap<>(), "");
            Assert.fail();
        } catch (OCCIException e) {
            Assert.assertEquals(ErrorType.BAD_REQUEST, e.getType());
            Assert.assertEquals(Status.CLIENT_ERROR_BAD_REQUEST.getReasonPhrase(), e.getMessage());
        }
    }

    @Test
    public void testRequestInstanceFailureExecuteRequest() throws JSONException, IOException {
        VMWareComputePlugin plugin = Mockito.mock(VMWareComputePlugin.class);
        Mockito.doReturn(null).when(plugin).createInstanceRequestData(
                Mockito.anyMap(),
                Mockito.anyString()
        );
        Mockito.doThrow(IOException.class).when(plugin).doRequest(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(JSONObject.class)
        );
        Mockito.doCallRealMethod().when(plugin).requestInstance(
                Mockito.any(Token.class),
                Mockito.anyList(),
                Mockito.anyMap(),
                Mockito.anyString()
        );

        Token token = new Token("", null, new Date(), new HashMap<>());
        try {
            plugin.requestInstance(token, new ArrayList<>(), new HashMap<>(), "");
            Assert.fail();
        } catch (OCCIException e) {
            Assert.assertEquals(ErrorType.INTERNAL_SERVER_ERROR, e.getType());
            Assert.assertEquals(Status.SERVER_ERROR_INTERNAL.getReasonPhrase(), e.getMessage());
        }
    }

    @Test
    public void testRequestInstanceSuccessButMalformedJSON() throws JSONException, IOException {
        StatusLine status = Mockito.mock(StatusLine.class);
        Mockito.doReturn(200).when(status).getStatusCode();

        HttpResponse response = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(status).when(response).getStatusLine();

        JSONObject respData = Mockito.mock(JSONObject.class);
        Mockito.doThrow(JSONException.class).when(respData).get(Mockito.anyString());

        VMWareComputePlugin plugin = Mockito.mock(VMWareComputePlugin.class);
        Mockito.doReturn(null).when(plugin).createInstanceRequestData(
                Mockito.anyMap(),
                Mockito.anyString()
        );
        Mockito.doReturn(response).when(plugin).doRequest(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(JSONObject.class)
        );
        Mockito.doReturn(respData).when(plugin).extractJsonFromResponse(Mockito.eq(response));
        Mockito.doCallRealMethod().when(plugin).requestInstance(
                Mockito.any(Token.class),
                Mockito.anyList(),
                Mockito.anyMap(),
                Mockito.anyString()
        );

        Token token = new Token("", null, new Date(), new HashMap<>());
        try {
            plugin.requestInstance(token, new ArrayList<>(), new HashMap<>(), "");
            Assert.fail();
        } catch (OCCIException e) {
            Assert.assertEquals(ErrorType.INTERNAL_SERVER_ERROR, e.getType());
            Assert.assertEquals(Status.SERVER_ERROR_INTERNAL.getReasonPhrase(), e.getMessage());
        }
    }

    @Test
    public void testRequestInstanceErrorAndMalformedJSON() throws JSONException, IOException {
        StatusLine status = Mockito.mock(StatusLine.class);
        Mockito.doReturn(400).when(status).getStatusCode();

        HttpResponse response = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(status).when(response).getStatusLine();

        JSONObject respData = Mockito.mock(JSONObject.class);
        Mockito.doThrow(JSONException.class).when(respData).get(Mockito.anyString());

        VMWareComputePlugin plugin = Mockito.mock(VMWareComputePlugin.class);
        Mockito.doReturn(null).when(plugin).createInstanceRequestData(
                Mockito.anyMap(),
                Mockito.anyString()
        );
        Mockito.doReturn(response).when(plugin).doRequest(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(JSONObject.class)
        );
        Mockito.doReturn(respData).when(plugin).extractJsonFromResponse(Mockito.eq(response));
        Mockito.doCallRealMethod().when(plugin).requestInstance(
                Mockito.any(Token.class),
                Mockito.anyList(),
                Mockito.anyMap(),
                Mockito.anyString()
        );

        Token token = new Token("", null, new Date(), new HashMap<>());
        try {
            plugin.requestInstance(token, new ArrayList<>(), new HashMap<>(), "");
            Assert.fail();
        } catch (OCCIException e) {
            Assert.assertEquals(ErrorType.INTERNAL_SERVER_ERROR, e.getType());
            Assert.assertEquals(Status.SERVER_ERROR_INTERNAL.getReasonPhrase(), e.getMessage());
        }
    }

    private void testTreatingRequestInstanceErrors(String error, ErrorType errorType, Status serverErrorInternal)
            throws JSONException, IOException {
        StatusLine status = Mockito.mock(StatusLine.class);
        Mockito.doReturn(400).when(status).getStatusCode();

        HttpResponse response = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(status).when(response).getStatusLine();

        JSONObject respData = Mockito.mock(JSONObject.class);
        Mockito.doReturn(error).when(respData).get(Mockito.eq("type"));

        VMWareComputePlugin plugin = Mockito.mock(VMWareComputePlugin.class);
        Mockito.doReturn(null).when(plugin).createInstanceRequestData(
                Mockito.anyMap(),
                Mockito.anyString()
        );
        Mockito.doReturn(response).when(plugin).doRequest(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(JSONObject.class)
        );
        Mockito.doReturn(respData).when(plugin).extractJsonFromResponse(Mockito.eq(response));
        Mockito.doCallRealMethod().when(plugin).requestInstance(
                Mockito.any(Token.class),
                Mockito.anyList(),
                Mockito.anyMap(),
                Mockito.anyString()
        );

        Token token = new Token("", null, new Date(), new HashMap<>());
        try {
            plugin.requestInstance(token, new ArrayList<>(), new HashMap<>(), "");
            Assert.fail();
        } catch (OCCIException e) {
            Assert.assertEquals(errorType, e.getType());
            Assert.assertEquals(serverErrorInternal.getReasonPhrase(), e.getMessage());
        }
    }

    @Test
    public void testRequestInstanceErrorAlreadyExists() throws JSONException, IOException {
        String error = "com.vmware.vapi.std.errors.already_exists";
        testTreatingRequestInstanceErrors(error, ErrorType.INTERNAL_SERVER_ERROR, Status.SERVER_ERROR_INTERNAL);
    }

    @Test
    public void testRequestInstanceErrorInvalidArgument() throws JSONException, IOException {
        String error = "com.vmware.vapi.std.errors.invalid_argument";
        testTreatingRequestInstanceErrors(error, ErrorType.INTERNAL_SERVER_ERROR, Status.SERVER_ERROR_INTERNAL);
    }

    @Test
    public void testRequestInstanceErrorResourceInaccessible() throws JSONException, IOException {
        String error = "com.vmware.vapi.std.errors.resource_inaccessible";
        testTreatingRequestInstanceErrors(error, ErrorType.INTERNAL_SERVER_ERROR, Status.SERVER_ERROR_INTERNAL);
    }

    @Test
    public void testRequestInstanceErrorResourceInUse() throws JSONException, IOException {
        String error = "com.vmware.vapi.std.errors.resource_in_use";
        testTreatingRequestInstanceErrors(error, ErrorType.INTERNAL_SERVER_ERROR, Status.SERVER_ERROR_INTERNAL);
    }

    @Test
    public void testRequestInstanceErrorUnableToAllocateResource() throws JSONException, IOException {
        String error = "com.vmware.vapi.std.errors.unable_to_allocate_resource";
        testTreatingRequestInstanceErrors(error, ErrorType.INTERNAL_SERVER_ERROR, Status.SERVER_ERROR_INTERNAL);
    }

    @Test
    public void testRequestInstanceErrorUnsupported() throws JSONException, IOException {
        String error = "com.vmware.vapi.std.errors.unsupported";
        testTreatingRequestInstanceErrors(error, ErrorType.INTERNAL_SERVER_ERROR, Status.SERVER_ERROR_INTERNAL);
    }

    @Test
    public void testRequestInstanceErrorGeneralError() throws JSONException, IOException {
        String error = "com.vmware.vapi.std.errors.error";
        testTreatingRequestInstanceErrors(error, ErrorType.INTERNAL_SERVER_ERROR, Status.SERVER_ERROR_INTERNAL);
    }

    @Test
    public void testRequestInstanceErrorNotFound() throws JSONException, IOException {
        String error = "com.vmware.vapi.std.errors.not_found";
        testTreatingRequestInstanceErrors(error, ErrorType.NOT_FOUND, Status.CLIENT_ERROR_NOT_FOUND);
    }

    @Test
    public void testRequestInstanceErrorServiceUnavailable() throws JSONException, IOException {
        String error = "com.vmware.vapi.std.errors.service_unavailable";
        testTreatingRequestInstanceErrors(error, ErrorType.SERVICE_UNAVAILABLE, Status.SERVER_ERROR_SERVICE_UNAVAILABLE);
    }

    @Test
    public void testRequestInstanceErrorUnauthenticated() throws JSONException, IOException {
        String error = "com.vmware.vapi.std.errors.unauthenticated";
        testTreatingRequestInstanceErrors(error, ErrorType.UNAUTHORIZED, Status.CLIENT_ERROR_UNAUTHORIZED);
    }

    @Test
    public void testRequestInstanceErrorUnauthorized() throws JSONException, IOException {
        String error = "com.vmware.vapi.std.errors.unauthorized";
        testTreatingRequestInstanceErrors(error, ErrorType.UNAUTHORIZED, Status.CLIENT_ERROR_UNAUTHORIZED);
    }
}
