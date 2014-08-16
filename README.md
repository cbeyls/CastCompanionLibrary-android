# CastCompanionLibrary-android

This is a fork of the Cast Companion Library for Android by Google. It features fixed bugs, easier integration, improved performance, simpler code and cleaned up resources.

CastCompanionLibrary-android is a library project to enable developers integrate Cast capabilities into their applications faster and easier.

## IMPORTANT - Differences with the original library

### Min SDK version
This library is compatible with API level **9** and above (the original library mentions API level 10 and above).

### Manifest declaration
This library does not require any Intent filter declaration for the `VideoCastNotificationService`. This is a recap of what you must declare inside your manifest's `<application>` tag for video-centric applications:

        <receiver android:name="com.google.sample.castcompanionlibrary.remotecontrol.VideoIntentReceiver" >
            <intent-filter>
                <action android:name="android.media.AUDIO_BECOMING_NOISY" />
                <action android:name="android.intent.action.MEDIA_BUTTON" />
                <action android:name="android.media.VOLUME_CHANGED_ACTION" />
                <action android:name="com.google.sample.castcompanionlibrary.action.toggleplayback" />
                <action android:name="com.google.sample.castcompanionlibrary.action.stop" />
            </intent-filter>
        </receiver>

        <service
            android:name="com.google.sample.castcompanionlibrary.notification.VideoCastNotificationService"
            android:exported="false" />

        <meta-data
            android:name="com.google.android.gms.version"
            android:value="@integer/google_play_services_version" />

### VideoCastManager initialization
You **must** initialize `VideoCastManager` (or `DataCastManager`) by calling its static `initialize()` method in your `Application.onCreate()` callback.
This is because the services and activities of this library expect the VideoCastManager to be already initialized when they start up.

### ImageLoader
This library provides a pluggable image loading system to load the videos artwork in all its components. The default implementation features a simple network queue and a small memory cache. You may provide your own implementation instead, in order to use your favourite image loader library. To do so, you need to implement the `com.google.sample.castcompanionlibrary.cast.imageloader.ImageLoader` interface. Read the interface documentation for more information.
You can also find [an implementation using the image loader of the Volley library](https://gist.github.com/cbeyls/f35a75b59ac2dc4610b7).

You then need to pass your implementation as a last parameter to the `VideoCastManager.initialize()` method to enable it.

### VideoCastActivity
This library provides a base class named `VideoCastActivity` that your activities may inherit from for an easier integration in video-centric applications. It takes cares of the following automatically:

* Retrieving the VideoCastManager instance in a protected field named `mCastManager`.
* Updating the context of the VideoCastManager.
* Handling hardware volume keys to control the Cast device volume when connected to it.
* Calling `incrementUiCounter()` and `decrementUiCounter()` so the library can keep track of the visibility of your application's UI and act accordingly (show or hide the notification, look for Cast devices).
* *Optional*: populating the Action Bar with a Cast button and initializing it.

### MiniControllerActivity
This other base Activity provides the same features as `VideoCastActivity`, plus automatically registers and unregisters a MiniController. It requires that the layout of your Activity contains a MiniController widget with the id `R.id.mini_controller`.

### Default volume increment step change
The default volume increment when using the hardware volume keys in activities inheriting from `VideoCastActivity` (including the default Cast Controller Activity) is **5%** or 0.05. If you want to change it, you must call the static method `VideoCastActivity.setVolumeIncrement()`. For example, you can call it in `Application.onCreate()`.

## Contributors for this version
* Christophe Beyls

If you use this version of the Cast Companion Library in your project, please link to this GitHub's project page and mention the author.

## Dependencies
* google-play-services_lib library from the Android SDK (at least version 4.3)
* android-support-v7-appcompat (version 19.0.1 or above)
* android-support-v7-mediarouter (version 19.0.1 or above)

## Setup Instructions
* Set up the project dependencies

## Documentation
See the "CastCompanionLibray.pdf" inside the project for a more extensive documentation.

## References and How to report bugs
* [Cast Developer Documentation](http://developers.google.com/cast/)
* [Design Checklist](http://developers.google.com/cast/docs/design_checklist)
* If you find any issues with this library, please open a bug here on GitHub
* Question are answered on [StackOverflow](http://stackoverflow.com/questions/tagged/google-cast)

## How to make contributions?
Please read and follow the steps in the CONTRIBUTING.md

## License
See LICENSE

## Google+
Google Cast Developers Community on Google+ [http://goo.gl/TPLDxj](http://goo.gl/TPLDxj)

## Change List
1.4 -> 1.5
 * Fixed the issue where VideoCastNotificationService was not setting up data namespace if one was configured
 * Fixed issue 50
 * Added aversion number that will be printed in the log statements for tracking purposes
 * Correcting the typo in the name of method checkGooglePlaySevices() by introducing a new method and deprecating the old one (issue 48)
 * Fixing many typos in comments and some resources
 * Updating documentation to reflect the correct name of callbacks for the custom namespace for VideoCastManager

1.3 -> 1.4
 * Added support for MediaRouteButton
 * Added "alias" resources for Mini Controller play/pause/stop buttons so clients can customize them easily
 * Added a color resource to control thw color of the title of the custom VideoMediaRouteControllerDialog
 * Fixed some typos in JavaDoc

1.2 -> 1.3
 * Fixing issue 32
 * Fixing issue 33
 * Adding a better BaseCastManager.clearContext() variation
 * Implementing enhancement 30
 * Making sure play/pause button is hidden when ProgressBar is shown in VideoMediaRouteControllerDialog
 * probably some more adjustments and bug fixes

1.1 -> 1.2
 * Improving thread-safety in calling various ConsumerImpl callbacks
 * (backward incompatible) Changing the signature of IMediaAuthListener.onResult
 * Adding an API to BaseCastManager so clients can clear the "context" to avoid any leaks
 * Various bug fixes

1.0 -> 1.1
 * Added gradle build scripts (make sure you have Android Support Repository)
 * For live media, the "pause" button at various places is replaced with a "stop" button
 * Refactored the VideoCastControllerActivity to enable configuration changes without losing any running process
 * Added new capabilities for clients to hook in an authorization process prior to casting a video
 * A number of bug fixes, style fixes, etc
 * Updated documentation
