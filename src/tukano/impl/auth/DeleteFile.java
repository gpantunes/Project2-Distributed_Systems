package tukano.impl.auth;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;
import org.pac4j.scribe.builder.api.DropboxApi20;
import tukano.impl.auth.msgs.DeleteFolderV2Args;

public class DeleteFile extends Auth{

	private static final String DELETE_FOLDER_V2_URL = "https://api.dropboxapi.com/2/files/delete_v2";
	
	private static final int HTTP_SUCCESS = 200;
	private static final String CONTENT_TYPE_HDR = "Content-Type";
	private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
	
	private final Gson json;
	private final OAuth20Service service;
	private final OAuth2AccessToken accessToken;
		
	public DeleteFile() {
		json = new Gson();
		accessToken = new OAuth2AccessToken(accessTokenStr);
		service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
	}
	
	public void execute( String directoryName ) throws Exception {
		
		var delFolder = new OAuthRequest(Verb.POST, DELETE_FOLDER_V2_URL);
		delFolder.addHeader(CONTENT_TYPE_HDR, JSON_CONTENT_TYPE);

		delFolder.setPayload(json.toJson(new DeleteFolderV2Args(directoryName)));

		service.signRequest(accessToken, delFolder);
		
		Response r = service.execute(delFolder);
		if (r.getCode() != HTTP_SUCCESS) 
			throw new RuntimeException(String.format("Failed to delete: %s, Status: %d, \nReason: %s\n", directoryName, r.getCode(), r.getBody()));
	}
	
	public static void main(String[] args) throws Exception {

		if( args.length != 1 ) {
			System.err.println("usage: java Delete <path>");
			System.exit(0);
		}
		
		var path = args[0];
		var cd = new DeleteFile();
		
		cd.execute(path);
		System.out.println(path + "' deleted successfuly.");
	}

}
