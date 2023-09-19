// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.signature;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.Parameters;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.internal.KeyTypeManager;
import com.google.crypto.tink.proto.Ed25519KeyFormat;
import com.google.crypto.tink.proto.Ed25519PrivateKey;
import com.google.crypto.tink.proto.Ed25519PublicKey;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.subtle.Ed25519Verify;
import com.google.crypto.tink.subtle.Hex;
import com.google.crypto.tink.subtle.Random;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Unit tests for Ed25519PrivateKeyManager. */
@RunWith(Theories.class)
public class Ed25519PrivateKeyManagerTest {
  private final Ed25519PrivateKeyManager manager = new Ed25519PrivateKeyManager();
  private final KeyTypeManager.KeyFactory<Ed25519KeyFormat, Ed25519PrivateKey> factory =
      manager.keyFactory();

  @Before
  public void register() throws Exception {
    SignatureConfig.register();
  }

  @Test
  public void basics() throws Exception {
    assertThat(manager.getKeyType())
        .isEqualTo("type.googleapis.com/google.crypto.tink.Ed25519PrivateKey");
    assertThat(manager.getVersion()).isEqualTo(0);
    assertThat(manager.keyMaterialType()).isEqualTo(KeyMaterialType.ASYMMETRIC_PRIVATE);
  }

  @Test
  public void validateKeyFormat_empty() throws Exception {
    factory.validateKeyFormat(Ed25519KeyFormat.getDefaultInstance());
  }

  @Test
  public void createKey_checkValues() throws Exception {
    Ed25519PrivateKey privateKey = factory.createKey(Ed25519KeyFormat.getDefaultInstance());
    assertThat(privateKey.getVersion()).isEqualTo(0);
    assertThat(privateKey.getPublicKey().getVersion()).isEqualTo(privateKey.getVersion());
    assertThat(privateKey.getKeyValue()).hasSize(32);
    assertThat(privateKey.getPublicKey().getKeyValue()).hasSize(32);
  }

  @Test
  public void validateKey_empty_throws() throws Exception {
    assertThrows(
        GeneralSecurityException.class,
        () -> manager.validateKey(Ed25519PrivateKey.getDefaultInstance()));
  }

  // Tests that generated keys are different.
  @Test
  public void createKey_differentValues() throws Exception {
    Ed25519KeyFormat format = Ed25519KeyFormat.getDefaultInstance();
    Set<String> keys = new TreeSet<>();
    int numTests = 100;
    for (int i = 0; i < numTests; i++) {
      keys.add(Hex.encode(factory.createKey(format).getKeyValue().toByteArray()));
    }
    assertThat(keys).hasSize(numTests);
  }

  @Test
  public void createKeyThenValidate() throws Exception {
    manager.validateKey(factory.createKey(Ed25519KeyFormat.getDefaultInstance()));
  }

  @Test
  public void validateKey_wrongVersion() throws Exception {
    Ed25519PrivateKey validKey = factory.createKey(Ed25519KeyFormat.getDefaultInstance());
    Ed25519PrivateKey invalidKey = Ed25519PrivateKey.newBuilder(validKey).setVersion(1).build();
    assertThrows(GeneralSecurityException.class, () -> manager.validateKey(invalidKey));
  }

  @Test
  public void validateKey_wrongLength64_throws() throws Exception {
    Ed25519PrivateKey validKey = factory.createKey(Ed25519KeyFormat.getDefaultInstance());
    Ed25519PrivateKey invalidKey =
        Ed25519PrivateKey.newBuilder(validKey)
            .setKeyValue(ByteString.copyFrom(Random.randBytes(64)))
            .build();
    assertThrows(GeneralSecurityException.class, () -> manager.validateKey(invalidKey));
  }

  @Test
  public void validateKey_wrongLengthPublicKey64_throws() throws Exception {
    Ed25519PrivateKey validKey = factory.createKey(Ed25519KeyFormat.getDefaultInstance());
    Ed25519PrivateKey invalidKey =
        Ed25519PrivateKey.newBuilder(validKey)
            .setPublicKey(
                Ed25519PublicKey.newBuilder(validKey.getPublicKey())
                    .setKeyValue(ByteString.copyFrom(Random.randBytes(64))))
            .build();
    assertThrows(GeneralSecurityException.class, () -> manager.validateKey(invalidKey));
  }

  /** Tests that a public key is extracted properly from a private key. */
  @Test
  public void getPublicKey_checkValues() throws Exception {
    Ed25519PrivateKey privateKey = factory.createKey(Ed25519KeyFormat.getDefaultInstance());
    Ed25519PublicKey publicKey = manager.getPublicKey(privateKey);
    assertThat(publicKey).isEqualTo(privateKey.getPublicKey());
  }

