package edu.gslis.main.mpi;

import java.io.Serializable;

public class Task implements Serializable {

	private static final long serialVersionUID = -1779414089848609697L;
	String id = null;		

	
	Task (String id) {
		this.id = id;
	}
	
	boolean shutdown = false;
}
