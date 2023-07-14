package org.sunbird.auth.verifier;

import java.security.PublicKey;

public class KeyData {
  private String keyId;
  private PublicKey publicKey;

  public KeyData(String keyId, PublicKey publicKey) {
    this.keyId = keyId;
    this.publicKey = publicKey;
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(PublicKey publicKey) {
    this.publicKey = publicKey;
  }
}
