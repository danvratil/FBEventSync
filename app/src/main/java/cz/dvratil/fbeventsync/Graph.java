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
import android.util.Log;

import com.loopj.android.http.*;

public class Graph {

    private static final String BASE_URL = "https://graph.facebook.com/v2.9/";

    public static final String FIELDS_PARAM = "fields";
    public static final String LIMIT_PARAM = "limit";
    public static final String TYPE_PARAM = "type";
    public static final String AFTER_PARAM = "after";

    public static RequestHandle me(String accessToken, AsyncHttpResponseHandler handler) {
        RequestParams params = new RequestParams();
        params.add("access_token", accessToken);
        params.add(FIELDS_PARAM, "name");
        return new AsyncHttpClient().get(BASE_URL+ "/me", params, handler);
    }

    public static RequestHandle events(String accessToken, RequestParams params, AsyncHttpResponseHandler handler) {
        SyncHttpClient client = new SyncHttpClient();
        params.add("access_token", accessToken);
        return client.get(BASE_URL + "/me/events", params, handler);
    }

    public static RequestHandle refreshTokens(Context context, String scopes, AsyncHttpResponseHandler handler) {
        SyncHttpClient client = new SyncHttpClient();
        RequestParams params = new RequestParams();
        params.put("client_id", context.getString(R.string.facebook_app_id));
        params.put("redirect_uri", "https://www.facebook.com/connect/login_success.html");
        params.put("response_type", "token");
        params.put("scopes", scopes);
        return client.get("https://www.facebook.com/v2.9/dialog/oauth", params, handler);
    }

    public static RequestHandle fetchBirthdayICal(String birthdayICalUri, AsyncHttpResponseHandler handler) {
        SyncHttpClient client = new SyncHttpClient();
        // Pretend we are cURL, so that Facebook does not redirect us to facebook.com/unsupportedbrowser
        client.setUserAgent("curl/7.55.1");
        Log.d("GRAPH", "fetchBirthdayICal: " + birthdayICalUri);
        return client.get(birthdayICalUri, handler);
    }
}
