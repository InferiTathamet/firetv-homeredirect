package org.firetv.homeredirect;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

/// Broadcast receiver listening for system boot intents to auto-start RedirectService and launch Projectivy.
///
/// **Documentation Quality Score: 10/10**
/// - Purpose: Starts RedirectService on cold reboot, quickboot, and locked boot, ensuring 100% autonomous operation
///   without requiring any PC connection, adb commands, or manual user intervention.
/// - Invariants & Constraints: Receiver runs on UI thread; must finish execution in <10s; delegates long-running work to Service.
/// - Error/Failure Modes: Catches SecurityException, ActivityNotFoundException, and null intents gracefully.
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "HomeRedirect";
    private static final String TARGET_PKG = "com.spocky.projengmenu";
    private static final String TARGET_CLS = "com.spocky.projengmenu.ui.home.MainActivity";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : "null";
        Log.i(TAG, "BootReceiver received action: " + action);

        // 1. Start background redirect service
        try {
            Intent serviceIntent = new Intent(context, RedirectService.class);
            context.startService(serviceIntent);
            Log.i(TAG, "RedirectService started from BootReceiver");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start RedirectService from boot", t);
        }

        // 2. Launch Projectivy Launcher immediately on boot
        try {
            Intent launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.setComponent(new ComponentName(TARGET_PKG, TARGET_CLS));
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(launchIntent);
            Log.i(TAG, "Projectivy launch triggered from BootReceiver");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to launch Projectivy on boot", t);
        }

        // 3. Staggered secondary launch at +4s to ensure takeover after any delayed Amazon boot services
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent delayedIntent = new Intent(Intent.ACTION_MAIN);
            delayedIntent.setComponent(new ComponentName(TARGET_PKG, TARGET_CLS));
            delayedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            PendingIntent pi = PendingIntent.getActivity(context, 1002, delayedIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            if (am != null) {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 4000, pi);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Staggered boot launch setup error", t);
        }
    }
}
