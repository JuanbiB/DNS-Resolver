import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

class SendDNS {

    static final short DNSPORT = 53;
    static int verbose = 0;
    static int recursive = 0;

    static ArrayList<String> readFile(String file_name) throws FileNotFoundException {
	ArrayList<String> arrList = new ArrayList<String>();

	File file = new File(file_name);
	Scanner sc = new Scanner(file);

	while (sc.hasNextLine()){
	    arrList.add(sc.nextLine());
	}
	return arrList;
    }

    static void usage() {
	System.out.println("Usage: java SendDNS <-r -n -v> <server-ip-address> <hostname>");
	System.exit(1);
    }

    static byte[] parseInetAddress(String inetAddr) throws NumberFormatException {
	String[] sp = inetAddr.split("\\.");
	byte[] addr = new byte[4];
	if(sp.length!=4){
	    throw new NumberFormatException();
	}
	for(int i=0; i<4; i++){
	    int x = Integer.parseInt(sp[i]);
	    if(x<0 || x>255)
		throw new NumberFormatException();
	    addr[i] = (byte) x;
	}
	return addr;
    }

    static public void main(String[] args) throws IOException {
	boolean debug = false;
	ArrayList<String> rootservers = readFile("root-servers.txt");	
	String nameserver = null;
	String hostname = null;
	
	/* test arguments first */
	if(args.length < 1) {
	    usage();
	}
	
	// if no nameserver is provided, then use one from rootservers.
	
	/* If user doesn't provide a name server, we provide it for them. */
	// no flags are provided at all in this instance.
	else if(args.length == 1) {
	    nameserver = rootservers.get(0);
	    hostname = args[0];
	}
	// options with 2 arguments. probably want verbose and recursive flags.
	/* java SendDNS -v <host-name> 
	 * java SendDNS -r <host-name> */
	else if (args.length == 2) {
	    if (args[0].equals("-v")) {
		verbose = 1;
	    }
	    else if (args[0].equals("-r")) {
		recursive = 1;
	    }
	    // need to handle recursive shit still
	    nameserver = rootservers.get(0);
	    hostname = args[1];
	}
	// possible options with 3 arguments.
	/* java SendDNS -n <server-ip-address> <host-name> - specifying nameserver. 
	 * java SendDNS -r -v <host-name>*/
	else if (args.length == 3) {
	    if (args[0].equals("-n")) {
		nameserver = args[1];
		hostname = args[2];
	    }
	    /* This means that only the -r and -v commands were specified,
	     * so we can just turn those flags on and set the hostname
	     * and nameserver appropriately. */
	    else  {
		verbose = 1;
		recursive = 1;
		nameserver = rootservers.get(0);
		hostname = args[2];
	    }
	}
	// possible options with 4 arguments
	/* java SendDNS -v -n <name-server> <host-name> - specifying verbose and nameserver 
	 * java SendDNS -r -n <name-server> <host-name> - specifying recursive and nameserver*/
	else if (args.length == 4) {
	    nameserver = args[2];
	    hostname = args[3];
	    if (args[0].equals("-v")) {
		verbose = 1;
	    }
	    else if (args[0].equals("-r")) {
		recursive = 1;
	    }
	}
	// when all possible arguments are specified
	/* java SendDNS -r -v -n <name-server> <host-name> */
	else if (args.length == 5) {
	    recursive = 1;
	    verbose = 1;
	    nameserver = args[3];
	    hostname = args[4];
	}
	else {
	    System.out.println("Woah, too many args.\n");
	}

	System.out.printf("Sending DNS Query (%s) to server %s\n", hostname, nameserver);

	byte[] serveraddr = null;
	try {
	    serveraddr = parseInetAddress(nameserver);
	}
	catch(NumberFormatException e){
	    System.out.printf("Invalid nameserver %s\n",nameserver);
	    System.exit(1);
	}

	boolean found = false;
	DNS result = sendRequest(serveraddr, hostname);
	List<DNS.ResourceRecord> name_servers = new ArrayList<DNS.ResourceRecord>();	
	List<DNS.ResourceRecord> alias_servers = new ArrayList<DNS.ResourceRecord>();
	String found_ip = "";
	while (true){
	    if (result != null) {
		/* if the -v flag is specified, then we print the response each time. */
		if (verbose == 1) {
		    System.out.print("Response:\n");
		    System.out.println(result);
		}
		name_servers.clear();
		alias_servers.clear();
		for (DNS.ResourceRecord rec: result.rrlist) {
		    /* We found it, this is what we were looking for. All of our lives. */
		    if (rec.type == 1 && rec.name.equals(hostname) && rec.value != null) {
			found_ip = rec.value;
			found = true;
		    }
		    /* This is a name server we can query. */
		    else if (rec.type == 1 && rec.value != null) {
			name_servers.add(rec);
		    }
		    /* This is an alias name for the host. */
		    else if (rec.type == 5 && rec.value != null) {
			System.out.println("Found an alias for " + hostname + ": " + rec.value);
			alias_servers.add(rec);
			hostname = rec.value;
		    }
		}
		if (found) break;
		/* There's no finding this chump. No aliases to refer to and no name server to hit up. */
		if (name_servers.isEmpty() && alias_servers.isEmpty()) {
		    System.out.println("Host " + hostname + " could not be resolved.");
		    return;
		}
		serveraddr = null;
		try {
		    /* If there's no aliases for this host. */
		    if (!alias_servers.isEmpty()) {
			serveraddr = parseInetAddress(rootservers.get(0));
		    }
		    /* If there are name servers to ask, ask them. */
		    else {
			serveraddr = parseInetAddress(name_servers.get(0).value);		
		    }
		}
		catch(NumberFormatException e) {
		    System.out.printf("Invalid nameserver %s\n",nameserver);
		    System.exit(1);
		}

		result = sendRequest(serveraddr, hostname);
	    }
	    // If we timed out. TODO: Figure this out. Probably ask the next name server.
	    else {
		return;
	    }
	}
	if (found) {
	    System.out.println(hostname + " resolved to: " + found_ip);
	}
    }

