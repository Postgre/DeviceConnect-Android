/*
 VideoChatActivity.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.webrtc.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.webrtc.BuildConfig;
import org.deviceconnect.android.deviceplugin.webrtc.R;
import org.deviceconnect.android.deviceplugin.webrtc.WebRTCApplication;
import org.deviceconnect.android.deviceplugin.webrtc.core.MySurfaceViewRenderer;
import org.deviceconnect.android.deviceplugin.webrtc.core.PeerConfig;
import org.deviceconnect.android.deviceplugin.webrtc.core.PeerUtil;
import org.deviceconnect.android.deviceplugin.webrtc.core.WebRTCController;
import org.deviceconnect.android.deviceplugin.webrtc.fragment.PercentFrameLayout;
import org.deviceconnect.android.event.Event;
import org.deviceconnect.android.event.EventManager;
import org.deviceconnect.android.profile.VideoChatProfile;
import org.webrtc.EglBase;
import org.webrtc.RendererCommon;

import java.util.List;

/**
 * VideoChatActivity.
 * @author NTT DOCOMO, INC.
 */
public class VideoChatActivity extends Activity {
    /**
     * Tag for debugging.
     */
    private static final String TAG = "WEBRTC";

    /**
     * Defined a extra config.
     * Constant Value: {@value}
     */
    public static final String EXTRA_CONFIG = "config";

    /**
     * Defined a extra address.
     * Constant Value: {@value}
     */
    public static final String EXTRA_ADDRESS_ID = "address_id";

    /**
     * Defined a extra video uri.
     * Constant Value: {@value}
     */
    public static final String EXTRA_VIDEO_URI = "video_uri";

    /**
     * Defined a extra audio uri.
     * Constant Value: {@value}
     */
    public static final String EXTRA_AUDIO_URI = "audio_uri";

    /**
     * Defined a extra offer.
     * Constant Value: {@value}
     */
    public static final String EXTRA_OFFER = "offer";

    private PercentFrameLayout mLocalLayout;
    private PercentFrameLayout mRemoteLayout;

    private MySurfaceViewRenderer mLocalRender;
    private MySurfaceViewRenderer mRemoteRender;

    private WebRTCController mWebRTCController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mLocalLayout = (PercentFrameLayout) findViewById(R.id.local_view_layout);
        mRemoteLayout = (PercentFrameLayout) findViewById(R.id.remote_video_layout);

        mLocalRender = (MySurfaceViewRenderer) findViewById(R.id.local_video_view);
        mLocalRender.setType(MySurfaceViewRenderer.TYPE_LOCAL);
        mRemoteRender = (MySurfaceViewRenderer) findViewById(R.id.remote_video_view);
        mRemoteRender.setType(MySurfaceViewRenderer.TYPE_REMOTE);

        EglBase eglBase = EglBase.create();
        mRemoteRender.init(eglBase.getEglBaseContext(), null);
        mLocalRender.init(eglBase.getEglBaseContext(), null);
        mLocalRender.setZOrderMediaOverlay(true);

