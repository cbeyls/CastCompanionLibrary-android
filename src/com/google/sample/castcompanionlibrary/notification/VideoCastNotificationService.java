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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.view.View;
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

import static com.google.sample.castcompanionlibrary.utils.LogUtils.LOGD;
import static com.google.sample.castcompanionlibrary.utils.LogUtils.LOGE;

/**
 * A service to provide status bar Notifications when we are casting. For JB+ versions, notification
 * area provides a play/pause toggle and an "x" button to disconnect but that for GB, we do not
 * show that due to the framework limitations.
 */
public class VideoCastNotificationService extends Service implements VideoCastManager.UiVisibilityListener {

	public static final String ACTION_TOGGLE_PLAYBACK =
			"com.google.sample.castcompanionlibrary.action.toggleplayback";
	public static final String ACTION_STOP =
			"com.google.sample.castcompanionlibrary.action.stop";
	static int NOTIFICATION_ID = 1;
	static final String TAG = LogUtils.makeLogTag(VideoCastNotificationService.class);

	VideoCastManager mCastManager;
	private Class<? extends Activity> mTargetActivity;
	Notification mNotification;
	boolean mVisible;
	private ImageLoader.Request mVideoArtRequest;

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

		mCastManager = VideoCastManager.getInstance();
		mTargetActivity = mCastManager.getTargetActivity();
		if (null == mTargetActivity) {
			mTargetActivity = VideoCastControllerActivity.class;
		}
		if (!mCastManager.isConnected()) {
			mCastManager.reconnectSessionIfPossible(this, false);
		}

		mCastManager.addUiVisibilityListener(this);
		mVisible = !mCastManager.isUiVisible();

