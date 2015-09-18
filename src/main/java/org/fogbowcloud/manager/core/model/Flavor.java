package org.fogbowcloud.manager.core.model;

public class Flavor {

	private Integer capacity;
	private String name;
	private String id;
	
	/**
	 * Number of cores of the CPU.
	 */
	private String cpu;
	
	/**
	 * RAM memory in MB.
	 */
	private String memInMB;
	
	/**
	 * Disk in GB.
	 */
	private String disk;

	public Flavor(String name, String cpu, String memInMB, String disk) {
		this.setName(name);
		this.setCpu(cpu);
		this.setMem(memInMB);
		this.setDisk(disk);
	}
	
	public Flavor(String name, String cpu, String memInMB, Integer capacity) {
		this.setName(name);
		this.setCpu(cpu);
		this.setMem(memInMB);
		this.setCapacity(capacity);
	}
	
	public Flavor(String name, String id, String cpu, String memInMB, String disk) {
		this.setName(name);
		this.setCpu(cpu);
		this.setMem(memInMB);
		this.setDisk(disk);
		this.setId(id);
	}
	
	public Flavor(String name, String cpu, String memInMB, String disk, Integer capacity) {		
		this.setCapacity(capacity);
		this.setName(name);
		this.setCpu(cpu);
		this.setMem(memInMB);
		this.setDisk(disk);
	}

	public String getDisk() {
		return disk;
	}
	
	public void setDisk(String disk) {
		this.disk = disk;
	}

	public Integer getCapacity() {
		return capacity;
	}

	public void setCapacity(Integer capacity) {
		this.capacity = capacity;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCpu() {
		return cpu;
	}

	public void setCpu(String cpu) {
		this.cpu = cpu;
	}

	public String getMem() {
		return memInMB;
	}

	public void setMem(String mem) {
		this.memInMB = mem;
	}
	
	public String getId() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Flavor) {
			Flavor otherFlavor = (Flavor) obj;
			return otherFlavor.getName().equals(getName());
		}
		return false;
	}
	
	@Override
	public String toString() {
		return "Name: " + getName() + ", cpu: " + cpu + ", mem: " + memInMB + ", capacity: " + capacity;
	}
}
