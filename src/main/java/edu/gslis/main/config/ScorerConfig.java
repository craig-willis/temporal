package edu.gslis.main.config;

import java.util.Map;

public class ScorerConfig {
    public String name;
    public String className;
    public String expander;
    public Map<String, Object> params;
    public String priorPath;
    String numFeedbackTerms;
    String numFeedbackDocs;
    String beta;
    String sd;
    String lambda;
    
    public double[] betaArray = new double[]{0.5};
    public double[] lambdaArray = new double[]{0.5};
    public int[] numFeedbackTermsArray = new int[]{20};    
    public int[] numFeedbackDocsArray = new int[]{20};
    public double[] sdArray = new double[]{1};
    
    
    public void setPriorPath(String priorPath) {
        this.priorPath = priorPath;
    }
    
    public String getPriorPath() {
        return priorPath;
    }
    
    public double[] getStdDev() {
        return sdArray;
    }
    
    public void setStdDev(String sdStr) {
        if (sdStr.contains(",")) {
            String[] values = sdStr.split(",");
            sdArray = new double[values.length];
            for (int i=0; i<values.length; i++)
                sdArray[i] = Double.parseDouble(values[i]);
        }
        else
            sdArray = new double[]{ Double.valueOf(sdStr) };
    }
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getClassName() {
        return className;
    }
    public void setClassName(String className) {
        this.className = className;
    }
    public String getExpander() {
        return expander;
    }
    public void setExpander(String expander) {
        this.expander = expander;
    }
    public Map<String, Object> getParams() {
        return params;
    }
    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public double[] getBetaArray() {
        return betaArray;
    }
    
    public String getBeta() {
        return beta;
    }
    
    public void setBeta(String betaStr) {
        this.beta = betaStr;
        if (betaStr.contains(",")) {
            String[] values = betaStr.split(",");
            betaArray = new double[values.length];
            for (int i=0; i<values.length; i++)
                betaArray[i] = Double.parseDouble(values[i].trim());
        }
        else
            betaArray = new double[]{ Double.valueOf(betaStr) };
    }
    
    public double[] getLambdaArray() {
        return lambdaArray;
    }

    public String getLambda() {
        return lambda;
    }

    public void setLambda(String lambdaStr) {
        this.lambda = lambdaStr;
        if (lambdaStr.contains(",")) {
            String[] values = lambdaStr.split(",");
            lambdaArray = new double[values.length];
            for (int i=0; i<values.length; i++)
                lambdaArray[i] = Double.parseDouble(values[i].trim());
        }
        else
            lambdaArray = new double[]{ Double.valueOf(lambdaStr) };
    }
    
    public String getNumFeedbackTerms() {
        return numFeedbackTerms;
    }
    public int[] getNumFeedbackTermsArray() {
        return numFeedbackTermsArray;
    }
    public void setNumFeedbackTerms(String numFeedbackTermsStr) {
        this.numFeedbackTerms = numFeedbackTermsStr;

        if (numFeedbackTermsStr.contains(",")) {
            String[] values = numFeedbackTermsStr.split(",");
            numFeedbackTermsArray = new int[values.length];
            for (int i=0; i<values.length; i++)
                numFeedbackTermsArray[i] = Integer.parseInt(values[i].trim());
        }
        else
            numFeedbackTermsArray = new int[]{ Integer.parseInt(numFeedbackTermsStr) };
    }
    
    public int[] getNumFeedbackDocsArray() {
        return numFeedbackDocsArray;
    }
    
    public String getNumFeedbackDocs() {
        return numFeedbackDocs;
    }
    public void setNumFeedbackDocs(String numFeedbackDocs) {
        this.numFeedbackDocs = numFeedbackDocs;
        if (numFeedbackDocs.contains(",")) {
            String[] values = numFeedbackDocs.split(",");
            numFeedbackDocsArray = new int[values.length];
            for (int i=0; i<values.length; i++)
                numFeedbackDocsArray[i] = Integer.parseInt(values[i].trim());
        }
        else
            numFeedbackDocsArray = new int[]{ Integer.parseInt(numFeedbackDocs) };
    }
    
}
