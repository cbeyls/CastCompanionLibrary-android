package com.google.sample.castcompanionlibrary.cast.player;

import java.util.Formatter;
import java.util.Locale;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaStatus;
import com.google.sample.castcompanionlibrary.R;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;
import com.google.sample.castcompanionlibrary.cast.activities.CastActivity;
import com.google.sample.castcompanionlibrary.cast.exceptions.NoConnectionException;
import com.google.sample.castcompanionlibrary.cast.exceptions.TransientNetworkDisconnectionException;
import com.google.sample.castcompanionlibrary.utils.Utils;

/**
 * A simpler and more robust version of a default Cast controller Activity.
 * 
 * @author Christophe Beyls
 * 
 */
public class VideoCastControllerActivity extends CastActivity implements IVideoCastController {

	private static final String FRAGMENT_TAG = "task";
	private View mPageView;
	private ImageView mPlayPause;
	private TextView mLiveText;
	private TextView mStart;
	private TextView mEnd;
	private SeekBar mSeekbar;
	private TextView mLine1;
	private TextView mLine2;
	private ProgressBar mLoading;
	private View mControllers;
	private Drawable mPauseDrawable;
	private Drawable mPlayDrawable;
	private Drawable mStopDrawable;
	private OnVideoCastControllerListener mListener;
	private int mStreamType;

