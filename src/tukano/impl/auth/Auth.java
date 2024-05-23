package tukano.impl.auth;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.google.gson.Gson;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import org.pac4j.scribe.builder.api.DropboxApi20;

public abstract class Auth {

    public static final String apiKey = "xppjq79125sypnf\n";
    public static final String apiSecret = "zexdhatp055eldc";
    public static final String accessTokenStr = "sl.B1xjGWIvgOM2Z9AED3ktQ0E6ebqT6gDhuNehCJ_oHFmrLkSi4IMAWVrA4JhgdQOC8vtazmwx8Fm110zjKYfN7WzC1chVmi7fk1_-HItwWo7uvp5-hl4h2IodSfFtBY_mAcfcmFGs1D7K4Xass_9S9fw";


    public final Gson json;
    public final OAuth20Service service;
    public final OAuth2AccessToken accessToken;


    public Auth() {
        json = new Gson();
        accessToken = new OAuth2AccessToken(accessTokenStr);
        service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
    }

}
