package org.fogbowcloud.manager.occi.member;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;

public class UsageServerResource extends MemberServerResource {

	public static final DecimalFormat USAGE_FORMAT;
	
	static {
		DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols(Locale.US);
		formatSymbols.setDecimalSeparator('.');
		USAGE_FORMAT = new DecimalFormat("0.000000", formatSymbols);
	}
	
	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();

		String memberId = (String) getRequestAttributes().get("memberId");

		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());

		if (memberId != null) {
			ResourceUsage usageContainer = application.getUsages(authToken, memberId);
			return generateResponse(memberId, usageContainer);
		}

		throw new OCCIException(ErrorType.BAD_REQUEST, "The memberId was not specified.");
	}

	private String generateResponse(String memberId, ResourceUsage usageContainer) {
		StringBuilder response = new StringBuilder();
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": memberId=" + memberId);
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": compute usage=" + formatDouble(usageContainer.getComputeUsage()));
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": storage usage=" + formatDouble(usageContainer.getStorageUsage()));

		return response.toString().trim();
	}
	
	public static double formatDouble(double doubleValue) {
		return Double.valueOf(USAGE_FORMAT.format(doubleValue));
	}

	public final static class ResourceUsage {

		private double computeUsage;
		private double storageUsage;

		public ResourceUsage(double computeUsage, double storageUsage) {
			this.computeUsage = computeUsage;
			this.storageUsage = storageUsage;
		}

		public double getComputeUsage() {
			return computeUsage;
		}

		public double getStorageUsage() {
			return storageUsage;
		}
	}
}