	private StringBuilder mFormatBuilder;
	private Formatter mFormatter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cast_activity);
		loadAndSetupViews();

		ActionBar bar = getSupportActionBar();
		bar.setDisplayHomeAsUpEnabled(true);
		bar.setDisplayShowTitleEnabled(false);
		bar.setBackgroundDrawable(getResources().getDrawable(R.drawable.actionbar_bg_gradient_light));

		Bundle extras = getIntent().getExtras();
		if (null == extras) {
			finish();
			return;
		}

		FragmentManager fm = getSupportFragmentManager();
		VideoCastControllerFragment mediaAuthFragment = (VideoCastControllerFragment) fm.findFragmentByTag(FRAGMENT_TAG);

		// if fragment is null, it means this is the first time, so create it
		if (mediaAuthFragment == null) {
			mediaAuthFragment = VideoCastControllerFragment.newInstance(extras);
			fm.beginTransaction().add(mediaAuthFragment, FRAGMENT_TAG).commit();
		}
		setOnVideoCastControllerChangedListener(mediaAuthFragment);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (intent.getBooleanExtra(VideoCastManager.EXTRA_SHOULD_START, false)) {
			// Force recreating this activity and fragment with the new parameters
			finish();
			startActivity(intent);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void loadAndSetupViews() {
		Resources res = getResources();
		mPauseDrawable = res.getDrawable(R.drawable.ic_av_pause_dark);
		mPlayDrawable = res.getDrawable(R.drawable.ic_av_play_dark);
		mStopDrawable = res.getDrawable(R.drawable.ic_av_stop_dark);
		mPageView = findViewById(R.id.pageView);
		mPlayPause = (ImageView) findViewById(R.id.imageView1);
		mLiveText = (TextView) findViewById(R.id.liveText);
		mStart = (TextView) findViewById(R.id.startText);
		mEnd = (TextView) findViewById(R.id.endText);
		mSeekbar = (SeekBar) findViewById(R.id.seekBar1);
		mLine1 = (TextView) findViewById(R.id.textView1);
		mLine2 = (TextView) findViewById(R.id.textView2);
		mLoading = (ProgressBar) findViewById(R.id.progressBar1);
		mControllers = findViewById(R.id.controllers);

		mFormatBuilder = new StringBuilder();
		mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());

		mPlayPause.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				try {
					mListener.onPlayPauseClicked(v);
				} catch (TransientNetworkDisconnectionException e) {
					Utils.showErrorDialog(VideoCastControllerActivity.this, R.string.failed_no_connection_trans);
				} catch (NoConnectionException e) {
					Utils.showErrorDialog(VideoCastControllerActivity.this, R.string.failed_no_connection);
				} catch (Exception e) {
					Utils.showErrorDialog(VideoCastControllerActivity.this, R.string.failed_perform_action);
				}
			}
		});

		mSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				try {
					if (null != mListener) {
						mListener.onStopTrackingTouch(seekBar);
					}
				} catch (Exception e) {
					finish();
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				try {
					if (null != mListener) {
						mListener.onStartTrackingTouch(seekBar);
					}
				} catch (Exception e) {
					finish();
				}
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				mStart.setText(stringForTime(progress));
				try {
					if (null != mListener) {
						mListener.onProgressChanged(seekBar, progress, fromUser);
					}
				} catch (Exception e) {
				}
			}
		});
	}

	@Override
	public void showLoading(boolean visible) {
		mLoading.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	// -------------- IVideoCastController implementation ---------------- //
	@Override
	public void adjustControllersForLiveStream(boolean isLive) {
		mLiveText.setVisibility(isLive ? View.VISIBLE : View.GONE);
		int visibility = isLive ? View.GONE : View.VISIBLE;
		mStart.setVisibility(visibility);
		mEnd.setVisibility(visibility);
		mSeekbar.setVisibility(visibility);
	}

	@Override
	public void setPlaybackStatus(int state) {
		switch (state) {
		case MediaStatus.PLAYER_STATE_PLAYING:
			mControllers.setVisibility(View.VISIBLE);
			mLoading.setVisibility(View.GONE);
			mPlayPause.setImageDrawable((mStreamType == MediaInfo.STREAM_TYPE_LIVE) ? mStopDrawable : mPauseDrawable);
			mPlayPause.setVisibility(View.VISIBLE);
			mLine2.setText(getString(R.string.casting_to_device, mCastManager.getDeviceName()));
			break;
		case MediaStatus.PLAYER_STATE_PAUSED:
			mControllers.setVisibility(View.VISIBLE);
			mLoading.setVisibility(View.GONE);
			mPlayPause.setImageDrawable(mPlayDrawable);
			mPlayPause.setVisibility(View.VISIBLE);
			mLine2.setText(getString(R.string.casting_to_device, mCastManager.getDeviceName()));
			break;
		case MediaStatus.PLAYER_STATE_IDLE:
			mLoading.setVisibility(View.GONE);
			mPlayPause.setImageDrawable(mPlayDrawable);
			mPlayPause.setVisibility(View.VISIBLE);
			mLine2.setText(getString(R.string.casting_to_device, mCastManager.getDeviceName()));
			break;
		case MediaStatus.PLAYER_STATE_BUFFERING:
			mLoading.setVisibility(View.VISIBLE);
			mPlayPause.setVisibility(View.INVISIBLE);
			mLine2.setText(R.string.loading);
			break;
		}
	}

	@Override
	public void updateSeekbar(int position, int duration) {
		// Call setMax before setProgress for an immediate display update
		mSeekbar.setMax(duration);
		mSeekbar.setProgress(position);
		mStart.setText(stringForTime(position));
		mEnd.setText(stringForTime(duration));
	}

	@SuppressWarnings("deprecation")
	@Override
	public void setImage(Bitmap bitmap) {
		if (null != bitmap) {
			if (mPageView instanceof ImageView) {
				((ImageView) mPageView).setImageBitmap(bitmap);
			} else {
				mPageView.setBackgroundDrawable(new BitmapDrawable(getResources(), bitmap));
			}
		}
	}

	@Override
	public void setLine1(String text) {
		mLine1.setText(text);
	}

	@Override
	public void setLine2(String text) {
		mLine2.setText(text);
	}

	@Override
	public void setOnVideoCastControllerChangedListener(OnVideoCastControllerListener listener) {
		mListener = listener;
	}

	@Override
	public void setStreamType(int streamType) {
		mStreamType = streamType;
	}

	@Override
	public void updateControllersStatus(boolean enabled) {
		mControllers.setVisibility(enabled ? View.VISIBLE : View.GONE);
		if (enabled) {
			adjustControllersForLiveStream(mStreamType == MediaInfo.STREAM_TYPE_LIVE);
		}
	}

	@Override
	public void closeActivity() {
		finish();
	}

	private String stringForTime(int timeMs) {
		int totalSeconds = timeMs / 1000;

		int seconds = totalSeconds % 60;
		int minutes = (totalSeconds / 60) % 60;
		int hours = totalSeconds / 3600;

		mFormatBuilder.setLength(0);
		if (hours > 0) {
			return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
		} else {
			return mFormatter.format("%02d:%02d", minutes, seconds).toString();
		}
	}
}
