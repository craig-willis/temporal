package edu.gslis.main.mpi;

import java.io.Serializable;

import edu.gslis.queries.GQuery;
import edu.gslis.utils.Stopper;

public class QueryTask implements Serializable {

	private static final long serialVersionUID = -1779414089848609697L;
	
	public String getCollectionName() {
		return collectionName;
	}
	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}
	public String getIndexPath() {
		return indexPath;
	}
	public void setIndexPath(String indexPath) {
		this.indexPath = indexPath;
	}
	public String getScorerClass() {
		return scorerClass;
	}
	public void setScorerClass(String scorerClass) {
		this.scorerClass = scorerClass;
	}
	public String getScorerName() {
		return scorerName;
	}
	public void setScorerName(String scorerName) {
		this.scorerName = scorerName;
	}
	public long getStartTime() {
		return startTime;
	}
	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}
	public long getEndTime() {
		return endTime;
	}
	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}
	public String getParamStr() {
		return paramStr;
	}
	public void setParamStr(String paramStr) {
		this.paramStr = paramStr;
	}
	public GQuery getQuery() {
		return query;
	}
	public void setQuery(GQuery query) {
		this.query = query;
	}
	public Stopper getStopper() {
		return stopper;
	}
	public void setStopper(Stopper stopper) {
		this.stopper = stopper;
	}
	String collectionName;
	String indexPath;
	String scorerClass;
	String scorerName;
	long startTime;
	long endTime;
	String paramStr;
	GQuery query;
	Stopper stopper;
	String queryFileName;
	String outputDir;
	
	public String getOutputDir() {
		return outputDir;
	}
	public void setOutputDir(String outputDir) {
		this.outputDir = outputDir;
	}
	public boolean shutdown=false;

	public String getQueryFileName() {
		return queryFileName;
	}
	public void setQueryFileName(String queryFileName) {
		this.queryFileName = queryFileName;
	}
}
