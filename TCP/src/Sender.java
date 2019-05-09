import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.math.BigInteger;

public class Sender  {
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_RED_BACKGROUND = "\u001B[41m";
	public static final String ANSI_WHITE = "\u001B[37m";
	public static final String ANSI_GREEN_BACKGROUND = "\u001B[42m";
	public static final String ANSI_PURPLE_BACKGROUND = "\u001B[45m";
	long startingTime;
	int port;
	InetAddress remoteIp;
	int remotePort;
	String fileName;
	int mtu;
	int sws;
	boolean seenb4 = false;
	//Fields relevant to the sender
	Random rand = new Random();
	int msl = 120; //Maximum segment life is 120 seconds
	long TIME = System.currentTimeMillis();
	//Last byte sent
	int seqNum;
	int numAcksRcvd = 0;
	//Last byte acked
	int lastByteAcked;

	//The advertised window
	long timeOut;

	//Starting index for file
	int index = 0;

	//Number of retransmissions
	int retransmit = 0;
	int dupAcks = 0;
	int numPackets = 0;

	//The byte form of the file
	byte[] fileByteArray;
	
	//Bytes sent
	int bytesSentForThisPacket;
	int totalBytesSent;
	
	boolean connectionEstd ;
	
	boolean firstPacket;

	//To store unacked packets
	HashMap<Integer, byte[]> buffer;
	HashMap<Integer, Integer> maxRetransmit;

	LinkedList<timeOut> objectList;
	LinkedList<Timer> timerList;
	ArrayList<Integer> retransmittedSegments;
	InputStream inFromFile;

	//Create two sockets for outgoing and incoming channels
	DatagramSocket in_channel;
	DatagramSocket out_channel;

	//Create two threads
	outGoing threadOne;
	inComing threadTwo;

	//Timers
	Timer timer;

	//Creates a data packet for the file with the appropriate sequence number, flag and data bytes
	//1 for SYN, 2 for ACK, 3 for SYN+ACK, 4 for DATA, 5 for FIN
	public byte[] generatePacket(int sequenceNumber, int ackNumber, int flag, int effWindow) {
		//System.out.println("********************************");
		//Create a message buffer of MTU size
		
		byte[] data = null;


		//*****
		byte[] sampleB = fileName.getBytes( );
		int sampleSize = sampleB.length;

		if(flag == 1){

			
			data = new byte[24 + sampleSize];
		
		}
		else{
			data = new byte[effWindow + 24];
		}

		//*****

		ByteBuffer bb = ByteBuffer.wrap(data);

		bb.putInt(sequenceNumber); //4 bytes

		//Put zeros for acks
		if(ackNumber == -1) {
			bb.putInt(0);
		}
		else {
			bb.putInt(ackNumber);
		}

		bb.putLong(System.nanoTime()); // 8 bytes of the time stamp

		//This part, only when we're sending data
		if(flag == 4) {
			//The remaining bytes of the buffer are filled with the file information
			for(int i = 24; i < data.length; i++) {
				//If reached the last index of the file
				if(index == fileByteArray.length - 1) {
					
					data[i] = fileByteArray[index];
					bytesSentForThisPacket++;
					break;
				}
				data[i] = fileByteArray[index++];
				
				bytesSentForThisPacket++;
			}
			
			//Amount of data to send
			int dataLength = (bytesSentForThisPacket << 4);
			//Create a mask and and those two numbers
			String mask = "00000000000000000000000000000001";

			int maskValue = new BigInteger(mask, 2).intValue();
			int header = maskValue | dataLength;


			bb.putInt(header);

			//Put in the checksum
			
			bb.put(checksum(sequenceNumber));


			
		}

		else {
			int nameIndex = 0;
			//Put in the checksum
			String mask = null;
			//SYN
			if(flag == 1) {
				
				
				byte[] nameBuffer = fileName.getBytes();
				int fileNameSize = nameBuffer.length;
				//Fill the data portion till
				
				//Write file name into buffer
				for(int i = 24; i < 24 + fileNameSize; i++){
					data[i] = nameBuffer[nameIndex++]; 
				}
					

				int dataLength = (fileNameSize << 4);
				mask = "00000000000000000000000000001000";
		

		                int maskValue = new BigInteger(mask, 2).intValue();
                		int header = maskValue | dataLength;
                		
				
        		
				//put in file name length + SYN flag
                        	bb.putInt(header);

                        	//Put in the checksum
                         	bb.put(checksum(sequenceNumber));
 	
					
			}
				
			
			//ACK
			else if(flag == 2) {
				mask = "00000000000000000000000000000010";
			int maskValue = new BigInteger(mask, 2).intValue();
			bb.putInt(maskValue);

			//Add the checksum
			bb.putInt(0);
			}
			//SYN+ACK
			else if(flag == 3) {
				mask = "00000000000000000000000000001010";
			int maskValue = new BigInteger(mask, 2).intValue();
			bb.putInt(maskValue);

			//Add the checksum
			bb.putInt(0);
			}
			else if(flag == 5) {
				mask = "00000000000000000000000000000100";
			int maskValue = new BigInteger(mask, 2).intValue();
			bb.putInt(maskValue);

			//Add the checksum
			bb.putInt(0);
			}
		}
		return data;
	}

