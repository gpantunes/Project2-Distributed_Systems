package tukano.impl.rest.servers;

import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

import tukano.api.java.Blobs;
import tukano.impl.auth.CreateDirectory;
import tukano.impl.auth.DeleteFile;
import tukano.impl.java.servers.JavaBlobs;
import tukano.impl.java.servers.ProxyJavaBlobs;
import tukano.impl.rest.servers.utils.CustomLoggingFilter;
import tukano.impl.rest.servers.utils.GenericExceptionMapper;
import utils.Args;

public class RestProxyBlobsServer extends AbstractRestServer {
    public static final int PORT = 8765;

    private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());

    RestProxyBlobsServer(int port) {
        super( Log, Blobs.NAME, port);
    }


    @Override
    void registerResources(ResourceConfig config) {
        config.property("stateless", stateless);
        config.register( RestProxyBlobsResource.class );
        config.register(new GenericExceptionMapper());
        config.register(new CustomLoggingFilter());
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        Args.use(args);
        new RestProxyBlobsServer(Args.valueOf("-port", PORT)).start();

        stateless = Boolean.parseBoolean(args[0]);

        if (stateless == false) {
            String[] dropBoxArgs = new String[1];
            dropBoxArgs[0] = ProxyJavaBlobs.DROPBOX_BLOBS_DIR;

            try {
                DeleteFile.main(dropBoxArgs);
            } catch (Exception e) {
                //throw new RuntimeException(e);
            }

            try {
                CreateDirectory.main(dropBoxArgs);
            } catch (Exception e) {
                //throw new RuntimeException(e);
            }
        }
    }
}
