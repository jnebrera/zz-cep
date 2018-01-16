package io.wizzie.ks.cep.builder;

import com.codahale.metrics.JmxAttributeGauge;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wizzie.bootstrapper.builder.*;
import io.wizzie.ks.cep.controllers.SiddhiController;
import io.wizzie.ks.cep.model.SiddhiAppBuilder;
import io.wizzie.metrics.MetricsManager;
import io.wizzie.ks.cep.model.ProcessingModel;
import org.apache.kafka.streams.KafkaStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.IOException;
import java.util.Properties;

import static io.wizzie.ks.cep.builder.config.ConfigProperties.*;
import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.*;
import static org.apache.kafka.clients.producer.ProducerConfig.*;
import static org.apache.kafka.streams.StreamsConfig.APPLICATION_ID_CONFIG;
import static org.apache.kafka.streams.StreamsConfig.NUM_STREAM_THREADS_CONFIG;


public class Builder implements Listener {
    private static final Logger log = LoggerFactory.getLogger(Builder.class);

    Config config;
    KafkaStreams streams;
    MetricsManager metricsManager;
    Bootstrapper bootstrapper;
    SiddhiController siddhiController;
    SiddhiAppBuilder siddhiAppBuilder;

    public Builder(Config config) throws Exception {
        this.config = config;
        metricsManager = new MetricsManager(config.getMapConf());
        registerKafkaMetrics(config, metricsManager);
        metricsManager.start();


        siddhiController = SiddhiController.getInstance();

        Properties consumerProperties = new Properties();
        consumerProperties.putAll(config.getMapConf());
        consumerProperties.put(BOOTSTRAP_SERVERS_CONFIG, config.get(KAFKA_CLUSTER));

        consumerProperties.put(GROUP_ID_CONFIG,  String.format("%s_%s", config.get(APPLICATION_ID), "zz-cep"));
        consumerProperties.put(ENABLE_AUTO_COMMIT_CONFIG, "true");
        consumerProperties.put(AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        consumerProperties.put(KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProperties.put(VALUE_DESERIALIZER_CLASS_CONFIG, config.getOrDefault(VALUE_DESERIALIZER, "io.wizzie.ks.cep.serializers.JsonDeserializer"));
        consumerProperties.put(MULTI_ID, config.getOrDefault(MULTI_ID, false));

        Properties producerProperties = new Properties();
        producerProperties.putAll(config.getMapConf());
        producerProperties.put(BOOTSTRAP_SERVERS_CONFIG, config.get(KAFKA_CLUSTER));
        producerProperties.put(KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producerProperties.put(PARTITIONER_CLASS_CONFIG, "io.wizzie.ks.cep.connectors.kafka.KafkaPartitioner");
        producerProperties.put(VALUE_SERIALIZER_CLASS_CONFIG, config.getOrDefault(VALUE_SERIALIZER, "io.wizzie.ks.cep.serializers.JsonSerializer"));
        producerProperties.put(MULTI_ID, config.getOrDefault(MULTI_ID, false));
        siddhiController.initKafkaController(consumerProperties, producerProperties);

        bootstrapper = BootstrapperBuilder.makeBuilder()
                .boostrapperClass(config.get(BOOTSTRAPER_CLASSNAME))
                .withConfigInstance(config)
                .listener(this)
                .build();
    }

    @Override
    public void updateConfig(SourceSystem sourceSystem, String bootstrapConfig) {
        if (streams != null) {
            metricsManager.clean();
            streams.close();
            log.info("Clean CEP process");
        }

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            ProcessingModel processingModel = objectMapper.readValue(bootstrapConfig, ProcessingModel.class);
            log.info("Processing plan: {}", processingModel);
            if(siddhiAppBuilder.validateSiddhiPlan(processingModel)) {
                siddhiController.addProcessingDefinition(processingModel);
                siddhiController.generateExecutionPlans();
                siddhiController.addProcessingModel2KafkaController();
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        log.info("Started CEP with conf {}", config.getProperties());
    }

    private void registerKafkaMetrics(Config config, MetricsManager metricsManager){
        Integer streamThreads = config.getOrDefault(NUM_STREAM_THREADS_CONFIG, 1);
        String appId = config.get(APPLICATION_ID_CONFIG);

        for (int i = 1; i <= streamThreads; i++) {
            try {

                // PRODUCER
                metricsManager.registerMetric("producer." + i + ".messages_send_per_sec",
                        new JmxAttributeGauge(new ObjectName("kafka.producer:type=producer-metrics,client-id="
                                + appId + "-1-StreamThread-"
                                + i + "-producer"), "record-send-rate"));

                metricsManager.registerMetric("producer." + i + ".output_bytes_per_sec",
                        new JmxAttributeGauge(new ObjectName("kafka.producer:type=producer-metrics,client-id="
                                + appId + "-1-StreamThread-"
                                + i + "-producer"), "outgoing-byte-rate"));

                metricsManager.registerMetric("producer." + i + ".incoming_bytes_per_sec",
                        new JmxAttributeGauge(new ObjectName("kafka.producer:type=producer-metrics,client-id="
                                + appId + "-1-StreamThread-"
                                + i + "-producer"), "incoming-byte-rate"));

                // CONSUMER
                metricsManager.registerMetric("consumer." + i + ".max_lag",
                        new JmxAttributeGauge(new ObjectName("kafka.consumer:type=consumer-fetch-manager-metrics,client-id="
                                + appId + "-1-StreamThread-"
                                + i + "-consumer"), "records-lag-max"));

                metricsManager.registerMetric("consumer." + i + ".output_bytes_per_sec",
                        new JmxAttributeGauge(new ObjectName("kafka.consumer:type=consumer-metrics,client-id="
                                + appId + "-1-StreamThread-"
                                + i + "-consumer"), "outgoing-byte-rate"));

                metricsManager.registerMetric("consumer." + i + ".incoming_bytes_per_sec",
                        new JmxAttributeGauge(new ObjectName("kafka.consumer:type=consumer-metrics,client-id="
                                + appId + "-1-StreamThread-"
                                + i + "-consumer"), "incoming-byte-rate"));

                metricsManager.registerMetric("consumer." + i + ".records_per_sec",
                        new JmxAttributeGauge(new ObjectName("kafka.consumer:type=consumer-fetch-manager-metrics,client-id="
                                + appId + "-1-StreamThread-"
                                + i + "-consumer"), "records-consumed-rate"));

            } catch (MalformedObjectNameException e) {
                log.warn("Metric not found");
            }
        }
    }

    public void close() {
        metricsManager.interrupt();
        if (streams != null) streams.close();
        siddhiController.shutdown();
    }

}
