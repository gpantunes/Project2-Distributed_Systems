package tukano.impl.java.servers;

import static java.lang.String.format;
import static tukano.api.java.Result.error;
import static tukano.api.java.Result.ok;
import static tukano.api.java.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.java.Result.ErrorCode.FORBIDDEN;
import static tukano.api.java.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.java.Result.ErrorCode.NOT_FOUND;
import static tukano.impl.java.clients.Clients.ShortsClients;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.github.scribejava.core.model.Response;
import tukano.api.java.Result;
import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.auth.CreateDirectory;
import tukano.impl.auth.DeleteFile;
import tukano.impl.auth.DownloadFile;
import tukano.impl.auth.UploadFile;
import tukano.impl.java.clients.Clients;
import utils.IO;
import utils.Token;

public class ProxyJavaBlobs implements ExtendedBlobs {

    public static final String DROPBOX_BLOBS_DIR = "/blobFiles";

    private static Logger Log = Logger.getLogger(ProxyJavaBlobs.class.getName());

    private static final int CHUNK_SIZE = 4096;

    @Override
    public Result<Void> upload(String blobId, byte[] bytes) {
        Log.info("######################### upload no proxy");

        createDir(blobId);
        var parts = blobId.split("-");

		try {
			String[] args = new String[2];
			args[0] = DROPBOX_BLOBS_DIR + "/" + parts[0] + "/" + parts[1];
			args[1] = new String(bytes, StandardCharsets.UTF_8);
			UploadFile.main(args);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

        return ok();
    }

    @Override
    public Result<byte[]> download(String blobId) {
        Log.info(() -> format("download : blobId = %s\n", blobId));

        var parts = blobId.split("-");
        byte[] content;

		try{
			String[] args = new String[1];
			args[0] = DROPBOX_BLOBS_DIR + "/" + parts[0] + "/" + parts[1];
			Response res = DownloadFile.main(args);

			if(!res.isSuccessful())
				return error(INTERNAL_ERROR);

			content = res.getBody().getBytes(StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return ok(content);

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

        var parts = blobId.split("-");

        try{
            String[] args = new String[1];
            args[0] = DROPBOX_BLOBS_DIR + "/" + parts[0] + "/" + parts[1];
            Response res = DeleteFile.main(args);

            if(!res.isSuccessful())
                return error(INTERNAL_ERROR);

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
            String[] args = new String[1];
            args[0] = DROPBOX_BLOBS_DIR + "/" + userId;
            Response res = DeleteFile.main(args);

            if(!res.isSuccessful())
                return error(INTERNAL_ERROR);

        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }

        return ok();
    }

    private boolean validBlobId(String blobId) {
        return ShortsClients.get().getShort(blobId).isOK();
    }

    private Result<Void> createDir(String blobId) {
        var parts = blobId.split("-");
        if (parts.length != 2)
            return null;

        try {
            String[] args = new String[1];
            args[0] = DROPBOX_BLOBS_DIR + "/" + parts[0];
            CreateDirectory.main(args);
        } catch (Exception e) {
            //throw new RuntimeException(e);
        }

        return ok();
    }

    private File toFilePath(String blobId) {
        var parts = blobId.split("-");
        if (parts.length != 2)
            return null;

        var res = new File(DROPBOX_BLOBS_DIR + parts[0] + "/" + parts[1]);
        res.getParentFile().mkdirs();

        return res;
    }
}
