package tukano.impl.java.servers;

import static java.lang.String.format;
import static tukano.api.java.Result.error;
import static tukano.api.java.Result.ok;
import static tukano.api.java.Result.ErrorCode.FORBIDDEN;
import static tukano.api.java.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.java.Result.ErrorCode.NOT_FOUND;
import static tukano.impl.java.clients.Clients.ShortsClients;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.github.scribejava.core.model.Response;
import tukano.api.Blob;
import tukano.api.java.Result;
import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.auth.CreateDirectory;
import tukano.impl.auth.DeleteFile;
import tukano.impl.auth.DownloadFile;
import tukano.impl.auth.UploadFile;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;
import utils.*;

public class JavaBlobs implements ExtendedBlobs {
	private static final String ADMIN_TOKEN = Args.valueOf("-token", "");
	
	private static final String BLOBS_ROOT_DIR = "blobFiles";
	
	private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());

	private static final int CHUNK_SIZE = 4096;

	ClientFactory<ExtendedShorts> client = ShortsClients;

	@Override
	public Result<Void> upload(String blobId, byte[] bytes) {
		Log.info("%%%%%%%%%%%%%%%%%% entrou no upload " + blobId);

		String filePath = BLOBS_ROOT_DIR + "/" + blobId;
		String directoryPath = BLOBS_ROOT_DIR + "/";

		/*try {
			Path directory = Paths.get(directoryPath);
			if (!Files.exists(directory))
				Files.createDirectories(directory);

			// Convert byte array to Path object
			Path path = Paths.get(filePath);

			// Write the bytes to the file
			Files.write(path, bytes);

		} catch (IOException e) {
			return error(INTERNAL_ERROR);
		}*/

		try{
			String[] args = new String[1];
			args[0] = "/" + BLOBS_ROOT_DIR;
			CreateDirectory.main(args);
		} catch (Exception e) {
			//throw new RuntimeException(e);
		}

		Log.info("%%%%%%%%%%%%%%%%%%% bytes " + bytes);
		Log.info("%%%%%%%%%%%%%%%%%%% string" + bytes.toString());

		try{
			String[] args = new String[2];
			args[0] = "/" + BLOBS_ROOT_DIR + "/" + blobId;
			args[1] = new String(bytes, StandardCharsets.UTF_8);
			UploadFile.main(args);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		Hibernate.getInstance().persistOne(new Blob(blobId, blobId));

		return ok();
	}

	@Override
	public Result<byte[]> download(String blobId) {
		var result = client.get().getShortByBlobId(blobId);

		Log.info("#################### download " + blobId);

		if(!result.isOK())
			return error(NOT_FOUND);

		byte[] content;

		/*try{
			String filePath = BLOBS_ROOT_DIR + "/" + blobId;
			Path path = Paths.get(filePath);

			content = Files.readAllBytes(path);
		}catch (IOException e){
			return error(INTERNAL_ERROR);
		}*/


		try{
			String[] args = new String[1];
			args[0] = "/" + BLOBS_ROOT_DIR + "/" + blobId;
			Response res = DownloadFile.main(args);

			if(!res.isSuccessful())
				return error(INTERNAL_ERROR);

			Log.info("#################### body: " + res.getBody() + " message: " + res.getMessage());

			content = res.getBody().getBytes(StandardCharsets.UTF_8);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return Result.ok(content);
	}


	/*@Override
	public Result<Void> upload(String blobId, byte[] bytes) {
		Log.info(() -> format("upload : blobId = %s, sha256 = %s\n", blobId, Hex.of(Hash.sha256(bytes))));

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

		if (file.exists())
			return ok(IO.read(file));
		else
			return error(NOT_FOUND);
	}*/

	@Override
	public Result<Void> downloadToSink(String blobId, Consumer<byte[]> sink) {
		Log.info(() -> format("downloadToSink : blobId = %s\n", blobId));

		Result res = download(blobId);

		if(!res.isOK())
			return error(INTERNAL_ERROR);
		else return ok();

		/*var file = toFilePath(blobId);

		if (file == null)
			return error(BAD_REQUEST);

		try (var fis = new FileInputStream(file)) {
			int n;
			var chunk = new byte[CHUNK_SIZE];
			while ((n = fis.read(chunk)) > 0)
				sink.accept(Arrays.copyOf(chunk, n));

			return ok();
		} catch (IOException x) {
			return error(INTERNAL_ERROR);
		}*/
	}

	@Override
	public Result<Void> delete(String blobId) {
		Log.info(() -> format("delete : blobId = %s", blobId));
		
		try{
			String[] args = new String[1];
			args[0] = "/" + BLOBS_ROOT_DIR + "/" + blobId;
			DeleteFile.main(args);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
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
