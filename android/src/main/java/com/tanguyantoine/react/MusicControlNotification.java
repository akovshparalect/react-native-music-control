package com.tanguyantoine.react;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.os.IBinder;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.NotificationCompat;
import android.view.KeyEvent;
import android.widget.RemoteViews;
import android.app.Notification;
import android.view.View;
import android.os.Build;
import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;

import java.util.Map;

public class MusicControlNotification {
    
    public static final int VISIBILITY_WHEN_PLAYING = 0;
    public static final int VISIBILITY_ALWAYS = 1;
    public static Bitmap cover = null;
    
    protected static final String REMOVE_NOTIFICATION = "music_control_remove_notification";
    protected static final String MEDIA_BUTTON = "music_control_media_button";
    protected static final String PACKAGE_NAME = "music_control_package_name";

    private static final String NOTIFICATION_CHANNEL = "Playback";
    private static final int NOTIFICATION_ID = 2;

    private final ReactApplicationContext context;
    private final MusicControlModule module;

    private int smallIcon;
    private int customIcon;
    private NotificationCompat.Action play, pause, stop, next, previous, skipForward, skipBackward;
    private boolean mNotificationNag = false; // todo
    private NotificationHelper mNotificationHelper;
    private Bitmap mCover = null;
    private Intent mOpenAppIntent;
    private Intent mRemoveNotifIntent;
    private RemoteViews mNormalLayout;
    private RemoteViews mExpandedLayout;
    private Notification notification = null;
    private MediaMetadataCompat mMediaData;

    public MusicControlNotification(MusicControlModule module, ReactApplicationContext context) {
        this.context = context;
        this.module = module;

        Resources r = context.getResources();
        String packageName = context.getPackageName();

        // Optional custom icon with fallback to the play icon
        smallIcon = r.getIdentifier("music_control_icon", "drawable", packageName);
        if(smallIcon == 0) smallIcon = r.getIdentifier("play", "drawable", packageName);

        mNotificationHelper = new NotificationHelper(context, NOTIFICATION_CHANNEL, "Podcast App");  // todo
        mNormalLayout = new RemoteViews(context.getPackageName(), R.layout.notification);
        mExpandedLayout = new RemoteViews(context.getPackageName(), R.layout.notification_expanded);
    }

    public void setCustomTextViewText(String text) {
        mNormalLayout.setTextViewText(R.id.customText, text);        
        mExpandedLayout.setTextViewText(R.id.customText, text);        
    }

    public synchronized void setCover(Bitmap cover) {
        mCover = cover;
        mNormalLayout.setImageViewBitmap(R.id.cover, mCover);
        mExpandedLayout.setImageViewBitmap(R.id.cover, mCover);        
        mNotificationHelper.notify(NOTIFICATION_ID, notification);        
    }
    
    public synchronized void setCustomNotificationIcon(String resourceName) {
        if(resourceName == null) {
            customIcon = 0;
            return;
        }
        
        Resources r = context.getResources();
        String packageName = context.getPackageName();

        customIcon = r.getIdentifier(resourceName, "drawable", packageName);
    }

    public synchronized void updateActions(long mask, Map<String, Integer> options) {
        play = createAction("play", "Play", mask, PlaybackStateCompat.ACTION_PLAY, play);
        pause = createAction("pause", "Pause", mask, PlaybackStateCompat.ACTION_PAUSE, pause);
        stop = createAction("stop", "Stop", mask, PlaybackStateCompat.ACTION_STOP, stop);
        next = createAction("next", "Next", mask, PlaybackStateCompat.ACTION_SKIP_TO_NEXT, next);
        previous = createAction("previous", "Previous", mask, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS, previous);

        if (options != null && options.containsKey("skipForward") && (options.get("skipForward") == 10 || options.get("skipForward") == 5 || options.get("skipForward") == 30)) {
            skipForward = createAction("skip_forward_" + options.get("skipForward").toString(), "Skip Forward", mask, PlaybackStateCompat.ACTION_FAST_FORWARD, skipForward);
        } else {
            skipForward = createAction("skip_forward_10", "Skip Forward", mask, PlaybackStateCompat.ACTION_FAST_FORWARD, skipForward);
        }

        if (options != null && options.containsKey("skipBackward") && (options.get("skipBackward") == 10 || options.get("skipBackward") == 5 || options.get("skipBackward") == 30)) {
            skipBackward = createAction("skip_backward_" + options.get("skipBackward").toString(), "Skip Backward", mask, PlaybackStateCompat.ACTION_REWIND, skipBackward);
        } else {
            skipBackward = createAction("skip_backward_10", "Skip Backward", mask, PlaybackStateCompat.ACTION_REWIND, skipBackward);
        }
    }

