#Calling with User Presence

In this tutorial, you will build a calling app that displays a real-time list of online users, and only allows calls to and from those online users. You will use Sinch app-to-app calling to make the calls, and Pubnub presence detection to create a real-time list of online users. Your finished product will look similar to this:

<img src="images/app-flow.png" style="width:100%;"/>

You can find the finished code for this tutorial at [github.com/sinch/presence-calling-android](http://www.github.com/sinch/presence-calling-android).

Please keep in mind that this is not a production ready app! In order to keep this tutorial fairly concise, there will surely be some user experience flaws pertaining to edge cases.

##Project Setup and User Login

First, create a new project (this tutorial is built for and tested in Android Studio) and name the first activity **LoginActivity**. The interface for this activity should be an EditText and a login button. You won't actually be authenticating a user, but you will need to associate the current user with the username they enter. Here is the code for the login screen:

    <EditText
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:id="@+id/usernameEditText"
        android:layout_alignParentTop="true"
        android:layout_centerHorizontal="true"/>

    <Button
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Login"
        android:id="@+id/loginButton"
        android:layout_below="@+id/usernameEditText"
        android:layout_centerHorizontal="true"/>
        
When the login button is clicked, you will start the next activity and pass along the entered username as a string extra. You will also need to create the next activity, **MainActivity**:

    //in OnCreate
    findViewById(R.id.loginButton).setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            String username = ((EditText) findViewById(R.id.usernameEditText)).getText().toString();
            //make sure the username isn't blank!
            if (username.length() == 0) {
                Toast.makeText(getApplicationContext(), "Enter a username.", Toast.LENGTH_SHORT);
            } else {
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                intent.putExtra("username", username);
                startActivity(intent);
            }
        }
    });

##Set up Pubnub

1. Sign up for an account at [pubnub.com/get-started](http://www.pubnub.com/get-started/).
2. Once you're logged in to the admin console, add the presence feature.
<img src="images/add-presence.png" style="width:100%;" />
3. Take note of your publish and subscribe keys. You will need these in the next section.
4. Download the Android SDK by clicking this link [github.com/pubnub/java/archive/master.zip](https://github.com/pubnub/java/archive/master.zip)
5. Add **Pubnub-Android-3.5.6.jar** and **bcprov-jdk15on-1.47.jar** to your libs folder and right-click "add as library"
6. Add `<uses-permission android:name="android.permission.INTERNET"/>` to **AndroidManifest.xml**

##List of Online Users

Start by creating the two layouts to display a list of users. In **activity_main.xml**, make a ListView: 

    <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="16dp">

        <ListView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:id="@+id/usersListView" />

    </RelativeLayout>
    
Each item in the list will use the layout in **user_list_item.xml**. This will be very simple - just the text of the username:

    <TextView xmlns:android="http://schemas.android.com/apk/res/android"
          android:id="@+id/userListItem"
          android:padding="16dp"
          android:layout_width="fill_parent"
          android:layout_height="fill_parent"
          android:textSize="20sp" />
          
Using Pubnub, each user can subscribe to the channel **calling_channel**, and listen for presence events on that channel (users joining and leaving). In this app, you will want users to subscribe to the channel when they are active in the app, and leave the channel when they put the app in the background or kill the app. In MainActivity onCreate, get the current username from the intent and create a new Pubnub object:

    //declare globally within the MainActivity
    private Pubnub pubnub;
    
    //in onCreate
    String username = getIntent().getStringExtra("username");
    pubnub = new Pubnub("your-publish-key", "your-subscribe-key");
    pubnub.setUUID(username);
    
Now, I'll show you the code to populate a list of active users, subscribe to calling_channel, start listening for presence events on calling_channel, and update the view as users join and leave calling_channel:

    //declare variables globally within the Activity
    private ArrayList users;
    private JSONArray hereNowUuids;
    
    //in onResume
    super.onResume();

    //setup to add & remove users from the ListView
    users = new ArrayList<String>();
    final ListView usersListView = (ListView)findViewById(R.id.usersListView);
    final ArrayAdapter usersArrayAdapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.user_list_item, users);
    usersListView.setAdapter(usersArrayAdapter);

    //first, figure out who is here now
    pubnub.hereNow("calling_channel", new Callback() {
        public void successCallback(String channel, Object response) {
            try {
                JSONObject hereNowResponse = new JSONObject(response.toString());
                hereNowUuids = new JSONArray(hereNowResponse.get("uuids").toString());
            } catch (JSONException e) {
                Log.d("JSONException",e.toString());
            }

            //add everyone but yourself to the ListView
            String currentUuid;
            for (int i=0;i<hereNowUuids.length();i++){
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
                    Log.d("JSONException",e.toString());
                }
            }
        }

        public void errorCallback(String channel, PubnubError error) {
            Log.d("PubnubError", error.toString());
        }
    });

    //subscribe to calling_channel
    //empty callback for the sake of simplicity in this tutorial
    try {
        pubnub.subscribe("calling_channel", new Callback() {
        });
    } catch (PubnubException e) {
        Log.d("PubnubException",e.toString());
    }

    //start listening for users to join & leave the channel
    try {
        pubnub.presence("calling_channel", new Callback() {

            @Override
            public void successCallback(String channel, Object message) {
                try {
                    JSONObject jsonMessage = new JSONObject(message.toString());
                    String action = jsonMessage.get("action").toString();
                    String uuid = jsonMessage.get("uuid").toString();

                    if (!uuid.equals(pubnub.getUUID())) {
                        //if a user subscribes to  calling_channel, add them to the list
                        if (action.equals("join")) {
                            users.add(uuid);
                            MainActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    usersArrayAdapter.notifyDataSetChanged();
                                }
                            });
                        //if a user unsubscribes from calling_channel, remove them from the list
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
                    Log.d("JSONException", e.toString());
                }
            }
        });
    } catch (PubnubException e) {
        Log.d("PubnubException",e.toString());
    }

