package team.genesis.android.activevoip;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.snackbar.Snackbar;

import java.nio.charset.StandardCharsets;

public class UI {
    public static void makeSnackBar(View view, String text){
        Snackbar.make(view, text, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }
    public static void makeInputWindow(Context context, final EditText input, String title, DialogInterface.OnClickListener onClickListener){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title).setIcon(android.R.drawable.ic_dialog_info).setView(input);
        builder.setPositiveButton(R.string.confirm, onClickListener);
        builder.show();
    }
    public static Handler getCycledHandler(String name){
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new Handler(thread.getLooper());
    }
}
