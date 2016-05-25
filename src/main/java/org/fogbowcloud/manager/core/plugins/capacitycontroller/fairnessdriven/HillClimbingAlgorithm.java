package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

public class HillClimbingAlgorithm {
	
	private boolean increasing;
	private double deltaC;
	private double minimumThreshold, maximumThreshold;
	private double maximumCapacityToSupply;	
	private final double MAXIMUM_CAPACITY;
	
	private double lastFairness, currentFairness;
	private long lastUpdated;	
	
	public HillClimbingAlgorithm(double deltaC,
			double minimumThreshold, double maximumThreshold, double maximumCapacityOfPeer) {
		increasing = false;
		
		if(minimumThreshold < 0 || maximumThreshold < 0 || deltaC > 1 || deltaC < 0)
			throw new IllegalArgumentException("Unexpected argument for the FDController: "+this+"\n"
					+ "Any of this conditions were (but shouldn't be) satisfied: minimumThreshold < 0 || maximumThreshold < 0 || deltaC > 1 || deltaC < 0");
		this.deltaC = deltaC;
		this.minimumThreshold = minimumThreshold;
		this.maximumThreshold = maximumThreshold;
		this.maximumCapacityToSupply = maximumCapacityOfPeer;
		this.MAXIMUM_CAPACITY = maximumCapacityOfPeer;
		
		currentFairness = lastFairness = -1;
		lastUpdated = 0;
	}
	
	@Override
	public String toString() {
		return "Params of HillClimbing Algorithm - minimumThreshold: "+minimumThreshold+", "
				+ "maximumThreshold: "+maximumThreshold+", deltaC: "+deltaC+".";
	}
	
	public void updateCapacity(){
		if(currentFairness>=0 && lastFairness>=0){
			if(currentFairness < minimumThreshold)
				increasing = false;
			else if(currentFairness > maximumThreshold)
				increasing = true;
			else if(currentFairness <= lastFairness)
					increasing = !increasing;
			
			if(increasing)
				maximumCapacityToSupply = Math.min(MAXIMUM_CAPACITY, maximumCapacityToSupply + (deltaC * MAXIMUM_CAPACITY));
			else
				maximumCapacityToSupply = Math.max(0, maximumCapacityToSupply - (deltaC * MAXIMUM_CAPACITY));
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
