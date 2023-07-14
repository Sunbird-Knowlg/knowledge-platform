package org.sunbird.auth.verifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.JsonKey;
import org.sunbird.common.LoggerUtil;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Collections;
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
    String payLoad = header + JsonKey.DOT_SEPARATOR + body;

    String channel = (String) requestContext.getOrDefault("channel", "");

    if(StringUtils.isEmpty(channel) || KeyManager.getPublicKey(channel) == null ) return Collections.EMPTY_MAP;

    boolean isValid =
        CryptoUtil.verifyRSASign(
            payLoad,
            decodeFromBase64(signature),
            KeyManager.getPublicKey(channel).getPublicKey(),
            JsonKey.SHA_256_WITH_RSA,
            requestContext);
    if (isValid) {
      Map<String, Object> tokenBody =
          mapper.readValue(new String(decodeFromBase64(body)), Map.class);
      boolean isExp = isExpired((Long) tokenBody.get("exp"));
      if (isExp) {
        logger.info("Token is expired " + token + ", request context data :" + requestContext);
        return Collections.EMPTY_MAP;
      }
      return tokenBody;
    }
    return Collections.EMPTY_MAP;
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
