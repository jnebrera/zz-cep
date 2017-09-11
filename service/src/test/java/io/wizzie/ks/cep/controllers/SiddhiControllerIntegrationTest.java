package io.wizzie.ks.cep.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.wizzie.ks.cep.model.*;
import io.wizzie.ks.cep.serializers.JsonDeserializer;
import io.wizzie.ks.cep.serializers.JsonSerializer;
import kafka.utils.MockTime;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.integration.utils.EmbeddedKafkaCluster;
import org.apache.kafka.streams.integration.utils.IntegrationTestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static io.wizzie.ks.cep.builder.config.ConfigProperties.APPLICATION_ID;
import static io.wizzie.ks.cep.builder.config.ConfigProperties.MULTI_ID;
import static org.junit.Assert.*;

public class SiddhiControllerIntegrationTest {
    private final static int NUM_BROKERS = 1;

    @ClassRule
    public static EmbeddedKafkaCluster CLUSTER = new EmbeddedKafkaCluster(NUM_BROKERS);
    private final static MockTime MOCK_TIME = CLUSTER.time;

    private static final int REPLICATION_FACTOR = 1;

    private static final Properties consumerNoMultiIdProperties = new Properties();
    ;
    private static final Properties producerNoMultiIdProperties = new Properties();


    @BeforeClass
    public static void startKafkaCluster() throws Exception {
        // inputs
        CLUSTER.createTopic("input1", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("input2", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("input3", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("input4", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("input5", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("input6", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("input7", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("input8", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("input9", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("aabb_input10", 1, REPLICATION_FACTOR);


        // sinks
        CLUSTER.createTopic("output1", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("output3", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("output4", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("output5", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("output6", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("output66", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("output77", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("output8", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("output9", 1, REPLICATION_FACTOR);
        CLUSTER.createTopic("aabb_output10", 1, REPLICATION_FACTOR);


        consumerNoMultiIdProperties.put("bootstrap.servers", CLUSTER.bootstrapServers());
        consumerNoMultiIdProperties.put("group.id", "cep");
        consumerNoMultiIdProperties.put("enable.auto.commit", "true");
        consumerNoMultiIdProperties.put("auto.commit.interval.ms", "1000");
        consumerNoMultiIdProperties.put("key.deserializer", StringDeserializer.class.getName());
        consumerNoMultiIdProperties.put("value.deserializer", StringDeserializer.class.getName());
        //Property just needed for testing.
        consumerNoMultiIdProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        producerNoMultiIdProperties.put("bootstrap.servers", CLUSTER.bootstrapServers());
        producerNoMultiIdProperties.put("acks", "all");
        producerNoMultiIdProperties.put("retries", 0);
        producerNoMultiIdProperties.put("batch.size", 16384);
        producerNoMultiIdProperties.put("linger.ms", 1);
        producerNoMultiIdProperties.put("buffer.memory", 33554432);
        producerNoMultiIdProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerNoMultiIdProperties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

    }

    @Test
    public void SiddhiControllerAddTwoStreamTest() throws InterruptedException {

        String jsonData = "{\"attributeName\":\"VALUE\"}";
        String jsonData2 = "{\"attributeName\":\"VALUE2\"}";


        KeyValue<String, Map<String, Object>> kvStream1 = null;

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            kvStream1 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData, Map.class));
        } catch (IOException e) {
            e.printStackTrace();
        }

        KeyValue<String, Map<String, Object>> kvStream2 = null;

        try {
            kvStream2 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData2, Map.class));
        } catch (IOException e) {
            e.printStackTrace();
        }

        SourceModel sourceModel = new SourceModel("stream1", "input1");
        List<SourceModel> sourceModelList = new LinkedList<>();
        sourceModelList.add(sourceModel);

        SinkModel sinkModel = new SinkModel("streamoutput1", "output1");
        List<SinkModel> sinkModelList = new LinkedList<>();
        sinkModelList.add(sinkModel);

        String id = "rule1";
        String version = "v1";
        String executionPlan = "from stream1 select * insert into streamoutput1";

        StreamMapModel streamMapModel = new StreamMapModel(sourceModelList, sinkModelList);

        RuleModel ruleModelObject = new RuleModel(id, version, streamMapModel, executionPlan, null);

        List<RuleModel> ruleModelList = new LinkedList<>();
        ruleModelList.add(ruleModelObject);


        List<StreamModel> streamsModel = Arrays.asList(
                new StreamModel("stream1", Arrays.asList(
                        new AttributeModel("attributeName", "string")
                )), new StreamModel("stream11", Arrays.asList(
                        new AttributeModel("attributeName", "string")
                )));

        ProcessingModel processingModel = new ProcessingModel(ruleModelList, streamsModel);

        SiddhiController siddhiController = SiddhiController.TEST_CreateInstance();
        siddhiController.initKafkaController(consumerNoMultiIdProperties, producerNoMultiIdProperties);

        siddhiController.addProcessingDefinition(processingModel);
        siddhiController.generateExecutionPlans();
        siddhiController.addProcessingModel2KafkaController();

        Properties consumerConfigA = new Properties();
        consumerConfigA.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        consumerConfigA.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-consumer-A");
        consumerConfigA.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfigA.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerConfigA.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("attributeName", "VALUE");

        Map<String, Object> expectedData2 = new HashMap<>();
        expectedData2.put("attributeName", "VALUE2");

        KeyValue<String, Map<String, Object>> expectedDataKv = new KeyValue<>(null, expectedData);
        KeyValue<String, Map<String, Object>> expectedDataKv2 = new KeyValue<>(null, expectedData2);

        Properties producerConfig = new Properties();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);


        try {
            System.out.println("Producing KVs: " + kvStream1 + kvStream2);
            IntegrationTestUtils.produceKeyValuesSynchronously("input1", Collections.singletonList(kvStream1), producerConfig, MOCK_TIME);
            IntegrationTestUtils.produceKeyValuesSynchronously("input1", Collections.singletonList(kvStream2), producerConfig, MOCK_TIME);

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        List<KeyValue<String, Map>> receivedMessagesFromOutput1 = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfigA, "output1", 1);
        System.out.println("Received after Siddhi: " + receivedMessagesFromOutput1);
        assertEquals(Arrays.asList(expectedDataKv, expectedDataKv2), receivedMessagesFromOutput1);

    }


    @Test
    public void SiddhiControllerAddOneStreamTest() throws InterruptedException {


        String jsonData = "{\"attributeName\":\"VALUE\"}";

        KeyValue<String, Map<String, Object>> kvStream1 = null;

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            kvStream1 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData, Map.class));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Properties producerConfig = new Properties();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);


        SiddhiController siddhiController = SiddhiController.TEST_CreateInstance();
        siddhiController.initKafkaController(consumerNoMultiIdProperties, producerNoMultiIdProperties);

        SourceModel sourceModel = new SourceModel("stream2", "input2");
        List<SourceModel> sourceModelList = new LinkedList<>();
        sourceModelList.add(sourceModel);

        SinkModel sinkModel = new SinkModel("streamoutput2", "output2");
        List<SinkModel> sinkModelList = new LinkedList<>();
        sinkModelList.add(sinkModel);

        String id = "rule2";
        String version = "v1";
        String executionPlan = "from stream2 select * insert into streamoutput2";

        StreamMapModel streamMapModel = new StreamMapModel(Arrays.asList(sourceModel), Arrays.asList(sinkModel));

        RuleModel ruleModelObject = new RuleModel(id, version, streamMapModel, executionPlan, null);

        List<RuleModel> ruleModelList = new LinkedList<>();
        ruleModelList.add(ruleModelObject);


        List<StreamModel> streamsModel = Arrays.asList(
                new StreamModel("stream2", Arrays.asList(
                        new AttributeModel("attributeName", "string")
                )));

        ProcessingModel processingModel = new ProcessingModel(ruleModelList, streamsModel);

        siddhiController.addProcessingDefinition(processingModel);
        siddhiController.generateExecutionPlans();
        siddhiController.addProcessingModel2KafkaController();

        Properties consumerConfigA = new Properties();
        consumerConfigA.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        consumerConfigA.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-consumer-A");
        consumerConfigA.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfigA.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerConfigA.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("attributeName", "VALUE");

        KeyValue<String, Map<String, Object>> expectedDataKv = new KeyValue<>(null, expectedData);

        try {
            System.out.println("Producing KV: " + kvStream1);
            IntegrationTestUtils.produceKeyValuesSynchronously("input2", Collections.singletonList(kvStream1), producerConfig, MOCK_TIME);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        List<KeyValue<String, Map>> receivedMessagesFromOutput1 = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfigA, "output2", 1);
        System.out.println("Received after Siddhi: " + receivedMessagesFromOutput1);
        assertEquals(Collections.singletonList(expectedDataKv), receivedMessagesFromOutput1);

    }


    @Test
    public void SiddhiControllerAddTwoRulesDifferentStreamsTest() throws InterruptedException {

        String jsonData = "{\"attributeName\":\"VALUE\"}";
        String jsonData2 = "{\"attributeName\":\"VALUE2\"}";


        KeyValue<String, Map<String, Object>> kvStream1 = null;

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            kvStream1 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData, Map.class));
        } catch (IOException e) {
            e.printStackTrace();
        }

        KeyValue<String, Map<String, Object>> kvStream2 = null;

        try {
            kvStream2 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData2, Map.class));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Properties producerConfig = new Properties();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);


        SiddhiController siddhiController = SiddhiController.TEST_CreateInstance();
        siddhiController.initKafkaController(consumerNoMultiIdProperties, producerNoMultiIdProperties);

        SourceModel sourceModel = new SourceModel("stream3", "input3");
        List<SourceModel> sourceModelList = new LinkedList<>();
        sourceModelList.add(sourceModel);

        SinkModel sinkModel = new SinkModel("streamoutput3", "output3");
        List<SinkModel> sinkModelList = new LinkedList<>();
        sinkModelList.add(sinkModel);

        String id = "rule3";
        String version = "v1";
        String executionPlan = "from stream3 select * insert into streamoutput3";

        StreamMapModel streamMapModel = new StreamMapModel(sourceModelList, sinkModelList);

        RuleModel ruleModelObject = new RuleModel(id, version, streamMapModel, executionPlan, null);


        String id2 = "rule33";
        String version2 = "v1";
        String executionPlan2 = "from stream3 select * insert into streamoutput3";

        StreamMapModel streamMapModel2 = new StreamMapModel(sourceModelList, sinkModelList);

        RuleModel ruleModelObject2 = new RuleModel(id2, version2, streamMapModel2, executionPlan2, null);

        List<RuleModel> ruleModelList = new LinkedList<>();
        ruleModelList.add(ruleModelObject);
        ruleModelList.add(ruleModelObject2);


        List<StreamModel> streamsModel = Arrays.asList(
                new StreamModel("stream3", Arrays.asList(
                        new AttributeModel("attributeName", "string")
                )), new StreamModel("stream33", Arrays.asList(
                        new AttributeModel("attributeName", "string")
                )));

        ProcessingModel processingModel = new ProcessingModel(ruleModelList, streamsModel);

        siddhiController.addProcessingDefinition(processingModel);
        siddhiController.generateExecutionPlans();
        siddhiController.addProcessingModel2KafkaController();

        Properties consumerConfigA = new Properties();
        consumerConfigA.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        consumerConfigA.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-consumer-A");
        consumerConfigA.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfigA.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerConfigA.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("attributeName", "VALUE");

        Map<String, Object> expectedData2 = new HashMap<>();
        expectedData2.put("attributeName", "VALUE2");

        KeyValue<String, Map<String, Object>> expectedDataKv = new KeyValue<>(null, expectedData);
        KeyValue<String, Map<String, Object>> expectedDataKv2 = new KeyValue<>(null, expectedData2);


        try {
            System.out.println("Producing KVs: " + kvStream1 + kvStream2);
            IntegrationTestUtils.produceKeyValuesSynchronously("input3", Collections.singletonList(kvStream1), producerConfig, MOCK_TIME);
            IntegrationTestUtils.produceKeyValuesSynchronously("input3", Collections.singletonList(kvStream2), producerConfig, MOCK_TIME);

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        List<KeyValue<String, Map>> receivedMessagesFromOutput1 = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfigA, "output3", 1);
        System.out.println("Received after Siddhi: " + receivedMessagesFromOutput1);
        assertEquals(Arrays.asList(expectedDataKv, expectedDataKv, expectedDataKv2, expectedDataKv2), receivedMessagesFromOutput1);

    }


    @Test
    public void SiddhiControllerAddAvgRuleStreamTest() throws InterruptedException {

        String jsonData = "{\"attributeName\":2}";


        KeyValue<String, Map<String, Object>> kvStream1 = null;

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            kvStream1 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData, Map.class));
        } catch (IOException e) {
            e.printStackTrace();
        }


        Properties producerConfig = new Properties();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);


