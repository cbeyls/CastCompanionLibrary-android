package com.google.sample.castcompanionlibrary.widgets;

import java.net.URL;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaStatus;
import com.google.sample.castcompanionlibrary.R;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;
import com.google.sample.castcompanionlibrary.cast.exceptions.CastException;
import com.google.sample.castcompanionlibrary.cast.exceptions.NoConnectionException;
import com.google.sample.castcompanionlibrary.cast.exceptions.OnFailedListener;
import com.google.sample.castcompanionlibrary.cast.exceptions.TransientNetworkDisconnectionException;

/**
 * A compound component that provides a superset of functionalities required for the global access
 * requirement. This component provides an image for the album art, a play/pause button, a seekbar
 * for trick-play with current time and duration and a mute/unmute button. Clients can add this
 * compound component to their layout xml and register that with the instance of
 * {@link VideoCastManager} by using the following pattern:<br/>
 * 
 * <pre>
 * mMiniController = (MiniController) findViewById(R.id.miniController1);
 * mCastManager.addMiniController(mMiniController);
 * mMiniController.setOnMiniControllerChangedListener(mCastManager);
 * </pre>
 * 
 * Then the {@link VideoCastManager} will manage the behavior, including its state and metadata and
 * interactions.
 */
public class MiniController extends FrameLayout implements IMiniController {

	protected ImageView mIcon;
	protected TextView mTitle;
	protected TextView mSubTitle;
	protected ImageView mPlayPause;
	protected View mLoading;

	private OnMiniControllerChangedListener mListener;
	private Uri mIconUri;
	private Drawable mPauseDrawable;
	private Drawable mPlayDrawable;
	private Drawable mStopDrawable;
	private int mStreamType = MediaInfo.STREAM_TYPE_BUFFERED;

	public MiniController(Context context) {
		super(context);
		init();
	}

