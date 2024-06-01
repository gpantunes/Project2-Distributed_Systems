package tukano.impl.auth;


import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;
import org.pac4j.scribe.builder.api.DropboxApi20;

public abstract class Auth {

    public static final String apiKey = "xppjq79125sypnf";
    public static final String apiSecret = "zexdhatp055eldc";
    public static final String accessTokenStr = "sl.B2Xo8kqjOXZortXnd9ygKoGSkbdTgvVN39eeUsRitsXriyiII_wuYouDU6IbYVJwvLz4VF-qsqbQVzdWlqvjDwwblxwlzJAeaxATnEpCcSHbZTk_mIkDLP2tbS4Sbw06yOZyZTfcKJ3RxiF4s3Crp4w";

    public final Gson json;
    public final OAuth20Service service;
    public final OAuth2AccessToken accessToken;


    public Auth() {
        json = new Gson();
        accessToken = new OAuth2AccessToken(accessTokenStr);
        service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
    }

}
