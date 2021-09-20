package team.genesis.android.activevoip.ui.edit;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import team.genesis.android.activevoip.MainActivity;
import team.genesis.android.activevoip.R;
import team.genesis.android.activevoip.db.ContactDao;
import team.genesis.android.activevoip.ui.home.ContactAdapter;


public class EditFragment extends Fragment {


    private ContactDao dao;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_edit, container, false);
        final RecyclerView listContact = root.findViewById(R.id.list_edit_contact);
        listContact.setLayoutManager(new LinearLayoutManager(getContext()));
        ContactAdapter adapter = new ContactAdapter((MainActivity) requireActivity(),dao.getAllContacts(),true);
        listContact.setAdapter(adapter);
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
}