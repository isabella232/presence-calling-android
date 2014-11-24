package com.sinch.userpresencecalling;

import android.media.AudioManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.pubnub.api.*;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.Sinch;
import com.sinch.android.rtc.SinchClient;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallClient;
import com.sinch.android.rtc.calling.CallClientListener;
import com.sinch.android.rtc.calling.CallListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity {

    static final String JSON_EXCEPTION = "JSON Exception";
    static final String PUBNUB_EXCEPTION = "Pubnub Exception";


    private Pubnub pubnub;
    private ArrayList users;
    private JSONArray hereNowUuids;
    private SinchClient sinchClient;
    private Button pickupButton;
    private Button hangupButton;
    private Call currentCall = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String username = getIntent().getStringExtra("username");
        pubnub = new Pubnub("pub", "sub");
        pubnub.setUUID(username);

        sinchClient = Sinch.getSinchClientBuilder()
            .context(this)
            .userId(username)
            .applicationKey("key")
            .applicationSecret("secret")
            .environmentHost("sandbox.sinch.com")
            .build();

        sinchClient.setSupportCalling(true);
        sinchClient.startListeningOnActiveConnection();
        sinchClient.start();
        sinchClient.getCallClient().addCallClientListener(new SinchCallClientListener());

        pickupButton = (Button) findViewById(R.id.pickupButton);
        hangupButton = (Button) findViewById(R.id.hangupButton);
    }

    @Override
    public void onResume() {
        super.onResume();

        users = new ArrayList<String>();
        final ListView usersListView = (ListView) findViewById(R.id.usersListView);
        final ArrayAdapter usersArrayAdapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.user_list_item, users);
        usersListView.setAdapter(usersArrayAdapter);

        pickupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentCall != null) {
                    currentCall.answer();
                }
            }
        });

        hangupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentCall != null) {
                    currentCall.hangup();
                }
            }
        });

        usersListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int i, long id) {
                if (currentCall == null) {
                    currentCall = sinchClient.getCallClient().callUser(users.get(i).toString());
                    currentCall.addCallListener(new SinchCallListener());
                    hangupButton.setText("Hang Up Call with " + users.get(i));
                } else {
                    Toast.makeText(getApplicationContext(),
                        "Can't call " + users.get(i) + " while on another call.",
                        Toast.LENGTH_SHORT).show();
                }
            }
        });

        pubnub.hereNow("calling_channel", new Callback() {
            public void successCallback(String channel, Object response) {
                try {
                    JSONObject hereNowResponse = new JSONObject(response.toString());
                    hereNowUuids = new JSONArray(hereNowResponse.get("uuids").toString());
                } catch (JSONException e) {
                    Log.d(JSON_EXCEPTION, e.toString());
                }

                String currentUuid;
                for (int i = 0; i < hereNowUuids.length(); i++) {
                    try {
                        currentUuid = hereNowUuids.get(i).toString();
                        if (!currentUuid.equals(pubnub.getUUID())) {
                            users.add(currentUuid);
                            MainActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    usersArrayAdapter.notifyDataSetChanged();
                                }
                            });
                        }
                    } catch (JSONException e) {
                        Log.d(JSON_EXCEPTION, e.toString());
                    }
                }
            }

            public void errorCallback(String channel, PubnubError e) {
                Log.d("PubnubError", e.toString());
            }
        });

        try {
            pubnub.subscribe("calling_channel", new Callback() {
            });
        } catch (PubnubException e) {
            Log.d(PUBNUB_EXCEPTION, e.toString());
        }

        try {
            pubnub.presence("calling_channel", new Callback() {

                @Override
                public void successCallback(String channel, Object message) {
                    try {
                        JSONObject jsonMessage = new JSONObject(message.toString());
                        String action = jsonMessage.get("action").toString();
                        String uuid = jsonMessage.get("uuid").toString();

                        if (!uuid.equals(pubnub.getUUID())) {
                            if (action.equals("join")) {
                                users.add(uuid);
                                MainActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        usersArrayAdapter.notifyDataSetChanged();
                                    }
                                });
                            } else if (action.equals("leave")) {
                                for (int i = 0; i < users.size(); i++) {
                                    if (users.get(i).equals(uuid)) {
                                        users.remove(i);
                                        MainActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                usersArrayAdapter.notifyDataSetChanged();
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    } catch (JSONException e) {
                        Log.d(JSON_EXCEPTION, e.toString());
                    }
                }
            });
        } catch (PubnubException e) {
            Log.d(PUBNUB_EXCEPTION, e.toString());
        }
    }

    private class SinchCallListener implements CallListener {
        @Override
        public void onCallEnded(Call call) {
            currentCall = null;
            hangupButton.setText("No call to hang up right now...");
            pickupButton.setText("No call to pick up right now...");
            setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
        }

        @Override
        public void onCallEstablished(Call call) {
            hangupButton.setText("Hang up call with " + call.getRemoteUserId());
            pickupButton.setText("No call to pick up right now...");
            setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        }

        @Override
        public void onCallProgressing(Call call) {
            hangupButton.setText("Ringing");
        }

        @Override
        public void onShouldSendPushNotification(Call call, List<PushPair> pushPairs) {
        }
    }

    private class SinchCallClientListener implements CallClientListener {
        @Override
        public void onIncomingCall(CallClient callClient, Call incomingCall) {
            if (currentCall == null) {
                currentCall = incomingCall;
                currentCall.addCallListener(new SinchCallListener());
                pickupButton.setText("Pick up call from " + incomingCall.getRemoteUserId());
                hangupButton.setText("Ignore call from " + incomingCall.getRemoteUserId());
            }
        }
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    public void onPause() {
        super.onPause();
        pubnub.unsubscribe("calling_channel");
    }


}
