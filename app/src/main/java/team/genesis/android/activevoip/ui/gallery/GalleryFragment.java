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

import team.genesis.android.activevoip.R;

public class GalleryFragment extends Fragment {

    private GalleryViewModel galleryViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        galleryViewModel =
                new ViewModelProvider(this).get(GalleryViewModel.class);
        View view = inflater.inflate(R.layout.fragment_gallery, container, false);
        view.findViewById(R.id.button_test).setOnClickListener(v -> {
            TextView status = view.findViewById(R.id.status_host);
            String hostName = ((EditText)view.findViewById(R.id.input_hostname)).getText().toString().toLowerCase();
            if(hostName.equals("")){
                status.setText(R.string.hostname_empty);
                status.setTextColor(ContextCompat.getColor(requireContext(),R.color.error_color));
                return;
            }
            int port = Integer.valueOf(((EditText)view.findViewById(R.id.input_port)).getText().toString(),10);
            if(port<1||port>65535){
                status.setText(R.string.port_outrange);
                status.setTextColor(ContextCompat.getColor(requireContext(),R.color.error_color));
                return;
            }
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