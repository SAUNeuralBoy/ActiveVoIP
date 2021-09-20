package team.genesis.android.activevoip.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import java.util.List;

import team.genesis.android.activevoip.MainActivity;
import team.genesis.android.activevoip.R;
import team.genesis.android.activevoip.db.ContactDB;
import team.genesis.android.activevoip.db.ContactDao;
import team.genesis.android.activevoip.db.ContactEntity;

public class HomeFragment extends Fragment {

    private HomeViewModel homeViewModel;
    private ContactDao dao;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        final TextView textView = root.findViewById(R.id.text_home);
        final RecyclerView listContact = root.findViewById(R.id.list_contact);
        listContact.setLayoutManager(new LinearLayoutManager(getContext()));
        ContactAdapter adapter = new ContactAdapter((MainActivity) requireActivity(),dao.getAllContacts());
        listContact.setAdapter(adapter);
        homeViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        dao.getAllContactsLive().observe(getViewLifecycleOwner(), contactEntities -> {
            adapter.setContactList(contactEntities);
            adapter.notifyDataSetChanged();
        });
        return root;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        dao = ((MainActivity)requireActivity()).getDao();
    }
    @Override
    public void onStop() {
        super.onStop();
        requireActivity().findViewById(R.id.button_edit).setVisibility(View.GONE);
        requireActivity().findViewById(R.id.button_add).setVisibility(View.GONE);
        requireActivity().findViewById(R.id.button_compass).setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().findViewById(R.id.button_edit).setVisibility(View.VISIBLE);
        requireActivity().findViewById(R.id.button_add).setVisibility(View.VISIBLE);
        requireActivity().findViewById(R.id.button_compass).setVisibility(View.VISIBLE);

    }
}