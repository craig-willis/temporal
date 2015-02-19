package edu.gslis.temporal.util;

import org.rosuda.REngine.Rserve.RConnection;


public class RUtil {
	

	private RConnection c;

	public RUtil() {
		
		try {
			c = new RConnection();
			

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public double acf(double[] data) throws Exception { 
	    return acf(data, 2);
	}
	public double acf(double[] data, int lag) throws Exception {
        c.assign("x", data);
        c.voidEval("ac <- acf(x, plot=F)");

        return c.eval("ac$acf[" + lag + "]").asDouble();
	}

	public double kurtosis(double[] data) throws Exception {
        c.assign("x", data);
        c.voidEval("library(moments)");

        c.voidEval("k <- kurtosis(x)");

        return c.eval("k").asDouble();	    
	}

   public double skewness(double[] data) throws Exception {
        c.assign("x", data);
        c.voidEval("library(moments)");

        c.voidEval("s <- skewness(x)");

        return c.eval("s").asDouble();      
    }

	public void close() {
		try {
			c.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
