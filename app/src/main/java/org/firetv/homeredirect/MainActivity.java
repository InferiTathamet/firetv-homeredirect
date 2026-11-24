package org.firetv.homeredirect;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.view.Gravity;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure service is started
        try {
            Intent serviceIntent = new Intent(this, RedirectService.class);
            startService(serviceIntent);
        } catch (Exception ignored) {}

        TextView tv = new TextView(this);
        tv.setText("Fire TV Home Redirect Service: ACTIVE\nTarget: Projectivy Launcher\nAuto-start on boot: ENABLED");
        tv.setTextSize(22);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(40, 40, 40, 40);
        setContentView(tv);
    }
}
