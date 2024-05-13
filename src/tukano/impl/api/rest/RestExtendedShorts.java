package tukano.impl.api.rest;

import jakarta.ws.rs.*;
import tukano.api.Short;
import tukano.api.rest.RestShorts;

import static tukano.api.rest.RestBlobs.BLOB_ID;

@Path(RestShorts.PATH)
public interface RestExtendedShorts extends RestShorts {

	String TOKEN = "token";

	
	@DELETE
	@Path("/{" + USER_ID + "}" + SHORTS)
	void deleteAllShorts(@PathParam(USER_ID) String userId, @QueryParam(PWD) String password, @QueryParam(TOKEN) String token);

	@GET
	@Path("/{" + BLOB_ID + "}/blob")
	Short getShortByBlobId(@PathParam(BLOB_ID) String blobId);
}
