package team.genesis.android.activevoip.ui.slideshow;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.security.crypto.EncryptedSharedPreferences;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import team.genesis.android.activevoip.Crypto;
import team.genesis.android.activevoip.MainActivity;
import team.genesis.android.activevoip.R;
import team.genesis.android.activevoip.SPManager;
import team.genesis.android.activevoip.UI;
import team.genesis.data.UUID;

public class SlideshowFragment extends Fragment {

    private SlideshowViewModel slideshowViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        slideshowViewModel =
                new ViewModelProvider(this).get(SlideshowViewModel.class);
        View view = inflater.inflate(R.layout.fragment_slideshow, container, false);

        EditText inputUUID = view.findViewById(R.id.input_uuid);

        SPManager sp = SPManager.getManager((MainActivity) getActivity());
        inputUUID.setText(sp.getUUID64());

        view.findViewById(R.id.button_input_contact_name).setOnClickListener(v -> {
//            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
//            final EditText input = new EditText(getContext());
//            builder.setTitle(getString(R.string.contact_input_title)).setIcon(android.R.drawable.ic_dialog_info).setView(input);
//            builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
//                if(input.getText().toString().equals("")){
//                    UI.makeSnackBar(view,getString(R.string.contact_empty));
//                    return;
//                }
//                inputUUID.setText(Crypto.to64(Crypto.md5(input.getText().toString().getBytes(StandardCharsets.UTF_8))));
//            });
//            builder.show();
            final EditText input = new EditText(getContext());
            UI.makeInputWindow(getContext(),input,getString(R.string.contact_input_title), (dialog, which) -> {
                if(input.getText().toString().equals("")){
                    UI.makeSnackBar(view,getString(R.string.contact_empty));
                    return;
                }
                inputUUID.setText(Crypto.to64(Crypto.md5(input.getText().toString().getBytes(StandardCharsets.UTF_8))));
            });
        });
        view.findViewById(R.id.button_copy_uuid).setOnClickListener(v -> {
            ((ClipboardManager)getActivity().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Label",inputUUID.getText()));
            UI.makeSnackBar(view,getString(R.string.copied));
        });
        view.findViewById(R.id.button_apply_uuid).setOnClickListener(v -> {
            sp.setUUID(inputUUID.getText().toString());
            sp.commit();
            UI.makeSnackBar(view,getString(R.string.uuid_set));
        });
        view.findViewById(R.id.button_rollback_uuid).setOnClickListener(v -> inputUUID.setText(sp.getUUID64()));
        view.findViewById(R.id.button_generate_uuid).setOnClickListener(v-> inputUUID.setText(Crypto.to64(Crypto.randomUUID().getBytes())));
        return view;
    }
}