package tukano.impl.rest.servers;

import jakarta.inject.Singleton;
import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.api.rest.RestExtendedBlobs;
import tukano.impl.java.servers.JavaBlobs;
import tukano.impl.java.servers.ProxyJavaBlobs;

import java.util.logging.Logger;

@Singleton
public class RestProxyBlobsResource extends RestResource implements RestExtendedBlobs {

    private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());

    final ExtendedBlobs impl;

    public RestProxyBlobsResource() {
        Log.info("%%%%%%%%%%%%%%%%%% construiu impl com ProxyJavaBlobs");
        this.impl = new ProxyJavaBlobs();
    }

    @Override
    public void upload(String blobId, byte[] bytes) {
        Log.info("%%%%%%%%%%%%%%%% chamou upload no ProxyJavaBlobs");
        super.resultOrThrow( impl.upload(blobId, bytes));
    }

    @Override
    public byte[] download(String blobId) {
        return super.resultOrThrow( impl.download( blobId ));
    }

    @Override
    public void delete(String blobId, String token) {
        super.resultOrThrow( impl.delete( blobId, token ));
    }

    @Override
    public void deleteAllBlobs(String userId, String password) {
        super.resultOrThrow( impl.deleteAllBlobs( userId, password ));
    }
}
