package org.sunbird.auth.verifier;

import org.sunbird.common.LoggerUtil;

import java.nio.charset.Charset;
import java.security.*;
import java.util.Map;

public class CryptoUtil {
  private static final Charset US_ASCII = Charset.forName("US-ASCII");
  private static final LoggerUtil logger = new LoggerUtil(CryptoUtil.class);

  public static boolean verifyRSASign(
      String payLoad,
      byte[] signature,
      PublicKey key,
      String algorithm,
      Map<String, Object> requestContext) {
    Signature sign;
    try {
      sign = Signature.getInstance(algorithm);
      sign.initVerify(key);
      sign.update(payLoad.getBytes(US_ASCII));
      return sign.verify(signature);
    } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
      logger.error(
          "verifyRSASign: Exception occurred while token verification: "
              + e.getMessage()
              + ", request context data :"
              + requestContext,
          e);
      return false;
    }
  }
}
