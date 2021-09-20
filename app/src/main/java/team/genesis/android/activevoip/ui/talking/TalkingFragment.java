package team.genesis.android.activevoip.ui.talking;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import team.genesis.android.activevoip.Crypto;
import team.genesis.android.activevoip.MainActivity;
import team.genesis.android.activevoip.R;
import team.genesis.android.activevoip.UI;
import team.genesis.android.activevoip.data.Contact;
import team.genesis.android.activevoip.network.Ctrl;
import team.genesis.android.activevoip.ui.home.HomeViewModel;

import static java.lang.System.exit;


public class TalkingFragment extends Fragment {

    private boolean calling;
    private TalkingViewModel viewModel;
    private Contact contact;
    private TextView status;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        viewModel = new ViewModelProvider(requireActivity()).get(TalkingViewModel.class);
        View root = inflater.inflate(R.layout.fragment_talking, container, false);
        status = root.findViewById(R.id.status_talking);

        try {
            contact = viewModel.getContact();
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            kpg.initialize(new KeyGenParameterSpec.Builder(
                    "talking_ec_key",
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256,
                            KeyProperties.DIGEST_SHA512)
                    .build());
            KeyPair kp = kpg.generateKeyPair();
            byte[] ourPk = kp.getPublic().getEncoded();
            ByteBuf buf = Unpooled.buffer();
            buf.writeInt(Ctrl.CALL.ordinal());
            buf.writeBytes(ourPk);
            Signature s = Signature.getInstance("SHA256withECDSA");
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(Crypto.to64(contact.uuid.getBytes()))) {
                s.initSign(((KeyStore.PrivateKeyEntry) keyStore.getEntry(Crypto.to64(contact.uuid.getBytes()), null)).getPrivateKey());
                s.update(ourPk);
                buf.writeBytes(s.sign());
                Handler uiHandler = new Handler();
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        if (!calling) return;
                        ((MainActivity) requireActivity()).write(buf.array(), contact.uuid);
                        uiHandler.postDelayed(this, 1000);
                    }
                };
                calling = true;
                uiHandler.post(r);
                status.setText(R.string.calling);

            } else {
                UI.makeSnackBar(getView(), getString(R.string.pair_failed));
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment).navigate(R.id.nav_home);
            }
        } catch (SignatureException | UnrecoverableEntryException | CertificateException | InvalidKeyException | KeyStoreException | IOException e) {
            UI.makeSnackBar(getView(), getString(R.string.pair_failed));
            Navigation.findNavController(requireActivity(), R.id.nav_host_fragment).navigate(R.id.nav_home);
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | NoSuchProviderException e) {
            e.printStackTrace();
            exit(0);
        }


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
        calling = false;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }
}