/*
 * Copyright (C) 2013 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.sample.castcompanionlibrary.notification;

import static com.google.sample.castcompanionlibrary.utils.LogUtils.LOGD;
import static com.google.sample.castcompanionlibrary.utils.LogUtils.LOGE;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.widget.RemoteViews;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.sample.castcompanionlibrary.R;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;
import com.google.sample.castcompanionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.google.sample.castcompanionlibrary.cast.exceptions.NoConnectionException;
import com.google.sample.castcompanionlibrary.cast.exceptions.TransientNetworkDisconnectionException;
import com.google.sample.castcompanionlibrary.cast.imageloader.ImageLoader;
import com.google.sample.castcompanionlibrary.cast.player.VideoCastControllerActivity;
import com.google.sample.castcompanionlibrary.utils.LogUtils;
import com.google.sample.castcompanionlibrary.utils.Utils;

/**
 * A service to provide status bar Notifications when we are casting. For JB+ versions, notification
 * area provides a play/pause toggle and an "x" button to disconnect but that for GB, we do not
 * show that due to the framework limitations.
 */
public class VideoCastNotificationService extends Service {

    public static final String ACTION_TOGGLE_PLAYBACK =
            "com.google.sample.castcompanionlibrary.action.toggleplayback";
    public static final String ACTION_STOP =
            "com.google.sample.castcompanionlibrary.action.stop";
    public static final String ACTION_VISIBILITY =
            "com.google.sample.castcompanionlibrary.action.notificationvisibility";
    private static int NOTIFICATION_ID = 1;

