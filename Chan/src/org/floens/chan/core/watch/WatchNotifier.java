package org.floens.chan.core.watch;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.service.WatchService;
import org.floens.chan.ui.activity.BoardActivity;
import org.floens.chan.utils.Logger;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.support.v4.app.NotificationCompat;

public class WatchNotifier {
    private final int NOTIFICATION_ID = 1;

    private final WatchService pinnedService;
    private final NotificationManager nm;

    public WatchNotifier(WatchService pinnedService) {
        this.pinnedService = pinnedService;
        nm = (NotificationManager) pinnedService.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void destroy() {
        nm.cancel(NOTIFICATION_ID);
    }

    public void update() {
        if (!WatchService.getActivityInForeground()) {
            prepareNotification();
        }
    }

    public void onForegroundChanged() {
        if (WatchService.getActivityInForeground()) {
            nm.cancel(NOTIFICATION_ID);
        } else {

        }
    }

    public void onPausePinsClicked() {
        nm.cancel(NOTIFICATION_ID);

        List<Pin> watchingPins = ChanApplication.getPinnedManager().getWatchingPins();
        for (Pin pin : watchingPins) {
            pin.watching = false;
        }

        ChanApplication.getPinnedManager().onPinsChanged();
    }

    private void prepareNotification() {
        List<Pin> watchingPins = ChanApplication.getPinnedManager().getWatchingPins();

        List<Pin> pins = new ArrayList<Pin>();
        int newPostsCount = 0;
        int newQuotesCount = 0;
        List<Post> newPosts = new ArrayList<Post>();
        boolean makeSound = false;
        boolean show = false;

        for (Pin pin : watchingPins) {
            PinWatcher watcher = pin.getPinWatcher();
            if (watcher == null)
                continue;

            boolean add = false;

            if (watcher.getWereNewPosts()) {
                newPostsCount += watcher.getNewPostsCount();
                newPosts.addAll(watcher.getNewPosts());
                show = true;
                add = true;
            }

            if (watcher.getWereNewQuotes()) {
                newQuotesCount += watcher.getNewQuoteCount();
                show = true;
                makeSound = true;
                add = true;
            }

            if (add) {
                pins.add(pin);
            }
        }

        if (show) {
            // "33 new posts, 3 quoting you"
            String title = newPostsCount + " new posts";
            if (newQuotesCount > 0) {
                title += ", " + newQuotesCount + " quoting you";
            }

            // "234 new posts in DPT"
            // "234 new posts in 5 threads"
            String descriptor;
            if (pins.size() == 1) {
                descriptor = pins.get(0).loadable.title;
            } else {
                descriptor = pins.size() + " threads";
            }

            String content = newPostsCount + " new posts in " + descriptor;

            List<CharSequence> lines = new ArrayList<CharSequence>();
            for (int i = newPosts.size() - 1; i >= 0; i--) {
                lines.add(newPosts.get(i).comment);
            }

            showNotification(content, title, content, Integer.toString(newPostsCount), lines, makeSound);
        }
    }

    @SuppressWarnings("deprecation")
    private void showNotification(String tickerText, String title, String content, String contentInfo,
            List<CharSequence> lines, boolean makeSound) {
        Intent intent = new Intent(pinnedService, BoardActivity.class);
        // intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
        // Intent.FLAG_ACTIVITY_SINGLE_TOP
        // | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(pinnedService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(pinnedService);
        builder.setContentIntent(pending);

        builder.setTicker(tickerText);
        builder.setContentTitle(title);
        builder.setContentText(content);
        builder.setContentInfo(contentInfo);
        builder.setSmallIcon(R.drawable.ic_stat_notify);

        Intent pauseWatching = new Intent(pinnedService, WatchService.class);
        pauseWatching.putExtra("pause_pins", true);

        PendingIntent pauseWatchingPending = PendingIntent.getService(pinnedService, 0, pauseWatching,
                PendingIntent.FLAG_UPDATE_CURRENT);

        builder.addAction(R.drawable.ic_action_pause, pinnedService.getString(R.string.watch_pause_pins),
                pauseWatchingPending);

        if (makeSound) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        }

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (CharSequence line : lines.subList(Math.max(0, lines.size() - 10), lines.size())) {
            style.addLine(line);
        }
        style.setBigContentTitle(title);
        // style.setSummaryText(content);

        builder.setStyle(style);

        Logger.test("SHOWING NOTIFICATION!");
        nm.notify(NOTIFICATION_ID, builder.getNotification());
    }
}