package tukano.impl.zookeeper;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RequestInterceptor implements ContainerRequestFilter {

    private static final Logger Log = Logger.getLogger(RequestInterceptor.class.getName());
    private Zookeeper zooKeeperClient;

    public RequestInterceptor() {
        try {
            this.zooKeeperClient = new Zookeeper("localhost:2181");
        } catch (Exception e) {
            Log.severe("Failed to connect to ZooKeeper");
            e.printStackTrace();
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String primaryReplicaUri = getPrimaryReplicaUri();
        URI originalUri = requestContext.getUriInfo().getRequestUri();
        URI newUri = URI.create(primaryReplicaUri + originalUri.getPath());

        Log.info("Redirecting request to primary replica: " + newUri);
        requestContext.setRequestUri(newUri);
    }

    private String getPrimaryReplicaUri() {
        return zooKeeperClient.getPrimaryReplicaUri();
    }
}
