package br.com.thiengo.thiengocalopsitafbexample;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import com.twitter.sdk.android.Twitter;
import com.twitter.sdk.android.core.TwitterAuthConfig;

import io.fabric.sdk.android.Fabric;


public class CustomApplication extends Application {

    // Note: Your consumer key and secret should be obfuscated in your source code before shipping.
    private static final String TWITTER_KEY = "";
    private static final String TWITTER_SECRET = "";

    @Override
    public void onCreate() {
        super.onCreate();
        TwitterAuthConfig authConfig = new TwitterAuthConfig(TWITTER_KEY, TWITTER_SECRET);
        Fabric.with(this, new Twitter(authConfig));

        /*SharedPreferences sp = getSharedPreferences("SP_TEST", MODE_PRIVATE);
        int value = sp.getInt("count", 0);

        Log.i("LOG", "Value: "+value);

        SharedPreferences.Editor editor = sp.edit();
        editor.putInt("count", value + 1 );
        editor.apply();*/
    }
}