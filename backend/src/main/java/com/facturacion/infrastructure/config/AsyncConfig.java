package com.facturacion.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Pool de hilos "procesosExecutor" usado por todos los metodos @Async("procesosExecutor").
 *
 * - corePoolSize = 3   : hilos que se mantienen vivos.
 * - maxPoolSize  = 10  : maximo de hilos simultaneos.
 * - queueCapacity = 0  : sin cola; cada tarea nueva toma un hilo libre o crea uno (hasta 10).
 * - CallerRunsPolicy   : si los 10 estan ocupados, la tarea la ejecuta el hilo que la pidio
 *                        en lugar de lanzar RejectedExecutionException (no se pierde ningun backup).
 */
@Configuration
public class AsyncConfig {

    public static final String EXECUTOR = "procesosExecutor";

    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor procesosExecutor(
            @Value("${facturacion.async.core-pool-size:3}") int corePoolSize,
            @Value("${facturacion.async.max-pool-size:10}") int maxPoolSize,
            @Value("${facturacion.async.queue-capacity:0}") int queueCapacity,
            @Value("${facturacion.async.thread-name-prefix:proceso-async-}") String prefix) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
