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

/**
 * MPJ example to demonstrate how to distribute tasks to workers.
 * 
 * export MPJ_HOME=~/dev/mpj-v0_44/ export PATH=$MPJ_HOME/bin:$PATH mpjrun.sh
 * -cp lib/* -np 2 edu.gslis.main.mpi.HelloWorld
 */
public class HelloWorker {

	public static void main(String args[]) throws Exception {
		
		MPI.Init(args);

		int me = MPI.COMM_WORLD.Rank();
		int size = MPI.COMM_WORLD.Size();

		if (me == 0) {
			System.out.println("Size = " + size);
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
				int[]source = {0};
				Request r1 = MPI.COMM_WORLD.Irecv(source, 0, 1, MPI.INT, MPI.ANY_SOURCE, 0);
				r1.Wait();
				
				System.out.println(me + " received ready from " + source[0]);
				if (source[0] > 0) {
					
					System.out.println(me + " sending task to " + source[0]);
					// Send task object to worker
					ByteBuffer byteBuff = ByteBuffer.allocateDirect(2000 + MPI.SEND_OVERHEAD);
					MPI.Buffer_attach(byteBuff);
					try {
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						ObjectOutput out = null;
						out = new ObjectOutputStream(bos);
						Task task = tasks.remove(0);
						out.writeObject(task);
						byte[] bytes = bos.toByteArray();

						Request r= MPI.COMM_WORLD.Isend(bytes, 0, bytes.length, MPI.BYTE, source[0], 0);
						r.Wait();
						System.out.println(me + " sent " + task + " to " + source[0]);						
					bos.close();
					} catch (IOException ex) {
					}
				}
			}
			
			int j=0;
			while (j < size-1) {				
				// Wait for ready worker
				int[]buf = {0};
				Request r1 = MPI.COMM_WORLD.Irecv(buf, 0, 1, MPI.INT, MPI.ANY_SOURCE, 0);
				r1.Wait();				
				int source = buf[0];
				System.out.println(me + " waiting to send shutdown to " + source);
				
				// Signal all is done
				// Send task object to worker
				ByteBuffer byteBuff = ByteBuffer.allocateDirect(2000 + MPI.SEND_OVERHEAD);
				MPI.Buffer_attach(byteBuff);
				try {
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					ObjectOutput out = null;
					out = new ObjectOutputStream(bos);
					Task sd = new Task("shutdown");
					sd.shutdown = true;
					out.writeObject(sd );
					byte[] bytes = bos.toByteArray();

					Request r= MPI.COMM_WORLD.Isend(bytes, 0, bytes.length, MPI.BYTE, source, 0);
					r.Wait();					
					System.out.println(me + " sent shutdown to " + source);
				bos.close();
				} catch (IOException ex) {
				}
				//buf[0] = -1;
				//System.out.println(me + " sent shutdown to " + source);
				//Request r3 = MPI.COMM_WORLD.Isend(buf, 0, 1, MPI.INT, source, 0);
				//r3.Wait();
				j++;
			}

		} else {
			System.out.println("me = " + me);
			boolean done = false;
			while (!done) 
			{
				// Send ready signal to master
				System.out.println(me + " ready ");
				int[] buf = {me};
				Request r1 = MPI.COMM_WORLD.Isend(buf, 0, 1, MPI.INT, 0, 0);
				r1.Wait();
				
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

		MPI.Finalize();
	}
}