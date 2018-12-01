package nz.sheehan.smsproxy;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TimeUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;

import java.sql.Time;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;

public class MainActivity extends AppCompatActivity implements Observer {

    private static final int MY_PERMISSIONS_REQUEST_SEND_SMS = 1;
    private static final int MY_PERMISSIONS_REQUEST_RECEIVE_SMS = 2;
    private static final int MY_PERMISSIONS_REQUEST_INTERNET = 3;

    private static String TAG = "MainActivity";
    private static String SERVER = "http://192.168.1.6:4000";
    private static final int POLL_DELAY = 5 * 1000;
    private static final int TEARDOWN_DELAY = 1000;
    private static final int CONNECTION_DELAY = 1000;

    private boolean smsSendEnabled = false;
    private boolean smsReceiveEnabled = false;
    private boolean internetEnabled = false;
    private ConnectionState state = ConnectionState.Disconnected;

    private SmsProxy proxy;
    private Handler poller;

    private TextView textViewLog;
    private ScrollView scrollViewLog;

    private Button buttonGrantSmsSendPermission,
        buttonGrantSmsReceivePermission,
        buttonGrantInternetPermission,
        buttonConnectToggle;

    private EditText editTextServer;

    private Context self;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        self = this;
        poller = new Handler();

        ObservableObject.getInstance().addObserver(this);