        SiddhiController siddhiController = SiddhiController.TEST_CreateInstance();
        siddhiController.initKafkaController(consumerNoMultiIdProperties, producerNoMultiIdProperties);

        SourceModel sourceModel = new SourceModel("stream4", "input4");
        List<SourceModel> sourceModelList = new LinkedList<>();
        sourceModelList.add(sourceModel);

        SinkModel sinkModel = new SinkModel("streamoutput4", "output4");
        List<SinkModel> sinkModelList = new LinkedList<>();
        sinkModelList.add(sinkModel);

        String id = "rule4";
        String version = "v1";
        String executionPlan = "from stream4 select avg(attributeName) as avg insert into streamoutput4";

        StreamMapModel streamMapModel = new StreamMapModel(sourceModelList, sinkModelList);

        RuleModel ruleModelObject = new RuleModel(id, version, streamMapModel, executionPlan, null);


        List<RuleModel> ruleModelList = new LinkedList<>();
        ruleModelList.add(ruleModelObject);


        List<StreamModel> streamsModel = Arrays.asList(
                new StreamModel("stream4", Arrays.asList(
                        new AttributeModel("attributeName", "integer")
                )), new StreamModel("stream44", Arrays.asList(
                        new AttributeModel("attributeName", "integer")
                )));

