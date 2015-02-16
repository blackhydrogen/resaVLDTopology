package generateTraj;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import org.bytedeco.javacpp.opencv_imgproc;
import topology.Serializable;
import util.ConfigUtil;

import java.util.Map;

import static org.bytedeco.javacpp.opencv_core.*;
import static tool.Constant.*;

/**
 * Created by Tom Fu
 * Input is raw video frames, output optical flow results between every two consecutive frames.
 * Maybe use global grouping and only one task/executor
 * Similar to frame producer, maintain an ordered list of frames
 * <p>
 * Modified on Feb 7, level I improvement
 */
public class imagePrepareGamma2 extends BaseRichBolt {
    OutputCollector collector;

    IplImage frame, image, grey;
    IplImagePyramid grey_pyramid;
    IplImage eig;
    IplImagePyramid eig_pyramid;

    static int scale_num = 1;
    static float scale_stride = (float) Math.sqrt(2.0);
    static int ixyScale = 0;

    double min_distance;
    double quality;
    int init_counter;


    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;
        this.frame = null;
        this.image = null;
        this.grey = null;

        this.grey_pyramid = null;

        this.min_distance = ConfigUtil.getDouble(map, "min_distance", 5.0);
        this.quality = ConfigUtil.getDouble(map, "quality", 0.001);
        this.init_counter = ConfigUtil.getInt(map, "init_counter", 1);
    }

    @Override
    public void execute(Tuple tuple) {
        int frameId = tuple.getIntegerByField(FIELD_FRAME_ID);
        IplImage imageFK = new IplImage();
        Serializable.Mat sMat = (Serializable.Mat) tuple.getValueByField(FIELD_FRAME_MAT);
        frame = sMat.toJavaCVMat().asIplImage();

        if (this.image == null || frameId == 0) { //only first time
            image = cvCreateImage(cvGetSize(frame), 8, 3);
            image.origin(frame.origin());

            grey = cvCreateImage(cvGetSize(frame), 8, 1);
            grey_pyramid = new IplImagePyramid(scale_stride, scale_num, cvGetSize(frame), 8, 1);

            this.eig_pyramid = new IplImagePyramid(scale_stride, scale_num, cvGetSize(this.grey), 32, 1);
        }

        cvCopy(frame, image, null);
        opencv_imgproc.cvCvtColor(image, grey, opencv_imgproc.CV_BGR2GRAY);
        grey_pyramid.rebuild(grey);

        IplImage grey_temp = cvCloneImage(grey_pyramid.getImage(ixyScale));

        Mat gMat = new Mat(grey_temp);
        Serializable.Mat sgMat = new Serializable.Mat(gMat);

        //collector.emit(STREAM_OPT_FLOW, tuple, new Values(frameId, sfMat, mbhMatX, mbhMatY));
        collector.emit(STREAM_GREY_FLOW, tuple, new Values(frameId, sgMat));

        int width = cvFloor(grey.width() / min_distance);
        int height = cvFloor(grey.height() / min_distance);
        if (frameId > 0) {
//            List<NewDensePoint> newPoints = new ArrayList<>();
//            if (frameId % init_counter == 0) { ///every init_counter frames, generate new dense points.
//
            this.eig = cvCloneImage(eig_pyramid.getImage(ixyScale));

            double[] maxVal = new double[1];
            maxVal[0] = 0.0;
            opencv_imgproc.cvCornerMinEigenVal(grey, this.eig, 3, 3);
            cvMinMaxLoc(eig, null, maxVal, null, null, null);
            double threshold = maxVal[0] * quality;
            int offset = cvFloor(min_distance / 2.0);
//
//                for (int i = 0; i < height; i++) {
//                    for (int j = 0; j < width; j++) {
//                        int x = cvFloor(j * min_distance + offset);
//                        int y = cvFloor(i * min_distance + offset);
//
//                        FloatBuffer floatBuffer = eig.getByteBuffer(y * eig.widthStep()).asFloatBuffer();
//                        float ve = floatBuffer.get(x);
//
//                        NewDensePoint np = new NewDensePoint(x, y, j, i);
//                        ///Level I by tom, we send out all the new dense points as a group.
//                        //int coutersIndex = LastPoint.calCountersIndexForNewTrace(j, i, width);
//                        if (ve > threshold) {
//                            //collector.emit(STREAM_NEW_TRACE, tuple, new Values(frameId, lp, coutersIndex));
//                            newPoints.add(np);
//                        }
//                    }
//                }
//            }
//            ///We require that, every round, this message will be sent out!
            Mat eigMat = new Mat(eig);
            Serializable.Mat sEigMat = new Serializable.Mat(eigMat);
            collector.emit(STREAM_EIG_FLOW, tuple, new Values(frameId, sEigMat, new EigRelatedInfo(width, height, offset, threshold)));
        }
        collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(STREAM_GREY_FLOW, new Fields(FIELD_FRAME_ID, FIELD_FRAME_MAT));
        outputFieldsDeclarer.declareStream(STREAM_EIG_FLOW, new Fields(FIELD_FRAME_ID, FIELD_FRAME_MAT, FIELD_EIG_INFO));
    }
}
