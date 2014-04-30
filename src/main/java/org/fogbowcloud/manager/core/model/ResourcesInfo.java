package org.fogbowcloud.manager.core.model;

import java.security.cert.Certificate;
import java.util.List;

public class ResourcesInfo {
	
	private String id;
	private String cpuIdle;
	private String cpuInUse;
	private String memIdle;
	private String memInUse;
	private List<Flavor> flavours;
	private Certificate cert;
	
	public ResourcesInfo(String id, String cpuIdle, String cpuInUse,
			String memIdle, String memInUse, List<Flavor> flavours, Certificate cert) {
		setCert(cert);
		setId(id);
		setCpuIdle(cpuIdle);
		setCpuInUse(cpuInUse);
		setMemIdle(memIdle);
		setMemInUse(memInUse);
		setFlavours(flavours);
	}
	
	public ResourcesInfo(String cpuIdle, String cpuInUse,
			String memIdle, String memInUse, List<Flavor> flavours, Certificate cert) {
		this(null, cpuIdle, cpuInUse, memIdle, memInUse, flavours, cert);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCpuIdle() {
		return cpuIdle;
	}

	public void setCpuIdle(String cpuIdle) {
		if (cpuIdle == null) {
			throw new IllegalArgumentException(
					"ResourceInfo cpu-idle is invalid.");
		}
		this.cpuIdle = cpuIdle;
	}

	public String getCpuInUse() {
		return cpuInUse;
	}

	public void setCpuInUse(String cpuInUse) {
		if (cpuInUse == null) {
			throw new IllegalArgumentException(
					"ResourceInfo cpu-inuse is invalid.");
		}
		this.cpuInUse = cpuInUse;
	}

	public String getMemIdle() {
		return memIdle;
	}

	public void setMemIdle(String memIdle) {
		if (memIdle == null) {
			throw new IllegalArgumentException(
					"ResourceInfo mem-idle is invalid.");
		}
		this.memIdle = memIdle;
	}

	public String getMemInUse() {
		return memInUse;
	}

	public void setMemInUse(String memInUse) {
		if (memInUse == null) {
			throw new IllegalArgumentException(
					"ResourceInfo mem-inuse is invalid.");
		}
		this.memInUse = memInUse;
	}

	public List<Flavor> getFlavours() {
		return flavours;
	}

	public void setFlavours(List<Flavor> flavours) {
		this.flavours = flavours;
	}

	public Certificate getCert() {
		return cert;
	}

	public void setCert(Certificate cert) {
		this.cert = cert;
	}
}
