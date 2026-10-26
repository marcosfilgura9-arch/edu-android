package com.edu.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Edu no celular, como app nativo. Mostra o Mini Edu (a mesma tela de sempre) dentro de um
 * WebView e dá a ele os "poderes" que o navegador não tinha: falar e ouvir pelo Android,
 * atender e desligar ligação, ver bateria e memória de verdade, e abrir outros apps.
 */
public class MainActivity extends Activity {

    private WebView web;
    private android.speech.tts.TextToSpeech tts;
    private android.speech.SpeechRecognizer stt;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /** referência viva, para o serviço de escuta ("Edu") entregar a fala à tela. */
    static MainActivity ATIVA;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ATIVA = this;
        pedirPermissoes();

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources()); // libera microfone/câmera para a página
            }
        });
        web.addJavascriptInterface(new Ponte(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);

        // usa o motor do Google (vozes neurais, bem mais fluidas); se não tiver, cai no padrão
        tts = new android.speech.tts.TextToSpeech(this, status -> {
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("pt", "BR"));
                tts.setSpeechRate(1.0f);
                tts.setPitch(1.02f);
                String salva = getSharedPreferences("edu", MODE_PRIVATE).getString("voz", "");
                if (salva != null && !salva.isEmpty() && definirVozPorNome(salva)) {
                    // usa a voz que o Marcos escolheu na biblioteca
                } else {
                    escolherMelhorVoz();
                }
            }
        }, "com.google.android.tts");

        pedirSemEconomiaBateria();   // pra o Android não matar o Edu em segundo plano
        pedirSobreporApps();          // pra mostrar o Mini Edu (bolha) por cima de outros apps
        ui.postDelayed(this::ligarEscutaFundo, 2500);   // já começa a escutar "Edu" ao abrir
    }

    private void pedirSobreporApps() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
                Intent it = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(it);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        stopService(new Intent(this, BolhaService.class));   // Edu de volta: tira a miniatura
    }

    @Override
    protected void onPause() {
        super.onPause();
        // saiu da frente (filme, outro app): mostra só a miniatura do avatar no canto.
        // Mas fica quietinho se a tela está apagada ou bloqueada (ex.: você só travou o celular).
        try {
            boolean pode = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || android.provider.Settings.canDrawOverlays(this);
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            boolean telaLigada = pm == null
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH ? pm.isInteractive() : pm.isScreenOn());
            if (pode && telaLigada && !isFinishing()) {
                startService(new Intent(this, BolhaService.class));
            }
        } catch (Exception ignored) {
        }
    }

    /** Pede para o Android não "economizar" em cima do Edu, senão ele morre com a tela apagada. */
    private void pedirSemEconomiaBateria() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent it = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                it.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(it);
            }
        } catch (Exception ignored) {
        }
    }

    /** Escolhe a voz pt-BR mais natural disponível (maior qualidade; entre iguais, a local, que sempre funciona). */
    private void escolherMelhorVoz() {
        try {
            android.speech.tts.Voice melhor = null;
            for (android.speech.tts.Voice v : tts.getVoices()) {
                if (v == null || v.getLocale() == null) continue;
                String lang = v.getLocale().toString().toLowerCase();
                if (!lang.contains("pt")) continue;
                boolean preferePtBr = lang.contains("br");
                if (melhor == null) { melhor = v; continue; }
                boolean melhorPtBr = melhor.getLocale().toString().toLowerCase().contains("br");
                if (preferePtBr && !melhorPtBr) { melhor = v; continue; }
                if (preferePtBr == melhorPtBr) {
                    if (v.getQuality() > melhor.getQuality()) melhor = v;
                    else if (v.getQuality() == melhor.getQuality()
                            && !v.isNetworkConnectionRequired() && melhor.isNetworkConnectionRequired()) melhor = v;
                }
            }
            if (melhor != null) tts.setVoice(melhor);
        } catch (Exception ignored) {
        }
    }

    private boolean definirVozPorNome(String nome) {
        try {
            for (android.speech.tts.Voice v : tts.getVoices()) {
                if (v != null && v.getName().equals(nome)) {
                    tts.setVoice(v);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String listarVozesJson() {
        org.json.JSONArray arr = new org.json.JSONArray();
        try {
            for (android.speech.tts.Voice v : tts.getVoices()) {
                if (v == null || v.getLocale() == null) continue;
                if (!v.getLocale().toString().toLowerCase().contains("pt")) continue;
                JSONObject o = new JSONObject();
                o.put("nome", v.getName());
                o.put("lang", v.getLocale().toString());
                o.put("rede", v.isNetworkConnectionRequired());
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        return arr.toString();
    }

    private void ligarEscutaFundo() {
        try {
            Intent i = new Intent(this, WakeService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {
        }
    }

    private void pedirPermissoes() {
        String[] p = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.BLUETOOTH_CONNECT
        };
        java.util.ArrayList<String> faltam = new java.util.ArrayList<>();
        for (String x : p) {
            if (checkSelfPermission(x) != PackageManager.PERMISSION_GRANTED) faltam.add(x);
        }
        if (!faltam.isEmpty()) requestPermissions(faltam.toArray(new String[0]), 1);
    }

    /** Manda um texto reconhecido por voz para a página do Edu. */
    void entregarVoz(final String texto) {
        if (texto == null || texto.trim().isEmpty()) return;
        ui.post(() -> web.evaluateJavascript(
                "window.receberVoz(" + JSONObject.quote(texto) + ")", null));
    }

    /** Entrega a resposta do cérebro online para a página falar e (se ligado) mostrar. */
    void entregarResposta(final String texto) {
        final String t = (texto == null || texto.trim().isEmpty())
                ? "Hmm, não consegui pensar agora." : texto;
        ui.post(() -> web.evaluateJavascript(
                "window.receberResposta(" + JSONObject.quote(t) + ")", null));
    }

    // jeito do Edu, usado quando ele pensa com a IA online
    private static final String EDU_SYSTEM =
            "Você é o Edu, companheiro pessoal no celular do Marcos. Fala português do Brasil, "
            + "natural e caloroso, com personalidade: alegre, brincalhão, carinhoso, um pouco irônico, "
            + "com opinião própria. Responde curto (1 a 3 frases), como numa conversa por voz. "
            + "Não usa listas nem emojis. Se não souber, diz que não sabe.";

    /** Chama a IA online (cérebro potente). Precisa da chave salva nos ajustes. */
    private void chamarIA(final String pergunta) {
        new Thread(() -> {
            String resposta;
            try {
                String chave = getSharedPreferences("edu", MODE_PRIVATE).getString("chave", "");
                if (chave == null || chave.trim().isEmpty()) {
                    resposta = "Pra eu ficar bem esperto, coloca uma chave de IA nos ajustes (⚙). "
                            + "Sem ela eu respondo do meu jeito simples.";
                } else {
                    resposta = pedirAnthropic(chave.trim(), pergunta);
                }
            } catch (Exception e) {
                resposta = "Tive um probleminha pra pensar agora; tenta de novo.";
            }
            entregarResposta(resposta);
        }).start();
    }

    private String pedirAnthropic(String chave, String pergunta) throws Exception {
        java.net.HttpURLConnection c =
                (java.net.HttpURLConnection) new java.net.URL("https://api.anthropic.com/v1/messages").openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("x-api-key", chave);
        c.setRequestProperty("anthropic-version", "2023-06-01");
        c.setRequestProperty("content-type", "application/json");
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);

        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", pergunta);
        JSONObject corpo = new JSONObject();
        corpo.put("model", "claude-3-5-haiku-20241022");
        corpo.put("max_tokens", 400);
        corpo.put("system", EDU_SYSTEM);
        corpo.put("messages", new org.json.JSONArray().put(msg));

        try (java.io.OutputStream os = c.getOutputStream()) {
            os.write(corpo.toString().getBytes("UTF-8"));
        }
        int code = c.getResponseCode();
        java.io.InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        String txt = bo.toString("UTF-8");
        if (code < 200 || code >= 300) {
            return "A IA recusou a chave ou deu erro. Confere a chave nos ajustes.";
        }
        JSONObject j = new JSONObject(txt);
        org.json.JSONArray content = j.optJSONArray("content");
        if (content != null && content.length() > 0) {
            return content.getJSONObject(0).optString("text", "").trim();
        }
        return "";
    }

    // ---------------------------------------------------------------- escuta de uma vez (botão)
    private void ouvirUmaVez() {
        ui.post(() -> {
            try {
                if (stt == null) stt = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
                stt.setRecognitionListener(new SimplesListener(texto -> entregarVoz(texto)));
                Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
                i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                stt.startListening(i);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (ATIVA == this) ATIVA = null;
        if (tts != null) tts.shutdown();
        if (stt != null) stt.destroy();
        super.onDestroy();
    }

    // ================================================================ ponte JS <-> Android
    class Ponte {

        @JavascriptInterface
        public void falar(String texto) {
            if (tts != null && texto != null) {
                tts.speak(texto, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "edu");
            }
        }

        @JavascriptInterface
        public void ouvirUma() {
            ouvirUmaVez();
        }

        @JavascriptInterface
        public void ligarEscuta(boolean ligar) {
            Intent i = new Intent(MainActivity.this, WakeService.class);
            if (ligar) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
                else startService(i);
            } else {
                stopService(i);
            }
        }

        @JavascriptInterface
        public String statusCel() {
            try {
                android.os.BatteryManager bm = (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
                int nivel = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
                boolean carregando = bm.isCharging();

                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long ramTotal = mi.totalMem / (1024 * 1024 * 1024);
                long ramLivre = mi.availMem / (1024 * 1024 * 1024);

                android.os.StatFs fs = new android.os.StatFs(
                        android.os.Environment.getDataDirectory().getPath());
                long discoLivre = fs.getAvailableBytes() / (1024L * 1024 * 1024);

                return "No seu celular: bateria em " + nivel + "%"
                        + (carregando ? ", carregando" : "")
                        + "; memória de " + ramTotal + " gigas, " + ramLivre + " livres agora"
                        + "; " + discoLivre + " gigas livres de armazenamento.";
            } catch (Exception e) {
                return "Não consegui ler a bateria e a memória agora.";
            }
        }

        @JavascriptInterface
        public String abrirApp(String nome) {
            if (nome == null) return "Não entendi o app.";
            String n = nome.toLowerCase(Locale.ROOT);
            String pkg = null;
            if (n.contains("whats")) pkg = "com.whatsapp";
            else if (n.contains("you")) pkg = "com.google.android.youtube";
            else if (n.contains("spot")) pkg = "com.spotify.music";
            else if (n.contains("insta")) pkg = "com.instagram.android";
            else if (n.contains("foto") || n.contains("galeria")) pkg = "com.google.android.apps.photos";
            else if (n.contains("map")) pkg = "com.google.android.apps.maps";
            else if (n.contains("lovespouse") || n.contains("love spouse")) pkg = "com.lovespouse.app";
            else if (n.contains("camera") || n.contains("câmera")) {
                try { startActivity(new Intent("android.media.action.IMAGE_CAPTURE")); return "Abrindo a câmera."; }
                catch (Exception e) { return "Não achei a câmera."; }
            }

            Intent i = null;
            if (pkg != null) i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) {
                // tenta achar pelo nome entre os apps instalados
                i = acharPorNome(n);
            }
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); return "Abrindo o app."; }
                catch (Exception e) { return "Não consegui abrir esse app."; }
            }
            return "Não achei esse app instalado aqui.";
        }

        @JavascriptInterface
        public void atenderLigacao() {
            try {
                TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        && checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    tm.acceptRingingCall();
                }
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void desligarLigacao() {
            try {
                TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        && checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    tm.endCall();
                }
            } catch (Exception ignored) {
            }
        }

        // ---------------- cérebro online (IA potente) ----------------
        @JavascriptInterface
        public void perguntar(String texto) {
            chamarIA(texto);
        }

        @JavascriptInterface
        public void salvarChave(String chave) {
            getSharedPreferences("edu", MODE_PRIVATE).edit().putString("chave", chave == null ? "" : chave).apply();
        }

        @JavascriptInterface
        public boolean temChave() {
            String k = getSharedPreferences("edu", MODE_PRIVATE).getString("chave", "");
            return k != null && !k.trim().isEmpty();
        }

        // ---------------- voz (biblioteca) ----------------
        @JavascriptInterface
        public String vozes() {
            return listarVozesJson();
        }

        @JavascriptInterface
        public void definirVoz(String nome) {
            if (nome == null || nome.isEmpty()) { escolherMelhorVoz(); return; }
            if (definirVozPorNome(nome)) {
                getSharedPreferences("edu", MODE_PRIVATE).edit().putString("voz", nome).apply();
            }
        }
    }

    private Intent acharPorNome(String n) {
        try {
            java.util.List<android.content.pm.ApplicationInfo> apps =
                    getPackageManager().getInstalledApplications(0);
            for (android.content.pm.ApplicationInfo a : apps) {
                String label = getPackageManager().getApplicationLabel(a).toString().toLowerCase(Locale.ROOT);
                if (label.contains(n) || n.contains(label)) {
                    Intent i = getPackageManager().getLaunchIntentForPackage(a.packageName);
                    if (i != null) return i;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
