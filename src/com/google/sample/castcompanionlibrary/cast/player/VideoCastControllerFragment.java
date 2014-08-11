package com.google.sample.castcompanionlibrary.cast.player;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.View;
import android.widget.SeekBar;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.sample.castcompanionlibrary.R;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;
import com.google.sample.castcompanionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.google.sample.castcompanionlibrary.cast.exceptions.NoConnectionException;
import com.google.sample.castcompanionlibrary.cast.exceptions.TransientNetworkDisconnectionException;
import com.google.sample.castcompanionlibrary.cast.imageloader.ImageLoader;
import com.google.sample.castcompanionlibrary.utils.Utils;

/**
 * Simpler and more robust version of VideoCastControllerFragment with no support of
 * IMediaAuthListener.
 * 
 * @author Christophe Beyls
 */
public class VideoCastControllerFragment extends Fragment implements OnVideoCastControllerListener {

	private static final String EXTRAS = "extras";

	private MediaInfo mSelectedMedia;
	private VideoCastManager mCastManager;
	private Handler mHandler;
	private IVideoCastController mCastController;
	private ImageLoader.Request mImageRequest;
	private int mPlaybackState = MediaStatus.PLAYER_STATE_UNKNOWN;
	private boolean mIsFresh = false;

	/**
	 * Call this static method to create an instance of this fragment.
	 * 
	 * @param extras
	 * @return
	 */
	public static VideoCastControllerFragment newInstance(Bundle extras) {
		VideoCastControllerFragment f = new VideoCastControllerFragment();
		Bundle b = new Bundle();
		b.putBundle(EXTRAS, extras);
		f.setArguments(b);
		return f;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mCastController = (IVideoCastController) activity;
		mCastManager = VideoCastManager.getInstance(activity);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Retain this fragment across configuration changes.
		setRetainInstance(true);
		mHandler = new Handler();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean("dummy", true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		if (savedInstanceState == null) {
			Bundle extras = getArguments().getBundle(EXTRAS);
			if (extras.getBoolean(VideoCastManager.EXTRA_SHOULD_START)) {
				MediaInfo mediaInfo = Utils.toMediaInfo(extras.getBundle(VideoCastManager.EXTRA_MEDIA));
				int startPoint = extras.getInt(VideoCastManager.EXTRA_START_POINT, 0);
				String customDataStr = extras.getString(VideoCastManager.EXTRA_CUSTOM_DATA);
				JSONObject customData = null;
				if (!TextUtils.isEmpty(customDataStr)) {
					try {
						customData = new JSONObject(customDataStr);
					} catch (JSONException e) {
					}
				}

				mIsFresh = true;
				updateMetadata(mediaInfo);

				// need to start remote playback
				setPlaybackStatus(MediaStatus.PLAYER_STATE_BUFFERING);
				try {
					mCastManager.loadMedia(mediaInfo, true, startPoint, customData);
				} catch (Exception e) {
					mCastController.closeActivity();
				}
			}
		}
	}

	private void setPlaybackStatus(int playbackState) {
		if (mPlaybackState != playbackState) {
			mPlaybackState = playbackState;
			mCastController.setPlaybackStatus(playbackState);
		}
	}

	private final VideoCastConsumerImpl mCastConsumer = new VideoCastConsumerImpl() {

		@Override
		public void onDisconnected() {
			mCastController.closeActivity();
		}

		@Override
		public void onApplicationDisconnected(int errorCode) {
			mCastController.closeActivity();
		}

		@Override
		public void onRemoteMediaPlayerMetadataUpdated() {
			try {
				updateMetadata(mCastManager.getRemoteMediaInformation());
			} catch (TransientNetworkDisconnectionException e) {
			} catch (NoConnectionException e) {
			}
		}

		@Override
		public void onRemoteMediaPlayerStatusUpdated() {
			updatePlayerStatus();
		}

		@Override
		public void onConnectionSuspended(int cause) {
			mCastController.updateControllersStatus(false);
		}

		@Override
		public void onConnectivityRecovered() {
			mCastController.updateControllersStatus(true);
		}
	};

	private final Runnable mUpdateSeekbarRunnable = new Runnable() {

		@Override
		public void run() {
			if ((mPlaybackState != MediaStatus.PLAYER_STATE_BUFFERING) && mCastManager.isConnected()) {
				try {
					int duration = (int) mCastManager.getMediaDuration();
					if (duration > 0) {
						int currentPos = (int) mCastManager.getCurrentMediaPosition();
						mCastController.updateSeekbar(currentPos, duration);
					}
				} catch (TransientNetworkDisconnectionException e) {
				} catch (NoConnectionException e) {
				}
			}

			mHandler.postDelayed(mUpdateSeekbarRunnable, 1000L);
		}
	};

	private void stopTrickplayTimer() {
		mHandler.removeCallbacks(mUpdateSeekbarRunnable);
	}

	private void restartTrickplayTimer() {
		stopTrickplayTimer();
		mHandler.postDelayed(mUpdateSeekbarRunnable, 100L);
	}

	private void updateMetadata(MediaInfo mediaInfo) {
		if ((mSelectedMedia != null) && mSelectedMedia.equals(mediaInfo)) {
			return;
		}
		mSelectedMedia = mediaInfo;
		if (mSelectedMedia == null) {
			showImage(null);
			return;
		}
		showImage(Utils.getImageUrl(mSelectedMedia, 1));
		MediaMetadata mm = mSelectedMedia.getMetadata();
		mCastController.setLine1(mm.getString(MediaMetadata.KEY_TITLE));
		int streamType = mSelectedMedia.getStreamType();
		mCastController.setStreamType(streamType);
		mCastController.adjustControllersForLiveStream(streamType == MediaInfo.STREAM_TYPE_LIVE);
	}

	private final ImageLoader.Callbacks mImageLoaderCallbacks = new ImageLoader.Callbacks() {

		@Override
		public void onResponse(Bitmap bitmap) {
			if (bitmap == null) {
				bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.dummy_album_art_large);
			}
			mCastController.setImage(bitmap);
		}
	};

