package tukano.impl.auth;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.google.gson.Gson;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import org.pac4j.scribe.builder.api.DropboxApi20;

public abstract class Auth {

    public static final String apiKey = "5i0yoqg5vqihusq";
    public static final String apiSecret = "g5ftzysjjqgxhok";
    public static final String accessTokenStr = "sl.B1lFf43pOwziiiQx1tdlK-rdOPvSFtp1LUtcWbvIGIoHbnnbPlo5sJr4HDt2MnJ2cxx-K-aus8wk1WHXy1FcJFhVAmt5nhExQbzVz9q11MSie6RbIgwPoXVpFymNAhZNbU_X9-hXEmbW";

    public final Gson json;
    public final OAuth20Service service;
    public final OAuth2AccessToken accessToken;


    public Auth() {
        json = new Gson();
        accessToken = new OAuth2AccessToken(accessTokenStr);
        service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
    }

}
