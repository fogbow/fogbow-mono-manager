package org.fogbowcloud.manager.core.model;

//TODO rename this class to Flavor
public class Flavour {
	
	private int capacity;
	private String name;
	private String cpu;
	private String mem;

	public Flavour(String name, String cpu, String mem, int capacity) {
		this.setName(name);
		this.setCpu(cpu);
		this.setMem(mem);
		this.setCapacity(capacity);
		
	}

	public int getCapacity() {
		return capacity;
	}

	public void setCapacity(int capacity) {
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
		return mem;
	}

	public void setMem(String mem) {
		this.mem = mem;
	}
}
