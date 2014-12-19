package org.fogbowcloud.manager.core.plugins.util;

import java.net.URI;

import org.apache.http.client.methods.HttpPost;

public class HttpPatch extends HttpPost {

    public final static String METHOD_NAME = "PATCH";

    public HttpPatch() {
        super();
    }

    public HttpPatch(final URI uri) {
        super();
        setURI(uri);
    }

    public HttpPatch(final String uri) {
        super();
        setURI(URI.create(uri));
    }

    @Override
    public String getMethod() {
        return METHOD_NAME;
    }

}
