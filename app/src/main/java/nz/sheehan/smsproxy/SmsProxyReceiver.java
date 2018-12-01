package nz.sheehan.smsproxy;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsProxyReceiver extends BroadcastReceiver {

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onReceive(Context context, Intent intent) {
        ObservableObject.getInstance().updateValue(intent);

    }
}
