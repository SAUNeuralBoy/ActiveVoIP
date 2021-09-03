package team.genesis.android.activevoip;

import android.view.View;

import com.google.android.material.snackbar.Snackbar;

public class UI {
    public static void makeSnackBar(View view, String text){
        Snackbar.make(view, text, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }
}
