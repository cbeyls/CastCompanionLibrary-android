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

package com.google.sample.castcompanionlibrary.cast.dialog.video;

import static com.google.sample.castcompanionlibrary.utils.LogUtils.LOGE;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.MediaRouteControllerDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.sample.castcompanionlibrary.R;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;
import com.google.sample.castcompanionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.google.sample.castcompanionlibrary.cast.exceptions.NoConnectionException;
import com.google.sample.castcompanionlibrary.cast.exceptions.TransientNetworkDisconnectionException;
import com.google.sample.castcompanionlibrary.cast.imageloader.ImageLoader;
import com.google.sample.castcompanionlibrary.utils.LogUtils;

/**
 * A custom {@link MediaRouteControllerDialog} that provides an album art, a play/pause button and the ability to take
 * user to the target activity when the album art is tapped.
 */
public class VideoMediaRouteControllerDialog extends MediaRouteControllerDialog {

	private static final String TAG = LogUtils.makeLogTag(VideoMediaRouteControllerDialog.class);

	private ImageView mIcon;
	private ImageView mPausePlay;
	private View mTextContainer;
	private TextView mTitle;
	private TextView mSubTitle;
	private TextView mEmptyText;
	private View mLoading;
	private ImageLoader.Request mIconRequest;
	private final VideoCastManager mCastManager;
	private final VideoCastConsumerImpl castConsumerImpl;
	private final Drawable mPauseDrawable;
	private final Drawable mPlayDrawable;
	private final Drawable mStopDrawable;

	private int mStreamType;

	/**
	 * Creates a new VideoMediaRouteControllerDialog in the given context.
	 */
	public VideoMediaRouteControllerDialog(Context context) {
		super(context, R.style.CastDialog);
		mCastManager = VideoCastManager.getInstance();
		castConsumerImpl = new VideoCastConsumerImpl() {

			@Override
			public void onRemoteMediaPlayerStatusUpdated() {
				updatePlayPauseState();
			}

			/*
			 * (non-Javadoc)
			 * 
			 * @see com.google.sample.castcompanionlibrary.cast.VideoCastConsumerImpl #onMediaChannelMetadataUpdated()
			 */
			@Override
			public void onRemoteMediaPlayerMetadataUpdated() {
				updateMetadata();
			}

		};
		mCastManager.addVideoCastConsumer(castConsumerImpl);
		Resources res = context.getResources();
		mPauseDrawable = res.getDrawable(R.drawable.ic_av_pause_sm_dark);
		mPlayDrawable = res.getDrawable(R.drawable.ic_av_play_sm_dark);
		mStopDrawable = res.getDrawable(R.drawable.ic_av_stop_sm_dark);
	}

	/*
	 * Hides/show the icon and metadata and play/pause if there is no media
	 */
	private void hideControls(boolean hide, int resId) {
		int visibility = hide ? View.GONE : View.VISIBLE;
		mIcon.setVisibility(visibility);
		mTextContainer.setVisibility(visibility);
		mEmptyText.setText(resId == 0 ? R.string.no_media_info : resId);
		mEmptyText.setVisibility(hide ? View.VISIBLE : View.GONE);
		if (hide) {
			setLoadingVisibility(false);
			setPausePlayVisibility(false);
		}
	}

	private void updateMetadata() {
		try {
			MediaInfo info = mCastManager.getRemoteMediaInformation();
			mStreamType = info.getStreamType();
			hideControls(false, 0);
			MediaMetadata mm = info.getMetadata();
			mTitle.setText(mm.getString(MediaMetadata.KEY_TITLE));
			mSubTitle.setText(mm.getString(MediaMetadata.KEY_SUBTITLE));
			setIcon(mm.hasImages() ? mm.getImages().get(0).getUrl().toString() : null);
		} catch (TransientNetworkDisconnectionException e) {
			hideControls(true, R.string.failed_no_connection_short);
		} catch (Exception e) {
			LOGE(TAG, "Failed to get media information", e);
			hideControls(true, R.string.no_media_info);
		}
	}

	private final ImageLoader.Callbacks mIconImageLoaderCallbacks = new ImageLoader.Callbacks() {

		@Override
		public void onResponse(Bitmap bm) {
			if (bm == null) {
				mIcon.setImageResource(R.drawable.video_placeholder_200x200);
			} else {
				mIcon.setImageBitmap(bm);
			}
		}
	};