		mCastManager.addVideoCastConsumer(mConsumer);
		// Force build the notification if we are already playing
		mConsumer.onRemoteMediaPlayerStatusUpdated();
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return Service.START_REDELIVER_INTENT;
	}

	@Override
	public void onUiVisibilityChanged(boolean visible) {
		// Notification is visible when the UI is not
		mVisible = !visible;
		if (mVisible && null != mNotification) {
			startForeground(NOTIFICATION_ID, mNotification);
		} else {
			stopForeground(true);
		}
	}

	private void setupNotification(final MediaInfo info, final int mediaStatus) {
		if (null == info) {
			return;
		}
		mCastManager.cancelImageRequest(mVideoArtRequest);
		mVideoArtRequest = mCastManager.loadImage(Utils.getImageUrl(info, 0), new ImageLoader.Callbacks() {
			@Override
			public void onResponse(Bitmap bitmap) {
				try {
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
						mNotification = buildLegacyNotification(info, mediaStatus, bitmap);
					} else {
						mNotification = buildNotification(info, mediaStatus, bitmap);
					}
					if (mVisible) {
						startForeground(NOTIFICATION_ID, mNotification);
					}
				} catch (Exception e) {
					LOGE(TAG, "Failed to set notification for " + info.toString(), e);
				}
			}
		}, null);
	}

	void onRemoteMediaPlayerStatusUpdated(int mediaStatus) {
		LOGD(TAG, "onRemoteMediaPlayerMetadataUpdated() reached with status: " + mediaStatus);
		try {
			if (mCastManager.shouldRemoteUiBeVisible(mediaStatus, mCastManager.getIdleReason())) {
				setupNotification(mCastManager.getRemoteMediaInformation(), mediaStatus);
			} else {
				mCastManager.cancelImageRequest(mVideoArtRequest);
				mNotification = null;
				stopForeground(true);
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
		mCastManager.removeVideoCastConsumer(mConsumer);
		mCastManager.removeUiVisibilityListener(this);
	}

	/*
	 * Build the notification for Android < LOLLIPOP. We also need to add the appropriate "back stack"
	 * so when user goes into the CastPlayerActivity, she can have a meaningful "back" experience.
	 */
	Notification buildLegacyNotification(MediaInfo info, int mediaStatus, Bitmap bitmap) {
		RemoteViews rv = new RemoteViews(getPackageName(), R.layout.custom_notification);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			setPlaybackControls(rv, info, mediaStatus);
		}
		if (null != bitmap) {
			rv.setImageViewBitmap(R.id.iconView, bitmap);
		}
		rv.setTextViewText(R.id.titleView, info.getMetadata().getString(MediaMetadata.KEY_TITLE));
		String castingTo = getString(R.string.casting_to_device, mCastManager.getDeviceName());
		rv.setTextViewText(R.id.subTitleView, castingTo);
		Notification notification = new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_notification_cast)
				.setContentIntent(getContentPendingIntent(info))
				.setContent(rv)
				.setAutoCancel(false)
				.setOngoing(true)
				.build();

		// to get around a bug in GB version, we add the following line
		// see https://code.google.com/p/android/issues/detail?id=30495
		notification.contentView = rv;
		return notification;
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void setPlaybackControls(RemoteViews rv, MediaInfo info, int mediaStatus) {
		rv.setOnClickPendingIntent(R.id.playPauseView, getPlaybackPendingIntent());
		rv.setOnClickPendingIntent(R.id.removeView, getDisconnectPendingIntent());

		if (mediaStatus == MediaStatus.PLAYER_STATE_BUFFERING) {
			rv.setViewVisibility(R.id.loadingView, View.VISIBLE);
			rv.setViewVisibility(R.id.playPauseView, View.GONE);
		} else {
			rv.setViewVisibility(R.id.loadingView, View.GONE);
			rv.setViewVisibility(R.id.playPauseView, View.VISIBLE);
			rv.setImageViewResource(R.id.playPauseView, getPlayPauseIconResId(info, mediaStatus));
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	Notification buildNotification(MediaInfo info, int mediaStatus, Bitmap bitmap) {
		String castingTo = getString(R.string.casting_to_device, mCastManager.getDeviceName());
		return new Notification.Builder(this)
				.setSmallIcon(R.drawable.ic_notification_cast)
				.setContentTitle(info.getMetadata().getString(MediaMetadata.KEY_TITLE))
				.setContentText(castingTo)
				.setContentIntent(getContentPendingIntent(info))
				.setLargeIcon(bitmap)
				.addAction(getPlayPauseIconResId(info, mediaStatus),
						getString(getPlayPauseStringResId(info, mediaStatus)),
						getPlaybackPendingIntent())
				.addAction(R.drawable.ic_clear_white_36dp,
						getString(R.string.disconnect),
						getDisconnectPendingIntent())
				.setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1))
				.setOngoing(true)
				.setShowWhen(false)
				.setVisibility(Notification.VISIBILITY_PUBLIC)
				.build();
	}

	private int getPlayPauseIconResId(MediaInfo info, int mediaStatus) {
		if (mediaStatus == MediaStatus.PLAYER_STATE_PLAYING) {
			return (info.getStreamType() == MediaInfo.STREAM_TYPE_LIVE)
					? R.drawable.ic_stop_white_36dp
					: R.drawable.ic_pause_white_36dp;
		}
		return R.drawable.ic_play_arrow_white_36dp;
	}

	private int getPlayPauseStringResId(MediaInfo info, int mediaStatus) {
		if (mediaStatus == MediaStatus.PLAYER_STATE_PLAYING) {
			return (info.getStreamType() == MediaInfo.STREAM_TYPE_LIVE)
					? R.string.stop
					: R.string.pause;
		}
		return R.string.play;
	}

	private PendingIntent getContentPendingIntent(MediaInfo info) {
		Bundle mediaWrapper = Utils.fromMediaInfo(info);
		Intent contentIntent = new Intent(this, mTargetActivity);
		contentIntent.putExtra(VideoCastManager.EXTRA_MEDIA, mediaWrapper);

		// Gets a PendingIntent containing the entire back stack
		return TaskStackBuilder.create(this)
				.addParentStack(mTargetActivity)
				.addNextIntent(contentIntent)
				.getPendingIntent(NOTIFICATION_ID, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	private PendingIntent getPlaybackPendingIntent() {
		Intent playbackIntent = new Intent(ACTION_TOGGLE_PLAYBACK).setPackage(getPackageName());
		return PendingIntent.getBroadcast(this, 0, playbackIntent, 0);
	}

	private PendingIntent getDisconnectPendingIntent() {
		Intent stopIntent = new Intent(ACTION_STOP).setPackage(getPackageName());
		return PendingIntent.getBroadcast(this, 0, stopIntent, 0);
	}
}
