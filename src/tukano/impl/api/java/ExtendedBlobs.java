package tukano.impl.api.java;

import tukano.api.java.Blobs;
import tukano.api.java.Result;

public interface ExtendedBlobs extends Blobs {

    Result<Void> delete(String blobId);

    Result<Void> deleteAllBlobs(String userId, String token );
}
