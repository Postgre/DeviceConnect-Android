package org.deviceconnect.android.manager.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import org.deviceconnect.android.manager.DConnectSettings;
import org.deviceconnect.android.manager.profile.AuthorizationProfile;
import org.deviceconnect.message.DConnectMessage;
import org.deviceconnect.utils.URIBuilder;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Authorization extends AsyncTask<Void, Void, List<ServiceContainer>> {

    private static final String FILE_NAME = "__authorization__.dat";
    private static final String KEY_CLIENT_ID = "clientId";
    private static final String KEY_ACCESS_TOKEN = "accessToken";

    private DConnectSettings mSettings;
    private SharedPreferences mSharedPreferences;
    private String mClientId;
    private String mAccessToken;

    public Authorization(final Context context) {
        mSettings = DConnectSettings.getInstance();
        mSettings.load(context);
        load(context);
    }

    protected String getUri(final String path) {
        return getUri(path, null);
    }

    protected String getUri(final String path, final Map<String, String> params) {
        URIBuilder builder = new URIBuilder();
        builder.setScheme(mSettings.isSSL() ? "https" : "http");
        builder.setHost("localhost");
        builder.setPort(mSettings.getPort());
        builder.setPath(path);
        if (params != null) {
            for (String key : params.keySet()) {
                builder.addParameter(key, params.get(key));
            }
        }
        return builder.toString();
    }

    private String executeGrant() {
        String uri = getUri(AuthorizationProfile.PATH_REQUEST_GRANT);
        byte[] bytes = HttpUtil.get(uri);
        if (bytes != null) {
            return new String(bytes);
        }
        return null;
    }

    private String parseGrant(final String jsonString) {
        if (jsonString == null) {
            return null;
        }

        try {
            JSONObject obj = new JSONObject(jsonString);
            int result = obj.getInt("result");
            if (result == DConnectMessage.RESULT_OK) {
                return obj.getString(AuthorizationProfile.PARAM_CLIENT_ID);
            } else {
                return null;
            }
        } catch (JSONException e) {
            return null;
        }
    }

    private String executeAccessToken(final String clientId) {
        if (clientId == null) {
            return null;
        }

        Map<String, String> params = new HashMap<String, String>() {
            {put(AuthorizationProfile.PARAM_CLIENT_ID, clientId);}
            {put(AuthorizationProfile.PARAM_APPLICATION_NAME, "Manager");}
            {put(AuthorizationProfile.PARAM_SCOPE, "serviceDiscovery");}
        };
        String uri = getUri(AuthorizationProfile.PATH_ACCESS_TOKEN, params);
        byte[] bytes = HttpUtil.get(uri);
        if (bytes != null) {
            return new String(bytes);
        }
        return null;
    }

    private String parseAccessToken(final String jsonString) {
        if (jsonString == null) {
            return null;
        }

        try {
            JSONObject obj = new JSONObject(jsonString);
            int result = obj.getInt("result");
            if (result == DConnectMessage.RESULT_OK) {
                return obj.getString(AuthorizationProfile.PARAM_ACCESS_TOKEN);
            } else {
                return null;
            }
        } catch (JSONException e) {
            return null;
        }
    }

    protected String getAccessToken() {
        if (mAccessToken == null) {
            mClientId = parseGrant(executeGrant());
            mAccessToken = parseAccessToken(executeAccessToken(mClientId));
            save();
        }
        return mAccessToken;
    }

    protected void clearAccessToken() {
        mAccessToken = null;
        mClientId = null;
        save();
    }

    private void load(Context context) {
        mSharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        mClientId = mSharedPreferences.getString(KEY_CLIENT_ID, null);
        mAccessToken = mSharedPreferences.getString(KEY_ACCESS_TOKEN, null);
    }

    private void save() {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(KEY_CLIENT_ID, mClientId);
        editor.putString(KEY_ACCESS_TOKEN, mAccessToken);
        editor.commit();
    }
}