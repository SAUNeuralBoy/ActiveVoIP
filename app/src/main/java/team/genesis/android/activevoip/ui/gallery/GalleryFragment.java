package team.genesis.android.activevoip.ui.gallery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;

import team.genesis.android.activevoip.R;
import team.genesis.android.activevoip.SPManager;
import team.genesis.android.activevoip.UI;
import team.genesis.network.DNSLookupThread;
import team.genesis.tunnels.UDPActiveDatagramTunnel;

public class GalleryFragment extends Fragment {

    private GalleryViewModel galleryViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        galleryViewModel =
                new ViewModelProvider(this).get(GalleryViewModel.class);
        View view = inflater.inflate(R.layout.fragment_gallery, container, false);

        EditText inputHostname = view.findViewById(R.id.input_hostname);
        EditText inputPort = view.findViewById(R.id.input_port);

        SPManager sp = SPManager.getManager(getContext());
        inputHostname.setText(sp.getHostname());
        inputPort.setText(String.valueOf(sp.getPort()));

        view.findViewById(R.id.button_test).setOnClickListener(v -> {
            TextView status = view.findViewById(R.id.status_host);
            status.setTextColor(ContextCompat.getColor(requireContext(),R.color.error_color));
            String hostName = inputHostname.getText().toString().toLowerCase();
            if(hostName.equals("")){
                status.setText(R.string.hostname_empty);
                return;
            }
            int port = Integer.valueOf(inputPort.getText().toString(),10);
            if(port<1||port>65535){
                status.setText(R.string.port_outrange);
                return;
            }
            DNSLookupThread dns = new DNSLookupThread(hostName);
            dns.start();
            try {
                dns.join(1000);
            } catch (InterruptedException e) {
                status.setText(R.string.dns_not_working);
                return;
            }
            if(dns.getIP()==null){
                status.setText(R.string.unknown_host);
                return;
            }
            int cnt = 0;
            try {
                cnt = UDPActiveDatagramTunnel.getProbe(1000).probe(dns.getIP(),port,10);
            } catch (IOException e) {
                status.setText(R.string.system_network_failure);
            }
            if(cnt<=0)
                status.setText(R.string.server_no_response);
            else if(cnt<10) {
                status.setText(String.format(getString(R.string.packet_loss_by_percent), (10 - cnt) * 10));
                status.setTextColor(ContextCompat.getColor(requireContext(),R.color.distrubing_color));
            }
            else{
                status.setText(R.string.server_status_fine);
                status.setTextColor(ContextCompat.getColor(requireContext(),R.color.fine_color));
            }
        });
        view.findViewById(R.id.button_apply).setOnClickListener(v -> {
            String hostName = inputHostname.getText().toString().toLowerCase();
            if(hostName.equals("")){
                UI.makeSnackBar(view,getString(R.string.hostname_empty));
                return;
            }
            int port = Integer.valueOf(inputPort.getText().toString(),10);
            if(port<1||port>65535){
                UI.makeSnackBar(view,getString(R.string.port_outrange));
                return;
            }
            sp.setHostname(hostName);
            sp.setPort(port);
            sp.commit();
            UI.makeSnackBar(view,getString(R.string.host_set));

        });
        return view;
    }

    @Override
    public void onStop() {
        super.onStop();
        requireActivity().findViewById(R.id.button_compass).setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().findViewById(R.id.button_compass).setVisibility(View.GONE);
    }

}