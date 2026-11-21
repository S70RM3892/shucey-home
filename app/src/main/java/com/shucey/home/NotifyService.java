package com.shucey.home;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 通知のあるアプリを把握して、アイコンにバッジ（点）を付けるためのサービス。
 * 利用者が設定で「通知へのアクセス」を許可したときだけ動く。通知の中身は読まず、パッケージ名だけを使う。
 */
public class NotifyService extends NotificationListenerService {
    private static volatile Set<String> active = Collections.emptySet();
    private static volatile Runnable onChange;

    static boolean has(String pkg) { return active.contains(pkg); }
    static void setOnChange(Runnable r) { onChange = r; }

    private void refresh() {
        Set<String> s = new HashSet<>();
        try {
            for (StatusBarNotification n : getActiveNotifications()) {
                if (n.isClearable()) s.add(n.getPackageName()); // 常駐通知（再生中など）は数えない
            }
        } catch (RuntimeException ignored) {
            // 接続が切れた直後など
        }
        active = s;
        Runnable r = onChange;
        if (r != null) r.run();
    }

    @Override public void onListenerConnected() { refresh(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { refresh(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { refresh(); }

    @Override
    public void onListenerDisconnected() {
        active = Collections.emptySet();
        Runnable r = onChange;
        if (r != null) r.run();
    }
}
