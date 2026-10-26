package com.edu.app;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * Mini Edu no Android: quando o app sai da frente, mostra só a MINIATURA do avatar numa bolha
 * flutuante por cima de tudo. Esperto:
 *   - NÃO aparece com a tela apagada ou bloqueada (fica quietinho);
 *   - aparece de novo quando você liga/desbloqueia a tela e ainda está usando outra coisa;
 *   - tocar na bolha traz o Edu de volta; dá para arrastar.
 */
public class BolhaService extends Service {

    private WindowManager wm;
    private ImageView bolha;
    private WindowManager.LayoutParams lp;
    private boolean visivel = false;
    private BroadcastReceiver tela;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        bolha = new ImageView(this);
        bolha.setImageResource(R.mipmap.ic_launcher);

        int tipo = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(dp(68), dp(68), tipo,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(16);
        lp.y = dp(120);

        bolha.setOnTouchListener(new View.OnTouchListener() {
            float ix, iy; int px, py; boolean moveu;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ix = e.getRawX(); iy = e.getRawY(); px = lp.x; py = lp.y; moveu = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - ix), dy = (int) (e.getRawY() - iy);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moveu = true;
                        lp.x = px + dx; lp.y = py + dy;
                        if (visivel) wm.updateViewLayout(bolha, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moveu) abrirApp();
                        return true;
                }
                return false;
            }
        });

        // escuta a tela ligar/desligar/desbloquear
        tela = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                    esconder();
                } else if (Intent.ACTION_SCREEN_ON.equals(a) || Intent.ACTION_USER_PRESENT.equals(a)) {
                    if (podeMostrar()) mostrar();
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(tela, f);

        if (podeMostrar()) mostrar();   // só aparece se a tela estiver ligada e desbloqueada
    }

    private boolean podeMostrar() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            boolean ligada = (pm != null) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH
                    ? pm.isInteractive() : pm.isScreenOn());
            boolean bloqueada = (km != null) && km.isKeyguardLocked();
            return ligada && !bloqueada;
        } catch (Exception e) {
            return true;
        }
    }

    private void mostrar() {
        if (visivel || bolha == null) return;
        try { wm.addView(bolha, lp); visivel = true; } catch (Exception ignored) { }
    }

    private void esconder() {
        if (!visivel || bolha == null) return;
        try { wm.removeView(bolha); } catch (Exception ignored) { }
        visivel = false;
    }

    private void abrirApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        stopSelf();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        if (tela != null) {
            try { unregisterReceiver(tela); } catch (Exception ignored) { }
        }
        esconder();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
