package showTraj;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;
import org.bytedeco.javacpp.opencv_core;
import topology.Serializable;
import topology.StreamFrame;

import java.util.Map;

import static topology.StormConfigManager.getInt;
import static topology.StormConfigManager.getString;
import static showTraj.Constant.*;

/**
 * Created by Intern04 on 5/8/2014.
 */
public class RedisFrameAggregatorBolt2 extends BaseRichBolt {
    OutputCollector collector;

    RedisStreamProducer producer;

    private String host;
    private int port;
    private String queueName;
    private int accumulateFrameSize;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {

    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {

        host = getString(map, "redis.host");
        port = getInt(map, "redis.port");
        queueName = getString(map, "redis.queueName");
        this.collector = outputCollector;

        accumulateFrameSize = Math.max(getInt(map, "accumulateFrameSize"), 1);

        producer = new RedisStreamProducer(host, port, queueName, accumulateFrameSize);
        new Thread(producer).start();

    }

    // Fields("frameId", "frameMat", "patchCount")
    // Fields("frameId", "foundRectList")
    @Override
    public void execute(Tuple tuple) {
        int frameId = tuple.getIntegerByField(FIELD_FRAME_ID);
        Serializable.Mat sMat = (Serializable.Mat) tuple.getValueByField(FIELD_FRAME_MAT);
        opencv_core.Mat mat = sMat.toJavaCVMat();
        producer.addFrame(new StreamFrame(frameId, mat));

        System.out.println("finishedAdd: " + System.currentTimeMillis() + ":" + frameId);
        collector.ack(tuple);
    }
}