    static DNS sendRequest(byte[] serveraddr, String hostname) throws IOException {
	/* create a datagram socket to send messages */
	DatagramSocket dSocket = null;
	try {
	    dSocket = new DatagramSocket();
	}
	catch(IOException e) {
	    System.err.println(e);
	    System.exit(1);
	}

	// using a constant name server address for now.

	InetAddress serverAddress = null;
	/* get inet address of name server */
	try {
	    serverAddress = InetAddress.getByAddress(serveraddr);
	}
	catch (UnknownHostException e) {
	    System.err.println(e);
	    System.exit(1);
	}

	/* set up buffers */
	String line;
	byte[] inBuffer = new byte[1000];

	DatagramPacket outPacket = new DatagramPacket(new byte[1], 1, serverAddress, DNSPORT);
	DatagramPacket inPacket = new DatagramPacket(inBuffer, 1000);

	// construct the query message in a byte array
	byte[] query = new byte[1500];
	int querylen = DNS.constructQuery(query, 1500, hostname);

	// construct a DNS object from the byte array
	DNS dnsMessage = new DNS(query);
	if (verbose == 1)
	    System.out.println("Sending query: "+ dnsMessage.rrlist.get(0).name + " to "+ serverAddress);

	// send the byte array as a datagram
	outPacket.setData(query,0,querylen);
	for (int i = 0; i < 200; i++)
	    dSocket.send(outPacket);

	// await the response
	// TODO: Have it time out.
	boolean time_out = false;
	dSocket.setSoTimeout(100);
	try {
	    dSocket.receive(inPacket);
	}
	catch (SocketTimeoutException e) {
	    time_out = true;
	}

	if (time_out) {
	    // handle time out, go to next NS? 
	    System.out.println("timed out..");
	    return null;
	}
	else {
	    byte[] answerbuf = inPacket.getData();

	    DNS response = new DNS(answerbuf);
	    return response;
	}
    }
}