  @Test
  public void createPrimitive() throws Exception {
    Ed25519PrivateKey privateKey = factory.createKey(Ed25519KeyFormat.getDefaultInstance());
    PublicKeySign signer = manager.getPrimitive(privateKey, PublicKeySign.class);

    PublicKeyVerify verifier =
        new Ed25519Verify(privateKey.getPublicKey().getKeyValue().toByteArray());
    byte[] message = Random.randBytes(135);
    verifier.verify(signer.sign(message), message);
  }

  @Test
  public void testEd25519Template() throws Exception {
    KeyTemplate template = Ed25519PrivateKeyManager.ed25519Template();
    assertThat(template.toParameters())
        .isEqualTo(Ed25519Parameters.create(Ed25519Parameters.Variant.TINK));
  }

  @Test
  public void testRawEd25519Template() throws Exception {
    KeyTemplate template = Ed25519PrivateKeyManager.rawEd25519Template();
    assertThat(template.toParameters())
        .isEqualTo(Ed25519Parameters.create());
  }

  @Test
  public void testKeyTemplateAndManagerCompatibility() throws Exception {
    Parameters p = Ed25519PrivateKeyManager.ed25519Template().toParameters();
    assertThat(KeysetHandle.generateNew(p).getAt(0).getKey().getParameters()).isEqualTo(p);

    p = Ed25519PrivateKeyManager.rawEd25519Template().toParameters();
    assertThat(KeysetHandle.generateNew(p).getAt(0).getKey().getParameters()).isEqualTo(p);
  }

  @Test
  public void testDeriveKey() throws Exception {
    final int keySize = 32;
    byte[] keyMaterial = Random.randBytes(100);
    Ed25519PrivateKey key =
        factory.deriveKey(
            manager,
            Ed25519KeyFormat.newBuilder().setVersion(0).build(),
            new ByteArrayInputStream(keyMaterial));
    assertThat(key.getKeyValue()).hasSize(keySize);
    for (int i = 0; i < keySize; ++i) {
      assertThat(key.getKeyValue().byteAt(i)).isEqualTo(keyMaterial[i]);
    }
  }

  @Test
  public void testDeriveKey_handlesDataFragmentationCorrectly() throws Exception {
    int keySize = 32;
    byte randomness = 4;
    InputStream fragmentedInputStream =
        new InputStream() {
          @Override
          public int read() {
            return 0;
          }

          @Override
          public int read(byte[] b, int off, int len) {
            b[off] = randomness;
            return 1;
          }
        };

    Ed25519PrivateKey key =
        factory.deriveKey(
            manager, Ed25519KeyFormat.newBuilder().setVersion(0).build(), fragmentedInputStream);

    assertThat(key.getKeyValue()).hasSize(keySize);
    for (int i = 0; i < keySize; ++i) {
      assertThat(key.getKeyValue().byteAt(i)).isEqualTo(randomness);
    }
  }

  @Test
  public void testDeriveKeySignVerify() throws Exception {
    byte[] keyMaterial = Random.randBytes(100);
    Ed25519PrivateKey key =
        factory.deriveKey(
            manager,
            Ed25519KeyFormat.newBuilder().setVersion(0).build(),
            new ByteArrayInputStream(keyMaterial));

    PublicKeySign signer = manager.getPrimitive(key, PublicKeySign.class);
    PublicKeyVerify verifier = new Ed25519Verify(key.getPublicKey().getKeyValue().toByteArray());
    byte[] message = Random.randBytes(135);
    verifier.verify(signer.sign(message), message);
  }

  @Test
  public void testDeriveKeyNotEnoughRandomness() throws Exception {
    byte[] keyMaterial = Random.randBytes(10);
    assertThrows(
        GeneralSecurityException.class,
        () ->
            factory.deriveKey(
                manager,
                Ed25519KeyFormat.newBuilder().setVersion(0).build(),
                new ByteArrayInputStream(keyMaterial)));
  }

  @Test
  public void testDeriveKeyWrongVersion() throws Exception {
    byte[] keyMaterial = Random.randBytes(32);
    assertThrows(
        GeneralSecurityException.class,
        () ->
            factory.deriveKey(
                manager,
                Ed25519KeyFormat.newBuilder().setVersion(1).build(),
                new ByteArrayInputStream(keyMaterial)));
  }

  @DataPoints("templateNames")
  public static final String[] KEY_TEMPLATES =
      new String[] {
        "ED25519", "ED25519_RAW", "ED25519WithRawOutput",
      };

  @Theory
  public void testTemplates(@FromDataPoints("templateNames") String templateName) throws Exception {
    KeysetHandle h = KeysetHandle.generateNew(KeyTemplates.get(templateName));
    assertThat(h.size()).isEqualTo(1);
    assertThat(h.getAt(0).getKey().getParameters())
        .isEqualTo(KeyTemplates.get(templateName).toParameters());
  }
}
