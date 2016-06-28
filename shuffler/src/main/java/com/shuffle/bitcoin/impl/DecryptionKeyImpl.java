package com.shuffle.bitcoin.impl;

import com.google.inject.Guice;
import com.shuffle.JvmModule;
import com.shuffle.bitcoin.Address;
import com.shuffle.bitcoin.BitcoinCrypto;
import com.shuffle.bitcoin.DecryptionKey;
import com.shuffle.bitcoin.EncryptionKey;
import com.shuffle.protocol.FormatException;

import org.bitcoinj.core.ECKey;
import org.spongycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;


/**
 * A private key used for decryption.
 */

public class DecryptionKeyImpl implements DecryptionKey {

   final ECKey key;
   final byte[] encryptionKey;
   final PrivateKey privateKey;
   final PublicKey publicKey;
   BitcoinCrypto bitcoinCrypto;

   public DecryptionKeyImpl(org.bitcoinj.core.ECKey key) {
      this.key = key;
      this.encryptionKey = key.getPubKey();
      this.privateKey = null;
      this.publicKey = null;
      this.bitcoinCrypto = new BitcoinCrypto();
   }
   public DecryptionKeyImpl(KeyPair keyPair) {
      this.privateKey = keyPair.getPrivate();
      this.publicKey = keyPair.getPublic();
      this.key = ECKey.fromPrivate(this.privateKey.getEncoded());
      this.encryptionKey = this.publicKey.getEncoded();
      this.bitcoinCrypto = new BitcoinCrypto();
   }
   public DecryptionKeyImpl(org.bitcoinj.core.ECKey key, BitcoinCrypto bitcoinCrypto) {
      this.key = key;
      this.encryptionKey = key.getPubKey();
      this.privateKey = null;
      this.publicKey = null;
      this.bitcoinCrypto = bitcoinCrypto;
   }
   public DecryptionKeyImpl(KeyPair keyPair, BitcoinCrypto bitcoinCrypto) {
      this.privateKey = keyPair.getPrivate();
      this.publicKey = keyPair.getPublic();
      this.key = ECKey.fromPrivate(this.privateKey.getEncoded());
      this.encryptionKey = this.publicKey.getEncoded();
      this.bitcoinCrypto = bitcoinCrypto;
   }


   // returns private key in Private Key WIF (compressed, 52 characters base58, starts with a 'c')
   public java.lang.String toString() {
      return this.key.getPrivateKeyAsWiF(bitcoinCrypto.getParams());
   }

   public ECKey getKey() {
      return key;
   }

   @Override
   public EncryptionKey EncryptionKey() {
      ECKey pubkey = ECKey.fromPublicOnly(key.getPubKey());
      return new EncryptionKeyImpl(pubkey);
   }


   @Override
   public Address decrypt(Address m) throws FormatException {
      Guice.createInjector(new JvmModule()).injectMembers(this);
      java.lang.String input = m.toString();
      AddressImpl returnAddress = null;
      if (bitcoinCrypto.isValidAddress(input)) {
         return new AddressImpl(input,true);
      } else {
         try {
            KeyFactory kf = KeyFactory.getInstance("ECIES");
            PrivateKey privateKey = kf.generatePrivate(kf.getKeySpec((Key) key, KeySpec.class));

            //encrypt cipher
            Cipher cipher = Cipher.getInstance("ECIES");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] bytes = m.toString().getBytes(StandardCharsets.UTF_8);
            byte[] decrypted = cipher.doFinal(bytes);
            returnAddress = new AddressImpl(Hex.toHexString(decrypted),false);
         } catch (Exception e) {
            e.printStackTrace();
            throw new FormatException();
         }
      }
      return returnAddress;
   }

}
