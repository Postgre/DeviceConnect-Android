/*
 HitoeDeviceOrientationProfile
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.hitoe.profile;

import android.content.Intent;

import org.deviceconnect.android.deviceplugin.hitoe.HitoeApplication;
import org.deviceconnect.android.deviceplugin.hitoe.HitoeDeviceService;
import org.deviceconnect.android.deviceplugin.hitoe.data.HitoeConstants;
import org.deviceconnect.android.deviceplugin.hitoe.data.HitoeManager;
import org.deviceconnect.android.deviceplugin.hitoe.data.AccelerationData;
import org.deviceconnect.android.deviceplugin.hitoe.data.HitoeDevice;
import org.deviceconnect.android.event.Event;
import org.deviceconnect.android.event.EventDispatcher;
import org.deviceconnect.android.event.EventDispatcherFactory;
import org.deviceconnect.android.event.EventDispatcherManager;
import org.deviceconnect.android.event.EventError;
import org.deviceconnect.android.event.EventManager;
import org.deviceconnect.android.message.DConnectMessageService;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.DeviceOrientationProfile;
import org.deviceconnect.message.DConnectMessage;

import java.util.List;

/**
 * Implement DeviceOrientationProfile.
 * @author NTT DOCOMO, INC.
 */
public class HitoeDeviceOrientationProfile extends DeviceOrientationProfile {

    /**
     * Implementation of {@link HitoeManager.OnHitoeDeviceOrientationEventListener}.
     */
    private final HitoeManager.OnHitoeDeviceOrientationEventListener mDeviceOrientationEventListener =
            new HitoeManager.OnHitoeDeviceOrientationEventListener() {
                @Override
                public void onReceivedData(final HitoeDevice device, final AccelerationData data) {
                    notifyAccelerationData(device, data);
                }
            };

    /**
     * Event Dispatcher object.
     */
    private EventDispatcherManager mDispatcherManager;

    /**
     * Constructor.
     * @param mgr instance of {@link HitoeManager}
     */
    public HitoeDeviceOrientationProfile(final HitoeManager mgr) {
        mgr.setHitoeDeviceOrientationEventListener(mDeviceOrientationEventListener);
        mDispatcherManager = new EventDispatcherManager();
    }
    @Override
    public boolean onGetOnDeviceOrientation(final Intent request, final Intent response, final String serviceId) {
        if (serviceId == null) {
            MessageUtils.setEmptyServiceIdError(response);
        } else {
            AccelerationData data = getManager().getAccelerationData(serviceId);
            if (data == null) {
                MessageUtils.setNotFoundServiceError(response);
            } else {
                setResult(response, DConnectMessage.RESULT_OK);
                DeviceOrientationProfile.setOrientation(response, data.toBundle());

            }
        }
        return true;
    }

    @Override
    public boolean onPutOnDeviceOrientation(final Intent request, final Intent response,
                                  final String serviceId, final String sessionKey) {
        if (serviceId == null) {
            MessageUtils.setNotFoundServiceError(response, "Not found serviceID:" + serviceId);
        } else if (sessionKey == null) {
            MessageUtils.setInvalidRequestParameterError(response, "Not found sessionKey:" + sessionKey);
        } else {
            AccelerationData data = getManager().getAccelerationData(serviceId);
            if (data == null) {
                MessageUtils.setNotFoundServiceError(response);
            } else {
                EventError error = EventManager.INSTANCE.addEvent(request);
                if (error == EventError.NONE) {
                    String intervalString = request.getStringExtra("interval");
                    long interval = HitoeConstants.ADD_RECEIVER_PARAM_ACC_SAMPLING_INTERVAL;
                    try {
                        interval = Long.parseLong(intervalString);
                    } catch (NumberFormatException e) {
                    }
                    getManager().getAccelerationData(serviceId).setTimeStamp(interval);
                    addEventDispatcher(request);
                    setResult(response, DConnectMessage.RESULT_OK);
                } else {
                    MessageUtils.setUnknownError(response);
                }
            }
        }
        return true;
    }

    @Override
    public boolean onDeleteOnDeviceOrientation(final Intent request, final Intent response,
                                     final String serviceId, final String sessionKey) {
        if (serviceId == null) {
            MessageUtils.setEmptyServiceIdError(response);
        } else if (sessionKey == null) {
            MessageUtils.setInvalidRequestParameterError(response, "There is no sessionKey.");
        } else {
            removeEventDispatcher(request);
            EventError error = EventManager.INSTANCE.removeEvent(request);
            if (error == EventError.NONE) {
                setResult(response, DConnectMessage.RESULT_OK);
            } else if (error == EventError.INVALID_PARAMETER) {
                MessageUtils.setInvalidRequestParameterError(response);
            } else if (error == EventError.FAILED) {
                MessageUtils.setUnknownError(response, "Failed to delete event.");
            } else if (error == EventError.NOT_FOUND) {
                MessageUtils.setUnknownError(response, "Not found event.");
            } else {
                MessageUtils.setUnknownError(response);
            }
        }
        return true;
    }

    /**
     * Notify the device orientation event to DeviceConnectManager.
     * @param device Identifies the remote device
     * @param data Data of device orientation
     */
    private void notifyAccelerationData(final HitoeDevice device, final AccelerationData data) {
        List<Event> events = EventManager.INSTANCE.getEventList(device.getId(),
                getProfileName(), null, ATTRIBUTE_ON_DEVICE_ORIENTATION);
        synchronized (events) {
            for (Event event : events) {
                if (data == null) {
                    break;
                }

                Intent intent = EventManager.createEventMessage(event);

                DeviceOrientationProfile.setOrientation(intent, data.toBundle());
                mDispatcherManager.sendEvent(event, intent);
            }
        }
    }

    /**
     * Add Event Dispatcher.
     * @param request request parameter
     */
    private void addEventDispatcher(final Intent request) {
        Event event = EventManager.INSTANCE.getEvent(request);
        EventDispatcher dispatcher = EventDispatcherFactory.createEventDispatcher(
                (DConnectMessageService)getContext(), request);
        mDispatcherManager.addEventDispatcher(event, dispatcher);
    }

    /**
     * Remove Event Dispatcher.
     * @param request request parameter
     */
    private void removeEventDispatcher(final Intent request) {
        Event event = EventManager.INSTANCE.getEvent(request);
        mDispatcherManager.removeEventDispatcher(event);
    }

    /**
     * Gets a instance of HitoeManager.
     *
     * @return {@link HitoeManager}, or null on error
     */
    private HitoeManager getManager() {
        HitoeDeviceService service = (HitoeDeviceService) getContext();
        if (service == null) {
            return null;
        }
        HitoeApplication app = (HitoeApplication) service.getApplication();
        if (app == null) {
            return null;
        }
        return app.getHitoeManager();
    }
}