        ProcessingModel processingModel = new ProcessingModel(ruleModelList, streamsModel);

        siddhiController.addProcessingDefinition(processingModel);
        siddhiController.generateExecutionPlans();
        siddhiController.addProcessingModel2KafkaController();

        Properties consumerConfigA = new Properties();
        consumerConfigA.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        consumerConfigA.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-consumer-A");
        consumerConfigA.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfigA.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerConfigA.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("avg", 2.0);


        KeyValue<String, Map<String, Object>> expectedDataKv = new KeyValue<>(null, expectedData);


        try {
            System.out.println("Producing KVs: " + kvStream1);
            IntegrationTestUtils.produceKeyValuesSynchronously("input4", Collections.singletonList(kvStream1), producerConfig, MOCK_TIME);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        List<KeyValue<String, Map>> receivedMessagesFromOutput1 = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfigA, "output4", 1);
        System.out.println("Received after Siddhi: " + receivedMessagesFromOutput1);
        assertEquals(Arrays.asList(expectedDataKv), receivedMessagesFromOutput1);

    }


    @Test
    public void SiddhiControllerAddPartitionRuleStreamTest() throws InterruptedException {

        String jsonData = "{\"fieldA\":3, \"fieldB\":2}";


        KeyValue<String, Map<String, Object>> kvStream1 = null;

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            kvStream1 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData, Map.class));
        } catch (IOException e) {
            e.printStackTrace();
        }


        Properties producerConfig = new Properties();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);


        SiddhiController siddhiController = SiddhiController.TEST_CreateInstance();
        siddhiController.initKafkaController(consumerNoMultiIdProperties, producerNoMultiIdProperties);

        SourceModel sourceModel = new SourceModel("streaminput5", "input5");
        List<SourceModel> sourceModelList = new LinkedList<>();
        sourceModelList.add(sourceModel);

        SinkModel sinkModel = new SinkModel("streamoutput5", "output5");
        List<SinkModel> sinkModelList = new LinkedList<>();
        sinkModelList.add(sinkModel);

        String id = "rule5";
        String version = "v1";
        String executionPlan = "partition with (fieldA of streaminput5) begin from streaminput5#window.timeBatch(5 sec) select avg(fieldB) as avg insert into streamoutput5 end";

        StreamMapModel streamMapModel = new StreamMapModel(sourceModelList, sinkModelList);

        RuleModel ruleModelObject = new RuleModel(id, version, streamMapModel, executionPlan, null);


        List<RuleModel> ruleModelList = new LinkedList<>();
        ruleModelList.add(ruleModelObject);


        List<StreamModel> streamsModel = Arrays.asList(
                new StreamModel("streaminput5", Arrays.asList(
                        new AttributeModel("fieldA", "integer"),
                        new AttributeModel("fieldB", "integer")
                )));

        ProcessingModel processingModel = new ProcessingModel(ruleModelList, streamsModel);

        siddhiController.addProcessingDefinition(processingModel);
        siddhiController.generateExecutionPlans();
        siddhiController.addProcessingModel2KafkaController();

        Properties consumerConfigA = new Properties();
        consumerConfigA.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        consumerConfigA.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-consumer-A");
        consumerConfigA.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfigA.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerConfigA.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("avg", 2.0);


        KeyValue<String, Map<String, Object>> expectedDataKv = new KeyValue<>(null, expectedData);


        try {
            System.out.println("Producing KVs: " + kvStream1);
            IntegrationTestUtils.produceKeyValuesSynchronously("input5", Collections.singletonList(kvStream1), producerConfig, MOCK_TIME);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        List<KeyValue<String, Map>> receivedMessagesFromOutput1 = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfigA, "output5", 1);
        System.out.println("Received after Siddhi: " + receivedMessagesFromOutput1);
        assertEquals(Arrays.asList(expectedDataKv), receivedMessagesFromOutput1);

    }


    @Test
    public void SiddhiControllerAddOutputNotUsedRuleStreamTest() throws InterruptedException {

        String jsonData = "{\"fieldA\":\"yes\", \"fieldB\": 0}";
        String jsonData2 = "{\"fieldA\":\"no\", \"fieldB\":2}";


        KeyValue<String, Map<String, Object>> kvStream1 = null;
        KeyValue<String, Map<String, Object>> kvStream2 = null;


        ObjectMapper objectMapper = new ObjectMapper();
        try {
            kvStream1 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData, Map.class));
            kvStream2 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData2, Map.class));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Properties producerConfig = new Properties();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);


        SiddhiController siddhiController = SiddhiController.TEST_CreateInstance();
        siddhiController.initKafkaController(consumerNoMultiIdProperties, producerNoMultiIdProperties);

        SourceModel sourceModel = new SourceModel("streaminput6", "input6");
        List<SourceModel> sourceModelList = new LinkedList<>();
        sourceModelList.add(sourceModel);

        SinkModel sinkModel = new SinkModel("streamoutput6", "output6");
        SinkModel sinkModel2 = new SinkModel("streamoutput66", "output66");
        List<SinkModel> sinkModelList = new LinkedList<>();
        sinkModelList.add(sinkModel);
        sinkModelList.add(sinkModel2);

        String id = "rule6";
        String version = "v1";
        String executionPlan = "from every e1=streaminput6[fieldA == 'yes'] -> e2=streaminput6[fieldB > 0] select 'Correct' as description insert into streamoutput6";
        StreamMapModel streamMapModel = new StreamMapModel(sourceModelList, sinkModelList);

        RuleModel ruleModelObject = new RuleModel(id, version, streamMapModel, executionPlan, null);


        List<RuleModel> ruleModelList = new LinkedList<>();
        ruleModelList.add(ruleModelObject);


        List<StreamModel> streamsModel = Arrays.asList(
                new StreamModel("streaminput6", Arrays.asList(
                        new AttributeModel("fieldA", "string"),
                        new AttributeModel("fieldB", "integer")
                )));

        ProcessingModel processingModel = new ProcessingModel(ruleModelList, streamsModel);

        siddhiController.addProcessingDefinition(processingModel);
        siddhiController.generateExecutionPlans();
        siddhiController.addProcessingModel2KafkaController();

        Properties consumerConfigA = new Properties();
        consumerConfigA.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        consumerConfigA.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-consumer-A");
        consumerConfigA.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfigA.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerConfigA.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("description", "Correct");


        KeyValue<String, Map<String, Object>> expectedDataKv = new KeyValue<>(null, expectedData);


        try {
            System.out.println("Producing KVs: " + kvStream1 + kvStream2);
            IntegrationTestUtils.produceKeyValuesSynchronously("input6", Collections.singletonList(kvStream1), producerConfig, MOCK_TIME);
            IntegrationTestUtils.produceKeyValuesSynchronously("input6", Collections.singletonList(kvStream2), producerConfig, MOCK_TIME);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        List<KeyValue<String, Map>> receivedMessagesFromOutput1 = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfigA, "output6", 1);
        System.out.println("Received after Siddhi: " + receivedMessagesFromOutput1);
        assertEquals(Arrays.asList(expectedDataKv), receivedMessagesFromOutput1);

    }


    @Test
    public void SiddhiControllerSend2TwoOutputsStreamTest() throws InterruptedException {

        String jsonData = "{\"attributeName\":\"VALUE\"}";

        KeyValue<String, Map<String, Object>> kvStream1 = null;

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            kvStream1 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData, Map.class));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Properties producerConfig = new Properties();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);


        SiddhiController siddhiController = SiddhiController.TEST_CreateInstance();
        siddhiController.initKafkaController(consumerNoMultiIdProperties, producerNoMultiIdProperties);

        SourceModel sourceModel = new SourceModel("streaminput7", "input7");
        List<SourceModel> sourceModelList = new LinkedList<>();
        sourceModelList.add(sourceModel);

        SinkModel sinkModel = new SinkModel("streamoutput7", "output7");
        SinkModel sinkModel2 = new SinkModel("streamoutput7", "output77");
        List<SinkModel> sinkModelList = new LinkedList<>();
        sinkModelList.add(sinkModel);
        sinkModelList.add(sinkModel2);

        String id = "rule6";
        String version = "v1";
        String executionPlan = "from streaminput7 select * insert into streamoutput7";

        StreamMapModel streamMapModel = new StreamMapModel(sourceModelList, sinkModelList);
        RuleModel ruleModelObject = new RuleModel(id, version, streamMapModel, executionPlan, null);

        List<RuleModel> ruleModelList = new LinkedList<>();
        ruleModelList.add(ruleModelObject);


        List<StreamModel> streamsModel = Arrays.asList(
                new StreamModel("streaminput7", Arrays.asList(
                        new AttributeModel("attributeName", "string")
                )));

        ProcessingModel processingModel = new ProcessingModel(ruleModelList, streamsModel);

        siddhiController.addProcessingDefinition(processingModel);
        siddhiController.generateExecutionPlans();
        siddhiController.addProcessingModel2KafkaController();

        Properties consumerConfigA = new Properties();
        consumerConfigA.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        consumerConfigA.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-consumer-A");
        consumerConfigA.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfigA.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerConfigA.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("attributeName", "VALUE");

        KeyValue<String, Map<String, Object>> expectedDataKv = new KeyValue<>(null, expectedData);

        try {
            System.out.println("Producing KV: " + kvStream1);
            IntegrationTestUtils.produceKeyValuesSynchronously("input7", Collections.singletonList(kvStream1), producerConfig, MOCK_TIME);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        List<KeyValue<String, Map>> receivedMessagesFromOutput1 = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfigA, "output7", 1);
        List<KeyValue<String, Map>> receivedMessagesFromOutput2 = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfigA, "output77", 1);
        System.out.println("Received after Siddhi: " + receivedMessagesFromOutput1);
        System.out.println("Received after Siddhi: " + receivedMessagesFromOutput2);

        assertEquals(Collections.singletonList(expectedDataKv), receivedMessagesFromOutput1);
        assertEquals(Collections.singletonList(expectedDataKv), receivedMessagesFromOutput2);

    }

    @Test
    public void SiddhiControllerShouldFilterNullsStreamTest() throws InterruptedException {

        String jsonData = "{\"attributeName\":2}";


        KeyValue<String, Map<String, Object>> kvStream1 = null;

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            kvStream1 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData, Map.class));
        } catch (IOException e) {
            e.printStackTrace();
        }


        Properties producerConfig = new Properties();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);


        SiddhiController siddhiController = SiddhiController.TEST_CreateInstance();
        siddhiController.initKafkaController(consumerNoMultiIdProperties, producerNoMultiIdProperties);

        SourceModel sourceModel = new SourceModel("stream8", "input8");
        List<SourceModel> sourceModelList = new LinkedList<>();
        sourceModelList.add(sourceModel);

        SinkModel sinkModel = new SinkModel("streamoutput8", "output8");
        List<SinkModel> sinkModelList = new LinkedList<>();
        sinkModelList.add(sinkModel);

        String id = "rule8";
        String version = "v1";
        String executionPlan = "from stream8 select * insert into streamoutput8";
        Map<String, Object> options = new HashMap<>();
        options.put("filterOutputNullDimension", true);

        StreamMapModel streamMapModel = new StreamMapModel(sourceModelList, sinkModelList);

        RuleModel ruleModelObject = new RuleModel(id, version, streamMapModel, executionPlan, options);


        List<RuleModel> ruleModelList = new LinkedList<>();
        ruleModelList.add(ruleModelObject);


        List<StreamModel> streamsModel = Arrays.asList(
                new StreamModel("stream8", Arrays.asList(
                        new AttributeModel("attributeName", "integer"), new AttributeModel("attributeName2", "integer")
                )));

        ProcessingModel processingModel = new ProcessingModel(ruleModelList, streamsModel);

        siddhiController.addProcessingDefinition(processingModel);
        siddhiController.generateExecutionPlans();
        siddhiController.addProcessingModel2KafkaController();

        Properties consumerConfigA = new Properties();
        consumerConfigA.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        consumerConfigA.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-consumer-A");
        consumerConfigA.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfigA.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerConfigA.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("attributeName", 2);


        KeyValue<String, Map<String, Object>> expectedDataKv = new KeyValue<>(null, expectedData);


        try {
            System.out.println("Producing KVs: " + kvStream1);
            IntegrationTestUtils.produceKeyValuesSynchronously("input8", Collections.singletonList(kvStream1), producerConfig, MOCK_TIME);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        List<KeyValue<String, Map>> receivedMessagesFromOutput1 = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfigA, "output8", 1);
        System.out.println("Received after Siddhi: " + receivedMessagesFromOutput1);
        assertEquals(Arrays.asList(expectedDataKv), receivedMessagesFromOutput1);

    }

    @Test
    public void SiddhiControllerShouldNotSendAnEmptyMessageStreamTest() throws InterruptedException {

        String jsonData = "{\"attributeNotUsed\":2}";
        String jsonData2 = "{\"attributeName\":2}";

        KeyValue<String, Map<String, Object>> kvStream1 = null;
        KeyValue<String, Map<String, Object>> kvStream2 = null;


        ObjectMapper objectMapper = new ObjectMapper();
        try {
            kvStream1 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData, Map.class));
            kvStream2 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData2, Map.class));
        } catch (IOException e) {
            e.printStackTrace();
        }


        Properties producerConfig = new Properties();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);


        SiddhiController siddhiController = SiddhiController.TEST_CreateInstance();
        siddhiController.initKafkaController(consumerNoMultiIdProperties, producerNoMultiIdProperties);

        SourceModel sourceModel = new SourceModel("stream9", "input9");
        List<SourceModel> sourceModelList = new LinkedList<>();
        sourceModelList.add(sourceModel);

        SinkModel sinkModel = new SinkModel("streamoutput9", "output9");
        List<SinkModel> sinkModelList = new LinkedList<>();
        sinkModelList.add(sinkModel);

        String id = "rule9";
        String version = "v1";
        String executionPlan = "from stream9 select * insert into streamoutput9";
        Map<String, Object> options = new HashMap<>();
        options.put("filterOutputNullDimension", true);

        StreamMapModel streamMapModel = new StreamMapModel(sourceModelList, sinkModelList);

        RuleModel ruleModelObject = new RuleModel(id, version, streamMapModel, executionPlan, options);


        List<RuleModel> ruleModelList = new LinkedList<>();
        ruleModelList.add(ruleModelObject);


        List<StreamModel> streamsModel = Arrays.asList(
                new StreamModel("stream9", Arrays.asList(
                        new AttributeModel("attributeName", "integer"), new AttributeModel("attributeName2", "integer")
                )));

        ProcessingModel processingModel = new ProcessingModel(ruleModelList, streamsModel);

        siddhiController.addProcessingDefinition(processingModel);
        siddhiController.generateExecutionPlans();
        siddhiController.addProcessingModel2KafkaController();

        Properties consumerConfigA = new Properties();
        consumerConfigA.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        consumerConfigA.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-consumer-A");
        consumerConfigA.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfigA.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerConfigA.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("attributeName", 2);


        KeyValue<String, Map<String, Object>> expectedDataKv = new KeyValue<>(null, expectedData);


        try {
            System.out.println("Producing KVs: " + kvStream1);
            IntegrationTestUtils.produceKeyValuesSynchronously("input9", Collections.singletonList(kvStream1), producerConfig, MOCK_TIME);
            IntegrationTestUtils.produceKeyValuesSynchronously("input9", Collections.singletonList(kvStream2), producerConfig, MOCK_TIME);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        List<KeyValue<String, Map>> receivedMessagesFromOutput1 = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfigA, "output9", 1);
        System.out.println("Received after Siddhi: " + receivedMessagesFromOutput1);
        assertEquals(Arrays.asList(expectedDataKv), receivedMessagesFromOutput1);

    }


    @Test
    public void SiddhiControllerAddOneStreamWithMultiIdTest() throws InterruptedException {

        String jsonData = "{\"attributeName\":\"VALUE\"}";

        KeyValue<String, Map<String, Object>> kvStream1 = null;

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            kvStream1 = new KeyValue<>("KEY_A", objectMapper.readValue(jsonData, Map.class));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Properties producerConfig = new Properties();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);


        SiddhiController siddhiController = SiddhiController.TEST_CreateInstance();

        Properties internalConsumerProperties = new Properties();
        internalConsumerProperties.put("bootstrap.servers", CLUSTER.bootstrapServers());
        internalConsumerProperties.put("group.id", "cep");
        internalConsumerProperties.put("enable.auto.commit", "true");
        internalConsumerProperties.put("auto.commit.interval.ms", "1000");
        internalConsumerProperties.put("key.deserializer", StringDeserializer.class.getName());
        internalConsumerProperties.put("value.deserializer", StringDeserializer.class.getName());
        internalConsumerProperties.put(APPLICATION_ID, "aabb");
        internalConsumerProperties.put(MULTI_ID, true);
        //Property just needed for testing.
        internalConsumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Properties internalProducerProperties = new Properties();
        internalProducerProperties.put("bootstrap.servers", CLUSTER.bootstrapServers());
        internalProducerProperties.put("acks", "all");
        internalProducerProperties.put("retries", 0);
        internalProducerProperties.put("batch.size", 16384);
        internalProducerProperties.put("linger.ms", 1);
        internalProducerProperties.put("buffer.memory", 33554432);
        internalProducerProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        internalProducerProperties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        internalProducerProperties.put(APPLICATION_ID, "aabb");
        internalProducerProperties.put(MULTI_ID, true);


        siddhiController.initKafkaController(internalConsumerProperties, internalProducerProperties);

        SourceModel sourceModel = new SourceModel("stream10", "input10");
        List<SourceModel> sourceModelList = new LinkedList<>();
        sourceModelList.add(sourceModel);

        SinkModel sinkModel = new SinkModel("streamoutput10", "output10");
        List<SinkModel> sinkModelList = new LinkedList<>();
        sinkModelList.add(sinkModel);

        String id = "rule10";
        String version = "v1";
        String executionPlan = "from stream10 select * insert into streamoutput10";

        StreamMapModel streamMapModel = new StreamMapModel(Arrays.asList(sourceModel), Arrays.asList(sinkModel));

        RuleModel ruleModelObject = new RuleModel(id, version, streamMapModel, executionPlan, null);

        List<RuleModel> ruleModelList = new LinkedList<>();
        ruleModelList.add(ruleModelObject);


        List<StreamModel> streamsModel = Arrays.asList(
                new StreamModel("stream10", Arrays.asList(
                        new AttributeModel("attributeName", "string")
                )));

        ProcessingModel processingModel = new ProcessingModel(ruleModelList, streamsModel);

        siddhiController.addProcessingDefinition(processingModel);
        siddhiController.generateExecutionPlans();
        siddhiController.addProcessingModel2KafkaController();

        Properties consumerConfigA = new Properties();
        consumerConfigA.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
        consumerConfigA.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-consumer-A");
        consumerConfigA.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfigA.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerConfigA.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("attributeName", "VALUE");

        KeyValue<String, Map<String, Object>> expectedDataKv = new KeyValue<>(null, expectedData);

        try {
            System.out.println("Producing KV: " + kvStream1);
            IntegrationTestUtils.produceKeyValuesSynchronously("aabb_input10", Collections.singletonList(kvStream1), producerConfig, MOCK_TIME);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        List<KeyValue<String, Map>> receivedMessagesFromOutput1 = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfigA, "aabb_output10", 1);
        System.out.println("Received after Siddhi: " + receivedMessagesFromOutput1);
        assertEquals(Collections.singletonList(expectedDataKv), receivedMessagesFromOutput1);

    }

    @AfterClass
    public static void stop() {
        CLUSTER.stop();
    }


}
