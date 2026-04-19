package com.shardedmc.coordinator.rest;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestServer {
    private static final Logger logger = LoggerFactory.getLogger(RestServer.class);
    
    private final Javalin app;
    private final int port;
    
    public RestServer(int port) {
        this.port = port;
        this.app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        });
    }
    
    public void start(CoordinatorController controller) {
        controller.registerRoutes(app);
        app.start(port);
        logger.info("REST API server started on port {}", port);
    }
    
    public void stop() {
        app.stop();
        logger.info("REST API server stopped");
    }
}
