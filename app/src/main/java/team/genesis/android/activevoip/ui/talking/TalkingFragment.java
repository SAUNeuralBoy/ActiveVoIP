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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import team.genesis.android.activevoip.Crypto;
import team.genesis.android.activevoip.MainActivity;
import team.genesis.android.activevoip.R;
import team.genesis.android.activevoip.UI;
import team.genesis.android.activevoip.VoIPService;
import team.genesis.android.activevoip.data.Contact;


public class TalkingFragment extends Fragment {

    private MainActivity activity;
    private VoIPService service;
    private TalkingViewModel viewModel;
    private TextView textStatus;
    private LinearLayout layoutIncoming;
    private LinearLayout layoutTalking;
    private NavController navController;
    private Handler uiHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        activity = (MainActivity) requireActivity();
        service = activity.getService();
        uiHandler = new Handler();
        viewModel = new ViewModelProvider(activity).get(TalkingViewModel.class);
        View root = inflater.inflate(R.layout.fragment_talking, container, false);
        navController = Navigation.findNavController(activity, R.id.nav_host_fragment);
        textStatus = root.findViewById(R.id.status_talking);
        layoutIncoming = root.findViewById(R.id.layout_incoming_action);
        layoutTalking = root.findViewById(R.id.layout_talking);
        service.getLiveStat().observe(getViewLifecycleOwner(), new Observer<VoIPService.Status>() {
            @Override
            public void onChanged(VoIPService.Status status) {
                if(status== VoIPService.Status.READY) {
                    backHome();
                    return;
                }
                switch (status){
                    case CALLING:
                        setCalling();
                        break;
                    case REJECTED:
                        UI.makeSnackBar(root,getString(R.string.call_refused));
                        backHome();
                        break;
                    case INCOMING:
                        setIncoming(service.getContact());
                        break;
                    case TALKING:
                        textStatus.setText(R.string.talking);
                        break;
                }
            }
        });
        return root;
    }

    private void backHome() {
        navController.navigate(R.id.nav_home);
    }

    @Override
    public void onStop() {
        super.onStop();
        ImageButton compass = activity.findViewById(R.id.button_compass);
        compass.setVisibility(View.GONE);
        compass.setClickable(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        ImageButton compass = activity.findViewById(R.id.button_compass);
        compass.setVisibility(View.VISIBLE);
        compass.setClickable(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    private void resetVisibility(){
        layoutIncoming.setVisibility(View.GONE);
        layoutTalking.setVisibility(View.GONE);
    }
    private void setCalling(){
        resetVisibility();
        textStatus.setText(R.string.calling);
    }
    private void setIncoming(Contact contact){
        resetVisibility();
        textStatus.setText(String.format(getString(R.string.incoming_call_with_format),contact.alias,Crypto.to64(contact.uuid.getBytes()),Crypto.bytesToHex(contact.pkSHA256(),":")));
        layoutIncoming.setVisibility(View.VISIBLE);
    }
}