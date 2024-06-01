package tukano.impl.java.servers;

import static java.lang.String.format;
import static tukano.api.java.Result.error;
import static tukano.api.java.Result.ok;
import static tukano.api.java.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.java.Result.ErrorCode.CONFLICT;
import static tukano.api.java.Result.ErrorCode.FORBIDDEN;
import static tukano.api.java.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.java.Result.ErrorCode.NOT_FOUND;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.logging.Logger;

import tukano.api.java.Result;
import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.discovery.Discovery;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;
import utils.Hash;
import utils.IO;
import utils.Token;

public class JavaBlobs implements ExtendedBlobs {

	private static final String BLOBS_ROOT_DIR = "/tmp/blobs/";

	private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());

	private static final int CHUNK_SIZE = 4096;

	@Override
	public Result<Void> upload(String blobId, byte[] bytes) {
		Log.info(() -> format("download : blobId = %s\n", blobId));

		if (!validBlobId(blobId))
			return error(FORBIDDEN);

		var file = toFilePath(blobId);
		if (file == null)
			return error(BAD_REQUEST);

		if (file.exists()) {
			if (Arrays.equals(Hash.sha256(bytes), Hash.sha256(IO.read(file))))
				return ok();
			else
				return error(CONFLICT);

		}
		IO.write(file, bytes);

		return ok();
	}

	@Override
	public Result<byte[]> download(String blobId) {
		Log.info(() -> format("download : blobId = %s\n", blobId));

		var file = toFilePath(blobId);
		if (file == null)
			return error(BAD_REQUEST);

		if (file.exists()) {
			Log.info("################## vai retornar ok com o file");
			return ok(IO.read(file));
		}else
			return error(NOT_FOUND);
	}

	@Override
	public Result<Void> downloadToSink(String blobId, Consumer<byte[]> sink) {
		Log.info(() -> format("downloadToSink : blobId = %s\n", blobId));

		var file = toFilePath(blobId);

		if (file == null)
			return error(BAD_REQUEST);

		if( ! file.exists() )
			return error(NOT_FOUND);

		try (var fis = new FileInputStream(file)) {
			int n;
			var chunk = new byte[CHUNK_SIZE];
			while ((n = fis.read(chunk)) > 0)
				sink.accept(Arrays.copyOf(chunk, n));

			return ok();
		} catch (IOException x) {
			return error(INTERNAL_ERROR);
		}
	}

	@Override
	public Result<Void> delete(String blobId, String token) {
		Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));
	
		if( ! Token.matches( token ) )
			return error(FORBIDDEN);

		
		var file = toFilePath(blobId);

		if (file == null)
			return error(BAD_REQUEST);

		if( ! file.exists() )
			return error(NOT_FOUND);
			
		IO.delete( file );
		return ok();
	}
	
	@Override
	public Result<Void> deleteAllBlobs(String userId, String token) {
		Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId, token));

		if( ! Token.matches( token ) )
			return error(FORBIDDEN);
		
		try {
			var path = new File(BLOBS_ROOT_DIR + userId );
			Files.walk(path.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
			return ok();
		} catch (IOException e) {
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}
	}
	
	private boolean validBlobId(String blobId) {
		/*var servers = Discovery.getInstance().knownUrisOf("shots", 1);

		for(URI shortServer : servers){
			Log.info("###################### fez um loop no valid blobs");
			if(Clients.ShortsClients.get(shortServer).getShort(blobId).isOK()){
				return true;
			}
		}

		return false;*/
		return Clients.ShortsClients.get().getShort(blobId).isOK();
	}

	private File toFilePath(String blobId) {
		var parts = blobId.split("-");
		if (parts.length != 2)
			return null;

		var res = new File(BLOBS_ROOT_DIR + parts[0] + "/" + parts[1]);
		res.getParentFile().mkdirs();

		return res;
	}
}
