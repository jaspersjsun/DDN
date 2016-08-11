package frontend;

import java.io.*;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.json.JSONObject;

/**
 * Retrive the info of updates from file and send them to Kafka server
 *
 * Author: Shijie Sun
 * Email: septimus145@gmail.com
 * August, 2016
 */

public class InfoSender implements Runnable {

    protected String brokerList = "";		// list of broker
    protected String hostName = "";		// name of current host
    protected String clusterID = "";
    public KafkaProducer<String, String> producer = null;	// kafka producer
    public ConcurrentHashMap<String, String> group2ClusterMap = null;

    public InfoSender( String hostName, String brokerList, String clusterID, ConcurrentHashMap<String, String> group2ClusterMap ) {
        this.hostName = hostName;
        this.brokerList = brokerList;
        this.clusterID = clusterID;
        this.group2ClusterMap = group2ClusterMap;
        // setup producer
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", brokerList);
        producerProps.put("acks", "all");
        producerProps.put("retries", 0);
        producerProps.put("batch.size", 16384);
        producerProps.put("linger.ms", 1);
        producerProps.put("buffer.memory", 33554432);
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        this.producer = new KafkaProducer<String, String>(producerProps);
    }

    public void run() {
        while(true) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                File file = new File("/var/www/info/info_queue");
                File file2 = new File("/var/www/info/info_queue2");
                file.renameTo(file2);
            } catch (Exception e2) {
                //System.err.println("Change file Exception: " + e2.getMessage());
            }
            try (BufferedReader br = new BufferedReader(new FileReader("/var/www/info/info_queue2"))) {
                String line;
                String topic;
                int i = 0;
                // foreach record
                while ((line = br.readLine()) != null) {
                    i++;
                    // if record's group belongs to current cluster, topic is "internal_groups", otherwise topic is "external_groups"
                    JSONObject jObject = new JSONObject(line);
                    String cluster = group2ClusterMap.get(jObject.getString("group_id"));
                    if (cluster.equals(this.clusterID))
                        topic = "internal_groups";
                    else
                        topic = "external_groups";
                    jObject.put("cluster_id", cluster);
                    ProducerRecord<String, String> data = new ProducerRecord<>(topic, jObject.toString());
                    this.producer.send(data);
                }
                System.out.printf("Send %d msgs!\n",i);
            } catch (Exception e3) {
                //System.err.println("Read file Exception: " + e3.getMessage());
            }
            try {
                File file = new File("/var/www/info/info_queue2");
                file.delete();
            } catch (Exception e4) {
                //System.err.println("Deletc file Exception: " + e4.getMessage());
            }
        }
    }
}
