package org.fogbowcloud.manager.core.model;

import java.util.LinkedList;
import java.util.List;

public class ResourcesInfo {
	
	private String id;
	private String cpuIdle;
	private String cpuInUse;
	private String memIdle;
	private String memInUse;
	private List<Flavour> flavours;
	
	public ResourcesInfo(String id, String cpuIdle, String cpuInUse,
			String memIdle, String memInUse, List<Flavour> flavours) {
		setId(id);
		setCpuIdle(cpuIdle);
		setCpuInUse(cpuInUse);
		setMemIdle(memIdle);
		setMemInUse(memInUse);
		this.setFlavours(flavours);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("ResourceInfo id is invalid.");
		}
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

	public List<Flavour> getFlavours() {
		return flavours;
	}

	public void setFlavours(List<Flavour> flavours) {
		this.flavours = flavours;
	}
}
