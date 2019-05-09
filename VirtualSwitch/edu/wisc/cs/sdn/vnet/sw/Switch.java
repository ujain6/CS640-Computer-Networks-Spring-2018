package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.*;
/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{
	final int TTL = 15;
	HashMap<String,tableEntry> hashmap = new HashMap<String, tableEntry>();
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
	}


	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));

		/********************************************************************/

		System.out.println("Packet received on "+ inIface.getName());

		/* TODO: Handle packets                                             */
		//HashMap<String,tableEntry> hashmap = new HashMap<String, tableEntry>();

		String sourceMACAddress = etherPacket.getSourceMAC().toString();


		String destinationMACAddress = etherPacket.getDestinationMAC().toString();

		//First record the incoming link
		if(hashmap.containsKey(sourceMACAddress)){
		      //  System.out.println("New entry recieved for " + sourceMACAddress + " resetting entry");
			//Reset its entry
		//	hashmap.get(sourceMACAddress).setInterface(inIface);
			hashmap.get(sourceMACAddress).setTime(System.currentTimeMillis());
		}
		else{
			//System.out.println("Creating new record for " + sourceMACAddress + " on interface " + inIface.getName());
			tableEntry newEntry = new tableEntry(inIface);
			hashmap.put(sourceMACAddress, newEntry);
		}


		/***************************************************/
		//Switching starts here

		//If the destination host is present in the table
		if(hashmap.containsKey(destinationMACAddress)){
			//System.out.println("Entry found for " + destinationMACAddress);
			//Get the matching interface and timestamp of that MACAddress
			tableEntry entry = hashmap.get(destinationMACAddress);

			//The interface to which that destination is attached
			Iface outgoingInterface = entry.getInterface();

			//If the host is on the same segment on which the packet was received
			/*if(outgoingInterface == inIface){
				//Drop the frame
			        System.out.println("Same interface, dropping frame");
				return;
			}*/

			//If not, use the entry if not old than 15 seconds
		

				//If the entry has timed out, reset that entry from the table
				//and flood all hosts
				long currTime = System.currentTimeMillis();
				//System.out.println("CUrrent time is" + currTime);
				//System.out.println("Entry time is " + entry.getTime());
				if((currTime - entry.getTime())/1000 > TTL){
					//System.out.println("Entry timed out, flooding");
					//Remove that entry
					hashmap.remove(destinationMACAddress);
					//Flood all hosts
					floodAllHosts(inIface, etherPacket);
			 	}
				else{   
					//System.out.println("Entryf ound for destination, sending to corresponsing interface");
					//System.out.println("Sending packet on " + outgoingInterface.getName());
					//Use the table to send the packet to the appropriate IP address
					sendPacket(etherPacket, outgoingInterface);
				}

			

		}

		/********************************************************************/
	//If no route is found to the
	else{
		//System.out.println("No entry found for destination, flooding all interfaces");
		//Flood all hosts
		floodAllHosts(inIface, etherPacket);

	}

}//handlePacket ends her


	public void floodAllHosts(Iface inIface, Ethernet etherPacket){

		//Flood all hosts
		Map<String, Iface> map = getInterfaces();

		for(String name : map.keySet()){

			Iface flood = map.get(name);
			if(flood.getName().compareTo(inIface.getName()) != 0){
				//Send the packet on the interface
				//System.out.println("Sending packet on flood interface + " + flood.getName());
				sendPacket(etherPacket, flood);
			}
		}
	}

}//Class ends here
class tableEntry{

	Iface inIface;
	long timeStamp;

	public tableEntry(Iface newInterface){
		//Set the timeStamp
		timeStamp = System.currentTimeMillis();
		inIface = newInterface;
	}

	public long getTime(){
		return timeStamp;
	}

	public Iface getInterface(){
		return inIface;
	}

	public void setTime(long timeStamp){
		this.timeStamp = timeStamp;
	}

	public void setInterface(Iface newIface){
		this.inIface = newIface;
	}
}