	public byte[] checksum(int seqNum) {

				
		byte[] checksumBuffer = new byte[4];
		String binary = Integer.toBinaryString(seqNum);
		String padding = null;
		if(binary.length() != 32) {

			int offset = 32 - binary.length();
			StringBuilder sb = new StringBuilder();
			for(int i = 0; i < offset; i++)
				sb.append('0');

			sb.append(binary);
			
			padding = sb.toString();
		}

		String part1 = padding.substring(0, 16);
		String part2 = padding.substring(16);

		//Adding the 2 16-bit values
		String result = addBinary(part1, part2);
		//Taking 1's complement
		result = invert(result);
		int checksum = new BigInteger(result, 2).intValue();
		ByteBuffer bb = ByteBuffer.wrap(checksumBuffer);
		bb.putInt(checksum);

		return checksumBuffer;
	}

	public String addBinary(String p1, String p2) {

		// Initialize result
		String result = "";

		// Initialize digit sum
		int s = 0;

		// Travers both strings starting
		// from last characters
		int i = p1.length() - 1, j = p2.length() - 1;
		while (i >= 0 || j >= 0 || s == 1)
		{

			// Comput sum of last
			// digits and carry
			s += ((i >= 0)? p1.charAt(i) - '0': 0);
			s += ((j >= 0)? p2.charAt(j) - '0': 0);

			// If current digit sum is
			// 1 or 3, add 1 to result
			result = (char)(s % 2 + '0') + result;

			// Compute carry
			s /= 2;

			// Move to next digits
			i--; j--;
		}

		return result;

	}

