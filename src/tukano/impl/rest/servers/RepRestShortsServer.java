package tukano.impl.rest.servers;

import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

import tukano.api.java.Shorts;
import tukano.impl.java.servers.RepJavaShorts;
import tukano.impl.kafka.lib.KafkaUtils;
import tukano.impl.rest.servers.utils.CustomLoggingFilter;
import tukano.impl.rest.servers.utils.GenericExceptionMapper;
import utils.Args;


public class RepRestShortsServer extends AbstractRestServer {
    public static final int PORT = 8888;
    private static final String TOPIC = "X-SHORTS";


    private static Logger Log = Logger.getLogger(RestShortsServer.class.getName());


    RepRestShortsServer() {
        super(Log, Shorts.NAME, PORT);
    }

    @Override
    void registerResources(ResourceConfig config) {
        RepJavaShorts rep = new RepJavaShorts();
        config.register(new RestShortsResource(rep));
        config.register(new GenericExceptionMapper());
        config.register(new CustomLoggingFilter());
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        Args.use(args);

        KafkaUtils.createTopic(TOPIC);
        new RepRestShortsServer().start();
    }


}