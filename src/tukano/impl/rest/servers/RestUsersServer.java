package tukano.impl.rest.servers;

import java.net.InetAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import org.apache.zookeeper.CreateMode;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import tukano.api.java.Users;
import tukano.impl.rest.servers.utils.CustomLoggingFilter;
import tukano.impl.rest.servers.utils.GenericExceptionMapper;
import tukano.impl.zookeeper.RequestInterceptor;
import tukano.impl.zookeeper.Zookeeper;

public class RestUsersServer extends AbstractRestServer {
	private static Logger Log = Logger.getLogger(RestUsersServer.class.getName());

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
	}

	public static final int PORT = 3456;
	public static final String SERVICE = "UsersService";
	private static final String SERVER_URI_FMT = "http://%s:%s/rest";

	RestUsersServer(int port) {
		super(Log, Users.NAME, PORT);
	}

	@Override
	void registerResources(ResourceConfig config) {
		config.register(RestUsersResource.class);
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

		String root = "/users";
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
		config.register(RestUsersResource.class);
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
		String root = "/users";

		String childPath = zk.createNode(root + "/", serverUrlBytes, CreateMode.EPHEMERAL_SEQUENTIAL);
		Log.info("Created child node: " + childPath);

		var children = zk.getAndWatchChildren(root);
		Log.info("Current children: " + children);
	}

}