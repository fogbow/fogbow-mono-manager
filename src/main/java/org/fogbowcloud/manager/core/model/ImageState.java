package org.fogbowcloud.manager.core.model;


public enum ImageState {

	PENDING("pending"), ACTIVE("active"), FAILED("failed");

	private String value;

	private ImageState(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}
	
	public boolean in(ImageState... imageStates) {
		for (ImageState imageState : imageStates) {
			if (imageState.equals(this)){
				return true;
			}
		}
		return false;
	}	
}
