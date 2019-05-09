import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Random;
import java.math.BigInteger;

//Variables important to the server


public class Receiver {
	long stime;
	//Variables
	int port;
	int mtu;
	int sws;
	int nextByteExpected;
	int lastByteRcvd;
	int lastByteRead;
	int numPackets = 0;
	int dataSegments = 0;
	int discarded = 0;
	int outOfSeq = 0;
	boolean recvb4 = false;
	//String fileName;
	InetAddress clientIp;
	int clientPort;
	
	//The two sockets to handle D and ACKs
	DatagramSocket in_channel;
	DatagramSocket out_channel;

	//File Output Stream
	FileOutputStream fos;

	//InetAddress clientAddress;
	//int clientPort;	

	boolean connectionEstablished;
	boolean synack;
	boolean clientAck;

	public Receiver(int port, int mtu, int sws) throws SocketException, IOException{
		this.stime = System.currentTimeMillis();
		this.port = port;
		this.mtu = mtu;
		this.sws = sws;
		this.nextByteExpected = 0;
		this.lastByteRcvd = 0;
		this.lastByteRead = 0;
		
		this.clientAck = false;
		in_channel = new DatagramSocket(port);
		//out_channel = new DatagramSocket();
		synack = false;
		fos = null;
		//Dont open outstreamn yet
		//fos = new FileOutputStream(fileName);
		run();
	}

	public float ctime(){
		return ((System.currentTimeMillis() - stime)/1000);
	}

	public void run() throws IOException, FileNotFoundException{
		connectionEstablished = false;

		while(true) {

			byte[] dataPacket = new byte[mtu];
			DatagramPacket packet = new DatagramPacket(dataPacket, mtu);
			//System.out.println("---Waiting for packet---");
			in_channel.receive(packet);
			
			//System.out.println("--Received packet ---");

			//Packet data
			ByteBuffer bb = ByteBuffer.wrap(packet.getData());
			//Increment last byte Received

			int seqNumber = bb.getInt(); //Reads 4 bytes(32 bits)
			int ackNumber = bb.getInt();
			long timeStamp = bb.getLong();
			int length = bb.getInt();
			//System.out.println("length in binary" + Integer.toBinaryString(length));
			String dataPortion = Integer.toBinaryString(length);
			//System.out.println("Length field in binary " + dataPortion);
			int padding_length = 32 - dataPortion.length();
			StringBuilder sb = new StringBuilder(padding_length);
			for(int i=0;i<padding_length;i++){
				sb.append('0');
			}
			sb.append(dataPortion);
			String paddedDataPortion = sb.toString();
			//System.out.println("Length after padding " + paddedDataPortion);

			dataPortion = paddedDataPortion.substring(0, 28);
			//System.out.println("Only the first 28 bits " + dataPortion);
			int dataPortionSize = new BigInteger(dataPortion, 2).intValue();
			//System.out.println("the data size is " + dataPortionSize + "seqNum " + seqNumber);
			int checkSum = bb.getInt();
			
			


			//System.out.println("SYN bit is " + getBit(length, 3));

			//Goes into this loop and wait's until we receive SYN segment
			if(!connectionEstablished){

				//System.out.println("Waiting for connection to establish...");
				// check if the SYN flag is received
				if(getBit(length, 3) == 1){
				        
                                        System.out.format("\n rcv  S - - - 0 0 0");
					byte[] fileBuffer = Arrays.copyOfRange(packet.getData(), 24, 24 + dataPortionSize);
					System.out.println(fileBuffer.toString());
					
					String fileToCreate = new String(fileBuffer);
			//		System.out.println("Name " + fileToCreate);

					File file = new File(fileToCreate);
					fos = new FileOutputStream(file);

					//Send a SYN + ACK
					//System.out.println("Initiating connection, file name " + fileToCreate + " , sending back SYN + ACK");
					

					synack = true;		
					sendAck(packet,timeStamp, 3, false);
					System.out.format("\n snd S - A - 0 0 1");
					numPackets++;
				}

				//Receive an ACK
				if(getBit(length, 1) == 1 ){
					System.out.format("\n rcv  - - A - %d 0 %d",  seqNumber, ackNumber);
			//		System.out.println("Received ACK from client, proceed to tranfer");
					connectionEstablished = true;
					clientAck = true;
				}
				
				//If we receive a data segment, that means our syn plus ack was received
				//switch connectionestd to true
				if(getBit(length, 0) == 1){
					dataSegments++;
					System.out.format("\n rcv  - - D - %d %d %d", seqNumber, dataPortionSize, ackNumber);
					connectionEstablished = true;
				}

				
			}



			if(connectionEstablished){
				
				
				// received a data segment
				if(getBit(length,0) == 1){
					dataSegments++;
					System.out.format("\n rcv  - - D - %d %d %d",  seqNumber, dataPortionSize, ackNumber);
					//Drop packet if it is corrupted/Wrong checksum
					if(!checkCsum(seqNumber, checkSum)) {
						System.out.println("Dropping packets");
						sendAck(packet, timeStamp, 2, false);
						discarded++;
						continue;
					}
					
					//In-sequence packet
					if(seqNumber == nextByteExpected) {
						lastByteRcvd += dataPortionSize;	
					        	
						byte[] dataToWrite = Arrays.copyOfRange(packet.getData(), 24, dataPortionSize + 24);
						fos.write(dataToWrite);
						lastByteRead += dataToWrite.length;
						nextByteExpected = lastByteRead;

						//Send an ack
						sendAck(packet, timeStamp, 2, false);
						System.out.format("\n snd  - - A - 0 0 %d",  nextByteExpected);
					}



					//Out-of-sequence packets
					else{
						outOfSeq++;
						//Send the last contigous byte Received as ACK
						System.out.format("\n snd  - - A - 0 0 %d",  lastByteRead);
						sendAck(packet, timeStamp, 2, false);

					}
				}
				// if FIN segment has been received
				else if(getBit(length, 2) == 1){
					System.out.format("\n rcv - F - - %d 0 0", seqNumber);	
					System.out.format("\n snd  - F A - 1 0 %d", seqNumber + 1);
					//sending fin + ack
				        sendAck(packet, timeStamp, 5, true);	
					if(!recvb4){
						recvb4 = true;
					//Once we get the fin segment, we send the fin segment, we print out the final statistics
						System.out.format("\nAmount of data Received: %d bytes", lastByteRead);
						System.out.format("\nNumber of Ack Packets Sent: %d", numPackets);
						System.out.format("\nNumber of data packets received: %d", dataSegments);
						System.out.format("\nNumber of data packets discarded(out of sequence): %d", outOfSeq);
						System.out.format("\nNumber of data packets discarded(wrong checksum): %d", discarded);
				        }	
				}

			}//if




		}//while


	}

