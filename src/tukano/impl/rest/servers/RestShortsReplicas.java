package tukano.impl.rest.servers;

import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

import tukano.api.java.Shorts;
import tukano.impl.java.servers.JavaShortsRep;
import tukano.impl.kafka.lib.KafkaUtils;
import tukano.impl.rest.servers.utils.CustomLoggingFilter;
import tukano.impl.rest.servers.utils.GenericExceptionMapper;
import utils.Args;


public class RestShortsReplicas extends AbstractRestServer {
    public static final int PORT = 7189;
    private static final String TOPIC = "X-SHORTS";


    private static Logger Log = Logger.getLogger(RestShortsServer.class.getName());


    RestShortsReplicas() {
        super(Log, Shorts.NAME, PORT);
    }

    @Override
    void registerResources(ResourceConfig config) {
        JavaShortsRep rep = new JavaShortsRep();
        config.register(new RestShortsResource(rep));
        config.register(new GenericExceptionMapper());
        config.register(new CustomLoggingFilter());
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        Args.use(args);

        KafkaUtils.createTopic(TOPIC);
        new RestShortsReplicas().start();
    }


}