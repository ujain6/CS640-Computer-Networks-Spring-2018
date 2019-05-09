import java.util.*;
import java.net.*;
import java.io.*;

public class Iperfer {

	public static int Server(int portNumber){

		byte[] message = new byte[1000];


		try {
				//Server socket tries to bind with its port
					ServerSocket serverSocket = new ServerSocket(portNumber);



				//Start accepting connection from the client,
				//accept method waits until client requests a connection

				//clientSocket is created after accept returns, it is connected to
				//the host name and port of the client, the ori
					Socket clientSocket = serverSocket.accept();
			//	System.out.println("hchdsnc");

					InputStream in = clientSocket.getInputStream();

					//Continue reading packets from the input stream
					int bytesRead = 0;
					//Read until the client closes the connection

					double start = System.currentTimeMillis();
					while(in.read() != -1){

							//Read a 1000 byte chunk from the input stream into the buffer
							bytesRead += in.read(message,0,1000);

					}
					clientSocket.close();
					serverSocket.close();
					double end = System.currentTimeMillis();

						double time_elapsed = end - start;
								double rate = ((double)((8.0*bytesRead)/(1000*1000))/(double)(time_elapsed/1000));
				System.out.println("received=" + (bytesRead/1000) + " KB " + "rate=" + String.format("%.3f", rate) + " Mbps" );
					//Calculate bandwidth
					//System.out.println("received=" + (bytesRead/1024) + "KB ");
					//System.out.print("rate=" + ((bytesRead/2048)/(end-start)/1000) + "Mbps"); //Mbps

					//clientSocket.close();
		}
		catch(IOException e){
			System.err.println("Error IO Exception\n");
			//System.out.println(e);
		}
		return 0;
	}
	public static int Client(String host, int portNumber, double time){

		byte packets[] = new byte[1000];

		try(
				Socket client = new Socket(host,portNumber);
				OutputStream ClientSocket = client.getOutputStream();
		 ) {
			//System.out.println("Enter\n");

			double start = System.currentTimeMillis();
			double end = start + time*1000;
			double num_bytes =0.0;

			while(System.currentTimeMillis() < end) {
				ClientSocket.write(packets, 0, packets.length);
				num_bytes++;
			}

			double data = num_bytes*packets.length;


			double end_time = System.currentTimeMillis();

			double time_elapsed = end_time - start;

			double time_elapsed_in_seconds = time_elapsed/1000;

			client.close();

			double data_received = data/1000;

			double bandwidth = (data*8/Math.pow(10.0,6.0))/(time_elapsed_in_seconds);

			System.out.println("sent="+data_received+" KB rate="+ String.format("%.3f", bandwidth) +" Mbps");

		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			System.err.println("Host is unknown\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.err.println("IO Exception\n");
		}
		return 0;
	}

	public static void main(String[] args) throws IOException{
		// TODO Auto-generated method stub
		try{

			String hostname = "";
			int port = 0;
			double time = 0.0;
		if(args[0].equals("-c")){

			for(int i = 1; i<args.length;i++) {

				switch(args[i]) {

				case "-c": continue;

				case "-h":
					//System.out.println("hostname " + args[i+1]);
					hostname = args[i+1];
					break;
				case "-p":
					port = Integer.parseInt(args[i+1]);
					if(port < 1024 || port > 655356){
						System.err.println("Error: port number must be in the range 1024 to 65535\n");
						System.exit(1);
					}
					break;
				case "-t":
					time = Integer.parseInt(args[i+1]);
					break;

				}
			}
			Client(hostname,port,time);
		}
		else if(args[0].equals("-s")){
			
			for(int i = 1; i<args.length;i++) {

				switch(args[i]) {
					case "-p":
						port = Integer.parseInt(args[i+1]);
						if(port < 1024 || port > 655356){
							System.err.println("Error: port number must be in the range 1024 to 65535\n");
							System.exit(1);
						}
						break;
				}
			}
			Server(port);
		}
		else{
			System.err.println("Missing or additional arguments\n");
			System.exit(1);
		}

		}catch(ArrayIndexOutOfBoundsException e){
			System.err.println("Error: missing or additional arguments\n");
			System.exit(1);
	}



	}

}
