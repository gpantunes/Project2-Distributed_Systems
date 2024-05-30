package tukano.impl.auth;


import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;
import org.pac4j.scribe.builder.api.DropboxApi20;

public abstract class Auth {

    public static final String apiKey = "xppjq79125sypnf";
    public static final String apiSecret = "zexdhatp055eldc";
    public static final String accessTokenStr = "sl.B2M8Xig7X_jS7-D33e8kOnCUDXrkOngQWBN6g3p1peRQLwYJTxGFPx2NEq3GjQtDkR5ztH8_TmmNiwub4exCauKZ9az833zRDMJ-rui9G7-H4jCRRMOlkYbqhxdFlUMjCaPX-bUvXatJ4RBMdT-fCPs";

    public final Gson json;
    public final OAuth20Service service;
    public final OAuth2AccessToken accessToken;


    public Auth() {
        json = new Gson();
        accessToken = new OAuth2AccessToken(accessTokenStr);
        service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
    }

}