	public void setIcon(String url) {
		mIconRequest = mCastManager.loadImage(url, mIconImageLoaderCallbacks, mIconRequest);
	}

	private void updatePlayPauseState() {
		if (null != mPausePlay) {
			int state = mCastManager.getPlaybackStatus();
			switch (state) {
			case MediaStatus.PLAYER_STATE_PLAYING:
				setLoadingVisibility(false);
				setPausePlayVisibility(true, getPauseStopButton());
				break;
			case MediaStatus.PLAYER_STATE_PAUSED:
				setLoadingVisibility(false);
				setPausePlayVisibility(true, mPlayDrawable);
				break;
			case MediaStatus.PLAYER_STATE_IDLE:
				int idleReason = mCastManager.getIdleReason();
				if (idleReason == MediaStatus.IDLE_REASON_FINISHED) {
					hideControls(true, R.string.no_media_info);
				} else {
					setLoadingVisibility(false);
					setPausePlayVisibility((mStreamType == MediaInfo.STREAM_TYPE_LIVE)
							&& (idleReason == MediaStatus.IDLE_REASON_CANCELED), mPlayDrawable);
				}
				break;
			case MediaStatus.PLAYER_STATE_BUFFERING:
				setLoadingVisibility(true);
				setPausePlayVisibility(false);
				break;
			default:
				setLoadingVisibility(false);
				setPausePlayVisibility(false);
			}
		}
	}

	private Drawable getPauseStopButton() {
		switch (mStreamType) {
		case MediaInfo.STREAM_TYPE_LIVE:
			return mStopDrawable;
		case MediaInfo.STREAM_TYPE_BUFFERED:
		default:
			return mPauseDrawable;
		}
	}

	private void setLoadingVisibility(boolean show) {
		mLoading.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	private void setPausePlayVisibility(boolean show) {
		setPausePlayVisibility(show, null);
	}

	private void setPausePlayVisibility(boolean show, Drawable drawable) {
		if (show && (drawable != null)) {
			mPausePlay.setImageDrawable(drawable);
		}
		mPausePlay.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	@Override
	protected void onStop() {
		mCastManager.removeVideoCastConsumer(castConsumerImpl);
		mCastManager.cancelImageRequest(mIconRequest);
		mIconRequest = null;
		super.onStop();
	}

	/**
	 * Initializes this dialog's set of playback buttons and adds click listeners.
	 */
	@SuppressLint("InflateParams")
	@Override
	public View onCreateMediaControlView(Bundle savedInstanceState) {
		LayoutInflater inflater = getLayoutInflater();
		View controls = inflater.inflate(R.layout.custom_media_route_controller_controls_dialog, null);

		loadViews(controls);
		updateMetadata();
		updatePlayPauseState();
		setupCallbacks();
		return controls;
	}

	private void setupCallbacks() {

		mPausePlay.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				setLoadingVisibility(true);
				setPausePlayVisibility(false);
				try {
					mCastManager.togglePlayback();
				} catch (TransientNetworkDisconnectionException e) {
					setLoadingVisibility(false);
					setPausePlayVisibility(true);
					LOGE(TAG, "Failed to toggle playback due to network issues", e);
				} catch (NoConnectionException e) {
					setLoadingVisibility(false);
					setPausePlayVisibility(true);
					LOGE(TAG, "Failed to toggle playback due to network issues", e);
				}
			}
		});

		View.OnClickListener mediaClickListener = new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (null != mCastManager.getTargetActivity()) {
					try {
						mCastManager.onTargetActivityInvoked(v.getContext());
					} catch (Exception e) {
						LOGE(TAG, "Failed to start the target activity due to network issues", e);
					}
					cancel();
				}
			}
		};
		mIcon.setOnClickListener(mediaClickListener);
		mTextContainer.setOnClickListener(mediaClickListener);
	}

	private void loadViews(View controls) {
		mIcon = (ImageView) controls.findViewById(R.id.iconView);
		mPausePlay = (ImageView) controls.findViewById(R.id.playPauseView);
		mTextContainer = controls.findViewById(R.id.textContainer);
		mTitle = (TextView) controls.findViewById(R.id.titleView);
		mSubTitle = (TextView) controls.findViewById(R.id.subTitleView);
		mLoading = controls.findViewById(R.id.loadingView);
		mEmptyText = (TextView) controls.findViewById(R.id.emptyView);
	}
}
