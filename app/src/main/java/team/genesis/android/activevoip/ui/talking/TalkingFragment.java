package team.genesis.android.activevoip.ui.talking;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import team.genesis.android.activevoip.R;


public class TalkingFragment extends Fragment {


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_talking, container, false);
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
}