package team.genesis.android.activevoip.ui.talking;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.os.Handler;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.crypto.KeyAgreement;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import team.genesis.android.activevoip.Crypto;
import team.genesis.android.activevoip.MainActivity;
import team.genesis.android.activevoip.Network;
import team.genesis.android.activevoip.R;
import team.genesis.android.activevoip.UI;
import team.genesis.android.activevoip.data.Contact;
import team.genesis.android.activevoip.network.Ctrl;
import team.genesis.data.UUID;

import static java.lang.System.exit;


public class TalkingFragment extends Fragment {

    private TalkingViewModel viewModel;
    private Contact contact;
    private TextView status;
    private Handler uiHandler;
    private MainActivity activity;
    private byte[] derivedKey;
    private UUID ourId;
    private UUID otherId;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        activity = (MainActivity) requireActivity();
        viewModel = new ViewModelProvider(requireActivity()).get(TalkingViewModel.class);
        View root = inflater.inflate(R.layout.fragment_talking, container, false);
        status = root.findViewById(R.id.status_talking);
        contact = viewModel.getContact();
        uiHandler = new Handler();
        NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);

        viewModel.isReadyToTalk().observe(getViewLifecycleOwner(), aBoolean -> {
            if(aBoolean&&viewModel.getStatus()!=TalkingViewModel.Status.TALKING){
                viewModel.setStatus(TalkingViewModel.Status.TALKING);
                status.setText(Crypto.bytesToHex(derivedKey,":"));
            }
        });

        switch (viewModel.getStatus()){
            case CALLING:{ try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
                kpg.initialize(256);
                KeyPair kp = kpg.generateKeyPair();
                byte[] ourPk = kp.getPublic().getEncoded();
                ByteBuf buf = Unpooled.buffer();
                buf.writeInt(Ctrl.CALL.ordinal());
                Network.writeBytes(buf,ourPk);
                Signature s = Signature.getInstance("SHA256withECDSA");
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                if (keyStore.containsAlias(Crypto.to64(contact.uuid.getBytes()))) {
                    s.initSign(((KeyStore.PrivateKeyEntry) keyStore.getEntry(Crypto.to64(contact.uuid.getBytes()), null)).getPrivateKey());
                    s.update(ourPk);
                    byte[] sign = s.sign();
                    viewModel.setOurPk(ourPk);
                    Network.writeBytes(buf,sign);
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            if(viewModel.getStatus()== TalkingViewModel.Status.REJECTED){
                                UI.makeSnackBar(root,getString(R.string.call_refused));
                                navController.navigate(R.id.nav_home);
                                return;
                            }
                            if(viewModel.getStatus()== TalkingViewModel.Status.CALL_ACCEPTED){try {
                                KeyAgreement ka = KeyAgreement.getInstance("ECDH");
                                ka.init(kp.getPrivate());
                                ka.doPhase(KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePublic(new X509EncodedKeySpec(viewModel.getOtherPk())), true);
                                byte[] sharedSecret = ka.generateSecret();
                                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                                hash.update(sharedSecret);
                                // Simple deterministic ordering
                                List<ByteBuffer> keys = Arrays.asList(ByteBuffer.wrap(ourPk), ByteBuffer.wrap(viewModel.getOtherPk()));
                                Collections.sort(keys);
                                hash.update(keys.get(0));
                                hash.update(keys.get(1));
                                derivedKey = hash.digest();
                                ourId = new UUID(Crypto.md5(ourPk));
                                otherId = new UUID(Crypto.md5(viewModel.getOtherPk()));
                                ByteBuf ack = Unpooled.buffer();
                                ack.writeInt(Ctrl.CALL_ACK.ordinal());
                                activity.write(ack.array(),viewModel.getContact().uuid);
                                viewModel.setReadyToTalk(true);
                                return;
                            }catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                                exit(0);
                            } catch (InvalidKeyException | InvalidKeySpecException e) {
                                UI.makeSnackBar(getView(), getString(R.string.pair_failed));
                                navController.navigate(R.id.nav_home);
                                return;
                            }
                            }
                            if (viewModel.getStatus()!= TalkingViewModel.Status.CALLING) return;
                            activity.write(buf.array(), contact.uuid);
                            uiHandler.postDelayed(this, 1000);
                        }
                    };
                    uiHandler.post(r);
                    status.setText(R.string.calling);

                } else {
                    UI.makeSnackBar(getView(), getString(R.string.pair_failed));
                    navController.navigate(R.id.nav_home);
                }
            } catch (SignatureException | UnrecoverableEntryException | CertificateException | InvalidKeyException | KeyStoreException | IOException e) {
                UI.makeSnackBar(getView(), getString(R.string.pair_failed));
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment).navigate(R.id.nav_home);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                exit(0);
            }
            break;
            }
            case INVOKING:
                status.setText(String.format(getString(R.string.incoming_call_with_format), contact.alias, Crypto.to64(contact.uuid.getBytes()), Crypto.bytesToHex(contact.pkSHA256(),":")));
                root.findViewById(R.id.layout_incoming_action).setVisibility(View.VISIBLE);
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        if(viewModel.getStatus()== TalkingViewModel.Status.INCOMING)
                            navController.navigate(R.id.nav_home);
                        else if(viewModel.getStatus()== TalkingViewModel.Status.INVOKING) {
                            viewModel.setStatus(TalkingViewModel.Status.INCOMING);
                            uiHandler.postDelayed(this,2500);
                        }
                    }
                };
                uiHandler.post(r);
                break;
        }
        root.findViewById(R.id.button_reject_call).setOnClickListener(v -> {
            ByteBuf buf = Unpooled.buffer();
            buf.writeInt(Ctrl.CALL_REJECT.ordinal());
            activity.write(buf.array(),contact.uuid);
            navController.navigate(R.id.nav_home);
        });
        root.findViewById(R.id.button_accept_call).setOnClickListener(v -> {try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
            kpg.initialize(256);
            KeyPair kp = kpg.generateKeyPair();
            byte[] ourPk = kp.getPublic().getEncoded();
            ByteBuf buf = Unpooled.buffer();
            buf.writeInt(Ctrl.CALL_RESPONSE.ordinal());
            Network.writeBytes(buf, ourPk);
            Signature s = Signature.getInstance("SHA256withECDSA");
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(Crypto.to64(contact.uuid.getBytes()))) {
                PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) keyStore.getEntry(Crypto.to64(contact.uuid.getBytes()), null)).getPrivateKey();
                KeyAgreement ka = KeyAgreement.getInstance("ECDH");
                ka.init(kp.getPrivate());
                ka.doPhase(KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePublic(new X509EncodedKeySpec(viewModel.getOtherPk())), true);
                byte[] sharedSecret = ka.generateSecret();
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                hash.update(sharedSecret);
                // Simple deterministic ordering
                List<ByteBuffer> keys = Arrays.asList(ByteBuffer.wrap(ourPk), ByteBuffer.wrap(viewModel.getOtherPk()));
                Collections.sort(keys);
                hash.update(keys.get(0));
                hash.update(keys.get(1));
                derivedKey = hash.digest();
                ourId = new UUID(Crypto.md5(ourPk));
                otherId = new UUID(Crypto.md5(viewModel.getOtherPk()));
                s.initSign(privateKey);
                s.update(ourPk);
                byte[] sign = s.sign();
                Network.writeBytes(buf, sign);
                Network.writeBytes(buf, viewModel.getOtherPk());
                viewModel.setStatus(TalkingViewModel.Status.ACCEPT_CALL);
                v.setClickable(false);
                uiHandler.postDelayed(() -> {
                    if (viewModel.getStatus() == TalkingViewModel.Status.ACCEPT_CALL) {
                        UI.makeSnackBar(root, getString(R.string.call_interrupted));
                        navController.navigate(R.id.nav_home);
                    }
                }, 5000);
                activity.write(buf.array(),contact.uuid);
            } else {
                UI.makeSnackBar(getView(), getString(R.string.pair_failed));
                navController.navigate(R.id.nav_home);
            }
        }catch (InvalidKeyException | UnrecoverableEntryException | KeyStoreException | CertificateException | SignatureException | InvalidKeySpecException | IOException e) {
            UI.makeSnackBar(getView(), getString(R.string.pair_failed));
            navController.navigate(R.id.nav_home);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            exit(0);
        }
        });
        return root;
    }
    @Override
    public void onStop() {
        super.onStop();
        ImageButton compass = requireActivity().findViewById(R.id.button_compass);
        compass.setVisibility(View.GONE);
        compass.setClickable(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        ImageButton compass = requireActivity().findViewById(R.id.button_compass);
        compass.setVisibility(View.VISIBLE);
        compass.setClickable(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewModel.setStatus(TalkingViewModel.Status.DEAD);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }
}