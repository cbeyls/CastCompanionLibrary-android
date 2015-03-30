package com.google.sample.castcompanionlibrary.cast.activities;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;
import android.view.Menu;

import com.google.android.gms.cast.MediaStatus;
import com.google.sample.castcompanionlibrary.R;
import com.google.sample.castcompanionlibrary.cast.BaseCastManager;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;
import com.google.sample.castcompanionlibrary.utils.Utils;

/**
 * All activities featuring Google Cast control should inherit from this activity.
 * 
 * @author Christophe Beyls
 * 
 */
public class VideoCastActivity extends ActionBarActivity {

	public static final double DEFAULT_VOLUME_INCREMENT = 0.05;

	private static double sVolumeIncrement = DEFAULT_VOLUME_INCREMENT;

	protected VideoCastManager mCastManager;

	/**
	 * Call this method on application startup to override the default volume increment in all activities inheriting
	 * from this one.
	 * 
	 * @param volumeIncrement
	 */
	public static void setVolumeIncrement(double volumeIncrement) {
		sVolumeIncrement = volumeIncrement;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mCastManager = VideoCastManager.getInstance(this);
	}

	/**
	 * You can override this method to either add menu items in addition to the cast button (by calling this
	 * implementation first) or provide your own replacement menu items (by not calling this implementation). If you
	 * choose the second option, you must call yourself mCastManager.addMediaRouterButton(menu,
	 * R.id.media_route_menu_item);
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.cast_player_menu, menu);
		mCastManager.addMediaRouterButton(menu, R.id.media_route_menu_item);
		return true;
	}

	@Override
	protected void onStart() {
		super.onStart();
		mCastManager.incrementUiCounter();
	}

	@Override
	protected void onStop() {
		mCastManager.decrementUiCounter();
		super.onStop();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mCastManager.setContext(this);
	}

	@Override
	protected void onDestroy() {
		mCastManager.clearContext(this);
		super.onDestroy();
	}

	@Override
	public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
		if (mCastManager.isConnected()) {
			boolean isKeyDown = event.getAction() == KeyEvent.ACTION_DOWN;
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_VOLUME_UP:
				if (onVolumeChange(sVolumeIncrement, isKeyDown)) {
					return true;
				}
				break;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if (onVolumeChange(-sVolumeIncrement, isKeyDown)) {
					return true;
				}
				break;
			}
		}
		return super.dispatchKeyEvent(event);
	}

	private boolean onVolumeChange(double volumeIncrement, boolean isKeyDown) {
		if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				&& (mCastManager.isFeatureEnabled(BaseCastManager.FEATURE_LOCKSCREEN))
				&& (mCastManager.getPlaybackStatus() == MediaStatus.PLAYER_STATE_PLAYING)) {
			return false;
		}

		if (isKeyDown) {
			try {
				mCastManager.incrementVolume(volumeIncrement);
			} catch (Exception e) {
				Utils.showErrorDialog(this, R.string.failed_setting_volume);
			}
		}
		return true;
	}
}
