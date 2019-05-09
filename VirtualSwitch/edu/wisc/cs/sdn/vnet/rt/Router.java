package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import java.nio.ByteBuffer;
import net.floodlightcontroller.packet.*;
import java.util.*;
/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
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
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
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
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
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
		// check if the packet is an IPv4
		boolean flag;
		short header_version = etherPacket.getEtherType();
		// if the header version is IPv4 continue
		if(header_version == Ethernet.TYPE_IPv4) {

			// to  get the IPv4 header
			IPv4 header = (IPv4)etherPacket.getPayload();
			// get the checksum from the header
			short check_sum = header.getChecksum();
		//	System.out.println("Original checkSum" +check_sum);
			// set the checksum of the packet to zero
			header.setChecksum((short)0);
			short var_check = header.getChecksum();
			// get the header length
			byte hlen = header.getHeaderLength();
			// serialize() -> not sure
			short header_length = (short)(hlen*4);
			
			int optionsLength = 0;	
			if (header.getOptions() != null) {
				optionsLength = header.getOptions().length / 4;
				header_length = (byte) (hlen + optionsLength);

			}

			byte[] data = new byte[header_length];
			
			ByteBuffer bb = ByteBuffer.wrap(data);
	
			short totalLength = (short) header.getTotalLength();

			bb.put((byte) (((header.getVersion() & 0xf) << 4) | (hlen & 0xf)));

			bb.put(header.getDiffServ());

		//	System.out.println("Diff serv ==> " + header.getDiffServ());
			bb.putShort(totalLength);
		//	System.out.println("total length ==> " + header.getTotalLength());

			bb.putShort(header.getIdentification());
		//	System.out.println("ident==> " + header.getIdentification());

			bb.putShort((short) (((header.getFlags() & 0x7) << 13) | (header.getFragmentOffset() & 0x1fff)));

			bb.put(header.getTtl());
		//	System.out.println("ttl ==> "+header.getTtl());

			bb.put(header.getProtocol());
		//	System.out.println("protocol ==> "+header.getProtocol());

			bb.putShort(header.getChecksum());
		//	System.out.println("checksum ==> " + header.getChecksum());

			bb.putInt(header.getSourceAddress());
		//	System.out.println("src addrs ==> " + header.getSourceAddress());

			bb.putInt(header.getDestinationAddress());
		//	System.out.println("Destination addrs ==> " + header.getDestinationAddress());

			if (header.getOptions() != null){
		//		System.out.println("options ==> " + header.getOptions());

				bb.put(header.getOptions());
			}


			if (var_check == 0) {
		//		System.out.println("hello");
            			bb.rewind();
            			int accumulation = 0;
                          	for (int i = 0; i < hlen * 2; ++i) {
                			accumulation += 0xffff & bb.getShort();
            			}
            			accumulation = ((accumulation >> 16) & 0xffff)
                    				+ (accumulation & 0xffff);

            			short new_checksum= (short) (~accumulation & 0xffff);
				
		//		System.out.println("Calculated Checksum: " +new_checksum);
				//System.out.println("Original: " + check_sum);	
			        // check if the computed checksum is same as the original checksum
				if(check_sum == new_checksum) {

				//	System.out.println("Computed checksum is equal to original");
					header.setChecksum((short) (~accumulation & 0xffff));
					// decrement the ttl
					header.setTtl((byte)(header.getTtl() -1));
					// check for the ttl
					if(header.getTtl() > 0) {
				//		System.out.println("TTL is good");
						// decrement the TTL
						//header.setTtl(header.getTtl() -1);
                                       //         System.out.println("Checksum after TTL is decremented: " +header.getChecksum());
						// get the Keyset()
						Set<String>interface_key_Set = getInterfaces().keySet();
						boolean temp = true;
						// iterate through the keySet()
						for(String interface_names: interface_key_Set) {
								// get the interface through its mapped interface name
								Iface in_face = interfaces.get(interface_names);

								// get the ip address corresponding to the interface
								int in_face_ip = in_face.getIpAddress(); 
								
								// if destIP doesnt match interface IP continue
								if(header.getDestinationAddress() == in_face_ip) {
									temp = false;
									break;
										
								}

						}


								if(temp) {
							
								//	System.out.println("Looking for longest prefix");
									// using the lookup() in routeTable obtain routeEntry
									RouteEntry entry = routeTable.lookup(header.getDestinationAddress());
									
									//If we find a matching entry and the outgoing interface is not the 
									//same as the incoming interface
									if(entry!=null && entry.getInterface() != inIface) {
								//	System.out.println("Found a match");
								//	System.out.println(entry.toString());
									int next_hop_ip = 0;
									// check if the gateway address exists--> not sure about the checking condition
									  if(entry.getGatewayAddress() != 0)	{		
										next_hop_ip = entry.getGatewayAddress();
								//	  	System.out.println("Next hp IP is gatway address" + next_hop_ip);
									   }
									   else{
										next_hop_ip = header.getDestinationAddress();
									
									}
									
									  
									ArpEntry cache_entry = arpCache.lookup(next_hop_ip);
									if(cache_entry == null){
								//		System.out.println("ARP entry not found for next hop");
										return;
									}
									 // get the MACAdd
									   MACAddress entry_mac = cache_entry.getMac();
									 // this address is the new destination mac address for the ethernet frame 
									   byte[] dest_mac_address = entry_mac.toBytes();
									   etherPacket.setDestinationMACAddress(dest_mac_address);
									   

									   int interfaceIP = entry.getInterface().getIpAddress();
									   ArpEntry sourceArp = arpCache.lookup(interfaceIP);
									   
									  if(sourceArp == null){
								//		System.out.println("MAC address not found for " + entry.getInterface());
										return;
									  }

  
									   MACAddress entry_mac2 = sourceArp.getMac();
									   byte[] source_mac_address = entry_mac2.toBytes();
								        // setting the outgoing interfaces's MACAddress as the source MACADD
									   etherPacket.setSourceMACAddress(source_mac_address);	
								
								
									//Statistics for this packet
								//	System.out.println("-----------------------------");
								//	System.out.println("Destination IP address for the packet" + header.getDestinationAddress());
								//	System.out.println("Matched with the entry: " + entry.toString());
								//	System.out.println("The destination MAC address is" + entry_mac.toString());
								//	System.out.println("THe source MAC address is " + entry_mac2.toString());
								//	System.out.println("Destination MAC entry" + cache_entry.toString());
								//	System.out.println("source MAC entry" + sourceArp.toString());


	
									//Correctly update the checksum
								        header.resetChecksum();
									sendPacket(etherPacket, entry.getInterface());										
									}
                                     
								}
					
						}
					

					}

			} 			
						 

		}		
		
	}


}