    public Notification createNotification(boolean isPlaying, int mode)
    {
        if (mCover == null) {
            mNormalLayout.setImageViewResource(R.id.cover, R.drawable.fallback_cover);
            mExpandedLayout.setImageViewResource(R.id.cover, R.drawable.fallback_cover_large);
        } else {
            mNormalLayout.setImageViewBitmap(R.id.cover, mCover);
            mExpandedLayout.setImageViewBitmap(R.id.cover, mCover);
        }

        int playButton = ThemeHelper.getPlayButtonResource(isPlaying);

        mNormalLayout.setImageViewResource(R.id.play_pause, playButton);
        mExpandedLayout.setImageViewResource(R.id.play_pause, playButton);

        mNormalLayout.setImageViewResource(R.id.play_pause, playButton);
        mExpandedLayout.setImageViewResource(R.id.play_pause, playButton);

        int closeButtonVisibility = View.VISIBLE;
        mNormalLayout.setViewVisibility(R.id.close, closeButtonVisibility);
        mExpandedLayout.setViewVisibility(R.id.close, closeButtonVisibility);
        mNormalLayout.setOnClickPendingIntent(R.id.close, 
            PendingIntent.getBroadcast(context, 0, mRemoveNotifIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        mExpandedLayout.setOnClickPendingIntent(R.id.close, 
            PendingIntent.getBroadcast(context, 0, mRemoveNotifIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        String title = mMediaData.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
        String artist = mMediaData.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
        String album = mMediaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
        String timeString = getTimeString();
        
        mNormalLayout.setTextViewText(R.id.title, title);
        mNormalLayout.setTextViewText(R.id.artist, artist + timeString);
        mExpandedLayout.setTextViewText(R.id.title, title);
        mExpandedLayout.setTextViewText(R.id.artist, artist + timeString);
        mExpandedLayout.setTextViewText(R.id.album, album);
        
        if(isPlaying && pause != null) {
            mNormalLayout.setOnClickPendingIntent(R.id.play_pause, pause.getActionIntent());
            mExpandedLayout.setOnClickPendingIntent(R.id.play_pause, pause.getActionIntent());            
        }
        
        if(!isPlaying && play != null) {
            mNormalLayout.setOnClickPendingIntent(R.id.play_pause, play.getActionIntent());
            mExpandedLayout.setOnClickPendingIntent(R.id.play_pause, play.getActionIntent());      
        }

        if(skipBackward != null) {
            mNormalLayout.setOnClickPendingIntent(R.id.previous, skipBackward.getActionIntent());
            mExpandedLayout.setOnClickPendingIntent(R.id.previous, skipBackward.getActionIntent());  
        }

        if(skipForward != null) {
            mNormalLayout.setOnClickPendingIntent(R.id.next, skipForward.getActionIntent());
            mExpandedLayout.setOnClickPendingIntent(R.id.next, skipForward.getActionIntent());  
        }

        Notification notification = mNotificationHelper.getNewNotification(context);
        notification.contentView = mNormalLayout;
        notification.icon = (customIcon != 0 ? customIcon : smallIcon);        
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        notification.contentIntent = PendingIntent.getActivity(context, 0, mOpenAppIntent, 0);
        notification.deleteIntent = PendingIntent.getActivity(context, 0, mRemoveNotifIntent, 0);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // expanded view is available since 4.1
            notification.bigContentView = mExpandedLayout;
            // 4.1 also knows about notification priorities
            // HIGH is one higher than the default.
            notification.priority = Notification.PRIORITY_HIGH;
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notification.visibility = Notification.VISIBILITY_PUBLIC;
        }
        if(mNotificationNag) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                notification.priority = Notification.PRIORITY_MAX;
                notification.vibrate = new long[0]; // needed to get headsup
            } else {
                notification.tickerText = title + " - " + artist;
            }
        }

        return notification;
    }

    public synchronized void show(NotificationCompat.Builder builder, MediaMetadataCompat mediaData, boolean isPlaying) {
        mMediaData = mediaData;

        // Add the buttons
        builder.mActions.clear();
        if(previous != null) builder.addAction(previous);
        if(skipBackward != null) builder.addAction(skipBackward);
        if(play != null && !isPlaying) builder.addAction(play);
        if(pause != null && isPlaying) builder.addAction(pause);
        if(stop != null) builder.addAction(stop);
        if(next != null) builder.addAction(next);
        if(skipForward != null) builder.addAction(skipForward);

        // Set whether notification can be closed based on closeNotification control (default PAUSED)
        if(module.notificationClose == MusicControlModule.NotificationClose.ALWAYS) {
            builder.setOngoing(false);
        } else if(module.notificationClose == MusicControlModule.NotificationClose.PAUSED) {
            builder.setOngoing(isPlaying);
        } else { // NotificationClose.NEVER
            builder.setOngoing(true); 
        }
        
        builder.setSmallIcon(customIcon != 0 ? customIcon : smallIcon);

        String packageName = context.getPackageName();
        
        // Open the app when the notification is clicked
        mOpenAppIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        mOpenAppIntent.setAction("android.intent.action.fcm_jump_to_playback");
        builder.setContentIntent(PendingIntent.getActivity(context, 0, mOpenAppIntent, 0));

        // Remove notification
        mRemoveNotifIntent = new Intent(REMOVE_NOTIFICATION);
        mRemoveNotifIntent.putExtra(PACKAGE_NAME, context.getApplicationInfo().packageName);
        builder.setDeleteIntent(PendingIntent.getBroadcast(context, 0, mRemoveNotifIntent, 
                                PendingIntent.FLAG_UPDATE_CURRENT));

        // Finally show/update the notification
        notification = createNotification(isPlaying, VISIBILITY_WHEN_PLAYING);
        mNotificationHelper.notify(NOTIFICATION_ID, notification);
    }

    public void hide() {
        NotificationManagerCompat.from(context).cancel("MusicControl", 0);
        mNotificationHelper.cancel(NOTIFICATION_ID);      
    }

    /**
     * Code taken from newer version of the support library located in PlaybackStateCompat.toKeyCode
     * Replace this to PlaybackStateCompat.toKeyCode when React Native updates the support library
     */
    private int toKeyCode(long action) {
        if(action == PlaybackStateCompat.ACTION_PLAY) {
            return KeyEvent.KEYCODE_MEDIA_PLAY;
        } else if(action == PlaybackStateCompat.ACTION_PAUSE) {
            return KeyEvent.KEYCODE_MEDIA_PAUSE;
        } else if(action == PlaybackStateCompat.ACTION_SKIP_TO_NEXT) {
            return KeyEvent.KEYCODE_MEDIA_NEXT;
        } else if(action == PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) {
            return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
        } else if(action == PlaybackStateCompat.ACTION_STOP) {
            return KeyEvent.KEYCODE_MEDIA_STOP;
        } else if(action == PlaybackStateCompat.ACTION_FAST_FORWARD) {
            return KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        } else if(action == PlaybackStateCompat.ACTION_REWIND) {
            return KeyEvent.KEYCODE_MEDIA_REWIND;
        } else if(action == PlaybackStateCompat.ACTION_PLAY_PAUSE) {
            return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        }
        return KeyEvent.KEYCODE_UNKNOWN;
    }

    private NotificationCompat.Action createAction(String iconName, String title, long mask, long action, NotificationCompat.Action oldAction) {
        if((mask & action) == 0) return null; // When this action is not enabled, return null
        if(oldAction != null) return oldAction; // If this action was already created, we won't create another instance

        // Finds the icon with the given name
        Resources r = context.getResources();
        String packageName = context.getPackageName();
        int icon = r.getIdentifier(iconName, "drawable", packageName);

        // Creates the intent based on the action
        int keyCode = toKeyCode(action);
        Intent intent = new Intent(MEDIA_BUTTON);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        intent.putExtra(PACKAGE_NAME, packageName);
        PendingIntent i = PendingIntent.getBroadcast(context, keyCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action(icon, title, i);
    }

    private String getTimeString() {
        long duration = mMediaData.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        if(duration == 0L) 
            return new String("");

        String timeString = "";
        long durationInSecs = duration / 1000;
        long hours = durationInSecs / 3600;
        long minutes = (durationInSecs % 3600) / 60;
        long seconds = durationInSecs % 60;

        String BULLET_UNICODE = " \u2022 ";
        if (hours > 0)
            timeString = BULLET_UNICODE + String.format("%02d:%02d:%02d", hours, minutes, seconds);
        else
            timeString = BULLET_UNICODE + String.format("%02d:%02d", minutes, seconds);

        return timeString;
    }

    public static class NotificationService extends Service {
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            return START_NOT_STICKY;
        }

        @Override
        public void onTaskRemoved(Intent rootIntent) {
            // Destroy the notification and sessions when the task is removed (closed, killed, etc)
            if(MusicControlModule.INSTANCE != null) {
                MusicControlModule.INSTANCE.destroy();
            }
            stopSelf(); // Stop the service as we won't need it anymore
        }

    }

}