        Intent intent = getIntent();
        if (intent != null) {
            PeerConfig config = intent.getParcelableExtra(EXTRA_CONFIG);
            String videoUri = intent.getStringExtra(EXTRA_VIDEO_URI);
            String audioUri = intent.getStringExtra(EXTRA_AUDIO_URI);
            String addressId = intent.getStringExtra(EXTRA_ADDRESS_ID);
            boolean offer = intent.getBooleanExtra(EXTRA_OFFER, false);

            WebRTCController.Builder builder = new WebRTCController.Builder();
            builder.setApplication((WebRTCApplication) getApplication());
            builder.setWebRTCEventListener(mListener);
            builder.setContext(this);
            builder.setEglBase(eglBase);
            builder.setConfig(config);
            builder.setRemoteRender(mRemoteRender);
            builder.setLocalRender(mLocalRender);
            builder.setVideoUri(videoUri);
            builder.setAudioUri(audioUri);
            builder.setAddressId(addressId);
            builder.setOffer(offer);
            mWebRTCController = builder.create();
            updateVideoView();
        } else {
            openWebRTCErrorDialog();
            }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mWebRTCController != null) {
            mWebRTCController.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mWebRTCController != null) {
            mWebRTCController.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        hangup();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_BACK) {
            return super.onKeyDown(keyCode, event);
        } else {
            finish();
            return false;
        }
    }

    /**
     * Updated layout of the views.
     */
    private void updateVideoView() {
        mRemoteLayout.setPosition(0, 0, 100, 90);
        mRemoteRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        mRemoteRender.setMirror(false);

        mLocalLayout.setPosition(72, 72, 25, 25);
        mLocalRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        mLocalRender.setMirror(true);

        mLocalRender.requestLayout();
        mRemoteRender.requestLayout();
    }

    /**
     * Hang up a call.
     */
    private void hangup() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "@@@ VideoChatActivity::hangup");
        }

        if (mWebRTCController != null) {
            mWebRTCController.hangup();
        }
    }

    /**
     * Notifies call event.
     */
    private void sendCallEvent() {
        List<Event> events = EventManager.INSTANCE.getEventList(
                PeerUtil.getServiceId(mWebRTCController.getPeer()),
                VideoChatProfile.PROFILE_NAME, null, VideoChatProfile.ATTR_ONCALL);
        if (events.size() != 0) {
            Bundle[] args = new Bundle[1];
            args[0] = new Bundle();
            args[0].putString(VideoChatProfile.PARAM_NAME, mWebRTCController.getAddressId());
            args[0].putString(VideoChatProfile.PARAM_ADDRESSID, mWebRTCController.getAddressId());
            for (Event e : events) {
                Intent event = EventManager.createEventMessage(e);
                event.putExtra(VideoChatProfile.PARAM_ONCALL, args);
                sendBroadcast(event);
            }
        }
    }

    /**
     * Notifies hang up event.
     */
    private void sendHangupEvent() {
        List<Event> events = EventManager.INSTANCE.getEventList(
                PeerUtil.getServiceId(mWebRTCController.getPeer()),
                VideoChatProfile.PROFILE_NAME, null, VideoChatProfile.ATTR_HANGUP);
        if (events.size() != 0) {
            Bundle arg = new Bundle();
            arg.putString(VideoChatProfile.PARAM_NAME, mWebRTCController.getAddressId());
            arg.putString(VideoChatProfile.PARAM_ADDRESSID, mWebRTCController.getAddressId());
            for (Event e : events) {
                Intent event = EventManager.createEventMessage(e);
                event.putExtra(VideoChatProfile.PARAM_HANGUP, arg);
                sendBroadcast(event);
            }
        }
    }

    /**
     * Open a error dialog of WebRTC.
     */
    private void openWebRTCErrorDialog() {
        openErrorDialog(R.string.error_failed_to_connect_p2p_msg);
    }

    /**
     * Open a error dialog.
     *
     * @param resId resource id
     */
    private void openErrorDialog(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(VideoChatActivity.this);
                builder.setTitle(R.string.error_failed_to_connect_p2p_title);
                builder.setMessage(resId);
                builder.setPositiveButton(R.string.error_failed_to_connect_p2p_btn,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });

                AlertDialog dialog = builder.create();
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        finish();
                    }
                });
                dialog.show();
            }
        });
    }

    private WebRTCController.WebRTCEventListener mListener = new WebRTCController.WebRTCEventListener() {
        @Override
        public void onFoundPeer(WebRTCController controller) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "WebRTCEventListener#onFoundPeer");
            }
        }

        @Override
        public void onNotFoundPeer(WebRTCController controller) {
            openWebRTCErrorDialog();
        }

        @Override
        public void onCallFailed(WebRTCController controller) {
            openWebRTCErrorDialog();
        }

        @Override
        public void onAnswerFailed(WebRTCController controller) {
            openWebRTCErrorDialog();
        }

        @Override
        public void onConnected(WebRTCController controller) {
            sendCallEvent();
        }

        @Override
        public void onDisconnected(WebRTCController controller) {
            sendHangupEvent();
        }

        @Override
        public void onError(WebRTCController controller) {
            openWebRTCErrorDialog();
        }
    };
}
