package org.fogbowcloud.manager.occi.instance;

public enum MIMEMultipartArchive {
	
	INCLUDE_ONCE_URL("text/x-include-once-url", ""),
	INCLUDE_URL("text/x-include-url", "#include"),
	CLOUD_CONFIG_ARCHIVE("text/cloud-config-archive", ""),
	UPSTART_JOB("text/upstart-job", "#upstart-job"),
	CLOUD_CONFIG("text/cloud-config", "#cloud-config"),
	PART_HANDLER("text/part-handler", "#part-handler"),
	SHELLSCRIPT("text/x-shellscript", "#!"),
	CLOUD_BOOTHOOK("text/cloud-boothook", "#cloud-boothook");
	
	private final String CONTENT_TYPE = "Content-Type: ";
	private String type;
	private String begins;
	
	private MIMEMultipartArchive(String type, String begins){
		this.type = type;
		this.begins = begins;
	}
	
	public String getType(){
		return type;
	}
	
	public String getBegins() {
		if(begins == null || begins.isEmpty()){
			return this.getBeginsContentType();
		}
		return begins;
	}
	
	public String getBeginsContentType(){
		return CONTENT_TYPE+type;
	}
	
	public static String getTypeFromContent(String content){
		
		for(MIMEMultipartArchive mmap : values()){
			if(content.startsWith(mmap.getBegins()) || content.startsWith(mmap.getBeginsContentType())){
				return mmap.getType();
			}
		}
		
		return "";
		
	}
}
