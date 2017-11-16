/*
  class representing a DNS message
 */
import java.util.*;

class DNS {
	static final int RRTYPE_A=1;
	static final int RRTYPE_NS=2;
	static final int RRTYPE_CNAME=5;
	static final int RRTYPE_SOA=6;
	static final int RRTYPE_PTR=12;
	static final int RRTYPE_AAAA=28;
	static final int QUESTION=1;
	static final String[] rectypes = {null,"A","NS",null,null,"CNAME","SOA",null,null,null,null,null,"PTR",null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,"AAAA"};

	/* instance variables */
	int id,flags,qcount,acount,authcount,othercount;
	List<ResourceRecord> rrlist = new ArrayList<ResourceRecord>();

	/*
      class representing a single DNS resource record
	 */
	class ResourceRecord {
		int type;
		int iclass;
		int ttl;
		int datalen;
		int length;  // number of bytes in the resource record
		String name;
		String value;

		/*
	  ResourceRecord constructor

	  dnsarr -- array of bytes containing resource record data
	  bindex -- index in dnsarr where the resource record starts
		 */
		public ResourceRecord(byte[] dnsarr,int bindex){
			StringBuilder sb = new StringBuilder();
			int namelen = fromDNSStyle(dnsarr,bindex,sb);
			name = sb.toString();
			bindex += namelen;
			type = ((dnsarr[bindex]<<8)&0xff00) + (dnsarr[bindex+1]&0xff);
			iclass = ((dnsarr[bindex+2]<<8)&0xff00) + (dnsarr[bindex+3]&0xff);
			bindex += 4;
			// skip ttl
			bindex += 4;
			// get datalen
			datalen = ((dnsarr[bindex]<<8)&0xff00) + (dnsarr[bindex+1]&0xff);
			bindex += 2;

			int alen;
			if(type == RRTYPE_A){
				value = ""+(dnsarr[bindex++]&0xff);
				value += "."+(dnsarr[bindex++]&0xff);
				value += "."+(dnsarr[bindex++]&0xff);
				value += "."+(dnsarr[bindex++]&0xff);
				alen = 4;
			}
			else {
				sb = new StringBuilder();
				alen = fromDNSStyle(dnsarr,bindex,sb);
				value = sb.toString();
			}
			length =  namelen+10+datalen;
		}

		/* alternative RR constructor for questions */	
		ResourceRecord(byte[] dnsarr,int bindex,int question){
			StringBuilder sb = new StringBuilder();
			int namelen = fromDNSStyle(dnsarr,bindex,sb);
			name = sb.toString();
			bindex += namelen;
			type = ((dnsarr[bindex]<<8)&0xff00) + (dnsarr[bindex+1]&0xff);
			iclass = ((dnsarr[bindex+2]<<8)&0xff00) + (dnsarr[bindex+3]&0xff);
			bindex += 4;
			length = namelen+4;
		}

		public String toString(){
			return rectypes[type] + " " + name + " " + value;
		}
	}

	/*
      DNS constructor
      constructs a DNS object from a byte array received from a DNS server
	 */
	public DNS(byte[] arr){
		id = ((arr[0]<<8)&0xff00) + (arr[1]&0xff);
		flags = ((arr[2]<<8)&0xff00) + (arr[3]&0xff);
		qcount = ((arr[4]<<8)&0xff00) + (arr[5]&0xff);
		acount = ((arr[6]<<8)&0xff00) + (arr[7]&0xff);
		authcount = ((arr[8]<<8)&0xff00) + (arr[9]&0xff);
		othercount = ((arr[10]<<8)&0xff00) + (arr[11]&0xff);
		int tcount = qcount+acount+authcount+othercount;
		int bindex = 12;
		int k = 0;
		// process question records
		while(bindex<arr.length && k<qcount){
			ResourceRecord rr = new ResourceRecord(arr,bindex,QUESTION);
			bindex += rr.length;
			rrlist.add(rr);
			k++;
		}
		// process all other records
		while(bindex<arr.length && k<tcount){
			ResourceRecord rr = new ResourceRecord(arr,bindex);
			bindex += rr.length;
			rrlist.add(rr);
			k++;
		}
	}

