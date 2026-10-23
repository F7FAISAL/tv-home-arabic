package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

/**
 * «تحديث الأداة»'s two callbacks. From Android's installer (AppUpdater.install): shows its confirmation, or says why it
 * stopped. MY_PACKAGE_REPLACED, after any update of this app: puts the remote's key services back if Android dropped
 * them, and brings Home back over whatever task Android brought forward (AppUpdater.afterUpdate).
 */
public final class UpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            PendingResult done = goAsync();
            new Thread(() -> {
                try {
                    Context app = ctx.getApplicationContext();
                    AppUpdater.afterUpdate(app);
                } finally {
                    done.finish();
                }
            }).start();
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        if (status != PackageInstaller.STATUS_SUCCESS) {
            String why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Log.w("TvHome.Update", "install ended: " + status + " " + why);
            Toast.makeText(ctx, ctx.getString(R.string.update_failed_install), Toast.LENGTH_LONG).show();
        }
    }
}
