package org.fogbowcloud.manager.core.plugins.accounting;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.StringTokenizer;

public class ResourceUsage {
	
	String memberId;
	double consumed = 0;
	double donated = 0;
	
	public ResourceUsage(String memberId) {
		this.memberId = memberId;
	}
				
	public void addDonation(double donation) {
		this.donated += donation;
	}
	
	public void addConsumption(double consumption) {
		this.consumed += consumption;
	}

	public double getConsumed() {
		return consumed;
	}

	public double getDonated() {
		return donated;
	}
		
	public String getMemberId() {
		return memberId;
	}

	public String toString() {
		return "memberId=" + memberId + ", consumed=" + formatDouble(consumed) + ", donated="
				+ formatDouble(donated);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ResourceUsage) {
			ResourceUsage other = (ResourceUsage) obj;
			return other.getMemberId().equals(getMemberId()) && other.getDonated() == getDonated()
					&& other.getConsumed() == getConsumed();
		}
		return false;
	}

	public static ResourceUsage parse(String line) {
		StringTokenizer st = new StringTokenizer(line.trim(), ",");
		String memberId = st.nextToken().trim().replace("memberId=", "");
		Double consumed = Double.parseDouble(st.nextToken().trim().replace("consumed=", ""));
		Double donated = Double.parseDouble(st.nextToken().trim().replace("donated=", ""));
		ResourceUsage toReturn = new ResourceUsage(memberId);
		toReturn.addConsumption(consumed);
		toReturn.addDonation(donated);		
		return toReturn;
	}
	
	public static double formatDouble(double doubleValue) {
		DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols(Locale.US);
		formatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000000", formatSymbols);
		return Double.valueOf(df.format(doubleValue));
	}	
}
