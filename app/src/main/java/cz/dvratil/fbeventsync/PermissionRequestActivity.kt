/*
    Copyright (C) 2017 - 2018  Daniel Vrátil <me@dvratil.cz>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package cz.dvratil.fbeventsync

import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log

class PermissionRequestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val missingPerms = intent.extras?.getStringArrayList(MISSING_PERMISSIONS) ?: return
        ActivityCompat.requestPermissions(this, missingPerms.toTypedArray<String>(),
                PERMISSION_REQUEST_ID)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_ID -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permissions granted by user")
            } else {
                Log.d(TAG, "Permissions denied by user!")
            }
        }

        CalendarSyncAdapter.requestSync(this)
        finish()
    }

    companion object {
        const val MISSING_PERMISSIONS = "cz.dvratil.fbeventsync.PermissionRequestActivity.MISSING_PERMISSIONS"
        const val PERMISSION_NOTIFY = "cz.dvratil.fbeventsync.PermissionRequestActivity.PERMISSION_NOTIFY"

        private const val PERMISSION_REQUEST_ID = 0

        private const val TAG = "PERMS"
    }
}
