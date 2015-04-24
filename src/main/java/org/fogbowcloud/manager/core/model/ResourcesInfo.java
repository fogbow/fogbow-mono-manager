package org.fogbowcloud.manager.core.model;


public class ResourcesInfo {
	
	private String id;
	private String cpuIdle;
	private String cpuInUse;
	private String memIdle;
	private String memInUse;
	private String instancesIdle;
	private String instancesInUse;
	
	public ResourcesInfo(String id, String cpuIdle, String cpuInUse,
			String memIdle, String memInUse, 
			String instancesIdle, String instancesInUses) {
		setId(id);
		setCpuIdle(cpuIdle);
		setCpuInUse(cpuInUse);
		setMemIdle(memIdle);
		setMemInUse(memInUse);
		setInstancesIdle(instancesIdle);
		setInstancesInUse(instancesIdle);
	}
	
	public ResourcesInfo(String cpuIdle, String cpuInUse,
			String memIdle, String memInUse, 
			String instancesIdle, String instancesInUse) {
		this(null, cpuIdle, cpuInUse, memIdle, memInUse, 
				instancesIdle, instancesInUse);
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
	
	public void setInstancesIdle(String instancesIdle) {
		this.instancesIdle = instancesIdle;
	}
	
	public String getInstancesIdle() {
		return instancesIdle;
	}
	
	public void setInstancesInUse(String instancesInUse) {
		this.instancesInUse = instancesInUse;
	}
	
	public String getInstancesInUse() {
		return instancesInUse;
	}
}
