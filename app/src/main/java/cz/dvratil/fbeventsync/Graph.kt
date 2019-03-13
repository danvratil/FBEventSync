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

import android.content.Context

import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.AsyncHttpResponseHandler
import com.loopj.android.http.RequestHandle
import com.loopj.android.http.RequestParams
import com.loopj.android.http.SyncHttpClient

object Graph {

    private const val BASE_URL = "https://graph.facebook.com/v2.9/"

    const val FIELDS_PARAM = "fields"
    const val LIMIT_PARAM = "limit"
    const val AFTER_PARAM = "after"
    const val ACCESS_TOKEN_PARAM = "access_token"

    fun me(accessToken: String, handler: AsyncHttpResponseHandler): RequestHandle {
        val params = RequestParams().apply {
            add(ACCESS_TOKEN_PARAM, accessToken)
            add(FIELDS_PARAM, "name")
        }
        return AsyncHttpClient().get(BASE_URL + "/me", params, handler)
    }

    fun mePicture(accessToken: String, handler: AsyncHttpResponseHandler): RequestHandle {
        var params = RequestParams().apply {
            add(ACCESS_TOKEN_PARAM, accessToken)
        }
        return AsyncHttpClient().get(BASE_URL + "/me/picture", params, handler)
    }

    fun events(accessToken: String, params: RequestParams, handler: AsyncHttpResponseHandler): RequestHandle {
        val client = SyncHttpClient()
        params.add(ACCESS_TOKEN_PARAM, accessToken)
        return client.get(BASE_URL + "/me/events", params, handler)
    }

    fun refreshTokens(context: Context, scopes: String, handler: AsyncHttpResponseHandler): RequestHandle {
        val client = SyncHttpClient()
        val params = RequestParams().apply {
            put("client_id", context.getString(R.string.facebook_app_id))
            put("redirect_uri", "https://www.facebook.com/connect/login_success.html")
            put("response_type", "token")
            put("scopes", scopes)
        }
        return client.get("https://www.facebook.com/v2.9/dialog/oauth", params, handler)
    }

    fun fetchBirthdayICal(birthdayICalUri: String, handler: AsyncHttpResponseHandler): RequestHandle {
        val client = SyncHttpClient()
        client.connectTimeout = 30000 // 30 seconds
        client.responseTimeout = 60000 // 60 seconds
        // Pretend we are cURL, so that Facebook does not redirect us to facebook.com/unsupportedbrowser
        client.setUserAgent("curl/7.55.1")
        return client.get(birthdayICalUri, handler)
    }
}
