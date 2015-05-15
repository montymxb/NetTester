package osuappclub.com.nettester;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

public class MainActivity extends Activity implements
        View.OnClickListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<MessageApi.SendMessageResult>,
        MessageApi.MessageListener {

    private GoogleApiClient mGoogleApiClient;

    private static final String TAG = "NET_TESTER_WEARABLE";

    private TextView titleView;
    private LinearLayout layout;
    private Button checkWifiButton;

    private Animation animation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        //fetch a reference to our view stub
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        //todo bad stuff here, not advisable
        final MainActivity c = this;

        mGoogleApiClient = new GoogleApiClient.Builder(getApplication())
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        //connect our api client
        mGoogleApiClient.connect();

        /* Set a layout inflated listener for when our view is pumped up */
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                //Get a reference to our button and set an on click listener to it
                checkWifiButton = (Button) stub.findViewById(R.id.button2);
                titleView = (TextView)stub.findViewById(R.id.current_status);
                layout = (LinearLayout)stub.findViewById(R.id.primary_layout);

                //load the animation
                animation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.fade_in);

                checkWifiButton.setOnClickListener(c);
            }
        });
    }

    private String buildPayload(byte[] bytes) throws UnsupportedEncodingException {
        return new String(bytes, "UTF-8");
    }

    /**
     * Retrieves the currently applicable wearable nodes (aka num devices connected). and returns them in an ArrayList
     * @return ArrayList of connected and available nodes
     */
    private ArrayList<String> getNodes() {
        ArrayList<String> results = new ArrayList<String>();
        NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
        for (Node node : nodes.getNodes()) {
            results.add(node.getId());
        }
        return results;
    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (messageEvent != null && messageEvent.getPath().equals("wifi_result")) {

                    byte[] data = messageEvent.getData();
                    if (data != null) {
                        try {
                            if (titleView != null) {

                                String payload = buildPayload(data);
                                String[] parts = payload.split(":");

                                int level = Integer.parseInt(parts[0]) * -1;
                                int color;
                                String updatedStatus;
                                if (level <= 70) {
                                    //Excellent Wifi
                                    updatedStatus = "Good";
                                    color = R.color.green;
                                } else if (level <= 74) {
                                    //Adequate wifi
                                    updatedStatus = "So so";
                                    color = R.color.yellow;
                                } else {
                                    //Poor wifi
                                    updatedStatus = "Poor";
                                    color = R.color.red;
                                }

                                if (parts[1].equals("1")) {
                                    updatedStatus += ", " + Integer.parseInt(parts[0]);
                                }

                                titleView.setText(updatedStatus);

                                titleView.startAnimation(animation);

                                layout.setBackgroundColor(getResources().getColor(color));
                            }
                        } catch (UnsupportedEncodingException e) {
                            Toast.makeText(MainActivity.this, "Try Again!", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else if (messageEvent != null && messageEvent.getPath().equals("push_notification")) {
                    int notificationId = 001;
                    // Build intent for notification content
                    Intent viewIntent = new Intent(getApplication(), MainActivity.class);
                    PendingIntent viewPendingIntent = PendingIntent.getActivity(getApplication(), 0, viewIntent, 0);

                    NotificationCompat.Builder notificationBuilder =
                            new NotificationCompat.Builder(getApplication())
                                    .setSmallIcon(R.mipmap.ic_launcher)
                                    .setContentTitle("NetTester")
                                    .setContentText("Swipe right to test.")
                                    .setContentIntent(viewPendingIntent);

                    // Get an instance of the NotificationManager service
                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplication());

                    // Build the notification and issues it with notification manager.
                    notificationManager.notify(notificationId, notificationBuilder.build());
                } else {
                    Toast.makeText(MainActivity.this, "empty...", Toast.LENGTH_SHORT).show();
                }
                checkWifiButton.setText(getString(R.string.test));
                checkWifiButton.setEnabled(true);
            }
        });
    }

    @Override
    public void onClick(View v) {
        // Send a message to our google api client
        checkWifiButton.setEnabled(false);
        titleView.setText("");
        checkWifiButton.setText(getString(R.string.working));

        new Thread(new Runnable() {
            @Override
            public void run() {
                sendMessageToPhone();

                try {
                    Thread.sleep(4000);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // We should have results after 4 seconds yo
                            if (!checkWifiButton.isEnabled()) {
                                checkWifiButton.setEnabled(true);
                                checkWifiButton.setText(R.string.test);

                                layout.setBackgroundColor(getResources().getColor(R.color.default_color));
                                titleView.setText(R.string.title_text);
                            }
                        }
                    });
                } catch (InterruptedException iex) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "ben what the hell", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Sends a message to the phone linked to this wearable device
     */
    private void sendMessageToPhone() {
        Wearable.MessageApi.sendMessage(mGoogleApiClient, getNodes().get(0), "test_wifi", new byte[0]).setResultCallback(this);
    }

    @Override
    public void onResult(MessageApi.SendMessageResult result) {
        if (!result.getStatus().isSuccess()) {
            Log.e(TAG, "Failed to send message with status code: " + result.getStatus().getStatusCode());
        } else {
            Log.i(TAG, "Succeed sending message to phone!");
        }
    }


    /**** GOOGLE API CALLBACKS ****/
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "onConnected: " + connectionHint);
        //Set the listener for our watch so it can get back data
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "onConnectionSuspended: " + cause);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.d(TAG, "onConnectionFailed: " + result);
    }
}
