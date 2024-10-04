package io.invertase.firebase.messaging;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.facebook.react.HeadlessJsTaskService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import io.invertase.firebase.app.ReactNativeFirebaseApp;
import io.invertase.firebase.common.ReactNativeFirebaseEventEmitter;
import io.invertase.firebase.common.SharedUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ReactNativeFirebaseMessagingReceiver extends BroadcastReceiver {
  private static final String TAG = "RNFirebaseMsgReceiver";
  private static final String PREFS_NAME = "RNFirebaseMsgReceiverMessageId";

  static HashMap<String, RemoteMessage> notifications = new HashMap<>();

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "broadcast received for message ReactNativeFirebaseMessagingReceiver");
    if (ReactNativeFirebaseApp.getApplicationContext() == null) {
      ReactNativeFirebaseApp.setApplicationContext(context.getApplicationContext());
    }

    RemoteMessage remoteMessage = new RemoteMessage(intent.getExtras());
    ReactNativeFirebaseEventEmitter emitter = ReactNativeFirebaseEventEmitter.getSharedInstance();


    boolean aux = true;
    if (!remoteMessage.getData().containsKey("_sid")) {

      if(remoteMessage.getData().containsKey("_sid")) {

        if (remoteMessage.getData().get("_sid") == "SFMC") {
          aux = false;
        }
      }

      if(aux) {
        // Add a RemoteMessage if the message contains a notification payload
        if (remoteMessage.getNotification() != null) {
          notifications.put(remoteMessage.getMessageId(), remoteMessage);
          ReactNativeFirebaseMessagingStoreHelper.getInstance()
            .getMessagingStore()
            .storeFirebaseMessage(remoteMessage);
        }

        //  |-> ---------------------
        //      App in Foreground
        //   ------------------------
        if (SharedUtils.isAppInForeground(context)) {
          emitter.sendEvent(
            ReactNativeFirebaseMessagingSerializer.remoteMessageToEvent(remoteMessage, false));
          return;
        }

        //  |-> ---------------------
        //    App in Background/Quit
        //   ------------------------
        try {
          Intent backgroundIntent =
            new Intent(context, ReactNativeFirebaseMessagingHeadlessService.class);

          backgroundIntent.putExtra("message", remoteMessage);
          ComponentName name = context.startService(backgroundIntent);
          if (name != null) {
            HeadlessJsTaskService.acquireWakeLockNow(context);
          }
        } catch (IllegalStateException ex) {
          // By default, data only messages are "default" priority and cannot trigger Headless tasks
          Log.e(TAG, "Background messages only work if the message priority is set to 'high'", ex);
        }
      }
    }


    //  |-> ---------------------
    //    App in Quit / Salesforce
    //   ------------------------

    if (remoteMessage.getData().containsKey("_sid")) {

      Log.e("RNFirebaseMsgReceiver Salesforce: ", remoteMessage.getData().toString());


      Map<String, String> data = new HashMap<>();
      data.put("messageId", remoteMessage.getMessageId());
      data.put("title", remoteMessage.getData().get("title"));
      data.put("subtitle", remoteMessage.getData().get("subtitle"));
      data.put("alert", remoteMessage.getData().get("alert"));
      data.put("_sid", "FCM");
      data.put("_od", remoteMessage.getData().get("_od"));
      data.put("_r", remoteMessage.getData().get("_r"));
      data.put("_m", remoteMessage.getData().get("_m"));
      data.put("_h", remoteMessage.getData().get("_h"));
      data.put("priority", "high");
      Log.d(TAG, data.toString());

      RemoteMessage remoteMessageSF = new RemoteMessage.Builder(Objects.requireNonNull(remoteMessage.getMessageId()))
        .setData(data)
        .build();

      Log.e("RNFirebaseMsgReceiver Salesforce: ", "Send push");
      SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
      SharedPreferences.Editor editor = sharedPreferences.edit();

      editor.putString("messageId", remoteMessage.getMessageId());
      editor.apply();
      notifications.put(remoteMessage.getMessageId(), remoteMessage);

      ReactNativeFirebaseMessagingStoreHelper.getInstance()
        .getMessagingStore()
        .storeFirebaseMessage(remoteMessage);

      try {
        Intent backgroundIntent =
          new Intent(context, ReactNativeFirebaseMessagingHeadlessService.class);

        backgroundIntent.setPackage(context.getPackageName());
        backgroundIntent.putExtra("message", remoteMessageSF);
        ComponentName name = context.startService(backgroundIntent);
        if (name != null) {
          HeadlessJsTaskService.acquireWakeLockNow(context);
        }
      } catch (IllegalStateException ex) {
        // By default, data only messages are "default" priority and cannot trigger Headless tasks
        Log.e(TAG, "Background messages only work if the message priority is set to 'high'", ex);
      }



    }


  }
}
