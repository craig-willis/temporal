package edu.gslis.temporal.expansion;

import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Scorable;
import edu.gslis.utils.Stopper;

public class ScorableFeatureVector extends FeatureVector implements Scorable, 
	Comparable<ScorableFeatureVector> 
{

	double score = 0;
	
	public ScorableFeatureVector(Stopper stopper) {
		super(stopper);
	}
	
	public ScorableFeatureVector(double score) {
		super(null);
		this.score = score;
	}

	public void setScore(double score) {
		this.score = score;		
	}

	public double getScore() {
		return score;
	}

	public int compareTo(ScorableFeatureVector o) {
        if (this.score != o.score)
            return Double.compare(this.score, o.score);
        else    
        	return this.toString().compareTo(o.toString());
	}
}
