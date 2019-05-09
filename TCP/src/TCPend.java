import java.io.IOException;

public class TCPend {

	public static void main(String[] args) throws IOException{

		int port = -1;
		String remoteIp = null;
		int remotePort = -1;
		String fileName = null;
		int mtu = -1;
		int sws = -1;
		
		// Parse arguments
		for(int i = 0; i < args.length; i++)
		{
			String arg = args[i];

			if(arg.equals("-p"))
			{ port = Integer.parseInt(args[++i]); }
			else if(arg.equals("-s"))
			{ remoteIp = args[++i]; }
			else if(arg.equals("-a"))
			{ remotePort = Integer.parseInt(args[++i]); }
			else if(arg.equals("-f"))
			{ fileName = args[++i]; }
			else if(arg.equals("-m"))
			{ mtu = Integer.parseInt(args[++i]); }
			else if(arg.equals("-c"))
			{ sws = Integer.parseInt(args[++i]); }
		}
		
		
		
		//Sender or Receiver
		//java TCPend -p <port> -s <remote-IP> -a <remote-port> –f <file name> -m <mtu> -c <sws>
		if(fileName != null) {
			//Starts the receiver
			new Sender(port, remoteIp, remotePort, fileName, mtu, sws);

		}
	    //java TCPend -p <port> -m <mtu> -c <sws>
		else {
			new Receiver(port, mtu, sws);
				
		}
		

	}

}
