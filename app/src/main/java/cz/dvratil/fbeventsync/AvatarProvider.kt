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

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory
import android.util.Base64
import com.loopj.android.http.DataAsyncHttpResponseHandler
import cz.msebera.android.httpclient.Header

object AvatarProvider {

    private val PROFILE_PICTURE = "profile_picture"
    private val TAG = "AVATAR"

    fun getAvatar(context: Context, account: Account, avatarCallback: (avatar: Drawable) -> Unit ) {
        val logger = Logger.getInstance(context)
        val am = AccountManager.get(context)
        val picture = am.getUserData(account, AvatarProvider.PROFILE_PICTURE)
        if (picture == null) {
            am.getAuthToken(account, Authenticator.FB_OAUTH_TOKEN, null, false, {
                val token = it.result.getString(AccountManager.KEY_AUTHTOKEN)
                if (token != null)
                    Graph.mePicture(token, object : DataAsyncHttpResponseHandler() {
                        override fun onSuccess(statusCode: Int, headers: Array<out Header>?, responseBody: ByteArray?) {
                            if (responseBody == null) {
                                logger.warning(TAG, "Received empty body from Facebook, ignoring")
                                return
                            }

                            val ct = headers?.find { it.name.equals("content-type", true) }
                            if (ct == null || !ct.value.contains("image/")) {
                                logger.debug(TAG, "Avatar response is not an image: ${ct?.value ?: "unknown content-type"}")
                                return
                            }

                            val bitmap = BitmapFactory.decodeByteArray(responseBody, 0, responseBody.size)
                            if (bitmap == null) {
                                avatarCallback(getDefaultAvatar(context))
                            } else {
                                val b64 = Base64.encodeToString(responseBody, Base64.NO_WRAP)
                                am.setUserData(account, AvatarProvider.PROFILE_PICTURE, b64)

                                avatarCallback(roundedAvatar(context, bitmap))
                            }
                        }

                        override fun onFailure(statusCode: Int, headers: Array<out Header>?, responseBody: ByteArray?, error: Throwable?) {
                            logger.error(TAG, "Failed to retrieve avatar, status: ${statusCode}")
                        }
                    })
            }, null)
        } else {
            val data = Base64.decode(picture as String, Base64.DEFAULT)
            avatarCallback(roundedAvatar(context, BitmapFactory.decodeByteArray(data, 0, data.size)))
        }
    }

    fun getDefaultAvatar(context: Context): Drawable = context.resources.getDrawable(R.drawable.ic_person_black_50dp)

    private fun roundedAvatar(context: Context, bitmap: Bitmap?): Drawable {
        if (bitmap == null) {
            return getDefaultAvatar(context)
        } else {
            return RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply {
                cornerRadius = intrinsicWidth / 2.0f
            }
        }
    }

}