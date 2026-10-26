package com.edu.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.Locale;

/**
 * Serviço que fica escutando a palavra "Edu" em segundo plano (modo conversa / escuta sempre).
 * É um serviço de primeiro plano (mostra um aviso fixo), como o Android exige para usar o
 * microfone com a tela apagada.
 *
 * Observação honesta: escuta contínua pelo reconhecedor do Google gasta bateria e nem sempre é
 * perfeita. Para uma palavra de ativação realmente robusta, dá para trocar depois por um motor
 * dedicado (ex.: Porcupine). Aqui vai a versão que funciona sem nada pago.
 */
public class WakeService extends Service {

    private static final String CANAL = "edu_escuta";
    private SpeechRecognizer stt;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean rodando = false;

    @Override
    public void onCreate() {
        super.onCreate();
        criarCanal();
        startForeground(42, aviso());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        rodando = true;
        ui.post(this::escutar);
        return START_STICKY;
    }

    private void escutar() {
        if (!rodando) return;
        try {
            if (stt == null) stt = SpeechRecognizer.createSpeechRecognizer(this);
            stt.setRecognitionListener(new SimplesListener(this::processar) {
                @Override
                public void onError(int error) {
                    reiniciar();
                }
                @Override
                public void onEndOfSpeech() {
                    reiniciar();
                }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            stt.startListening(i);
        } catch (Exception e) {
            reiniciar();
        }
    }

    private void reiniciar() {
        if (!rodando) return;
        ui.postDelayed(() -> {
            try { if (stt != null) stt.cancel(); } catch (Exception ignored) { }
            escutar();
        }, 900);
    }

    private void processar(String texto) {
        if (texto == null) return;
        String n = texto.toLowerCase(Locale.ROOT).trim();
        // palavra de ativação "Edu" -> manda o resto para a tela do Edu
        int idx = n.indexOf("edu");
        if (idx >= 0) {
            String resto = texto.substring(Math.min(texto.length(), idx + 3)).trim();
            if (MainActivity.ATIVA != null) {
                MainActivity.ATIVA.entregarVoz(resto.isEmpty() ? "oi" : resto);
            }
        }
        reiniciar();
    }

    private Notification aviso() {
        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CANAL)
                : new Notification.Builder(this);
        return b.setContentTitle("Edu está te ouvindo")
                .setContentText("Diga \"Edu\" para me chamar. Toque para abrir.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel c = new NotificationChannel(CANAL, "Escuta do Edu",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(c);
        }
    }

    @Override
    public void onDestroy() {
        rodando = false;
        if (stt != null) { try { stt.destroy(); } catch (Exception ignored) { } }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