        smsSendEnabled = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED;
        smsReceiveEnabled = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED;
        internetEnabled = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.INTERNET) ==
                PackageManager.PERMISSION_GRANTED;

        buttonGrantSmsSendPermission = findViewById(R.id.buttonGrantSMSSendPermission);
        buttonGrantSmsReceivePermission = findViewById(R.id.buttonGrantSMSReceivePermission);
        buttonGrantInternetPermission = findViewById(R.id.buttonGrantInternetPermission);
        buttonConnectToggle = findViewById(R.id.buttonConnectToggle);

        editTextServer = findViewById(R.id.editTextServer);
        textViewLog = findViewById(R.id.textViewLog);
        scrollViewLog = findViewById(R.id.scrollViewLog);

        final Runnable checkOutboxRunnable = new Runnable() {
            public Runnable thisRunnable = this;
            @Override
            public void run() {
                if (state == ConnectionState.Connected) {
                    proxy.getOutbox(new NetworkCallback() {
                        @Override
                        public void onSuccess(Object result) {
                            ArrayList<Message> messages = (ArrayList<Message>) result;
                            for (int i = 0; i < messages.size(); i++) {
                                final Message message = messages.get(i);
                                proxy.removeFromOutbox(message.getId(), new NetworkCallback() {
                                    @Override
                                    public void onSuccess(Object result) {
                                        sendSms(message.getNumber(), message.getText());
                                        log("TX >> ".concat(message.getNumber()));
                                    }

                                    @Override
                                    public void onFailure(Exception error) {
                                        logError(error);
                                    }
                                });
                            }

                            // run this again in 10 seconds time
                            if (state == ConnectionState.Connected) {
                                poller.postDelayed(thisRunnable, POLL_DELAY);
                            }

                        }

                        @Override
                        public void onFailure(Exception error) {
                            logError(error);

                            // run this again in 10 seconds time
                            if (state == ConnectionState.Connected) {
                                poller.postDelayed(thisRunnable, POLL_DELAY);
                            }
                        }
                    });
                }
            }
        };

        buttonGrantSmsSendPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForSmsSendPermission();
            }
        });
        buttonGrantSmsReceivePermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForSmsReceivePermission();
            }
        });
        buttonGrantInternetPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForInternetPermission();
            }
        });
        buttonConnectToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSoftKeyboard();
                if (state == ConnectionState.Disconnected) {
                    state = ConnectionState.Connecting;
                    proxy = new SmsProxy(editTextServer.getText().toString(), self);
                    logClear();
                    log("connecting...");
                    poller.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            proxy.getVersion(new NetworkCallback() {
                                @Override
                                public void onSuccess(Object result) {
                                    String version = (String)result;
                                    log(String.format("sms-router v%s", version));
                                    log("listening...");

                                    // delay for 1 second to print the previous messages
                                    poller.postDelayed(checkOutboxRunnable, 1000);
                                    state = ConnectionState.Connected;
                                    updateStatus();
                                }

                                @Override
                                public void onFailure(Exception error) {
                                    logError(error);
                                    state = ConnectionState.Disconnected;
                                    updateStatus();
                                }
                            });
                        }
                    }, CONNECTION_DELAY);

                } else {
                    state = ConnectionState.Disconnecting;
                    log("disconnecting...");
                    poller.removeCallbacks(checkOutboxRunnable);

                    // add this one callback to set the state to disconnected.
                    // it adds the illusion that we are actually doing some teardown
                    poller.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            state = ConnectionState.Disconnected;
                            log("disconnected");
                            updateStatus();
                        }
                    }, TEARDOWN_DELAY);
                }
                updateStatus();
            }
        });

        ((TextView)findViewById(R.id.editTextServer)).setText(SERVER);

        updateStatus();
    }

    public void hideSoftKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager)  getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(editTextServer.getWindowToken(), 0);
    }

    /**
     * Logs a simple message to the onscreen console. The message will have the timestamp prepended.
     * @param message The message to print to the console.
     */
    private void log(String message) {
        String dateString = new SimpleDateFormat("dd/MM/YY HH:mm:ss").format(new Date());
        textViewLog.append(String.format("%s - %s", dateString, message.concat("\r\n")));
        scrollViewLog.fullScroll(View.FOCUS_DOWN); // scroll to the bottom
    }

    /**
     * Logs an error to the onscreen console.
     * @param error The exception that occurred.
     */
    private void logError(Exception error) {
        log(String.format("error: %s", error.getMessage()));
    }

    /**
     * Clears the onscreen console of any text.
     */
    private void logClear() {
        textViewLog.setText("");
    }

    /**
     * Checks if the SMS receive permission has been granted and if not, prompts for it.
     */
    private void checkForSmsReceivePermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.RECEIVE_SMS) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECEIVE_SMS},
                    MY_PERMISSIONS_REQUEST_RECEIVE_SMS);
        } else {
            smsReceiveEnabled = true;
            updateStatus();
        }
    }

    /**
     * Checks if the internet permission has been granted and if not, prompts for it.
     */
    private void checkForInternetPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.INTERNET) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.INTERNET},
                    MY_PERMISSIONS_REQUEST_INTERNET);
        } else {
            internetEnabled = true;
            updateStatus();
        }
    }

    /**
     * Checks if the SMS send permission has been granted and if not, prompts for it.
     */
    private void checkForSmsSendPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    MY_PERMISSIONS_REQUEST_SEND_SMS);
        } else {
            // Permission already granted. Enable the message button.
            smsSendEnabled = true;
            updateStatus();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (permissions.length > 0 && grantResults.length > 0) {
            switch (requestCode) {
                case MY_PERMISSIONS_REQUEST_SEND_SMS: {
                    smsSendEnabled = (permissions[0].equalsIgnoreCase(Manifest.permission.SEND_SMS)
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED);
                }
                case MY_PERMISSIONS_REQUEST_RECEIVE_SMS: {
                    smsReceiveEnabled = (permissions[0].equalsIgnoreCase(Manifest.permission.RECEIVE_SMS)
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED);
                }
                case MY_PERMISSIONS_REQUEST_INTERNET: {
                    internetEnabled = (permissions[0].equalsIgnoreCase(Manifest.permission.INTERNET)
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED);
                }
            }
            updateStatus();
        }
    }

    /**
     * Updates all the buttons and text on the screen based on the permissions and state.
     */
    private void updateStatus() {
        buttonGrantSmsSendPermission.setEnabled(!smsSendEnabled);
        buttonGrantSmsReceivePermission.setEnabled(!smsReceiveEnabled);
        buttonGrantInternetPermission.setEnabled(!internetEnabled);

        buttonGrantSmsSendPermission.setText(smsSendEnabled
                ? R.string.button_sms_send_granted : R.string.button_sms_send_denied);
        buttonGrantSmsReceivePermission.setText(smsReceiveEnabled
                ? R.string.button_sms_receive_granted : R.string.button_sms_receive_denied);
        buttonGrantInternetPermission.setText(internetEnabled
                ? R.string.button_internet_granted : R.string.button_internet_denied);

        buttonConnectToggle.setEnabled(smsSendEnabled && smsReceiveEnabled && internetEnabled);
        editTextServer.setEnabled(smsSendEnabled && smsReceiveEnabled && internetEnabled && state == ConnectionState.Disconnected);

        String buttonText = "";
        boolean buttonEnabled = true;
        switch (state) {
            case Connected: buttonText = "Disconnect from server"; break;
            case Disconnected: buttonText = "Connect to server"; break;
            case Connecting: buttonText = "Connecting..."; buttonEnabled = false; break;
            case Disconnecting: buttonText = "Disconnecting..."; buttonEnabled = false; break;
        }

        buttonConnectToggle.setText(buttonText);
        buttonConnectToggle.setEnabled(buttonEnabled);
    }

    /**
     * Receives a new SMS message, if it is multipart, it will be reassembled.
     * @param observable
     * @param data
     */
    @Override
    public void update(Observable observable, Object data) {
        Intent intent = (Intent)data;
        Bundle bundle = intent.getExtras();
        SmsMessage[] messages;
        String format = bundle.getString("format");

        Object[] pdus = (Object[])bundle.get("pdus");

        if (pdus != null) {
            messages = new SmsMessage[pdus.length];
            for (int i = 0; i < pdus.length; i++) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    messages[i] = SmsMessage.createFromPdu((byte[])pdus[i], format);
                } else {
                    messages[i] = SmsMessage.createFromPdu((byte[])pdus[i]);
                }

            }

            SmsMessage sms = messages[0];
            String text = sms.getDisplayMessageBody();
            String number = sms.getDisplayOriginatingAddress();

            try {
                if (!(messages.length == 1 || sms.isReplace())) {
                    StringBuilder bodyText = new StringBuilder();
                    for (int i = 0; i < messages.length; i++) {
                        bodyText.append(messages[i].getMessageBody());
                    }
                    text = bodyText.toString();
                }
            } catch (Exception error) {
                logError(error);
                return;
            }

            receiveSms(number, text);

        }
    }

    private void receiveSms(final String number, String text) {

        if (state == ConnectionState.Connected) {
            proxy.addToInbox(number, text, new NetworkCallback() {
                @Override
                public void onSuccess(Object result) {
                    log(String.format("RX << %s", number));
                }

                @Override
                public void onFailure(Exception error) {
                    logError(error);
                }
            });
        }

    }

    private void sendSms(String number, String text) {
        checkForSmsSendPermission();
        if (smsSendEnabled) {
            SmsManager manager = SmsManager.getDefault();
            ArrayList<String> parts = manager.divideMessage(text);
            if (parts.size() == 1) {
                manager.sendTextMessage(number, null, text, null, null);
            } else {
                manager.sendMultipartTextMessage(number, null, parts, null, null);
            }
        }
    }
}
