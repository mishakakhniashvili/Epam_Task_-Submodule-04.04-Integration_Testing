package com.epam.integration;

import com.epam.gymcrm.GymCrmApplication;
import com.epam.trainerworkload.TrainerWorkloadApplication;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.apache.activemq.broker.BrokerService;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetSocketAddress;

final class IntegrationTestEnvironment implements AutoCloseable {

    private static final String JWT_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String BROKER_NAME = "bdd-integration-broker";
    private static final String QUEUE_NAME =
            "trainer.workload.events.integration";

    private BrokerService broker;
    private MongoServer mongoServer;
    private ConfigurableApplicationContext gymContext;
    private ConfigurableApplicationContext workloadContext;
    private String gymBaseUrl;
    private String workloadBaseUrl;

    void start() throws Exception {
        startBroker();

        mongoServer = new MongoServer(new MemoryBackend());
        InetSocketAddress mongoAddress = mongoServer.bind();
        String mongoUri = "mongodb://localhost:"
                + mongoAddress.getPort()
                + "/trainer_workload_integration";

        workloadContext = new SpringApplicationBuilder(
                TrainerWorkloadApplication.class
        ).run(
                "--server.port=0",
                "--spring.application.name=trainer-workload-integration",
                "--security.jwt.secret-base64=" + JWT_SECRET,
                "--eureka.client.enabled=false",
                "--spring.data.mongodb.uri=" + mongoUri,
                "--spring.data.mongodb.auto-index-creation=true",
                "--spring.activemq.broker-url=vm://"
                        + BROKER_NAME + "?create=false",
                "--workload.queue.name=" + QUEUE_NAME,
                "--spring.jms.listener.auto-startup=true",
                "--spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
        );

        gymContext = new SpringApplicationBuilder(
                GymCrmApplication.class
        ).run(
                "--server.port=0",
                "--spring.application.name=gym-crm-integration",
                "--spring.datasource.url=jdbc:h2:mem:gymcrmintegration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.jpa.show-sql=false",
                "--spring.jpa.open-in-view=false",
                "--security.jwt.secret-base64=" + JWT_SECRET,
                "--security.jwt.expiration-minutes=30",
                "--security.login.max-failed-attempts=3",
                "--security.login.block-duration-minutes=5",
                "--security.cors.allowed-origins=http://localhost:3000",
                "--eureka.client.enabled=false",
                "--workload.outbox.scheduling.enabled=true",
                "--workload.outbox.dispatch-delay=50",
                "--workload.outbox.batch-size=10",
                "--spring.activemq.broker-url=vm://"
                        + BROKER_NAME + "?create=false",
                "--workload.queue.name=" + QUEUE_NAME,
                "--spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
        );

        gymBaseUrl = baseUrl(gymContext);
        workloadBaseUrl = baseUrl(workloadContext);
    }

    String gymBaseUrl() {
        return gymBaseUrl;
    }

    String workloadBaseUrl() {
        return workloadBaseUrl;
    }

    @Override
    public void close() throws Exception {
        if (gymContext != null) {
            gymContext.close();
        }
        if (workloadContext != null) {
            workloadContext.close();
        }
        if (mongoServer != null) {
            mongoServer.shutdownNow();
        }
        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
        }
    }

    private void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName(BROKER_NAME);
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.start();
        broker.waitUntilStarted();
    }

    private String baseUrl(ConfigurableApplicationContext context) {
        Integer port = context.getEnvironment().getProperty(
                "local.server.port",
                Integer.class
        );

        if (port == null) {
            throw new IllegalStateException(
                    "Application did not expose a local server port"
            );
        }

        return "http://localhost:" + port;
    }
}
