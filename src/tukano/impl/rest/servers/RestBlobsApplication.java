package tukano.impl.rest.servers;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import tukano.impl.rest.servers.utils.CustomLoggingFilter;
import tukano.impl.rest.servers.utils.GenericExceptionMapper;
import tukano.impl.zookeeper.RequestInterceptor;

@ApplicationPath("/rest")
public class RestBlobsApplication extends ResourceConfig {
    public RestBlobsApplication() {
        register(RestBlobsResource.class);
        register(RolesAllowedDynamicFeature.class);
        register(new GenericExceptionMapper());
        register(new CustomLoggingFilter());
        register(RequestInterceptor.class);
    }
}
