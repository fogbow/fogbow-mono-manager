package org.fogbowcloud.manager.core.plugins.util;

import org.apache.http.StatusLine;

public class HttpResponseWrapper {

	private StatusLine statusLine;
	private String content;
	
	public HttpResponseWrapper(StatusLine statusLine, String content) {
		this.statusLine = statusLine;
		this.content = content;
	}

	public StatusLine getStatusLine() {
		return statusLine;
	}

	public void setStatusLine(StatusLine statusLine) {
		this.statusLine = statusLine;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
	
}
