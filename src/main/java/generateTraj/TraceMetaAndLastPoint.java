package generateTraj;

import topology.Serializable;

/**
 * Created by Tom.fu on 2/12/2014.
 */
public class TraceMetaAndLastPoint implements java.io.Serializable {
    public String traceID;//useless
    public Serializable.CvPoint2D32f lastPoint;
    public int countersIndex;

    public TraceMetaAndLastPoint(String traceID, Serializable.CvPoint2D32f point, int countersIndex){
        this.traceID = traceID;
        this.lastPoint = new Serializable.CvPoint2D32f(point);
        this.countersIndex = countersIndex;
    }
}
