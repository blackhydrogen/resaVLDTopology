package topology;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;
import logodetection.Debug;
import logodetection.Util;
import org.bytedeco.javacpp.opencv_core;
import tool.RedisStreamProducerBeta;
import util.ConfigUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static tool.Constants.*;
import static topology.StormConfigManager.getInt;
import static topology.StormConfigManager.getString;

/**
 * Created by Intern04 on 5/8/2014.
 */
public class tRedisFrameAggregatorBeta extends BaseRichBolt {
    OutputCollector collector;

    RedisStreamProducerBeta producer;

    //int lim = 31685; // SONY
    private HashMap<Integer, List<Serializable.Rect>> processedFrames;
    private HashMap<Integer, Serializable.Mat> frameMap;
    private List<Serializable.Rect> listHistory;

    private String host;
    private int port;
    private String queueName;
    private int persistFrames;
//    private int accumulateFrameSize;
    private int sleepTime;
    private int startFrameID;
    private int maxWaitCount;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {

    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {

        host = getString(map, "redis.host");
        port = getInt(map, "redis.port");
        queueName = getString(map, "redis.queueName");
        processedFrames = new HashMap<>();
        frameMap = new HashMap<>();
        this.collector = outputCollector;

        persistFrames = Math.max(getInt(map, "persistFrames"), 1);
//        accumulateFrameSize = Math.max(getInt(map, "accumulateFrameSize"), 1);
        this.sleepTime = ConfigUtil.getInt(map, "sleepTime", 10);
        this.startFrameID = ConfigUtil.getInt(map, "startFrameID", 1);
        this.maxWaitCount = ConfigUtil.getInt(map, "maxWaitCount", 4);
        listHistory = null;

        opencv_core.IplImage fk = new opencv_core.IplImage();

        producer = new RedisStreamProducerBeta(host, port, queueName, startFrameID, maxWaitCount, sleepTime);
        new Thread(producer).start();

    }

    // Fields("frameId", "frameMat", "patchCount")
    // Fields("frameId", "foundRectList")
    @Override
    public void execute(Tuple tuple) {
        String streamId = tuple.getSourceStreamId();
        int frameId = tuple.getIntegerByField(FIELD_FRAME_ID);

        if (streamId.equals(PROCESSED_FRAME_STREAM)) {
            List<Serializable.Rect> list = (List<Serializable.Rect>) tuple.getValueByField(FIELD_DETECTED_RECT);
            if (!processedFrames.containsKey(frameId)) {
                processedFrames.put(frameId, list);
                //System.out.println("addtoProcessedFrames: " + System.currentTimeMillis() + ":" + frameId);
            }
        } else if (streamId.equals(RAW_FRAME_STREAM)) {
            Serializable.Mat sMat = (Serializable.Mat) tuple.getValueByField(FIELD_FRAME_MAT);
            if (!frameMap.containsKey(frameId)) {
                frameMap.put(frameId, sMat);
                //System.out.println("addtoFrameMap: " + System.currentTimeMillis() + ":" + frameId);
            } else {
                if (Debug.topologyDebugOutput)
                    System.err.println("FrameAggregator: Received duplicate message");
            }
        }

        if (frameMap.containsKey(frameId) && processedFrames.containsKey(frameId)) {
            opencv_core.Mat mat = frameMap.get(frameId).toJavaCVMat();
            List<Serializable.Rect> list = processedFrames.get(frameId);

            if (frameId % persistFrames == 0) {
                listHistory = list;
            } else if (listHistory == null) {
                listHistory = list;
            } else {
                list = listHistory;
            }

            if (list != null) {
                for (Serializable.Rect rect : list) {
                    Util.drawRectOnMat(rect.toJavaCVRect(), mat, opencv_core.CvScalar.MAGENTA);
                }
            }
            producer.addFrame(new StreamFrame(frameId, mat));
            System.out.println("finishedAdd: " + System.currentTimeMillis() + ":" + frameId);
            processedFrames.remove(frameId);
            frameMap.remove(frameId);
        }
        System.out.println("finished: " + System.currentTimeMillis() + ":" + frameId);
        collector.ack(tuple);
    }
}