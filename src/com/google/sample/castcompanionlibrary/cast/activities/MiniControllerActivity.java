package com.google.sample.castcompanionlibrary.cast.activities;

import com.google.sample.castcompanionlibrary.R;
import com.google.sample.castcompanionlibrary.cast.activities.VideoCastActivity;
import com.google.sample.castcompanionlibrary.widgets.IMiniController;

/**
 * This class takes care of automatically handling the lifecycle events of a MiniController view in the layout.
 * 
 * @author Christophe Beyls
 * 
 */
public class MiniControllerActivity extends VideoCastActivity {

	private IMiniController mMiniController;
	private boolean mMiniControllerRetrieved;

	@Override
	protected void onStart() {
		super.onStart();
		if (!mMiniControllerRetrieved) {
			mMiniController = (IMiniController) findViewById(getMiniControllerId());
			mMiniControllerRetrieved = true;
		}
		if (mMiniController != null) {
			mCastManager.addMiniController(mMiniController);
		}
	}

	@Override
	protected void onStop() {
		if (mMiniController != null) {
			mCastManager.removeMiniController(mMiniController);
		}
		super.onStop();
	}

	/**
	 * Override this method to return the MiniController id, if different from R.id.mini_controller
	 */
	protected int getMiniControllerId() {
		return R.id.mini_controller;
	}
}
