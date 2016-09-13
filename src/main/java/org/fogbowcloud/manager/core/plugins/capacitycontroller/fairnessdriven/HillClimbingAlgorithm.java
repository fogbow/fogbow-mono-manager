package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

public class HillClimbingAlgorithm {
	
	private static final int INITIAL_VALUE = -1;
	private boolean increasing;
	private double deltaC;
	private double minimumThreshold, maximumThreshold;
	private double maximumCapacityToSupply;	
	
	private double lastFairness, currentFairness;
	private long lastUpdated;	
	
	public HillClimbingAlgorithm(double deltaC, double minimumThreshold, double maximumThreshold) {
		this.increasing = false;
		
		if (minimumThreshold < 0 || maximumThreshold < 0 || deltaC > 1 || deltaC < 0)
			throw new IllegalArgumentException("Unexpected argument for the FDController: " + 
					this + "\n Any of this conditions were (but shouldn't be) satisfied: "
					+ "minimumThreshold < 0 || maximumThreshold < 0 || deltaC > 1 || deltaC < 0");
		this.deltaC = deltaC;
		this.minimumThreshold = minimumThreshold;
		this.maximumThreshold = maximumThreshold;
		
		this.maximumCapacityToSupply = INITIAL_VALUE;
		this.currentFairness = INITIAL_VALUE; 
		this.lastFairness = INITIAL_VALUE;
		this.lastUpdated = 0;
	}
	
	@Override
	public String toString() {
		return "Params of HillClimbing Algorithm - minimumThreshold: "
				+ minimumThreshold + ", " + "maximumThreshold: "
				+ maximumThreshold + ", deltaC: " + deltaC + ".";
	}
	
	public void updateCapacity(double maximumCapacity) {
		if (this.maximumCapacityToSupply == INITIAL_VALUE) {
			this.maximumCapacityToSupply = maximumCapacity;
		}
		if (this.currentFairness >= 0 && this.lastFairness >= 0) {
			if(this.currentFairness < this.minimumThreshold) {
				this.increasing = false;				
			} else if (this.currentFairness > this.maximumThreshold) {
				this.increasing = true;
			} else if (this.currentFairness <= this.lastFairness) {
				this.increasing = !this.increasing;
			}
			if (increasing) {
				this.maximumCapacityToSupply = Math.min(maximumCapacity,
						this.maximumCapacityToSupply + (this.deltaC * maximumCapacity));
			} else {
				this.maximumCapacityToSupply = Math.max(0,
						this.maximumCapacityToSupply - (this.deltaC * maximumCapacity));
			}
		}
	}
	
	public double getMaximumCapacityToSupply() {
		return maximumCapacityToSupply;
	}
	
	public double getCurrentFairness() {
		return currentFairness;
	}
	
	public void setCurrentFairness(double currentFairness) {
		this.currentFairness = currentFairness;
	} 
	
	public double getLastFairness() {
		return lastFairness;
	}
	
	public long getLastUpdated() {
		return lastUpdated;
	}
	
	public void setLastFairness(double lastFairness) {
		this.lastFairness = lastFairness;
	}
	
	public void setLastUpdated(long lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

}
