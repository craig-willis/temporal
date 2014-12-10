package edu.gslis.old.temporal.scorers;

import java.text.DateFormat;

import edu.gslis.docscoring.QueryDocScorer;

public abstract class TemporalScorer extends QueryDocScorer {

    public abstract void setStartTime(long startTime);
    public abstract void setEndTime(long endTime);
    public abstract void setInterval(long interval);
    public abstract void setDateFormat(DateFormat df);
    public abstract void setTsIndex(String path);
    public abstract void close();

}
