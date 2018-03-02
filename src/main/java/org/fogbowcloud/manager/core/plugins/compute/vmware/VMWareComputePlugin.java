package org.fogbowcloud.manager.core.plugins.compute.vmware;

import org.apache.commons.io.Charsets;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.util.HttpRequestUtil;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Request;
import org.restlet.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class VMWareComputePlugin implements ComputePlugin {

    private final static Logger LOGGER = Logger.getLogger(VMWareComputePlugin.class);

    // TODO move strings to final static field

    private HttpClient client;

    public VMWareComputePlugin(Properties properties) {
        // TODO check what properties is gonna be needed and receive them here
    }

    HttpClient getClient() {
        if (client == null) {
            client = HttpRequestUtil.createHttpClient();
        }
        return client;
    }

    HttpResponse doRequest(String endpoint, String method, String accessId) throws IOException {
        return doRequest(endpoint, method, accessId, null);
    }

    HttpResponse doRequest(String endpoint, String method, String accessId, JSONObject data) throws IOException {
        LOGGER.trace("Executing request");
        HttpUriRequest request;
        // TODO complete methods
        HttpEntity entity;
        switch (method) {
            case "GET":
                request = new HttpGet(endpoint);
                break;
            case "POST":
                request = new HttpPost(endpoint);
                entity = new ByteArrayEntity(data.toString().getBytes(Charsets.UTF_8));
                ((HttpPost) request).setEntity(entity);
                break;
            case "PUT":
                request = new HttpPut(endpoint);
                entity = new ByteArrayEntity(data.toString().getBytes(Charsets.UTF_8));
                ((HttpPut) request).setEntity(entity);
                break;
            case "PATCH":
                request = new HttpPatch(endpoint);
                entity = new ByteArrayEntity(data.toString().getBytes(Charsets.UTF_8));
                ((HttpPatch) request).setEntity(entity);
                break;
            case "DELETE":
                request = new HttpDelete(endpoint);
                break;
            default:
                throw new OCCIException(ErrorType.METHOD_NOT_ALLOWED, "Received incorrect http method.");
        }
        request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
        request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
        request.addHeader(OCCIHeaders.COOKIE, "vmware-api-session-id=" + accessId);
        return getClient().execute(request);
    }

    @Override
    public String requestInstance(Token token, List<Category> categories, Map<String, String> xOCCIAtt, String imageId) {
        LOGGER.trace("Requesting instance");
        JSONObject data;
        try {
            data = createInstanceRequestData(xOCCIAtt, imageId);
        } catch (JSONException e) {
            LOGGER.error("Failed creating request body for instance creation.", e);
            throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
        }

        HttpResponse response;
        try {
            response = doRequest("", "POST", token.getAccessId(), data);
        } catch (IOException e) {
            LOGGER.error("Failed requesting instance to VMWare.", e);
            throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.INTERNAL_ERROR);
        }

        JSONObject json = extractJsonFromResponse(response);
        if (response.getStatusLine().getStatusCode() == 200) {
            try {
                return (String) json.get("value");
            } catch (JSONException e) {
                LOGGER.error("Response JSON from VMWare did not contain the Instance Id.", e);
                throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.IRREGULAR_SYNTAX);
            }
        } else {
            String error;
            try {
                error = (String) json.get("type");
            } catch (JSONException e) {
                LOGGER.error("Response JSON from VMWare did not contain the Error Type.", e);
                throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.IRREGULAR_SYNTAX);
            }
            // TODO treat this cases in a separate class or method
            // TODO improve messages, maybe use the message returned within the JSON
            switch (error) {
                case "com.vmware.vapi.std.errors.already_exists":
                    LOGGER.error("A VM with given name already exists.");
                    throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.INVALID_LABEL);
                case "com.vmware.vapi.std.errors.invalid_argument":
                    LOGGER.error("Invalid argument passed to VM creation.");
                    throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.IRREGULAR_SYNTAX);
                case "com.vmware.vapi.std.errors.resource_inaccessible":
                    LOGGER.error("The cluster, host, or resource pool is inaccessible.");
                    throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.NO_VALID_HOST_FOUND);
                case "com.vmware.vapi.std.errors.resource_in_use":
                    LOGGER.error("There is a conflict with the storage specified.");
                    throw new OCCIException(
                            ErrorType.INTERNAL_SERVER_ERROR,
                            ResponseConstants.NOT_FOUND_STORAGE_LINK_ATTRIBUTE
                    );
                case "com.vmware.vapi.std.errors.unable_to_allocate_resource":
                    LOGGER.error("Could not allocate some resource needed for the VM.");
                    throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.ORDER_NOT_CREATED);
                case "com.vmware.vapi.std.errors.unsupported":
                    LOGGER.error("Specified Guest OS is not supported.");
                    throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.IMAGES_NOT_SPECIFIED);
                case "com.vmware.vapi.std.errors.error":
                    LOGGER.error("An error occurred during the VM creation");
                    throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.INTERNAL_ERROR);
                case "com.vmware.vapi.std.errors.not_found":
                    LOGGER.error("Some resource specified was not found.");
                    throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.IMAGES_NOT_SPECIFIED);
                case "com.vmware.vapi.std.errors.service_unavailable":
                    LOGGER.error("Could not complete the request because on of the services did not respond.");
                    throw new OCCIException(ErrorType.SERVICE_UNAVAILABLE, ResponseConstants.SERVICE_UNAVAILABLE);
                case "com.vmware.vapi.std.errors.unauthenticated":
                    LOGGER.error("Requires authentication to create VM.");
                    throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
                case "com.vmware.vapi.std.errors.unauthorized":
                    LOGGER.error("User is unauthorized to create VM.");
                    throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED_USER);
                default:
                    LOGGER.error("Unknown error type [" + error + "].");
                    throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.IRREGULAR_SYNTAX);
            }
        }
    }

    @NotNull
    JSONObject extractJsonFromResponse(HttpResponse response) {
        LOGGER.trace("Extracting JSON from response");
        byte[] bodyData;
        try {
            InputStream body = response.getEntity().getContent();
            bodyData = new byte[body.available()];
            int read = body.read(bodyData);
            if (read != bodyData.length) {
                LOGGER.warn("Amount of data read from request response differs from how much it said was available");
            }
        } catch (IOException e) {
            LOGGER.error("Failed reading response from VMWare.", e);
            throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.INTERNAL_ERROR);
        }

        JSONObject json;
        try {
            json = new JSONObject(new String(bodyData));
        } catch (JSONException e) {
            LOGGER.error("Response from VMWare was not a JSON.", e);
            throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.IRREGULAR_SYNTAX);
        }
        return json;
    }

    JSONObject createInstanceRequestData(Map<String, String> xOCCIAtt,String imageId) throws JSONException {
        LOGGER.trace("Creating request data");
        JSONObject vmDetails = new JSONObject();

        JSONObject specs = new JSONObject();
        specs.put("name", "fogbow-instance");
        specs.put("guest_OS", imageId);

        JSONObject placement = new JSONObject();
        // TODO change to value received from properties
        placement.put("datastore", "defaultDatastore");
        // TODO change to value received from properties
        placement.put("folder", "defaultFolder");
        // TODO change to value received from properties
        placement.put("resource_pool", "defaultResourcePool");
        specs.put("placement", placement);

        String memSize = RequirementsHelper.getSmallestValueForAttribute(
                xOCCIAtt.get(RequirementsHelper.GLUE_MEM_RAM_TERM),
                RequirementsHelper.GLUE_MEM_RAM_TERM
        );
        if (memSize == null || memSize.isEmpty()) {
            // TODO change to value received from properties
            memSize = "1024";
        }
        JSONObject memory = new JSONObject();
        memory.put("size_MiB", Long.parseLong(memSize));
        memory.put("hot_add_enabled", false);
        specs.put("memory", memory);

        String diskSize = RequirementsHelper.getSmallestValueForAttribute(
                xOCCIAtt.get(RequirementsHelper.GLUE_DISK_TERM),
                RequirementsHelper.GLUE_DISK_TERM
        );
        if (diskSize == null || diskSize.isEmpty()) {
            // TODO change to value received from properties
            diskSize = "3.436e+10";
        }
        JSONArray disks = new JSONArray();
        JSONObject vmdk = new JSONObject();
        vmdk.put("capacity", Long.parseLong(diskSize));
//        TODO decide what naming convention to use, unset means VMWare will choose for you
//        vmdk.put("name", "name");
        disks.put(vmdk);
        specs.put("disks", disks);

        String cpuSize = RequirementsHelper.getSmallestValueForAttribute(
                xOCCIAtt.get(RequirementsHelper.GLUE_VCPU_TERM),
                RequirementsHelper.GLUE_VCPU_TERM
        );
        if (cpuSize == null || cpuSize.isEmpty()) {
            // TODO change to value received from properties
            cpuSize = "1";
        }
        JSONObject cpu = new JSONObject();
        memory.put("count", Long.parseLong(cpuSize));
        memory.put("cores_per_socket", Long.parseLong(cpuSize));
        memory.put("hot_add_enabled", false);
        memory.put("hot_remove_enabled", false);
        specs.put("cpu", cpu);

        return vmDetails;
    }

    @Override
    public List<Instance> getInstances(Token token) {
        return null;
    }

    @Override
    public Instance getInstance(Token token, String instanceId) {
        return null;
    }

    @Override
    public void removeInstance(Token token, String instanceId) {

    }

    @Override
    public void removeInstances(Token token) {

    }

    @Override
    public ResourcesInfo getResourcesInfo(Token token) {
        return null;
    }

    @Override
    public void bypass(Request request, Response response) {

    }

    @Override
    public void uploadImage(Token token, String imagePath, String imageName, String diskFormat) {

    }

    @Override
    public String getImageId(Token token, String imageName) {
        return null;
    }

    @Override
    public ImageState getImageState(Token token, String imageName) {
        return null;
    }

    @Override
    public String attach(Token token, List<Category> categories, Map<String, String> xOCCIAtt) {
        return null;
    }

    @Override
    public void dettach(Token token, List<Category> categories, Map<String, String> xOCCIAtt) {

    }
}
