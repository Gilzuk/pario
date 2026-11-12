package com.example.pairo;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.window.OnBackInvokedDispatcher;

public class MainActivity extends Activity {
    private GameView game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        drawEdgeToEdge(getWindow());
        game = new GameView(this);
        game.setKeepScreenOn(true);
        setContentView(game);

        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+ (and required for predictive back on Android 16).
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> { if (!game.goBack()) finish(); });
        }
    }

    /** Pre-Android 13 back handling. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!game.goBack()) super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        game.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        game.pause();
    }

    @Override
    protected void onDestroy() {
        game.release();
        super.onDestroy();
    }

    /** Lets the gradient run behind the system bars on every Android version. */
    @SuppressWarnings("deprecation")
    private static void drawEdgeToEdge(Window window) {
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
