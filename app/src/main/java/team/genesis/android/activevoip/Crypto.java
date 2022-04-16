package team.genesis.android.activevoip;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import team.genesis.data.UUID;

import static java.lang.System.exit;

public class Crypto {
    public static String to64(byte[] bytes){
        return Base64.encodeToString(bytes,Base64.NO_WRAP);
    }
    public static byte[] from64(String s){
        return Base64.decode(s,Base64.NO_WRAP);
    }
    public static UUID randomUUID(){
        byte[] randBytes = new byte[16];
        new SecureRandom().nextBytes(randBytes);
        return new UUID(randBytes);
    }
    public static byte[] md5(byte[] data){
        try {
            return MessageDigest.getInstance("MD5").digest(data);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            exit(0);
        }
        return null;
    }
    public static byte[] sha256(byte[] data){
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            exit(0);
        }
        return null;
    }
    public static String bytesToHex(byte[] bytes,String separator){
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            String hex = Integer.toHexString(aByte & 0xFF);
            if (hex.length() < 2) {
                sb.append(0);
            }
            sb.append(hex);
            sb.append(separator);
        }
        sb.delete(sb.length()-separator.length(),sb.length());
        return sb.toString();
    }
    public static SecretKey getOrCreate(String alias){
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            try {
                if(keyStore.containsAlias(alias))
                    //return ((KeyStore.SecretKeyEntry)keyStore.getEntry(alias,null)).getSecretKey();
                    return (SecretKey) keyStore.getKey(alias,null);
            } catch (KeyStoreException | UnrecoverableEntryException ignored) {
            }
            KeyGenerator keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            keyGen.init(new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setDigests(KeyProperties.DIGEST_SHA256,
                            KeyProperties.DIGEST_SHA512)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException | KeyStoreException | CertificateException | IOException e) {
            e.printStackTrace();
            exit(0);
            return null;
        }
    }
    public static SecretKey masterKey = getOrCreate("master_key");
    public static byte[] encryptWithMasterKey(byte[] data){
        try {
            Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
            gcm.init(Cipher.ENCRYPT_MODE, masterKey);
            byte[] iv = gcm.getIV();
            gcm.updateAAD(md5(iv), 0, 16);
            byte[] cipher = gcm.doFinal(data);
            byte[] buf = new byte[iv.length + cipher.length];
            System.arraycopy(iv, 0, buf, 0, iv.length);
            System.arraycopy(cipher, 0, buf, iv.length, cipher.length);
            return buf;
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
            exit(0);
            return null;
        }
    }
    public static byte[] decryptWithMasterKey(byte[] cipherText) throws DecryptException {
        try {
            Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = Arrays.copyOf(cipherText, 12);
            gcm.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(128, iv));
            gcm.updateAAD(md5(iv));
            return gcm.doFinal(Arrays.copyOfRange(cipherText, 12, cipherText.length));
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchPaddingException e) {
            e.printStackTrace();
            exit(0);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new DecryptException();
        }
        return null;
    }
    public static class DecryptException extends Exception {}
}
