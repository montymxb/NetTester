package osuappclub.com.nettester;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
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
import java.util.List;


public class MainActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener, ResultCallback<MessageApi.SendMessageResult>, View.OnClickListener {

    private static final String TAG = "NET_TESTER_PHONE";
    private GoogleApiClient mGoogleApiClient;
    private TextView titleView;
    private Switch aSwitch;
    private boolean hasDevices = false;
    private Button interactionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        titleView = (TextView)findViewById(R.id.title_view);
        aSwitch = (Switch)findViewById(R.id.switch1);

        interactionButton = (Button)findViewById(R.id.wake_button);
        interactionButton.setOnClickListener(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        //Based on our response show something new
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (messageEvent.getPath().equals("test_wifi")) {
                    performWifiAnalysis();
                }
            }
        });
    }

    private void performWifiAnalysis() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

        final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiManager.startScan();

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                List<ScanResult> scanResultList = wifiManager.getScanResults();

                int sum = 0;

                for (ScanResult sr : scanResultList) {
                    sum += sr.level;
                }

                final int avg = sum / scanResultList.size();

                //Toast.makeText(MainActivity.this, "Result: " + avg, Toast.LENGTH_SHORT).show();
                titleView.setText(avg+"");

                final boolean isChecked = aSwitch.isChecked();

                if(hasDevices) {
                    /* If we have a werable use let's talk with it */
                    new Thread(new Runnable() {
                        @Override
                        public void run() {

                            String state;
                            if (isChecked) {
                                state = "1";
                            } else {
                                state = "0";
                            }

                            try {
                                String p1 = new String(getPayload(avg), "utf-8");
                                String payload = p1 + ":" + state;

                                Wearable.MessageApi.sendMessage(
                                        mGoogleApiClient, getNodes().get(0), "wifi_result", payload.getBytes())
                                        .setResultCallback(MainActivity.this);
                            } catch (UnsupportedEncodingException uee) {
                                uee.printStackTrace();
                            }
                        }
                    }).start();
                } else {
                    /* Let's just work directly with our phone */
                    interactionButton.setText("Scan");
                }

                unregisterReceiver(this);
            }
        }, filter);
    }

    private byte[] getPayload(int levelAvg) {
        return (levelAvg + "").getBytes();
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
    public void onResult(MessageApi.SendMessageResult result) {
        if (!result.getStatus().isSuccess()) {
            Log.e(TAG, "Failed to send message with status code: " + result.getStatus().getStatusCode());
        } else {
            Log.i(TAG, "Succeed sending message to watch!");
        }
    }

    @Override
    public void onClick(View v) {
        if(hasDevices) {
            /* We wake the wearable when we have it */
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Wearable.MessageApi.sendMessage(mGoogleApiClient, getNodes().get(0), "push_notification", new byte[0]).setResultCallback(MainActivity.this);
                }
            }).start();
        } else {
            /* Perform wifi analysis on the phone directly */
            interactionButton.setText("...");
            performWifiAnalysis();
        }
    }

    /**** GOOGLE API CLIENT CALLBACKS ****/

    @Override
    public void onConnected(Bundle connectionHint) {
        Toast.makeText(this, "Connected!", Toast.LENGTH_LONG).show();
        //Register for callback
        Wearable.MessageApi.addListener(mGoogleApiClient, this);

        int deviceCount = getNodes().size();
        /* If NO Devices (Wearables) found then make sure to set flag */
        if(deviceCount == 0) {
            Log.i("OSU", "No devices found!");
            hasDevices = false;
            interactionButton.setText("Scan");
        } else {
            hasDevices = true;
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.d(TAG, "onConnectionFailed: " + result);
        /* On failure just make our device a scanner on it's own */
        hasDevices = false;
        interactionButton.setText("Scan");
    }


    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "onConnectionSuspended: " + cause);
    }
}