	public String invert(String binary) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < binary.length(); i++) {
			if(binary.charAt(i) == '0') {
				sb.append('1');
			}
			else {
				sb.append('0');
			}
		}
		return sb.toString();

	}


	public class outGoing extends Thread{

		//The outgoing channel of the packet runs
		public void run() {

			byte[] data = null;

			try {

				//Keep sending packets as long as the sliding window has enough space for an MSS,
				while(true) {
					if(!connectionEstd){

						//seqNum = 0, -1, 1 syn, 0 data
						data = generatePacket(seqNum, -1, 1, 0);

						DatagramPacket synPacket = new DatagramPacket(data, data.length, remoteIp, remotePort);
						out_channel.send(synPacket);
						System.out.format("\nsnd  S - - - 0 0 0");
						try{
							//Sleep this thread for 5000 seconds, if connection is false, again send a
							sleep(5000);
							continue;

						} catch(InterruptedException e){
							//SYN+ACK received,
							connectionEstd = true;
						}
					}


					if(connectionEstd) {
						
						int effWindow = calcEffWin(seqNum, lastByteAcked);

						//If we have reached the last index of our file, send a FIN segment
						//with zero bytes of data
						if(index == fileByteArray.length - 1) {
							
							data = generatePacket(seqNum, -1, 5, 0);
							buffer.put(seqNum, data);
							DatagramPacket finPacket = new DatagramPacket(data, data.length, remoteIp, remotePort);
							out_channel.send(finPacket);
							numPackets++;
							//Set a timer for this fin segment, ack we sent wuold be the seq number of this 
							//fin segment plus 1
							//Create a new timer object
							timerList.add(new Timer());
							//Create a new timer task reference, SET TIMER FOR ACK# SEQ + 1
							objectList.add(new timeOut(seqNum + 1, 0));
							//Give the newly create task to this new timer
							timerList.getLast().schedule(objectList.getLast(), timeOut);
							System.out.format("\nsnd  - F - - %d 0 0",  seqNum);
							break; //Of the while loop, stop sending packets
							
							
						}
						else {
							if(effWindow > 0) {

								data = generatePacket(seqNum, -1, 4, effWindow);

								//Put the sent packet on the unacked queue
								buffer.put(seqNum, data);

								//Put into a datagram packet and send it

								DatagramPacket dataPacket = new DatagramPacket(data, data.length, remoteIp, remotePort);

								out_channel.send(dataPacket);
								numPackets++;
								
								//Start the timer task on the packet
								int timerId = seqNum + bytesSentForThisPacket;
								

								//Create a new timer object
								timerList.add(new Timer());
								//Create a new timer task reference
								objectList.add(new timeOut(timerId, bytesSentForThisPacket));
								//Give the newly create task to this new timer
								timerList.getLast().schedule(objectList.getLast(), timeOut);

								//ack number is 0 for these
								System.out.format("\nsnd  - - - D %d %d 1",  seqNum, bytesSentForThisPacket);
								seqNum += bytesSentForThisPacket;
								totalBytesSent += bytesSentForThisPacket; //Increment the total bytes sent
								bytesSentForThisPacket = 0; //Reset this value
							}

							else if(effWindow == 0) {
								//System.out.println(ANSI_PURPLE_BACKGROUND + ANSI_WHITE + "\n Effective window reduced to zero, sleeping for 2.5 secs" + ANSI_RESET);
								Thread.sleep(2500);
								
							}
						}


					}
				} //While loop
				
			} catch(IOException e) {

				e.printStackTrace();

			} catch(InterruptedException e) {
				e.printStackTrace();
			}
		}

		public int calcEffWin(int lastByteSent, int lastByteAcked) {
			return sws - (lastByteSent - lastByteAcked);
		}



	}

	//Process ACKs and FIN segments
	public class inComing extends Thread{

		long estRTT;
		long estDev;
		long sdDev;


		public void run() {

			try {

				int count = 0;


				while(true) {

					boolean ack = false;
					boolean fin = false;
					byte data[] = new byte[mtu];

					DatagramPacket p = new DatagramPacket(data, mtu);
					out_channel.receive(p);


					//Packet data
					ByteBuffer bb = ByteBuffer.wrap(p.getData());

					bb.getInt();
					int ackRcvd = bb.getInt(); //Reads 4 bytes (32 bits)
					long ackTimestamp = bb.getLong(); //Timestamp of the ack
					int length = bb.getInt();


					//If connection hasn't been established yet
					//if not connected, check this packet for SYN+ACK
					//Keeps going back until we receive a SYN+ACK
					if(!connectionEstd) {


						//Check if this packet has the bits set, S + A
						if(getBit(length, 3) == 1 && getBit(length, 1) == 1) {

						        System.out.format("\nrecv  S - A - 0 0 1");
							//We have received the SYN+ACK packet, wake up our
							//processing thread, after sending an ack for this

							if(ackRcvd == 0) {
								connectionEstd = true;

								//Send an ack WITH number 0
								sendAck(p, ackTimestamp, 0);

								threadOne.interrupt();

							}

						}
						else {
							continue;
						}

					}

					if(connectionEstd){
						
						//Check if this packet has the bits set, S + A
						if(getBit(length, 3) == 1 && getBit(length, 1) == 1) {

						        System.out.format("\nrecv S - A - 0 0 1");
							//We have received the SYN+ACK packet, wake up our
						
							sendAck(p, ackTimestamp, 0);							

						}
					}


					//Check what bit is set in the packet
					if(getBit(length, 1) == 1)
						ack = true;
					if(getBit(length, 2) == 1)
						fin = true;

					//If ACK is there
					if(ack) {
						System.out.format("\nrcv - - A - 0 0 %d", ackRcvd);
						//Check if the packet was received in order
						if(ackRcvd >= lastByteAcked + 1) {
							
							removeTimer(ackRcvd); 
				
							
							//Whenever we receive an ACK, delete the packets in our buffer
	
							//Increment lastAckedbyte acked
							lastByteAcked  = ackRcvd;
							numAcksRcvd++;
							//Calculate the RTT from the packet
							//Only calculate timeout for segments that have not been retransmitted
							if(!retransmittedSegments.contains(ackRcvd)){
								calcTimeout(ackTimestamp);
							}
							//Remvove packets that have been acked
							Iterator<Integer> it = buffer.keySet().iterator();
							while(it.hasNext()){
								int seqToRem = it.next(); 
								if(seqToRem < lastByteAcked){
									it.remove();
								}
							}

						}
						else if(ackRcvd == lastByteAcked) {
							//System.out.println(ANSI_GREEN_BACKGROUND + ANSI_WHITE + "Receiving previously recieved ACK count = " + count + ANSI_RESET);
							//Received a triple ack
							if(count == 3) {	
								
								//Retransmit the packet
								retransmit();
								//Start a retransmission timer
								count = 0;
							}
							else if(count == 2) {
								count++;
								dupAcks++; //Duplicate ack
							}
							else {
								count++;
							}
						}

						if(ackRcvd == fileByteArray.length){
							
							//if(!seenb4){
							//	seenb4 = true;
							//}
							
						}



					}
					//Check for FIN flag
					else if(fin){

						//Send an ACK packet of zero length
						byte[] finSegment = generatePacket(seqNum, -1, 5, 0);
						DatagramPacket finPacket = new DatagramPacket(finSegment, 24, remoteIp, remotePort);
						out_channel.send(finPacket);
						numPackets++;
						in_channel.close(); //Close the output channel, but still listening to connections
						//Start a timer for the fin segment
						objectList.add(new timeOut(seqNum, 0));
						System.out.format("\nrcv - F A - 1 0 %d", ackRcvd);
						break;
					}

				} //When the last ack has been received
								System.out.println("Amount of Data Transferred " + totalBytesSent);
								System.out.println("Number of packets sent " + numPackets);
								System.out.println("No of Retransmissions" + retransmit);
								System.out.println("No of Duplicate Acknowledgements" + dupAcks);
				//Close the inputStream
				inFromFile.close();



			} catch(IOException e) {
				e.printStackTrace();
			}

		}

		//Method that generates an ACK packet with the appropriate ACK number
		public void sendAck(DatagramPacket p, long timeStamp, int ack) throws IOException{


			//Create a message buffer of MTU size
			byte[] data = new byte[24];
			ByteBuffer bb = ByteBuffer.wrap(data);
			bb.putInt(0); //For empty sequence number
			bb.putInt(ack); //this is the ACK number
			bb.putLong(timeStamp); //put in the time stamp, to calculate RTT
                       
			//ACK flag is set
			String mask = "00000000000000000000000000000010";
			int maskValue = new BigInteger(mask, 2).intValue();
			bb.putInt(maskValue);

			DatagramPacket ackPacket = new DatagramPacket(data, 24, remoteIp, remotePort);
			//System.out.println("Sent ACK #" + ack);
			out_channel.send(ackPacket);
			System.out.format("\nsnd - - A - - 0 0 1");

		}

		public int getBit(int n, int k) {
			return (n >> k) & 1;
		}
		//fast retransmit
		public void retransmit() throws IOException{
			//System.out.println(ANSI_GREEN_BACKGROUND + ANSI_WHITE +"FAST RETRANSMITTING segment #" + lastByteAcked + ANSI_RESET);
			//Get the corresponding segment from this sequence number
			byte[] retransmission = buffer.get(lastByteAcked);
			
			ByteBuffer bb = ByteBuffer.wrap(retransmission);
			bb.getInt();
			bb.getInt();
			bb.getLong();
			int length = bb.getInt();
			length = calcLen(length);
			//using seqNum + number of bytes in this packet as the timer Id, that is
			//the ack recvd
			//Remove the timer for that ack
			

			DatagramPacket p = new DatagramPacket(retransmission, retransmission.length, remoteIp, remotePort);
			out_channel.send(p);
			System.out.format("snd - - - D %d %d 1", lastByteAcked, length);	
			removeTimer(lastByteAcked + length);
			//When we fast retran
			//Add this sequence to retransmitted segments so that we don't
			//calculate their RTT
			retransmittedSegments.add(lastByteAcked);
			
			numPackets++;
			retransmit++;
		}

		public void removeTimer(int ackNumber){
						
	
			//Remove the timer, ackRcvd => timer we need to remove
			for(timeOut timerThread : objectList){
				if(timerThread.getSeq() == ackNumber){
											
																	
					//Timer thread remove
					timerThread.cancel();
					
				}
			}
	
		}

		public void calcTimeout(long timeStamp) {


			if(numAcksRcvd == 1) {
				//the time stamps are in nano seconds, we convert them to milliseconds
				//and calculate our RTT
				estRTT = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - timeStamp);
				estDev = 0;
				timeOut = 2 * estRTT;
			}
			else {

				long sampleRTT = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - timeStamp);
				sdDev = Math.abs(sampleRTT - estRTT);
				estRTT = (long) (0.875 * estRTT + 0.125 * sampleRTT);
				estDev = (long) (0.75 * estDev + 0.25 * sdDev);
				timeOut = estRTT + 4*estDev;
			}

		}


	}

	//Sender constructor
	public Sender(int port, String remoteIp, int remotePort, String fileName, int mtu, int sws) throws SocketException, IOException {
		this.startingTime = System.currentTimeMillis();
		//Starting seq number is 0
		this.seqNum = 0;
		this.port = port;

		this.remoteIp = InetAddress.getByName(remoteIp);
		this.remotePort = remotePort;
		this.fileName = fileName;
		this.mtu = mtu;
		this.sws = sws;
		this.timeOut = 5000; //Timeout initially set to 5 seconds
		this.buffer = new HashMap<Integer, byte[]>();
		this.maxRetransmit = new HashMap<Integer, Integer>(); 
		this.objectList = new LinkedList<timeOut>();
                this.timerList = new LinkedList<Timer>();
		this.retransmittedSegments = new ArrayList<Integer>();
		connectionEstd = false;
	        firstPacket = true;	
 		bytesSentForThisPacket = 0;
		totalBytesSent = 0;
		//Activate the channels

		in_channel = new DatagramSocket(this.port);
		out_channel = new DatagramSocket();

		//Open the file for streaming
		inFromFile = new FileInputStream(fileName);

		//Convert file to byte array
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];

		for (int readNum; (readNum = inFromFile.read(buf)) != -1;) {
			bos.write(buf, 0, readNum); //no doubt here is 0
			//Writes len bytes from the specified byte array starting at offset off to this byte array output stream.

		}

		fileByteArray = bos.toByteArray();
		System.out.println(fileByteArray.length);

		//Start the two threads
		threadOne = new outGoing();
		threadTwo = new inComing();
		threadOne.start();
		threadTwo.start();

	}

	public class timeOut extends TimerTask  {

		//Sequence number for which to check the timer for
		int ackNumber;
		int bytesSent;

		public timeOut(int ackNumber, int bytesSent) {
			this.ackNumber = ackNumber;
			this.bytesSent = bytesSent;
		}


		public void run() {

			//Time out occured, send the segment for which this timer was set, get
			//the segment from the buffer send it
			int seqToCheck = ackNumber - bytesSent;
			
			for(Integer seqNum : buffer.keySet()) {
				if(seqNum == seqToCheck){
						
					//ADD THE ACK# TO RETRANSMITTED
					//retransmittedSegments.add(this.ackNumber);
					if(maxRetransmit.containsKey(this.ackNumber)){
						//Increment its copunt by 16
						int oldCount = maxRetransmit.get(this.ackNumber);
						if(oldCount == 16){
						
							//Throw an error on this process
							System.err.println("Reached maximum retransmission limit for " + (ackNumber - bytesSent) + " CLOsing down");
							System.exit(1);

						}
						maxRetransmit.put(this.ackNumber, maxRetransmit.get(this.ackNumber) + 1 );
					}
					//Add this to the hashmap
					
	
				//	System.out.format("\nsegment found in buffer, resending segment #%d", seqToCheck);
					byte[] data = buffer.get(seqToCheck);
					DatagramPacket p = new DatagramPacket(data, data.length, remoteIp, remotePort);
					ByteBuffer bb = ByteBuffer.wrap(data);
					bb.getInt();
					bb.getInt();
					bb.getLong();
					int dataLength = bb.getInt();
					dataLength = calcLen(dataLength);


					try {	
						//Out the channel
						out_channel.send(p);
						System.out.format("snd - - - D %d %d 1", seqToCheck, bytesSent);
						numPackets++;
						//First remove the task associated with the same seq number
						//from the object list
						if(objectList.indexOf(ackNumber) != -1 ){
							objectList.remove(objectList.indexOf(ackNumber));
						}
						timerList.add(new Timer());
						objectList.add(new timeOut(ackNumber, dataLength));
						timerList.getLast().schedule(objectList.getLast(), timeOut);
	
					
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

			
				}
			}
		
			

			

		}
		

		
		
		public int getSeq(){
			return ackNumber;
		}

		public int getBytes(){
			return bytesSent;
		}

	}

		public int calcLen(int length){

			String dataPortion = Integer.toBinaryString(length);
                        int padding_length = 32 - dataPortion.length();
                        StringBuilder sb = new StringBuilder(padding_length);
                        for(int i=0;i<padding_length;i++){
                                sb.append('0');
                        }
                        sb.append(dataPortion);
                        String paddedDataPortion = sb.toString();

                        dataPortion = paddedDataPortion.substring(0, 28);
                        int dataPortionSize = new BigInteger(dataPortion, 2).intValue();
			return dataPortionSize;	

		}
		
		public long cTime(){
			return (System.currentTimeMillis() - startingTime)/1000 ; 
		}

}
