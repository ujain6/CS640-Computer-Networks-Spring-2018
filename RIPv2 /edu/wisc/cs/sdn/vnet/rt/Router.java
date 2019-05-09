package edu.wisc.cs.sdn.vnet.rt;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;


//import net.floodlightcontroller.packet.Ethernet;
//import net.floodlightcontroller.packet.UDP;
//import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.*;
import java.lang.Thread;
import java.util.Timer;
import java.util.TimerTask;


/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;


	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/** Timers for ping and garbage collector */
	private Timer timer1;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		//Calls a routetable function
	
		this.arpCache = new ArpCache();


		//Add the directly connected interfaces
		//initialize();
//		timers();

	}
	
	//If not using this, we start the timers on the routeTable to delete old entries
	public void routeTableGarbage(){

		this.routeTable.garbageCollector();	

	}

	public void initialize(){

		//Initialize the router table to add the router's
		//own interfaces
//		System.out.println("1.) Adding directly connected interfaces");
		for(Iface iface : this.interfaces.values()) {

			int mask = iface.getSubnetMask();
			int destination = iface.getIpAddress() & mask;

			//Cost with 0
			routeTable.insert(destination, 0, mask, iface, 0);
			//Send the initial RIP request
//			System.out.println("---- Sending INITIAL REQUEST on interface " + iface.getName());
			sendRip(iface, true, 1);

		}
		
	}

	public void timers() {
		
		this.timer1 = new Timer();
		//Ping starts immediately, runs every 10 seconds
		this.timer1.scheduleAtFixedRate(new pingRIP(), 0, 10000);
	}

	/**
	 * 
	 * Function that sends regular unsolicited RESPPNSES every 10 secsonds
	 * 
	 */

	public void ping(){

			for(Iface iface : this.interfaces.values()) {
				//System.out.println("---- Pinging interface " + iface.getName());
				sendRip(iface, true, 2);

			}

	}



	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }

	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			//			System.err.println("Error setting up routing table from file "
			//					+ routeTableFile);
			//			System.exit(1);
			//If we're not loading the route table, oad returns false
			return;
		}

		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}

	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			//			System.err.println("Error setting up ARP cache from file "
			//					+ arpCacheFile);
			//			System.exit(1);
			return;
		}

		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}
	public boolean UDPchecks(IPv4 ipPacket) {
		UDP udpPacket = (UDP) ipPacket.getPayload();
		short origCKsum = udpPacket.getChecksum();
		udpPacket.resetChecksum();
		byte[] serialized = udpPacket.serialize();
		udpPacket.deserialize(serialized, 0, serialized.length);
		short calcCKsum = udpPacket.getChecksum();
		if (calcCKsum != origCKsum) {
			return false;
		}
		if (udpPacket.getDestinationPort() != UDP.RIP_PORT) {
			return false;
		}
		if (udpPacket.getSourcePort() != UDP.RIP_PORT) {
			return false;
		}
		return true;
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
		/* TODO: Handle packets                                             */

		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			this.handleIpPacket(etherPacket, inIface);
			break;
			// Ignore all other packet types, for now
		}

		/********************************************************************/
	}

	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{	

		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();


		// Verify checksum
		short origCksum = ipPacket.getChecksum();
		ipPacket.resetChecksum();
		byte[] serialized = ipPacket.serialize();
		ipPacket.deserialize(serialized, 0, serialized.length);
		short calcCksum = ipPacket.getChecksum();
		if (origCksum != calcCksum)
		{ return; }

		// Check TTL
		ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
		if (0 == ipPacket.getTtl())
		{ return; }

		// Reset checksum now that TTL is decremented
		ipPacket.resetChecksum();
		
		boolean forwardOrNot = true;

		for (Iface iface : this.interfaces.values()) {

				if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
					forwardOrNot = false;
				}
					if (ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) {

						if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
						}
						if (UDPchecks(ipPacket)
								|| (ipPacket.getDestinationAddress() == iface.getIpAddress())) {
							forwardOrNot = false;
							handleRip(etherPacket, inIface);
						}
					}
		}

		
		if (forwardOrNot) {
				this.forwardIpPacket(etherPacket, inIface);
	        }


		
		
}
	
	private void handleRip(Ethernet etherPacket, Iface inIface) {
		//Get the RIP packet from inside the IP header
		IPv4 ipPacket = (IPv4) etherPacket.getPayload();
		UDP udp = (UDP) ipPacket.getPayload();
		//Verify the UDP checksum
		
		short origCksum = udp.getChecksum();
		udp.resetChecksum();
		byte[] serialized = udp.serialize();
		udp.deserialize(serialized, 0, serialized.length);
		short calcCksum = udp.getChecksum();
		if (origCksum != calcCksum)
		{ return; }

		//Check if the incoming UDP packet has destination port number 520
		if(udp.getDestinationPort() != udp.RIP_PORT)
		{	return; }

		//After you verify that it is an RIP message, you read in the router entries from the packet
		RIPv2 rip = (RIPv2)udp.getPayload();

		//Check if the packet is a RESPONSE or a REQUEST

		//Send a RIP packet
		if(rip.getCommand() == rip.COMMAND_REQUEST) {
			//Send the packet

			if ( ipPacket.getDestinationAddress() == IPv4.toIPv4Address("224.0.0.9") && etherPacket.getDestinationMAC().toLong() == MACAddress.valueOf("FF:FF:FF:FF:FF:FF").toLong());
			{
	//			System.out.println("Got a request packet, sending broadcast RESPONSE packet");
				//BROADCAST = TRUE, RESPONSE(2)
				this.sendRip(inIface, true, 2);
			}

		}
		//Update your table depending upon the information inside 
		//the RIP packet
		else if(rip.getCommand() == rip.COMMAND_RESPONSE) {
	//		System.out.println("Got a response RIP, processing the RIPv2 entries");
			updateRouteTable(rip, inIface);
			//System.out.println(routeTable.toString());
		}
	}

	
	/**
	 * 
	 * Sends a request/response type RIP packet
	 * 
	 * @param iface
	 * @param broadcast - if the request is to be a broadcast
	 * @param type - type of request - RESPONSE/REQUEST
	 * 
	 */
	private void sendRip(Iface iface, boolean broadcast, int type){
		

	//	System.out.println("SENDING RIP PACKET");
		Ethernet etherPacket = new Ethernet();
		etherPacket.setEtherType(etherPacket.TYPE_IPv4);		
		etherPacket.setSourceMACAddress("FF:FF:FF:FF:FF:FF");
		UDP udp = new UDP();

		//Create the UDP payload
		RIPv2 rip = new RIPv2();

		if(type == 1){
			//System.out.println("SENDING REQUEST---");
			rip.setCommand(rip.COMMAND_REQUEST);
		}
		else{
			//System.out.println("SENDING RESPONSE---");
			rip.setCommand(rip.COMMAND_RESPONSE);
		}

		//Set the UDP data
		udp.setSourcePort(udp.RIP_PORT);
		udp.setDestinationPort(udp.RIP_PORT);

		//Set into the IP packet
		IPv4 ip = new IPv4();
		ip.setVersion((byte)4);
		ip.setProtocol(IPv4.PROTOCOL_UDP);
		ip.setSourceAddress(iface.getIpAddress());
		//Send the RIP packets to all hosts, broadcast response
		if(broadcast){
			//System.out.println("BROADCAST PACKET");
			ip.setDestinationAddress("224.0.0.9");
			etherPacket.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
		}
		//If a particular host requests a RIP response
		else{
			//System.out.println("Sending response to " + iface.getIpAddress() + " MAC addr " + iface.getMacAddress().toString());
			ip.setDestinationAddress(iface.getIpAddress());
			etherPacket.setDestinationMACAddress(iface.getMacAddress().toString());
		}

		//If we have to send a reponse, then we send all the entries present
		//in our database, as RIPv2 entries in the rip packets
		if(type == 2){
	//		System.out.println("Preparing to send a response, packing in the database");
			
			CopyOnWriteArrayList<RouteEntry> entries = routeTable.getEntries();
			synchronized(entries){
				for(RouteEntry entry :  entries){

					int nextHop = iface.getIpAddress();
					RIPv2Entry ripAdvert = new RIPv2Entry(entry.getDestinationAddress(), 
							entry.getMaskAddress(), entry.getMetric());
				
					ripAdvert.setNextHopAddress(nextHop);
					rip.addEntry(ripAdvert);
	//				System.out.println("Added "+ ripAdvert.toString());

				}	
			}

		}

		//Set the payloads
		udp.setPayload(rip);
		ip.setPayload(udp);
		etherPacket.setPayload(ip);

		//Finally send the packet
		etherPacket.serialize();
	//	System.out.println("Packet sent ======>>");
		this.sendPacket(etherPacket, iface);
		return;
	}

	private void updateRouteTable(RIPv2 rip, Iface inIface){
	

	//	 System.out.println("----Processing RIP packet------");
		//Get the router entries from the packet
		List<RIPv2Entry> ripv2Entries = rip.getEntries();

		//Now, this information has costs to different routers
		//Use them to update your costs
		for(RIPv2Entry entry : ripv2Entries) {

			int destinationAddress = entry.getAddress();
			int mask = entry.getSubnetMask();
			int gwIp = entry.getNextHopAddress();			
			//If route is not present in the table, only add
			//if not one of the directly connected routers
			//Was using find fucntion
			RouteEntry corresEntry = routeTable.lookup(entry.getAddress());
			
			//If we dont have that address in our directory, then we add it
			if(corresEntry == null){
	//			System.out.println("Adding entry " + entry.getAddress());
				
				routeTable.insert(destinationAddress, gwIp, mask, inIface, entry.getMetric() + 1);
				sendRip(inIface, false, 2);
			}
			//If the route is already there
			else{
			
					
							
					int oldCost = corresEntry.getMetric();
					//New cost is the 1 + cost advertised by the entry
					int newCost = 1 + entry.getMetric();

					//Update the route entry to bbe the new informAIOTN
					
					if(newCost < oldCost){

						routeTable.update(destinationAddress, mask, inIface, newCost);
	//					System.out.println("Updated the metric from " + oldCost + "to " + newCost);
						sendRip(inIface, false, 2);		
			
					}
	
					

			}
		}

	}

	private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		System.out.println("Forward IP packet");

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		int dstAddr = ipPacket.getDestinationAddress();

		// Find matching route table entry 
		RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

		// If no entry matched, do nothing
		if (null == bestMatch)
		{ return; }

		// Make sure we don't sent a packet back out the interface it came in
		Iface outIface = bestMatch.getInterface();
		if (outIface == inIface)
		{ return; }

		// Set source MAC address in Ethernet header
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop)
		{ nextHop = dstAddr; }

		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if (null == arpEntry)
		{ return; }
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
		this.sendPacket(etherPacket, outIface);
	}


class pingRIP extends TimerTask{
	
	public void run(){

		ping();
	}
	

}


}

