package org.fogbowcloud.manager.xmpp.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.fogbowcloud.manager.xmpp.core.model.DateUtils;

public class ManagerItem {
	
	    public static final String ISO_8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
	    private long lastTime;
	    private ResourcesInfo resourcesInfo;

	    public ManagerItem(ResourcesInfo resourcesInfo) {
	        if (resourcesInfo == null) {
	            throw new IllegalArgumentException();
	        }
	        lastTime = new DateUtils().currentTimeMillis();
	        this.resourcesInfo = resourcesInfo;
	    }

	    public ResourcesInfo getResourcesInfo() {
	        return resourcesInfo;
	    }

	    public long getLastTime() {
	        return lastTime;
	    }

	    public String getFormattedTime() {
	        SimpleDateFormat dateFormatISO8601 = new SimpleDateFormat(
	                ISO_8601_DATE_FORMAT, Locale.ROOT);
	        dateFormatISO8601.setTimeZone(TimeZone.getTimeZone("GMT"));
	        
	        Date date = new Date(lastTime);

	        return dateFormatISO8601.format(date);
	    }
	    
	    /**
	     * This method was implemented just for unit test.
	     *  
	     * @param lastTime
	     */
	    protected void setLastTime(long lastTime){
	    	this.lastTime = lastTime;
	    }
	

}