    private static final String TAG = LogUtils.makeLogTag(VideoCastNotificationService.class);
    private ImageLoader.Request mVideoArtRequest;
    private boolean mIsPlaying;
    private Class<?> mTargetActivity;
    private int mStatus;
    private Notification mNotification;
    private boolean mVisible;
    private VideoCastManager mCastManager;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            LOGD(TAG, "onReceive(): " + intent.getAction());
        }
    };
    private final VideoCastConsumerImpl mConsumer = new VideoCastConsumerImpl() {
        @Override
        public void onApplicationDisconnected(int errorCode) {
            LOGD(TAG, "onApplicationDisconnected() was reached");
            stopSelf();
        }

        @Override
        public void onRemoteMediaPlayerStatusUpdated() {
            int mediaStatus = mCastManager.getPlaybackStatus();
            VideoCastNotificationService.this.onRemoteMediaPlayerStatusUpdated(mediaStatus);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        LOGD(TAG, "onCreate()");
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mBroadcastReceiver, filter);

        mCastManager = VideoCastManager.getInstance();
        mTargetActivity = mCastManager.getTargetActivity();
        if (!mCastManager.isConnected()) {
            mCastManager.reconnectSessionIfPossible(this, false);
        }

        mCastManager.addVideoCastConsumer(mConsumer);
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOGD(TAG, "onStartCommand");
        if ((intent != null) && ACTION_VISIBILITY.equals(intent.getAction())) {
            mVisible = intent.getBooleanExtra("visible", false);
            LOGD(TAG, "onStartCommand(): Action: ACTION_VISIBILITY " + mVisible);
            if (mVisible && null != mNotification) {
                startForeground(NOTIFICATION_ID, mNotification);
            } else {
                stopForeground(true);
            }
        }
        return Service.START_REDELIVER_INTENT;
    }

    private void setupNotification(final MediaInfo info) {
        if (null == info) {
            return;
        }
        mCastManager.cancelImageRequest(mVideoArtRequest);
        mVideoArtRequest = mCastManager.loadImage(Utils.getImageUrl(info, 0), new ImageLoader.Callbacks() {
    		@Override
    		public void onResponse(Bitmap bitmap) {
    			try {
    				build(info, bitmap, mIsPlaying, mTargetActivity);
                    if (mVisible) {
                        startForeground(NOTIFICATION_ID, mNotification);
                    }
                } catch (Exception e) {
                    LOGE(TAG, "Failed to set notification for " + info.toString(), e);
                }
    		}
    	}, null);
    }

    /**
     * Removes the existing notification.
     */
    private void removeNotification() {
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).
                cancel(NOTIFICATION_ID);
    }

    private void onRemoteMediaPlayerStatusUpdated(int mediaStatus) {
        mStatus = mediaStatus;
        mIsPlaying = mediaStatus == MediaStatus.PLAYER_STATE_PLAYING;
        LOGD(TAG, "onRemoteMediaPlayerMetadataUpdated() reached with status: " + mStatus);
        try {
            switch (mediaStatus) {
                case MediaStatus.PLAYER_STATE_BUFFERING: // (== 4)
                case MediaStatus.PLAYER_STATE_PLAYING: // (== 2)
                case MediaStatus.PLAYER_STATE_PAUSED: // (== 3)
                    setupNotification(mCastManager.getRemoteMediaInformation());
                    break;
                case MediaStatus.PLAYER_STATE_IDLE: // (== 1)
                    if (mCastManager.shouldRemoteUiBeVisible(mediaStatus,
                            mCastManager.getIdleReason())) {
                    	setupNotification(mCastManager.getRemoteMediaInformation());
                    	break;
                    }
                case MediaStatus.PLAYER_STATE_UNKNOWN: // (== 0)
                    stopForeground(true);
                    break;
            }
        } catch (TransientNetworkDisconnectionException e) {
            LOGE(TAG, "Failed to update the playback status due to network issues", e);
        } catch (NoConnectionException e) {
            LOGE(TAG, "Failed to update the playback status due to network issues", e);
        }
    }

    /*
     * (non-Javadoc)
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        mCastManager.cancelImageRequest(mVideoArtRequest);
        mVideoArtRequest = null;
        LOGD(TAG, "onDestroy was called");
        removeNotification();
        unregisterReceiver(mBroadcastReceiver);
        mCastManager.removeVideoCastConsumer(mConsumer);
    }

    /*
     * Build the notification. We also need to add the appropriate "back stack"
     * so when user goes into the CastPlayerActivity, she can have a meaningful "back" experience.
     */
    private void build(MediaInfo info, Bitmap bitmap, boolean isPlaying,
            Class<?> targetActivity) {
        Bundle mediaWrapper = Utils.fromMediaInfo(info);
        if (null == mTargetActivity) {
            mTargetActivity = VideoCastControllerActivity.class;
        }
        Intent contentIntent = new Intent(this, mTargetActivity);
        contentIntent.putExtra("media", mediaWrapper);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this)
        		.addParentStack(mTargetActivity)
        		.addNextIntent(contentIntent);
        if (stackBuilder.getIntentCount() > 1) {
            stackBuilder.editIntentAt(1).putExtra("media", mediaWrapper);
        }

        // Gets a PendingIntent containing the entire back stack
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(NOTIFICATION_ID, PendingIntent.FLAG_UPDATE_CURRENT);

        MediaMetadata mm = info.getMetadata();

        RemoteViews rv = new RemoteViews(getPackageName(), R.layout.custom_notification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            addPendingIntents(rv, isPlaying, info);
        }
        if (null != bitmap) {
            rv.setImageViewBitmap(R.id.iconView, bitmap);
        }
        rv.setTextViewText(R.id.titleView, mm.getString(MediaMetadata.KEY_TITLE));
        String castingTo = getResources().getString(R.string.casting_to_device,
                mCastManager.getDeviceName());
        rv.setTextViewText(R.id.subTitleView, castingTo);
        mNotification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_notification_cast)
                .setContentIntent(resultPendingIntent)
                .setContent(rv)
                .setAutoCancel(false)
                .setOngoing(true)
                .build();

        // to get around a bug in GB version, we add the following line
        // see https://code.google.com/p/android/issues/detail?id=30495
        mNotification.contentView = rv;
    }

    private void addPendingIntents(RemoteViews rv, boolean isPlaying, MediaInfo info) {
        Intent playbackIntent = new Intent(ACTION_TOGGLE_PLAYBACK).setPackage(getPackageName());
        PendingIntent playbackPendingIntent = PendingIntent.getBroadcast(this, 0, playbackIntent, 0);

        Intent stopIntent = new Intent(ACTION_STOP).setPackage(getPackageName());
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, 0, stopIntent, 0);

        rv.setOnClickPendingIntent(R.id.playPauseView, playbackPendingIntent);
        rv.setOnClickPendingIntent(R.id.removeView, stopPendingIntent);

        int playPauseImageResId;
        if (isPlaying) {
        	playPauseImageResId = (info.getStreamType() == MediaInfo.STREAM_TYPE_LIVE)
        			? R.drawable.ic_av_stop_sm_dark : R.drawable.ic_av_pause_sm_dark;
        } else {
        	playPauseImageResId = R.drawable.ic_av_play_sm_dark;
        }
        rv.setImageViewResource(R.id.playPauseView, playPauseImageResId);
    }
}
