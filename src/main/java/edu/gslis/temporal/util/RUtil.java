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
	
   public RUtil(int port) {
        
        try {
            c = new RConnection("localhost", port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	
   public int[] minima(int[] x, double[] y) throws Exception {
       
       c.assign("x", x);
//       c.assign("y", y);
       c.assign("weights", y);
       c.voidEval("weights = weights / sum(weights)");
       
//       c.voidEval("y <- as.vector(y)");
//       c.voidEval("dens <- density(y, bw=\"sj\")");
       c.voidEval("dens <- density(x, weights=weights, bw=\"sj\")");
       c.voidEval("second_deriv <- diff(sign(diff(dens$y)))");
       c.voidEval("min <- which(second_deriv == 2) + 1");
       
       //c.voidEval("max <- which(second_deriv == -2) + 1");
       c.voidEval("mp <- dens$x[min] / (max(dens$x) - min(dens$x))");
       return c.eval("x[length(x)*mp]").asIntegers();
   }
   
   
	public double acf(double[] data) throws Exception { 
	    return acf(data, 2);
	}
	
	public double silvermantest(double[] data, int modes) throws Exception {
	    c.voidEval("library(silvermantest)");
        c.assign("x", data);
        return c.eval("silverman.test(x, " + modes + ")@p_value").asDouble();	    	
	}
	
	public double ccf(double[] x, double[]y, int lag) throws Exception  {
        c.assign("x", x);
        c.assign("y", y);
        c.voidEval("cc <- ccf(x, y, plot=F)");

        return c.eval("cc$acf[which(cc$lag == " + lag + ")]").asDouble();	    
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
