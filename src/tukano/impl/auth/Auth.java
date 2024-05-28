package tukano.impl.auth;


import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;
import org.pac4j.scribe.builder.api.DropboxApi20;

public abstract class Auth {

    public static final String apiKey = "xppjq79125sypnf";
    public static final String apiSecret = "zexdhatp055eldc";
    public static final String accessTokenStr = "sl.B2G7NtLgior6Tfz5AcyrlqwAJWjY-Q2Ou-saUdtZC33CSNdzWsrJVEAvZQKy3r8Yq78fCWmTGIugP0eSpwHqWqxRmgnN4aN7Sia2P3oOjw5rSIyshK37fd99XXn5Q92eUsw3zFLxv8-bLw2AHSBuOqg";


    public final Gson json;
    public final OAuth20Service service;
    public final OAuth2AccessToken accessToken;


    public Auth() {
        json = new Gson();
        accessToken = new OAuth2AccessToken(accessTokenStr);
        service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
    }

}
