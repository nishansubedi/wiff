# WIFF 

## A Packet Sniffer and Analyzer

WIFF is a packet sniffer that can be configured to read packets from a network interface or set pcap file. Wiff can then be configured to extract and analyze data from individual packets, or from a set of related packets and send collected data to an external storage destination.

Supported Data Sources:

* Network interface
* pcap file (or folder containing a set of pcap files)
* RabbitMQ

Implemented Data Analysis

* Packet Stitching (form complete request and response from packets)
* Request/Response header extraction
* Request round trip time

Supported Data Destinations

* RabbitMQ
* Elasticsearch


WIFF has been designed to make each of these facets easily extendable.

### Getting Started

For a basic installation you will need the following:

* Java 1.7, or later
* An [Elasticsearch](https://github.com/elasticsearch/elasticsearch) cluster
* Java Cryptography Extension (JCE), if TLS uses [stronger algorithms](http://docs.oracle.com/javase/7/docs/technotes/guides/security/SunProviders.html#importlimits)


### Installation

* Download and unzip WIFF
* Download [JNetPcap](http://jnetpcap.com/download) and copy .dll or .so files to the unzipped WIFF folder. 
* Modify a config file (I suggest config/wiff-simple.conf). Set the following
 * capture_source
 * wiff.reporter.elastic.host
 * wiff.reporter.elastic.port
* Run bin/wiffctl --config=[config_file] start  

Or, in Windows (PowerShell) 

* Run bin\wiffctl.ps1 -a start -config_file [config_file]

Start visiting pages in your browser or with curl. Documents containing request-response pair should appear in Elasticseach.

## Design

WIFF is designed as a pipeline. Data flows into one end and out of the other. In this section I will describe the pipelines's components.

![wiff design diagram](https://github.com/wayfair/wiff/raw/master/wiff_diagram.png)

**WiffProperties** - A simple wrapper around java.util.Properties, this class allows us to read and store key=value pairs from a configuration file. It also permits us to retrieve values as specific datatypes.

**Wiff** - This class is responsible for loading a WiffProperties object using a file dictated by a command line argument and using those properties to set up the pipeline. This class, then, instantiates each of the pipeline components.

**WiffByteBuffer** - A simple wrapper around java.nio.ByteBuffer created to improve efficiency of the ByteBufferPool

**ByteBufferPool** - A simple class created to prevent reading data too fast (and therefore running out of memory). Use of this class in conjuction with WiffCapture or WiffProcessor caps the amount of data waiting to be processed at any given time.

**WiffCapture** - This class is responsible for retrieving packets from a data source. Packets data is wrapped in a WiffByteBuffer retrieved from the ByteBufferPool and placed in the WiffQueue. The data source can be a network interface, a capture file, or a folder containing capture files (they wll be read in order of creation). 

* **Note:** In order to ensure we do not process a capture file while it is still being written to, if the capture source is a folder, there must be at least 3 files present to trigger the reading of files.

**WiffQueue** - Classes implementing this interface are basically temporary storage for the data that has been retrieved from a data source but has yet to be processed further (waiting for a consumer to pick it up).

**WiffPacket** - This class wraps a byte array containing packet information.

**WiffConsumer** - Classes implementing this interface represent a worker thread. Consumers are responsible for retrieving data from the WiffQueue and calling each service's processData function with the retrieved data as input. If the ByteBuffer pool is in use, the consumer returns the WiffByteBuffer to the pool. A consumer may contain a WiffPacket object if that is the type of data in the WiffQueue.

**WiffService** - Classes implementing this interface either extract information or create metrics from the data sent to it. This is then sent a reporter for more permanent storage.

**WiffParser** - Classes implementing this interface are simple data transformers. They may take data of one type and return another, or, just restructure the data passed in. Used in conjunction with a WiffReporter, this class makes it easy for several services to use the same type of WiffReporter.

**WiffReporter** - Classes implementing this interface expect messages from WiffServices. These messages may be parsed using a WiffParser and are sent to an external application.

**WiffProcessor** - Classes implementing this interface are alternative data sources. Just like WiffCapture they are responsible for putting data into the WiffQueue.

### A Little More Detail 

In this section I will dive a little deeper into WIFFs inner-workings. The diagram below zooms in on the lower half of the pipeline. Specifically, it details the interaction of the classes involved in constructing a TCP stream from a set of packets, formatting the HTTP within, and sending it to Elasticsearch.

![diagram](https://github.com/wayfair/wiff/raw/master/wiff_http_parsing.png)

1. WiffPackets are delivered to the WiffStitch service by WiffConsumers (not shown).
2. WiffStitch keeps track of connections between clients and servers. Each connection has TcpReconstructor object. WiffStitch sends the packet to the appropriate object.
3. The TcpReconstructor orders the packets according to sequence number and stitches their payloads together, decrypting first if necessary.
4. When TcpReconstruction is complete, WiffStitch sends the TCP stream to the ElasicsearchClient.
5. ElasticsearchClient uses the HTTPParse to interpret the TCP stream as HTTP and format a Elasticsearch bulk API message.
6. ElasticsearchClient sends the message to Elasticsearch 

**NOTE:** Only the following cipher suites are currently supported for SSL Decryption, though others should be easy to add.

* TLS_RSA_WITH_RC4_128_SHA
* TLS_RSA_WITH_3DES_EDE_CBC_SHA
* TLS_RSA_WITH_AES_128_CBC_SHA
* TLS_RSA_WITH_AES_256_CBC_SHA
* TLS_RSA_WITH_CAMELLIA_128_CBC_SHA
* TLS_RSA_WITH_CAMELLIA_256_CBC_SHA