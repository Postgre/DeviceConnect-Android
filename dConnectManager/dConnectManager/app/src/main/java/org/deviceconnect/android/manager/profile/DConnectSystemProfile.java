/*
 DConnectSystemProfile.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.manager.profile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.deviceconnect.android.event.EventManager;
import org.deviceconnect.android.manager.DConnectMessageService;
import org.deviceconnect.android.manager.DConnectService;
import org.deviceconnect.android.manager.DevicePlugin;
import org.deviceconnect.android.manager.DevicePluginManager;
import org.deviceconnect.android.manager.R;
import org.deviceconnect.android.manager.request.DConnectRequest;
import org.deviceconnect.android.manager.request.RemoveEventsRequest;
import org.deviceconnect.android.manager.setting.KeywordDialogActivity;
import org.deviceconnect.android.manager.setting.SettingActivity;
import org.deviceconnect.android.manager.util.DConnectUtil;
import org.deviceconnect.android.profile.DConnectProfile;
import org.deviceconnect.android.profile.DConnectProfileProvider;
import org.deviceconnect.android.profile.SystemProfile;
import org.deviceconnect.android.profile.api.DConnectApi;
import org.deviceconnect.android.profile.api.DeleteApi;
import org.deviceconnect.android.profile.api.GetApi;
import org.deviceconnect.android.profile.api.PutApi;
import org.deviceconnect.message.DConnectMessage;
import org.deviceconnect.message.intent.message.IntentDConnectMessage;
import org.deviceconnect.profile.SystemProfileConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * System プロファイル.
 * 
 * @author NTT DOCOMO, INC.
 */
public class DConnectSystemProfile extends SystemProfile {
    /** プロファイル管理クラス. */
    private final DConnectProfileProvider mProvider;

    /** プラグイン管理クラス. */
    private final DevicePluginManager mPluginMgr;

    /**
     * コンストラクタ.
     * 
     * @param provider プロファイル管理クラス
     * @param pluginMgr プラグイン管理クラス
     */
    public DConnectSystemProfile(final DConnectProfileProvider provider, final DevicePluginManager pluginMgr) {
        mProvider = provider;
        mPluginMgr = pluginMgr;

        addApi(mGetRequest);
        addApi(mPutKeywordRequest);
        addApi(mDeleteEvents);
    }

    @Override
    protected Class<? extends Activity> getSettingPageActivity(final Intent request, final Bundle param) {
        return SettingActivity.class;
    }

    private final DConnectApi mGetRequest = new GetApi() {
        @Override
        public boolean onRequest(final Intent request, final Intent response) {
            setResult(response, DConnectMessage.RESULT_OK);
            setVersion(response, DConnectUtil.getVersionName(getContext()));
            SharedPreferences sp = getContext().getSharedPreferences(getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE);
            setName(response, sp.getString(getContext().getString(R.string.key_settings_dconn_name), null));
            setUuid(response, sp.getString(getContext().getString(R.string.key_settings_dconn_uuid), null));
            // サポートしているプロファイル一覧設定
            List<String> supports = new ArrayList<String>();
            List<DConnectProfile> profiles = mProvider.getProfileList();
            for (int i = 0; i < profiles.size(); i++) {
                supports.add(profiles.get(i).getProfileName());
            }
            setSupports(response, supports.toArray(new String[supports.size()]));

            // プラグインの一覧を設定
            List<Bundle> plugins = new ArrayList<Bundle>();
            List<DevicePlugin> p = mPluginMgr.getDevicePlugins();
            for (int i = 0; i < p.size(); i++) {
                DevicePlugin plugin = p.get(i);
                String serviceId = mPluginMgr.appendServiceId(plugin, null);
                Bundle b = new Bundle();
                b.putString(PARAM_ID, serviceId);
                b.putString(PARAM_NAME, plugin.getDeviceName());
                b.putString(PARAM_PACKAGE_NAME, plugin.getPackageName());
                b.putString(PARAM_VERSION, plugin.getVersionName());
                setSupports(b, plugin.getSupportProfiles());
                plugins.add(b);
            }
            response.putExtra(PARAM_PLUGINS, plugins.toArray(new Bundle[plugins.size()]));
            return true;
        }
    };

    private final DConnectApi mPutKeywordRequest = new PutApi() {
        @Override
        public String getAttribute() {
            return SystemProfileConstants.ATTRIBUTE_KEYWORD;
        }

        @Override
        public boolean onRequest(final Intent request, final Intent response) {
            DConnectRequest req = new DConnectRequest() {
                /** ロックオブジェクト. */
                protected final Object mLockObj = new Object();
                /** リクエストコード. */
                protected int mRequestCode;

                @Override
                public void setResponse(final Intent response) {
                    super.setResponse(response);
                    synchronized (mLockObj) {
                        mLockObj.notifyAll();
                    }
                }

                @Override
                public boolean hasRequestCode(final int requestCode) {
                    return mRequestCode == requestCode;
                }

                @Override
                public void run() {
                    // リクエストコードを作成する
                    mRequestCode = UUID.randomUUID().hashCode();

                    // キーワード表示用のダイアログを表示
                    Intent intent = new Intent(getContext(), KeywordDialogActivity.class);
                    intent.putExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, mRequestCode);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                            | Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);

                    // ダイアログからの返答を待つ
                    if (mResponse == null) {
                        waitForResponse();
                    }

                    // レスポンスを返却
                    setResult(response, DConnectMessage.RESULT_OK);
                    ((DConnectService) getContext()).sendResponse(request, response);
                }

                /**
                 * 各デバイスからのレスポンスを待つ.
                 *
                 * この関数から返答があるのは以下の条件になる。
                 * <ul>
                 * <li>デバイスプラグインからレスポンスがあった場合
                 * <li>指定された時間無いにレスポンスが返ってこない場合
                 * </ul>
                 */
                protected void waitForResponse() {
                    synchronized (mLockObj) {
                        try {
                            mLockObj.wait(mTimeout);
                        } catch (InterruptedException e) {
                            // do-nothing
                        }
                    }
                }

            };
            req.setContext(getContext());
            req.setRequest(request);
            ((DConnectMessageService) getContext()).addRequest(req);
            return false;
        }
    };

    private final DConnectApi mDeleteEvents = new DeleteApi() {
        @Override
        public String getAttribute() {
            return SystemProfileConstants.ATTRIBUTE_EVENTS;
        }

        @Override
        public boolean onRequest(final Intent request, final Intent response) {
            // dConnectManagerに登録されているイベントを削除
            String origin = request.getStringExtra(IntentDConnectMessage.EXTRA_ORIGIN);
            EventManager.INSTANCE.removeEvents(origin);
            // 各デバイスプラグインにイベントを削除依頼を送る
            RemoveEventsRequest req = new RemoveEventsRequest();
            req.setContext(getContext());
            req.setRequest(request);
            req.setDevicePluginManager(mPluginMgr);
            ((DConnectMessageService) getContext()).addRequest(req);
            return false;
        }
    };

    public static boolean isWakeUpRequest(final Intent request) {
        String profile = getProfile(request);
        String inter = getInterface(request);
        String attribute = getAttribute(request);
        return PROFILE_NAME.equals(profile) && INTERFACE_DEVICE.equals(inter) && ATTRIBUTE_WAKEUP.equals(attribute);
    }



}