	/*
	 * Gets the image at the given url and populates the image view with that. It tries to cache the
	 * image to avoid unnecessary network calls.
	 */
	private void showImage(String url) {
		mImageRequest = mCastManager.loadImage(url, mImageLoaderCallbacks, mImageRequest);
	}

	private void updatePlayerStatus() {
		if (mSelectedMedia == null) {
			return;
		}
		int mediaStatus = mCastManager.getPlaybackStatus();
		switch (mediaStatus) {
		case MediaStatus.PLAYER_STATE_PLAYING:
		case MediaStatus.PLAYER_STATE_PAUSED:
		case MediaStatus.PLAYER_STATE_BUFFERING:
			mIsFresh = false;
			setPlaybackStatus(mediaStatus);
			break;
		case MediaStatus.PLAYER_STATE_IDLE:
			switch (mCastManager.getIdleReason()) {
			case MediaStatus.IDLE_REASON_FINISHED:
				if (!mIsFresh) {
					mCastController.closeActivity();
				}
				break;
			case MediaStatus.IDLE_REASON_CANCELED:
				try {
					if (mCastManager.isRemoteStreamLive()) {
						setPlaybackStatus(mediaStatus);
					}
				} catch (TransientNetworkDisconnectionException e) {
				} catch (NoConnectionException e) {
				}
			}
			break;
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		boolean shouldFinish = !mCastManager.isConnected()
				|| (mCastManager.getPlaybackStatus() == MediaStatus.PLAYER_STATE_IDLE
						&& mCastManager.getIdleReason() == MediaStatus.IDLE_REASON_FINISHED && !mIsFresh);
		if (shouldFinish) {
			mCastController.closeActivity();
			return;
		}
		mCastManager.addVideoCastConsumer(mCastConsumer);
		if (!mIsFresh) {
			mCastConsumer.onRemoteMediaPlayerMetadataUpdated();
			updatePlayerStatus();
		}
		restartTrickplayTimer();
	}

	@Override
	public void onStop() {
		stopTrickplayTimer();
		mCastManager.removeVideoCastConsumer(mCastConsumer);
		mIsFresh = false;
		super.onStop();
	}

	@Override
	public void onDestroy() {
		mCastManager.cancelImageRequest(mImageRequest);
		mImageRequest = null;
		super.onDestroy();
	}

	// ------- Implementation of OnVideoCastControllerListener interface ----------------- //

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		try {
			switch (mPlaybackState) {
			case MediaStatus.PLAYER_STATE_PLAYING:
				setPlaybackStatus(MediaStatus.PLAYER_STATE_BUFFERING);
				mCastManager.play(seekBar.getProgress());
				break;
			case MediaStatus.PLAYER_STATE_PAUSED:
				mCastManager.seek(seekBar.getProgress());
				break;
			}
			restartTrickplayTimer();
		} catch (Exception e) {
			mCastController.closeActivity();
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		stopTrickplayTimer();
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
	}

	@Override
	public void onPlayPauseClicked(View v) throws TransientNetworkDisconnectionException, NoConnectionException {
		togglePlayback();
	}

	private void togglePlayback() throws TransientNetworkDisconnectionException, NoConnectionException {
		switch (mPlaybackState) {
		case MediaStatus.PLAYER_STATE_PAUSED:
			setPlaybackStatus(MediaStatus.PLAYER_STATE_BUFFERING);
			mCastManager.play();
			restartTrickplayTimer();
			break;
		case MediaStatus.PLAYER_STATE_PLAYING:
			setPlaybackStatus(MediaStatus.PLAYER_STATE_BUFFERING);
			mCastManager.pause();
			break;
		case MediaStatus.PLAYER_STATE_IDLE:
			setPlaybackStatus(MediaStatus.PLAYER_STATE_BUFFERING);
			if ((mSelectedMedia.getStreamType() == MediaInfo.STREAM_TYPE_LIVE) && (mCastManager.getIdleReason() == MediaStatus.IDLE_REASON_CANCELED)) {
				mCastManager.play();
			} else {
				mCastManager.loadMedia(mSelectedMedia, true, 0);
			}
			restartTrickplayTimer();
			break;
		}
	}
}
