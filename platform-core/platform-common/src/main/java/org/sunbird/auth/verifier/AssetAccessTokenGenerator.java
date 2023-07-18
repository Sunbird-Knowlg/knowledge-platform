package org.sunbird.auth.verifier;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.LoggerUtil;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ClientException;

import java.nio.charset.StandardCharsets;
import java.util.Date;

public class AssetAccessTokenGenerator {

    private static final LoggerUtil logger = new LoggerUtil(AccessTokenValidator.class);

    public static String generateAssetAccessToken(String contentId) {
        logger.info("Private container access token generation request for contentId " + contentId);
        String accessSecret = Platform.config.getString("protected_content_key");

        if(accessSecret == null || StringUtils.isEmpty(accessSecret)) throw new ClientException("BLOB_ACCESS_KEY_MISSING", "Please configure cloud access key for private container");

        String jws = Jwts.builder().claim("contentid",contentId).setHeaderParam("typ","JWT").setIssuedAt(new Date()).signWith(SignatureAlgorithm.HS256,accessSecret.getBytes(StandardCharsets.UTF_8)).compact();

        return jws;
    }

}
