package tukano.impl.auth;

import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import tukano.impl.auth.msgs.DownloadFileArgs;

public class DownloadFile  extends Auth{

	private static final String DOWNLOAD_FILE_URL = "https://content.dropboxapi.com/2/files/download";
	
	private static final int HTTP_SUCCESS = 200;
	
	private static final String CONTENT_TYPE_HDR = "Content-Type";
	private static final String JSON_CONTENT_TYPE = "application/octet-stream";

	public DownloadFile() {
		super();
	}
	
	public Response execute(String filePath) throws Exception {
		
		var downloadFile = new OAuthRequest(Verb.POST, DOWNLOAD_FILE_URL);
		
		String downloadArgsJson = json.toJson(new DownloadFileArgs(filePath));
		downloadFile.addHeader("Dropbox-API-Arg", downloadArgsJson);
		
		downloadFile.addHeader(CONTENT_TYPE_HDR, JSON_CONTENT_TYPE);
		
		// Set the payload with the file content
	    //uploadFile.setPayload(fileContent.getBytes(StandardCharsets.UTF_8));

		service.signRequest(accessToken, downloadFile);
		
		Response r = service.execute(downloadFile);
		if (r.getCode() != HTTP_SUCCESS) 
			throw new RuntimeException(String.format("Failed to download file: %s, Status: %d, \nReason: %s\n", filePath, r.getCode(), r.getBody()));
	
		return r;
		//System.out.println("MESSAGE: " + readBytes(r.getStream()).toString());
	}

	public static Response main(String[] args) throws Exception {

		if( args.length != 1 ) {
			System.err.println("usage: java DownloadFile <path>");
			System.exit(0);
		}
		
		var filePath = args[0];
		
		var cd = new DownloadFile();
		
		return cd.execute(filePath);
		//System.out.println("File '" + filePath + "' was downloaded successfuly.");
	}

}
