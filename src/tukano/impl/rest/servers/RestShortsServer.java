package tukano.impl.rest.servers;

import java.net.InetAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.apache.zookeeper.CreateMode;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import tukano.api.java.Blobs;
import tukano.api.java.Shorts;
import tukano.impl.rest.servers.utils.CustomLoggingFilter;
import tukano.impl.rest.servers.utils.GenericExceptionMapper;
import tukano.impl.zookeeper.RequestInterceptor;
import tukano.impl.zookeeper.Zookeeper;
import utils.Args;

public class RestShortsServer extends AbstractRestServer {
	private static Logger Log = Logger.getLogger(RestBlobsServer.class.getName());

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
	}

	public static final int PORT = 4567;
	public static final String SERVICE = "ShortsService";
	private static final String SERVER_URI_FMT = "https://%s:%s/rest";

	RestShortsServer(int port) {
		super(Log, Shorts.NAME, port);
	}

	@Override
	void registerResources(ResourceConfig config) {
		config.register(RestShortsResource.class);
		config.register(RolesAllowedDynamicFeature.class);
		config.register(RequestInterceptor.class);
		config.register(new GenericExceptionMapper());
		config.register(new CustomLoggingFilter());
	}

	public static void main(String[] args) throws NoSuchAlgorithmException {
		try {
			Zookeeper zk = initializeZooKeeper("zookeeper");
			String serverURI = startServer();
			registerServerInZooKeeper(zk, serverURI);
			Log.info(String.format("%s Server ready @ %s\n", SERVICE, serverURI));

		} catch (Exception e) {
			Log.severe("Server initialization error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static Zookeeper initializeZooKeeper(String host) throws Exception {
		Zookeeper zk = new Zookeeper(host);
		zk.registerWatcher(zk);

		String root = "/shorts";
		try {
			String path = zk.createNode(root, new byte[0], CreateMode.PERSISTENT);
			Log.info("Created root node: " + path);
		} catch (Exception e) {
			Log.info("Root node already exists: " + root);
		}
		return zk;
	}

	private static String startServer() throws Exception {
		ResourceConfig config = new ResourceConfig();
		config.register(RestShortsResource.class);
		config.register(RolesAllowedDynamicFeature.class);
		config.register(new GenericExceptionMapper());
		config.register(new CustomLoggingFilter());

		String ip = InetAddress.getLocalHost().getHostName();
		String serverURI = String.format(SERVER_URI_FMT, ip, PORT);
		JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);

		return serverURI;
	}

	private static void registerServerInZooKeeper(Zookeeper zk, String serverURI) throws Exception {
		byte[] serverUrlBytes = serverURI.getBytes();
		String root = "/shorts";

		String childPath = zk.createNode(root + "/", serverUrlBytes, CreateMode.EPHEMERAL_SEQUENTIAL);
		Log.info("Created child node: " + childPath);

		var children = zk.getAndWatchChildren(root);
		Log.info("Current children: " + children);
	}
}