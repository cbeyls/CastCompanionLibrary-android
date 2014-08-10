package com.google.sample.castcompanionlibrary.cast.imageloader;

import java.net.URL;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;

/**
 * Default implementation of an ImageLoader. It performs only one network request at a time and uses
 * a simple memory cache to store the last 2 loaded images.
 * 
 * @author Christophe Beyls
 * 
 */
public class DefaultImageLoader implements ImageLoader {

	// By default, only cache bitmaps smaller than 1/4 of the total heap size
	private static final long MAX_BITMAP_SIZE = Runtime.getRuntime().maxMemory() / 4L;

	private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(2);

	private class DefaultRequest extends AsyncTask<Void, Void, Bitmap> implements ImageLoader.Request {

		private final String url;
		private final Callbacks callbacks;

		public DefaultRequest(String url, Callbacks callbacks) {
			this.url = url;
			this.callbacks = callbacks;
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			return performRequest(url);
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			callbacks.onResponse(result);
		}

		@Override
		public String getUrl() {
			return url;
		}
	}

	private Bitmap performRequest(String url) {
		// Using a single background thread in sync with the cache guarantees that we only
		// perform a single network request per image.
		synchronized (cache) {
			Bitmap bitmap = cache.get(url);
			if (bitmap != null) {
				return bitmap;
			}

			try {
				bitmap = BitmapFactory.decodeStream(new URL(url).openStream());
				if ((bitmap != null) && shouldCache(url, bitmap)) {
					cache.put(url, bitmap);
				}
				return bitmap;
			} catch (Exception e) {
				return null;
			}
		}
	}

	/**
	 * Override this method if you want to change the default cache logic.
	 */
	protected boolean shouldCache(String url, Bitmap bitmap) {
		return sizeOf(bitmap) <= MAX_BITMAP_SIZE;
	}

	protected static long sizeOf(Bitmap bitmap) {
		return bitmap.getRowBytes() * bitmap.getHeight();
	}

	@Override
	public ImageLoader.Request load(String url, ImageLoader.Callbacks callbacks) {
		DefaultRequest request = new DefaultRequest(url, callbacks);
		request.execute();
		return request;
	}

	@Override
	public void cancelRequest(ImageLoader.Request request) {
		((DefaultRequest) request).cancel(true);
	}
}
