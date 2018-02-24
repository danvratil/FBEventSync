/*
    Copyright (C) 2017  Daniel Vrátil <me@dvratil.cz>

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

import com.loopj.android.http.JsonHttpResponseHandler

import org.json.JSONObject

import cz.msebera.android.httpclient.Header


class GraphResponseHandler(private var mContext: Context) : JsonHttpResponseHandler() {

    private var mLogger=  Logger.getInstance(mContext)

    var response: JSONObject? = null

    override fun onSuccess(statusCode: Int, headers: Array<Header>?, response: JSONObject?) {
        if (response?.has("error") == true) {
            onFailure(statusCode, headers, null, response)
        } else {
            this.response = response
        }
    }

    override fun onFailure(statusCode: Int, headers: Array<Header>?, throwable: Throwable?, errorResponse: JSONObject?) {
        if (errorResponse == null) {
            mLogger.error(TAG, "Graph error: failure and empty response (code $statusCode)")
            return
        }
        try {
            val err = errorResponse.getJSONObject("error")
            val errCode = err.getInt("code")
            when {
                errCode == 190 -> {
                    requestTokenRefresh()
                    return
                }
                errorResponse.has("message") -> mLogger.error(TAG, "Graph error: ${errorResponse.getString("message")}")
                else -> mLogger.error(TAG, "Graph error: ${errorResponse}")
            }
            response = errorResponse
        } catch (e: org.json.JSONException) {
            mLogger.error(TAG, "onFailure: $e")
        }
    }

    private fun requestTokenRefresh() {
        Graph.refreshTokens(mContext, AuthenticatorActivity.TOKEN_SCOPE, this)
    }

    companion object {
        private const val TAG = "GRAPH"
    }
}