	//Method that generates an ACK packet with the appropriate ACK number
	public void sendAck(DatagramPacket p, long timeStamp,int flag, boolean lastPacket) throws IOException{
		numPackets++;
		clientIp = p.getAddress();
		clientPort = p.getPort();

		//Create a message buffer of MTU size
		byte[] data = new byte[20];
		ByteBuffer bb = ByteBuffer.wrap(data);

		bb.putInt(0); //Sequence number
		if(lastPacket){
			bb.putInt(lastByteRead + 1); //this is the ACK number
		}
		else{
			bb.putInt(lastByteRead); //this is the ACK number
		}

		

		bb.putLong(timeStamp); //put in the time stamp, to calculate RTT

		// send a SYN + ACK segment
		String mask = null;

		// ACK
		if(flag == 2){
			mask = "0000000000000000000000000000010";
		}
		// SYN + ACK
		else if(flag == 3 ) {
			mask = "0000000000000000000000000001010";
		}
		// Fin
		else if(flag == 5) {
			mask = "00000000000000000000000000000100";
		}

		int maskValue = new BigInteger(mask, 2).intValue();
		bb.putInt(maskValue);

		DatagramPacket synAckPacket = new DatagramPacket(data, data.length, clientIp, clientPort);
		in_channel.send(synAckPacket);
		

	}

	

	//Verify the checksum, if the calculated checksum != checksum received, error
	public boolean checkCsum(int seqNum, int checksumRcvd) {
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

		int calculatedChecksum = new BigInteger(result, 2).intValue();

		if(calculatedChecksum != checksumRcvd)
			return false;

		return true;
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



	public int getBit(int n, int k) {
		return (n >> k) & 1;
	}
}
