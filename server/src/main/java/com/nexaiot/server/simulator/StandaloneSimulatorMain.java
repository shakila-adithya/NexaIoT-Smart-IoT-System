package com.nexaiot.server.simulator;

import java.util.concurrent.CountDownLatch;

/** Command-line entry point for one offline-first virtual IoT device. */
public final class StandaloneSimulatorMain {

    private StandaloneSimulatorMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        try (StandaloneSimulatorConfiguration configuration =
                StandaloneSimulatorConfiguration.fromArgs(args);
                StandaloneSimulatorRunner runner = new StandaloneSimulatorRunner(configuration)) {
            runner.start();
            Runtime.getRuntime().addShutdownHook(new Thread(runner::close, "simulator-shutdown"));
            new CountDownLatch(1).await();
        } catch (RuntimeException exception) {
            System.err.println("Simulator startup failed: " + exception.getMessage());
            System.exit(2);
        }
    }
}
