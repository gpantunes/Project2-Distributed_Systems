package tukano.impl.auth;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;
import org.pac4j.scribe.builder.api.DropboxApi20;
import tukano.impl.auth.msgs.UploadFileArgs;

import java.nio.charset.StandardCharsets;

public class UploadFile  extends Auth{

	private static final String apiKey = "6ace8lzioa49gnd";
	private static final String apiSecret = "nx0lha07s6bxz3p";
	private static final String accessTokenStr = "sl.B1QBFFJP48WXXFLDK-di3HH92xC4a1qgWfYp_0EcNlWC05zEenUzAdEVDxb8rvqBHGf2oiWkv02PBSZIbx05EAfK1AzoVD95FQ6eUXMvZ_k40P7m5yUwp1xuYSaRghsqKNhnVYDK5dbnBGY";
	
	private static final String UPLOAD_FILE_URL = "https://content.dropboxapi.com/2/files/upload";
	
	private static final int HTTP_SUCCESS = 200;
	private static final String CONTENT_TYPE_HDR = "Content-Type";
	private static final String JSON_CONTENT_TYPE = "application/octet-stream";
	
	private final Gson json;
	private final OAuth20Service service;
	private final OAuth2AccessToken accessToken;
		
	public UploadFile() {
		json = new Gson();
		accessToken = new OAuth2AccessToken(accessTokenStr);
		service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
	}
	
	public void execute( String filePath, String fileContent ) throws Exception {
		
		var uploadFile = new OAuthRequest(Verb.POST, UPLOAD_FILE_URL);
		uploadFile.addHeader(CONTENT_TYPE_HDR, JSON_CONTENT_TYPE);
		
		String uploadArgsJson = json.toJson(new UploadFileArgs(false, "add", false, filePath, false));
		uploadFile.addHeader("Dropbox-API-Arg", uploadArgsJson);
		
		// Set the payload with the file content
	    uploadFile.setPayload(fileContent.getBytes(StandardCharsets.UTF_8));

		service.signRequest(accessToken, uploadFile);
		
		Response r = service.execute(uploadFile);
		if (r.getCode() != HTTP_SUCCESS) 
			throw new RuntimeException(String.format("Failed to upload file: %s, Status: %d, \nReason: %s\n", filePath, r.getCode(), r.getBody()));
	}
	
	public static void main(String[] args) throws Exception {

		if( args.length != 1 ) {
			System.err.println("usage: java UploadFile <dir> <file>");
			System.exit(0);
		}
		
		var filePath = args[0];
		
		var fileContent = "This is the content of the file.";
		var cd = new UploadFile();
		
		cd.execute(filePath, fileContent);
		System.out.println("File '" + filePath + "' was uploaded successfuly.");
	}

}