	public String toString(){
		String result = "";
		// use next line if you want to see the header
		//String result = ""+id+" "+flags+" "+qcount+" "+acount+" "+authcount+" "+othercount+"\n";
		int indx = 0;
		for(ResourceRecord rr : rrlist){
			result += rr+"\n";
		}
		return result;
	}

	static int constructQuery(byte[] qarr, int len, String hostname, boolean reverse){
		String[] addrarr = hostname.split("\\.");
		if(addrarr.length==4){
			try {
				Integer.parseInt(addrarr[0]);
				Integer.parseInt(addrarr[1]);
				Integer.parseInt(addrarr[2]);
				Integer.parseInt(addrarr[3]);
				reverse = true;
				hostname = addrarr[3]+"."+addrarr[2]+"."+addrarr[1]+"."+addrarr[0]+".in-addr.arpa";
			}
			catch(NumberFormatException e){
			}
		}

		// first part of the query is a fixed size header
		//struct dns_hdr *hdr = (struct dns_hdr*)query;

		// generate a random 16-bit number for session
		int queryid = ((int)(0x10000*Math.random())) & 0xffff;
		qarr[0] = (byte)((queryid & 0xff00)>>8);
		qarr[1] = (byte)(queryid & 0xff);

		// set question count = 1
		qarr[5] = 1;

		// add the name
		int querylen = 12;

		int namelen = toDNSStyle(hostname,qarr,querylen);
		querylen += namelen;

		// now the query type: A or PTR. 
		if(reverse){
			qarr[querylen+1] = RRTYPE_PTR;
		}
		else{
			qarr[querylen+1] = RRTYPE_A;
		}

		querylen += 2;

		// finally the class: INET
		qarr[querylen+1] = 1;
		querylen += 2;

		return querylen;
	}

	static int toDNSStyle(String name, byte[] dnsarr, int bindex){
		byte partlen=0;
		int nindex = 0;
		int dindex = bindex;
		for(;nindex<name.length();nindex++,dindex++) {
			if(name.charAt(nindex)!='.') {
				dnsarr[dindex+1]=(byte)name.charAt(nindex);
				partlen++;
			}
			else {
				dnsarr[dindex-partlen]=partlen;
				partlen=0;
			}
		}
		dnsarr[dindex-partlen]=partlen;
		dnsarr[dindex+1]=0;
		return name.length()+2;
	}

	static int fromDNSStyle(byte[] dnsarr, int index, StringBuilder name){
		byte part_remainder=0;
		int len=0;
		int return_len=0;
		int dindex = index;
		int origindex = index;
		while(dnsarr[dindex]!=0) {
			if(part_remainder==0) {
				// this condition checks for message compression, see RFC 1035 4.1.4
				if((dnsarr[dindex]&0xc0)==0xc0) { 
					if(return_len==0)
						return_len = dindex-origindex+2;
					//return_len = 2;
					dindex = ((dnsarr[dindex]&0x3f)<<8)+(dnsarr[dindex+1]&0xff);
					continue;
				}
				else {
					part_remainder=dnsarr[dindex];
					if(name.length()>0){
						name.append('.');
					}
				}
			}
			else {
				name.append((char)dnsarr[dindex]);
				part_remainder--;
			}
			dindex++;
		}
		if(return_len==0)
			return_len = dindex - origindex + 1;
		return (return_len!=0?return_len:1);
	}

	static public void main(String[] args){
		byte[] dns = new byte[1000];  // buffer for request message
		int k = 0;
		for(String x : args){
			k += toDNSStyle(x,dns,k);
		}

		ArrayList<Byte> list = new ArrayList<Byte>();
		for(byte x : dns){
			list.add(x);
		}

		StringBuilder b = new StringBuilder();
		k = 0;
		while(true){
			int rv = fromDNSStyle(dns,k,b);
			if(rv<=1) break;
			k += rv;
			b = new StringBuilder();
		}
	}
}
