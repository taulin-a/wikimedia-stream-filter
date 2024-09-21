package org.taulin;

import com.google.inject.Guice;
import com.google.inject.Injector;
import lombok.extern.slf4j.Slf4j;
import org.taulin.component.EventFilterRunner;
import org.taulin.factory.FilterModule;

@Slf4j
public class WikimediaStreamFilterApp {
    public static void main(String[] args) {
        final Injector injector = Guice.createInjector(new FilterModule());
        final EventFilterRunner eventFilterRunner = injector.getInstance(EventFilterRunner.class);
        eventFilterRunner.run();

        // shutdown hook
        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                log.info("Shutting down...");
                eventFilterRunner.close();

                mainThread.join();
            } catch (Exception e) {
                log.error("Error while shutting down: ", e);
                throw new RuntimeException(e);
            }
        }));
    }
}