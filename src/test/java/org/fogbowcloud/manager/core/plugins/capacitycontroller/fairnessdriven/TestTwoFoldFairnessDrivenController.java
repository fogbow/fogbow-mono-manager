package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;


public class TestTwoFoldFairnessDrivenController {
	
	private TwoFoldCapacityController twoFoldFdController;
	private FairnessDrivenCapacityController pairwiseController;
	private FairnessDrivenCapacityController globalController;
	
	@Before
	public void setUp() {
		this.twoFoldFdController = new TwoFoldCapacityController();
		
		this.globalController = Mockito.mock(GlobalFairnessDrivenController.class);
		this.twoFoldFdController.setGlobalController(this.globalController);
		this.pairwiseController = Mockito.mock(PairwiseFairnessDrivenController.class);
		this.twoFoldFdController.setPairwiseController(this.pairwiseController);
	}
	
	@Test
	public void testGetMaxCapacityToSupplyPairwiseController() {
		String memberId = "member_id";
		FederationMember member = new FederationMember(memberId);
		double biggerThenZero = 10.0;
		Mockito.when(this.pairwiseController.getCurrentFairness(Mockito.eq(member)))
				.thenReturn(biggerThenZero);
		double currentFairness = 50.0;
		Mockito.when(this.pairwiseController.getMaxCapacityToSupply(Mockito.eq(member)))
				.thenReturn(currentFairness);
		
		double maxCapacityToSupply = this.twoFoldFdController.getMaxCapacityToSupply(member);
		Assert.assertEquals(String.valueOf(currentFairness), String.valueOf(maxCapacityToSupply));
	}
	
	@Test
	public void testGetMaxCapacityToSupplyGlobalController() {		
		String memberId = "member_id";
		FederationMember member = new FederationMember(memberId);
		double lessZero = -1;
		Mockito.when(this.pairwiseController.getCurrentFairness(Mockito.eq(member)))
				.thenReturn(lessZero);		
		double currentFairness = 50.0;
		Mockito.when(this.globalController.getMaxCapacityToSupply(Mockito.eq(member)))
				.thenReturn(currentFairness);
		
		double maxCapacityToSupply = this.twoFoldFdController.getMaxCapacityToSupply(member);
		Assert.assertEquals(String.valueOf(currentFairness), String.valueOf(maxCapacityToSupply));		
	}
	
	@Test
	public void testUpdateCapacity() {
		String memberId = "member_id";
		FederationMember member = new FederationMember(memberId);
		double maximumCapacity = 0;
		this.twoFoldFdController.updateCapacity(member, maximumCapacity);
		
		Mockito.verify(this.pairwiseController, Mockito.times(1))
				.updateCapacity(Mockito.eq(member), Mockito.eq(maximumCapacity));
		Mockito.verify(this.globalController, Mockito.times(1))
		.updateCapacity(Mockito.eq(member), Mockito.eq(maximumCapacity));		
	}

}
