package org.sunbird.auth.verifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.sunbird.common.LoggerUtil;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ClientException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Map;

public class AccessTokenValidator {
  private static final LoggerUtil logger = new LoggerUtil(AccessTokenValidator.class);
  private static final ObjectMapper mapper = new ObjectMapper();
  private static Integer offset  = ZonedDateTime.now().getOffset().getTotalSeconds();

  private static Map<String, Object> validateToken(String token, Map<String, Object> requestContext)
          throws IOException {
    boolean isValid = false;

    Jws<Claims> jwspayload = null;
    try{
      RSAPublicKey publicKey = readPublicKey();
      jwspayload = Jwts.parser().setSigningKey(publicKey).parseClaimsJws(token);
      if(jwspayload!=null) isValid = true;
    } catch (Exception ex) {
      ex.printStackTrace();
      logger.error(
              "Exception in verifyUserAccessToken: Token via JJWT: "
                      + token
                      + ", request context data : "
                      + requestContext,
              ex);
    }

    if (isValid) {
      Map<String, Object> tokenBody =  mapper.readValue(new String(decodeFromBase64(token.split("\\.")[1])), Map.class);
      boolean isExp = isExpired(jwspayload.getBody().getExpiration().getTime());
      if (isExp) {
        logger.info("Token is expired " + token + ", request context data :" + requestContext);
        throw new ClientException("ERR_CONTENT_ACCESS_RESTRICTED", "Please provide valid user token ");
      }
      return tokenBody;
    }
    throw new ClientException("ERR_CONTENT_ACCESS_RESTRICTED", "Please provide valid user token ");
  }

  public static Map<String, Object>  verifyUserToken(String token, Map<String, Object> requestContext) throws IOException {
    Map<String, Object> payload = null;
    try {
      payload = validateToken(token, requestContext);
      logger.info(
          "learner access token validateToken() :"
              + payload.toString()
              + ", request context data : "
              + requestContext);
    } catch (Exception ex) {
      logger.error(
          "Exception in verifyUserAccessToken: Token : "
              + token
              + ", request context data : "
              + requestContext,
          ex);
      throw ex;
    }

    return payload;
  }

  private static boolean isExpired(Long expiration) {
    return ((System.currentTimeMillis() / 1000L) + offset > expiration);
  }

  private static byte[] decodeFromBase64(String data) {
    return Base64Util.decode(data, 11);
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
}