	public MiniController(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		Context context = getContext();
		Resources res = context.getResources();

		mPauseDrawable = res.getDrawable(R.drawable.ic_mini_controller_pause);
		mPlayDrawable = res.getDrawable(R.drawable.ic_mini_controller_play);
		mStopDrawable = res.getDrawable(R.drawable.ic_mini_controller_stop);

		LayoutInflater.from(context).inflate(R.layout.mini_controller, this);

		mIcon = (ImageView) findViewById(R.id.iconView);
		mTitle = (TextView) findViewById(R.id.titleView);
		mSubTitle = (TextView) findViewById(R.id.subTitleView);
		mPlayPause = (ImageView) findViewById(R.id.playPauseView);
		mLoading = findViewById(R.id.loadingView);

		mPlayPause.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (null != mListener) {
					setLoadingVisibility(true);
					try {
						mListener.onPlayPauseClicked();
					} catch (CastException e) {
						mListener.onFailed(R.string.failed_perform_action, -1);
					} catch (TransientNetworkDisconnectionException e) {
						mListener.onFailed(R.string.failed_no_connection_trans, -1);
					} catch (NoConnectionException e) {
						mListener.onFailed(R.string.failed_no_connection, -1);
					}
				}
			}
		});

		setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (null != mListener) {
					setLoadingVisibility(false);
					try {
						mListener.onTargetActivityInvoked(mIcon.getContext());
					} catch (Exception e) {
						mListener.onFailed(R.string.failed_perform_action, -1);
					}
				}
			}
		});
	}

	/**
	 * Sets the listener that should be notified when a relevant event is fired from this component.
	 * Clients can register the {@link VideoCastManager} instance to be the default listener so it
	 * can control the remote media playback.
	 * 
	 * @param listener
	 *            ; use null to unregister
	 */
	@Override
	public void setOnMiniControllerChangedListener(OnMiniControllerChangedListener listener) {
		mListener = listener;
	}

	@Override
	public void setStreamType(int streamType) {
		mStreamType = streamType;
	}

	private void setIcon(Bitmap bm) {
		if (bm == null) {
			mIcon.setImageResource(R.drawable.mini_controller_img_placeholder);
		} else {
			mIcon.setImageBitmap(bm);
		}
	}

	@Override
	public void setIcon(Uri uri) {
		if (null != mIconUri && mIconUri.equals(uri)) {
			return;
		}

		mIconUri = uri;

		if (uri == null) {
			setIcon((Bitmap) null);
			return;
		}

		new Thread(new Runnable() {
			Bitmap bm = null;

			@Override
			public void run() {
				try {
					URL imgUrl = new URL(mIconUri.toString());
					bm = BitmapFactory.decodeStream(imgUrl.openStream());
				} catch (Exception e) {
				}
				mIcon.post(new Runnable() {

					@Override
					public void run() {
						setIcon(bm);
					}
				});
			}
		}).start();
	}

	@Override
	public void setTitle(String title) {
		mTitle.setText(title);
	}

	@Override
	public void setSubTitle(String subTitle) {
		mSubTitle.setText(subTitle);
	}

	@Override
	public void setPlaybackStatus(int state, int idleReason) {
		switch (state) {
		case MediaStatus.PLAYER_STATE_PLAYING:
			mPlayPause.setVisibility(View.VISIBLE);
			mPlayPause.setImageDrawable(getPauseStopButton());
			setLoadingVisibility(false);
			break;
		case MediaStatus.PLAYER_STATE_PAUSED:
			mPlayPause.setVisibility(View.VISIBLE);
			mPlayPause.setImageDrawable(mPlayDrawable);
			setLoadingVisibility(false);
			break;
		case MediaStatus.PLAYER_STATE_IDLE:
			switch (mStreamType) {
			case MediaInfo.STREAM_TYPE_BUFFERED:
				mPlayPause.setVisibility(View.INVISIBLE);
				setLoadingVisibility(false);
				break;
			case MediaInfo.STREAM_TYPE_LIVE:
				if (idleReason == MediaStatus.IDLE_REASON_CANCELED) {
					mPlayPause.setVisibility(View.VISIBLE);
					mPlayPause.setImageDrawable(mPlayDrawable);
					setLoadingVisibility(false);
				} else {
					mPlayPause.setVisibility(View.INVISIBLE);
					setLoadingVisibility(false);
				}
				break;
			}
			break;
		case MediaStatus.PLAYER_STATE_BUFFERING:
			mPlayPause.setVisibility(View.INVISIBLE);
			setLoadingVisibility(true);
			break;
		default:
			mPlayPause.setVisibility(View.INVISIBLE);
			setLoadingVisibility(false);
			break;
		}
	}

	@Override
	public void setVisibility(boolean isVisible) {
		setVisibility(isVisible ? VISIBLE : GONE);
	}

	@Override
	public boolean isVisible() {
		return isShown();
	}

	private void setLoadingVisibility(boolean show) {
		mLoading.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	private Drawable getPauseStopButton() {
		switch (mStreamType) {
		case MediaInfo.STREAM_TYPE_BUFFERED:
			return mPauseDrawable;
		case MediaInfo.STREAM_TYPE_LIVE:
			return mStopDrawable;
		default:
			return mPauseDrawable;
		}
	}

	/**
	 * The interface for a listener that will be called when user interacts with the
	 * {@link MiniController}, like clicking on the play/pause button, etc.
	 */
	public interface OnMiniControllerChangedListener extends OnFailedListener {

		/**
		 * Notification that user has clicked on the Play/Pause button
		 * 
		 * @throws TransientNetworkDisconnectionException
		 * @throws NoConnectionException
		 * @throws CastException
		 */
		public void onPlayPauseClicked() throws CastException, TransientNetworkDisconnectionException, NoConnectionException;

		/**
		 * Notification that the user has clicked on the album art
		 * 
		 * @param context
		 * @throws NoConnectionException
		 * @throws TransientNetworkDisconnectionException
		 */
		public void onTargetActivityInvoked(Context context) throws TransientNetworkDisconnectionException, NoConnectionException;

	}
}