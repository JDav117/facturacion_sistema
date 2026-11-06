package com.facturacion.infrastructure.concurrency;

import java.util.concurrent.ThreadLocalRandom;

/** Simula trabajo de E/S con una pausa aleatoria para que se vea el intercalado de hilos. */
final class Pausa {

    private Pausa() {
    }

    static void aleatoria() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(150, 450));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