In addition, you will want to unsubscribe users from the channel when they kill the app or put it in the background:

    @Override
    public void onPause() {
        super.onPause();
        pubnub.unsubscribe("calling_channel");
    }
    
And lastly, override the method **onBackPressed** so that it doesn't return users to the login screen:

    
    @Override
    public void onBackPressed() {
    }
    
Now, try running the app on two different devices and log in as two different users. When you log in on the second device, each user should see the other in the list of online users!

##Set up Sinch

1. Sign up for a Sinch developer account at [sinch.com/dashboard/#/signup](https://www.sinch.com/dashboard/#/signup).
2. Create a new app like so in the Sinch developer dashboard.
<img src="images/new-sinch-app.png" style="width:100%;" />
3. Take note of your app key and secret. You will need these in the next section.
4. Download the Sinch Android SDK from [sinch.com/downloads](https://www.sinch.com/downloads/).
5. Follow these steps for adding Sinch to your project:

**Android Studio**   
- Copy the two jar files into your project's libs folder    
- Right click the jar files and select "Add as library"    
- Create a new folder under src/main, and name it jniLibs    
- Move the armeabi and armeabi-v7a folders into the jniLibs folder you just created    

**Eclipse**    
- Copy the entire libs folder into your project's root directory.

##Make and Receive Calls

Users will stay in the main activity while making calls. First, add a **Hang up** and **Pick up** button to **activity_main.xml**:

    <Button
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:id="@+id/pickupButton"
        android:text="No call to pick up right now..."/>

    <Button
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:id="@+id/hangupButton"
        android:layout_below="@id/pickupButton"
        android:text="No call to hang up right now..."/>
        
You also need to add `android:layout_below="@id/hangupButton"` to the ListView to put the list of online users below the buttons.

Next, Sinch requires access to the microphone, as well as a few other permissions. Add the following to **AndroidManifest.xml**:

    <uses-feature
        android:name="android.hardware.microphone"
        android:required="false"/>

    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
    <uses-permission android:name="android.permission.READ_PHONE_STATE"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

(Sinch also requires internet access, but you added that earlier when setting up Pubnub.)

In **MainActivity** onCreate, start an instance of the Sinch client using your app key and secret from the Sinch dashboard. ThIn addition, define the hangup and pickup buttons:

    //declare globally within MainActivity
    private SinchClient sinchClient;
    private Button pickupButton;
    private Button hangupButton;
    private Call currentCall;

    //in onCreate
    sinchClient = Sinch.getSinchClientBuilder()
        .context(this)
        .userId(username)
        .applicationKey("your-sinch-key")
        .applicationSecret("your-sinch-secret")
        .environmentHost("sandbox.sinch.com")
        .build();

    //use the calling feature
    sinchClient.setSupportCalling(true);
    //start listening for incoming calls
    sinchClient.startListeningOnActiveConnection();
    sinchClient.start();

    pickupButton = (Button) findViewById(R.id.pickupButton);
    hangupButton = (Button) findViewById(R.id.hangupButton);
    
Next, you need to define listeners for the Sinch call client, as well as for individual calls. Do this inside of the MainActivity class:

    //you'll attach an instance of this listener to individual calls
    private class SinchCallListener implements CallListener {
        //when the call ends for any reason
        @Override
        public void onCallEnded(Call call) {
            //no current call
            currentCall = null;
            hangupButton.setText("No call to hang up right now...");
            pickupButton.setText("No call to pick up right now...");
            //volume buttons go back to controlling ringtone volume
            setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
        }

        //recipient picks up the call
        @Override
        public void onCallEstablished(Call call) {
            hangupButton.setText("Hang up call with " + call.getRemoteUserId());
            pickupButton.setText("No call to pick up right now...");
            //ringtone volume buttons now control the speaker volume
            setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        }

        //when call is "ringing"
        @Override
        public void onCallProgressing(Call call) {
            hangupButton.setText("Ringing");
        }

        //don't worry about this for now
        @Override
        public void onShouldSendPushNotification(Call call, List<PushPair> pushPairs) {}
    }

    //you'll attach an instance of this to the Sinch client
    private class SinchCallClientListener implements CallClientListener {
        //when there is an incoming call
        @Override
        public void onIncomingCall(CallClient callClient, Call incomingCall) {
            //only react if there is no current call
            if (currentCall == null) {
                currentCall = incomingCall;
                currentCall.addCallListener(new SinchCallListener());
                pickupButton.setText("Pick up call from " + incomingCall.getRemoteUserId());
                hangupButton.setText("Ignore call from " + incomingCall.getRemoteUserId());
            }
        }
    }
    
Now, go back to where you start the Sinch client, and right after you start it, add an instance of SinchCallClientListener. Now, your users will be notified when they have an incoming call:

    sinchClient.getCallClient().addCallClientListener(new SinchCallClientListener());
    
Next, make the pickup and hangup buttons functional in **MainActivity** onResume:

    //if there is an incoming call, answer it and stop listening for more incoming calls
    pickupButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (currentCall != null) {
                currentCall.answer();
            }
        }
    });

    //if there is a current call, hang it up
    hangupButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (currentCall != null) {
                currentCall.hangup();
            }
        }
    });
    
Now that you're prepared to accept incoming calls, you should let your users actually make calls!

    //in onResume
    usersListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int i, long id) {
            if (currentCall == null) {
                //call the user that was clicked
                currentCall = sinchClient.getCallClient().callUser(users.get(i).toString());
                //add a listener to the call
                currentCall.addCallListener(new SinchCallListener());
                //change hangup button text
                hangupButton.setText("Hang Up Call with " + users.get(i));
            } else {
                //clicking names won't do anything if there is a current call
                Toast.makeText(getApplicationContext(),
                    "Can't call " + users.get(i) + " while on another call.",
                    Toast.LENGTH_SHORT).show();
            }
        }
    });

And there you have it! You can now launch the app on 2 different devices or emulators, login as two different users, and call each other. Try playing around with killing one app and restarting it as another user - it's cool to see the "online" list update in real time on the other device. 

If you want to compare your code to our finished app, don't forget to check the [Github repo](http://www.github.com/sinch/presence-calling-android). You can also reach us at [dev@sinch.com](mailto:dev@sinch.com) with questions!










