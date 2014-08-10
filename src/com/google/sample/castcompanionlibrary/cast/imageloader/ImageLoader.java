package com.google.sample.castcompanionlibrary.cast.imageloader;

import android.graphics.Bitmap;

/**
 * Image Loader abstraction to delegate networking, caching and requests management.
 * 
 * You can provide your own implementation to use your favorite image loader library, or use the
 * default one.
 * 
 * All methods will be called on the UI thread and must call the callbacks on the UI thread.
 * 
 * @author Christophe Beyls
 * 
 */
public interface ImageLoader {

	public interface Callbacks {

		/**
		 * This method must be called at the end of any non-cancelled request, even if an error
		 * occurs.
		 * 
		 * @param bitmap
		 *            The loaded bitmap, or null in case of error.
		 */
		void onResponse(Bitmap bitmap);
	}

	public interface Request {

		/**
		 * @return the URL of the current request (mandatory).
		 */
		String getUrl();
	}

	/**
	 * Loads an image from the network.
	 * 
	 * @param url
	 *            Non-null url.
	 * @param callbacks
	 *            Mandatory callbacks to be called on the UI thread at the end of the request.
	 * @return A token representing the pending request, that the implementation will be able to
	 *         manage. May be null if the request is handled immediately or if tokens are not
	 *         supported.
	 */
	Request load(String url, Callbacks callbacks);

	/**
	 * Allows to cancel a previously created request if it's still running.
	 * 
	 * @param request
	 *            Non-null request returned by load().
	 */
	void cancelRequest(Request request);
}
