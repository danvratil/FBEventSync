package cz.dvratil.fbeventsync;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.ArrayList;

public class PermissionRequestActivity extends AppCompatActivity {

    public static final String MISSING_PERMISSIONS = "cz.dvratil.fbeventsync.PermissionRequestActivity.MISSING_PERMISSIONS";
    public static final String PERMISSION_NOTIFY = "cz.dvratil.fbeventsync.PermissionRequestActivity.PERMISSION_NOTIFY";

    private static final int PERMISSION_REQUEST_ID = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        ArrayList<String> missingPerms = extras.getStringArrayList(MISSING_PERMISSIONS);

        ActivityCompat.requestPermissions(this,missingPerms.toArray(new String[missingPerms.size()]),
                                          PERMISSION_REQUEST_ID);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_ID:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("PERMS", "Permissions granted by user");
                } else {
                    Log.d("PERMS","Permissions denied by user!");
                }
                break;
        }

        CalendarSyncAdapter.requestSync(this);
        finish();
    }
}
