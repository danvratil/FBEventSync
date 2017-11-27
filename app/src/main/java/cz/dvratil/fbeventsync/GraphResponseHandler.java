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

package cz.dvratil.fbeventsync;

import android.content.Context;

import org.json.JSONObject;

import com.loopj.android.http.JsonHttpResponseHandler;
import cz.msebera.android.httpclient.Header;


abstract public class GraphResponseHandler extends JsonHttpResponseHandler {

    private Logger mLogger = null;
    private Context mContext = null;

    public GraphResponseHandler(Context context) {
        mContext = context;
        mLogger = Logger.getInstance(context);
    }

    abstract public void handleResponse(JSONObject response);

    @Override
    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
        if (response.has("error")) {
            onFailure(statusCode, headers, null, response);
        } else {
            handleResponse(response);
        }
    }

    @Override
    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
        try {
            JSONObject err = errorResponse.getJSONObject("error");
            int errCode = err.getInt("code");
            if (errCode == 190) {
                requestTokenRefresh();
            } else if (errorResponse.has("message")) {
                mLogger.error("GRAPH", "Graph error:" + errorResponse.getString("message"));
            } else {
                mLogger.error("GRAPH", "Graph error:" + errorResponse.toString());
            }
        } catch (org.json.JSONException e) {
            mLogger.error("GRAPH", "JSONException: %s", e.getMessage());
        }
    }

    private void requestTokenRefresh() {
        Graph.refreshTokens(mContext, AuthenticatorActivity.TOKEN_SCOPE, this);
    }
}
