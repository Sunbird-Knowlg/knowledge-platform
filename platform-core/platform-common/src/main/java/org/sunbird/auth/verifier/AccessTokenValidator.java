package org.sunbird.auth.verifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sunbird.common.LoggerUtil;
import org.sunbird.common.exception.ClientException;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Map;

public class AccessTokenValidator {
  private static final LoggerUtil logger = new LoggerUtil(AccessTokenValidator.class);
  private static final ObjectMapper mapper = new ObjectMapper();
  private static Integer offset  = ZonedDateTime.now().getOffset().getTotalSeconds();

  private static Map<String, Object> validateToken(String token, Map<String, Object> requestContext)
          throws IOException {
    String[] tokenElements = token.split("\\.");
    String header = tokenElements[0];
    String body = tokenElements[1];
    String signature = tokenElements[2];
    String payLoad = header + "." + body;

    boolean isValid =
        CryptoUtil.verifyRSASign(
            payLoad,
            decodeFromBase64(signature),
            KeyManager.getPublicKey("publickey").getPublicKey(),
                "SHA256withRSA",
            requestContext);
    if (isValid) {
      Map<String, Object> tokenBody =
          mapper.readValue(new String(decodeFromBase64(body)), Map.class);
      boolean isExp = isExpired((Long) tokenBody.get("exp"));
      if (isExp) {
        logger.info("Token is expired " + token + ", request context data :" + requestContext);
        throw new ClientException("ERR_CONTENT_ACCESS_RESTRICTED", "Please provide valid user token ");
      }
      return tokenBody;
    }
    throw new ClientException("ERR_CONTENT_ACCESS_RESTRICTED", "Please provide valid user token ");
  }

  public static Map<String, Object>  verifyUserToken(String token, Map<String, Object> requestContext) {
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
    }

    return payload;
  }

  private static boolean isExpired(Long expiration) {
    return ((System.currentTimeMillis() / 1000L) + offset > expiration);
  }

  private static byte[] decodeFromBase64(String data) {
    return Base64Util.decode(data, 11);
  }
}
