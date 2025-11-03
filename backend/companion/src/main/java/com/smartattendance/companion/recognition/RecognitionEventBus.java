package com.smartattendance.companion.recognition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Simple in-memory event bus that stores recent recognition events and allows
 * listeners to subscribe for updates (UI overlays, HTTP streaming, etc.).
 */
public final class RecognitionEventBus {

    private static final int MAX_HISTORY = 256;

    private final CopyOnWriteArrayList<Consumer<RecognitionEvent>> listeners = new CopyOnWriteArrayList<>();
    private final Deque<RecognitionEvent> history = new ArrayDeque<>();

    public void publish(RecognitionEvent event) {
        if (event == null) {
            return;
        }
        synchronized (history) {
            history.addLast(event);
            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }
        for (Consumer<RecognitionEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {
            }
        }
    }

    public List<RecognitionEvent> snapshot() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void subscribe(Consumer<RecognitionEvent> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void unsubscribe(Consumer<RecognitionEvent> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
}
