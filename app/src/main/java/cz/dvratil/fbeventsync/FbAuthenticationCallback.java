package cz.dvratil.fbeventsync;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;

import com.facebook.login.LoginResult;
import com.facebook.FacebookException;
import com.facebook.FacebookCallback;

public class FbAuthenticationCallback implements FacebookCallback<LoginResult> {

    private Activity mActivity;

    public FbAuthenticationCallback(Activity activity) {
        mActivity = activity;
    }

    @Override
    public void onSuccess(LoginResult loginResult) {

    }

    @Override
    public void onCancel() {


    }

    @Override
    public void onError(FacebookException error) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle("Authentication Error")
               .setMessage("An error occurred during Facebook authentication: " + error.getLocalizedMessage())
               .setCancelable(false)
               .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int i) {
                       dialog.cancel();
                       mActivity.finish();
                   }
               })
               .create()
               .show();
    }
}

