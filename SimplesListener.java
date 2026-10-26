package com.edu.app;

import android.os.Bundle;
import android.speech.RecognitionListener;

import java.util.ArrayList;

/** Ouvinte simples do reconhecimento de voz: entrega o melhor texto reconhecido. */
public class SimplesListener implements RecognitionListener {

    public interface Callback {
        void onTexto(String texto);
    }

    private final Callback cb;

    public SimplesListener(Callback cb) {
        this.cb = cb;
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> lista = results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
        if (lista != null && !lista.isEmpty() && cb != null) {
            cb.onTexto(lista.get(0));
        }
    }

    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onError(int error) { }
    @Override public void onPartialResults(Bundle partialResults) { }
    @Override public void onEvent(int eventType, Bundle params) { }
}
