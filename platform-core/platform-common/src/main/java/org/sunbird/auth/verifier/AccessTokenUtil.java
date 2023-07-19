package org.sunbird.auth.verifier;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.LoggerUtil;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ClientException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AccessTokenUtil {
  private static final LoggerUtil logger = new LoggerUtil(AccessTokenUtil.class);
  private static final Integer offset  = ZonedDateTime.now().getOffset().getTotalSeconds();

  private static Map<String, Object> validateToken(String token) {
    boolean isValid = false;

    Jws<Claims> jwspayload = null;
    try{
      RSAPublicKey publicKey = readPublicKey();
      jwspayload = Jwts.parser().setSigningKey(publicKey).parseClaimsJws(token);
      if(jwspayload!=null) isValid = true;
    } catch (Exception ex) {
      ex.printStackTrace();
      logger.error("Exception in verifyUserAccessToken: Token via JJWT: " + token, ex);
    }

    if (isValid) {
      boolean isExp = true;
      if(jwspayload.getBody().getExpiration() != null) isExp = isExpired(jwspayload.getBody().getExpiration().getTime());
      if (isExp) {
        logger.info("Token is expired " + token);
        throw new ClientException("ERR_CONTENT_ACCESS_RESTRICTED", "Please provide valid user token ");
      }
      Map<String, Object> tokenBody =  new HashMap<>(jwspayload.getBody());
      return tokenBody;
    }
    throw new ClientException("ERR_CONTENT_ACCESS_RESTRICTED", "Please provide valid user token ");
  }

  public static Map<String, Object>  verifyUserToken(String token) {
    Map<String, Object> payload = null;
    try {
      payload = validateToken(token);
      logger.info("content access token validateToken() :" + payload);
    } catch (Exception ex) {
      logger.error("Exception in verifyUserAccessToken: Token : " + token, ex);
      throw ex;
    }

    return payload;
  }

  private static boolean isExpired(Long expiration) {
    return ((System.currentTimeMillis() / 1000L) + offset > expiration);
  }

  private static RSAPublicKey readPublicKey() {
    String basePath = Platform.config.getString("publickey.basepath");
    try {
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");

      String publicKeyContent = new String(Files.readAllBytes(Paths.get(basePath, "publickey")));
      publicKeyContent = publicKeyContent
              .replaceAll(System.lineSeparator(), "")
              .replace("-----BEGIN PUBLIC KEY-----", "")
              .replace("-----END PUBLIC KEY-----", "");

      X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyContent));
      return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    } catch (Exception e) {
      logger.info("Failed reading public key from :: " + basePath + "/publickey");
      return null;
    }
  }

  public static String generateAssetAccessToken(String contentId) {
    logger.info("Private container access token generation request for contentId " + contentId);
    String accessSecret = Platform.config.getString("protected_content_key");

    if(accessSecret == null || StringUtils.isEmpty(accessSecret)) throw new ClientException("BLOB_ACCESS_KEY_MISSING", "Please configure cloud access key for private container");

    String jws = Jwts.builder().claim("contentid",contentId).setHeaderParam("typ","JWT").setIssuedAt(new Date()).signWith(SignatureAlgorithm.HS256,accessSecret.getBytes(StandardCharsets.UTF_8)).compact();

    return jws;
  }
}
