package edu.gslis.main.mpi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import mpi.MPI;
import mpi.Request;

public class MPIHelper 
{
	int me = -1;
	int size = -1;
	
	public static void main(String args[]) throws Exception {
	}
	
	public void init(String[] args) {		
		MPI.Init(args);

		this.me = MPI.COMM_WORLD.Rank();
		this.size = MPI.COMM_WORLD.Size();
	}
	
	public void sendInt(int id, int i) throws Exception {
		int[] buf = {i};
		Request r1 = MPI.COMM_WORLD.Isend(buf, 0, 1, MPI.INT, 0, 0);
		r1.Wait();
	}

	public int recvInt() throws Exception {
		int[] i = {0};
		Request r1 = MPI.COMM_WORLD.Irecv(i, 0, 1, MPI.INT, MPI.ANY_SOURCE, 0);
		r1.Wait();
		return i[0];
	}
	
	public void sendObject(int id, Object obj) throws Exception{
		ByteBuffer byteBuff = ByteBuffer.allocateDirect(2000 + MPI.SEND_OVERHEAD);
		MPI.Buffer_attach(byteBuff);
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutput out = null;
			out = new ObjectOutputStream(bos);
			out.writeObject(obj);
			byte[] bytes = bos.toByteArray();

			Request r= MPI.COMM_WORLD.Isend(bytes, 0, bytes.length, MPI.BYTE, id, 0);
			r.Wait();
			System.out.println(me + " sent " + obj + " to " + id);						
		bos.close();
		} catch (IOException ex) {
		}
	}
	
	
	public void run() throws Exception {

		if (me == 0) {
			// Primary thread/node distributes work to others
			List<Task> tasks = new ArrayList<Task>();
			tasks.add(new Task("task1"));
			tasks.add(new Task("task2"));
			tasks.add(new Task("task3"));
			tasks.add(new Task("task4"));
			tasks.add(new Task("task5"));
			tasks.add(new Task("task6"));

			while (tasks.size() > 0) 
			{
				// Wait for ready worker
				int source = recvInt();
				
				System.out.println(me + " received ready from " + source);
				if (source > 0) {
					
					System.out.println(me + " sending task to " + source);
					
					Task task = tasks.remove(0);
					sendObject(source, task);				
				}
			}
			
			int j=0;
			while (j < size-1) {
				
				// Wait for ready worker
				int source = recvInt();
				System.out.println(me + " waiting to send shutdown to " + source);
				
				// Signal all is done
				// Send task object to worker
				Task sd = new Task("shutdown");
				sd.shutdown = true;
				sendObject(source, sd);

				j++;
			}

		} else {
			System.out.println("me = " + me);
			boolean done = false;
			while (!done) 
			{
				// Send ready signal to master
				System.out.println(me + " ready ");
				sendInt(me, 0);

				System.out.println(me + " waiting ");
				// Receive task object
				byte[] bytes = new byte[2000];
				Request r2 = MPI.COMM_WORLD.Irecv(bytes, 0, 2000, MPI.BYTE, MPI.ANY_SOURCE, 0);
				r2.Wait();
				
				Task task = null;
				ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
				ObjectInput in = null;
				try {
					in = new ObjectInputStream(bis);
					Object obj = in.readObject();
					task = (Task) obj;
					System.out.println(me + " received " + task);
					if (task.shutdown) {
						System.out.println (me + " shutdown");
						done = true;
						break;							
					}
				} catch (IOException ex) {
				} catch (ClassNotFoundException cnf) {
				}
				bis.close();

				/*
				if (buf[0] == -1) {
					System.out.println (me + " done");
					done = true;
					break;
				}
				System.out.println(me + " received " + buf[0]);
				*/
				Thread.sleep(5000);							
			}
		}
	}
		
	public void finalized() {
		MPI.Finalize();
	}
}
