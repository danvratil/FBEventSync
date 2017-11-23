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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

import com.loopj.android.http.*;

public class Graph {

    private static final String BASE_URL = "https://graph.facebook.com/v2.9/";
    private static AsyncHttpClient mClient = new AsyncHttpClient();

    public static final String FIELDS_PARAM = "fields";

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

}
