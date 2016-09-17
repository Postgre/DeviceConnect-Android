package org.deviceconnect.android.deviceplugin.awsiot.local;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.awsiot.core.AWSIotController;
import org.deviceconnect.android.deviceplugin.awsiot.core.AWSIotPrefUtil;
import org.deviceconnect.android.deviceplugin.awsiot.core.RemoteDeviceConnectManager;
import org.deviceconnect.android.deviceplugin.awsiot.util.AWSIotUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AWSIotLocalManager {

    private static final boolean DEBUG = true;
    private static final String TAG = "AWS-Local";

    private RemoteDeviceConnectManager mRemoteManager;
    private AWSIotWebClientManager mAWSIotWebClientManager;
    private AWSIotWebServerManager mAWSIotWebServerManager;
    private AWSIotWebSocketClient mAWSIotWebSocketClient;
    private OnEventListener mOnEventListener;
    private Context mContext;
    private ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    private String mSessionKey = UUID.randomUUID().toString();

    private AWSIotController mIot;

    private AWSIotPrefUtil mPrefUtil;
    private long mSyncTime = 0;
    Handler mTimerHandler = new Handler();
    private String mSendData;
    private boolean mIsSendWait = false;
    private boolean mIsTimerEnable = false;

    public AWSIotLocalManager(final Context context, final AWSIotController controller, final RemoteDeviceConnectManager remote) {
        mContext = context;
        mIot = controller;
        mRemoteManager = remote;
        mPrefUtil = new AWSIotPrefUtil(mContext);
    }

    public AWSIotController getAWSIotController() {
        return mIot;
    }

    public void disconnect() {
        if (DEBUG) {
            Log.i(TAG, "AWSIotLocalManager#disconnect");
        }

        if (mAWSIotWebSocketClient != null) {
            mAWSIotWebSocketClient.close();
            mAWSIotWebSocketClient = null;
        }

        if (mAWSIotWebClientManager != null) {
            mAWSIotWebClientManager.destroy();
            mAWSIotWebClientManager = null;
        }

        if (mAWSIotWebServerManager != null) {
            mAWSIotWebServerManager.destroy();
            mAWSIotWebServerManager = null;
        }

        if (mIot != null) {
           unsubscribeTopic();
        }
    }

    public void setOnEventListener(final OnEventListener listener) {
        mOnEventListener = listener;
    }

    public void connectAWSIoT() {
        mAWSIotWebClientManager = new AWSIotWebClientManager(mContext, this);
        mAWSIotWebServerManager = new AWSIotWebServerManager(mContext, this);

        mAWSIotWebSocketClient = new AWSIotWebSocketClient("http://localhost:4035/websocket", mSessionKey) {
            @Override
            public void onMessage(final String message) {
                publishEvent(message);
            }
        };
        mAWSIotWebSocketClient.connect();

        subscribeTopic();
    }

    public void publish(final String message) {
        mIot.publish(mRemoteManager.getResponseTopic(), message);
    }

    public void publishEvent(final String message) {
        mSyncTime = (mPrefUtil.getSyncTime()) * 1000;
        if (mSyncTime <= 0) {
            mIot.publish(mRemoteManager.getEventTopic(), message);
        } else {
            mSendData = message;
            mIsSendWait = true;
            if (!mIsTimerEnable) {
                mIsTimerEnable = true;
                mIot.publish(mRemoteManager.getEventTopic(), mSendData);
                mIsSendWait = false;
                mTimerHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsSendWait) {
                            mIot.publish(mRemoteManager.getEventTopic(), mSendData);
                            mIsSendWait = false;
                            mTimerHandler.postDelayed(this, mSyncTime);
                        } else {
                            mIsTimerEnable = false;
                        }
                    }
                }, mSyncTime);
            }
        }
    }

    private void subscribeTopic() {
        mIot.subscribe(mRemoteManager.getRequestTopic(), mMessageCallback);
    }

    private void unsubscribeTopic() {
        mIot.unsubscribe(mRemoteManager.getRequestTopic());
    }

    private void parseMQTT(final String message) {
        try {
            JSONObject json = new JSONObject(message);
            int requestCode = json.optInt("requestCode");
            JSONObject request = json.optJSONObject(AWSIotUtil.KEY_REQUEST);
            if (request != null) {
                onReceivedDeviceConnectRequest(requestCode, request.toString());
            }
            JSONObject p2p = json.optJSONObject(AWSIotUtil.KEY_P2P_REMOTE);
            if (p2p != null) {
                mAWSIotWebClientManager.onReceivedSignaling(p2p.toString());
            }
            JSONObject p2pa = json.optJSONObject(AWSIotUtil.KEY_P2P_LOCAL);
            if (p2pa != null) {
                mAWSIotWebServerManager.onReceivedSignaling(p2pa.toString());
            }
        } catch (JSONException e) {
            if (DEBUG) {
                Log.w(TAG, "", e);
            }
        }
    }

    private void onReceivedDeviceConnectRequest(final int requestCode, final String request) {
        if (DEBUG) {
            Log.i(TAG, "onReceivedDeviceConnectRequest: request=" + request);
        }

        DConnectHelper.INSTANCE.sendRequest(request, new DConnectHelper.ConversionCallback() {
            @Override
            public String convertUri(final String uri) {
                Uri u = Uri.parse(uri);
                if (u.getHost().equals("localhost") || u.getHost().equals("127.0.0.1")) {
                    return mAWSIotWebServerManager.createWebServer(u.getAuthority(), u.getEncodedPath() + "?" + u.getEncodedQuery());
                } else {
                    return uri;
                }
            }
            @Override
            public String convertSessionKey(final String sessionKey) {
                return mSessionKey;
            }
        }, new DConnectHelper.FinishCallback() {
            @Override
            public void onFinish(final String response, final Exception error) {
                if (DEBUG) {
                    Log.d(TAG, "onReceivedDeviceConnectRequest: requestCode=" + requestCode + " response=" + response);
                }
                publish(AWSIotUtil.createResponse(requestCode, response));
            }
        });
    }

    private final AWSIotController.MessageCallback mMessageCallback = new AWSIotController.MessageCallback() {
        @Override
        public void onReceivedMessage(final String topic, final String message, final Exception err) {
            if (DEBUG) {
                Log.d(TAG, "AWSIoTLocalManager#onReceivedMessage");
                Log.d(TAG, "topic=" + topic);
                Log.d(TAG, "message=" + message);
            }

            if (err != null) {
                if (DEBUG) {
                    Log.w(TAG, "", err);
                }
                return;
            }

            if (mOnEventListener != null) {
                mOnEventListener.onReceivedMessage(topic, message);
            }

            if (!topic.equals(mRemoteManager.getRequestTopic())) {
                if (DEBUG) {
                    Log.e(TAG, "Not found the RemoteDeviceConnectManager. topic=" + topic);
                }
                return;
            }

            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    parseMQTT(message);
                }
            });
        }
    };

    public interface OnEventListener {
        void onConnected();
        void onReconnecting();
        void onDisconnected();
        void onReceivedMessage(String topic, String message);
    }
}
