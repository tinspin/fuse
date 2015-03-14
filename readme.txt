INTRODUCTION:

    I always wanted something that wrapped HTTP 
    in Java without forcing me or the CPU to work 
    slowly.

RUN:

    You need java 1.5 or later installed and 
    added to the path and JAVA_HOME set to run 
    the server.

    >run.sh OR
    >run.bat
    
    ##IMPORTANT##

    Since NIO has been very unstable from 
    1.6.0_00 until 1.6.0_18 with Selector bugs 
    ranging from 100% CPU to thread deadlock; 
    you should use 1.5 or 1.6.0_18 and later 
    on your live server.

BUILD AND TEST OR DOCUMENT:

    You need ant 1.7.0 installed and added 
    to the path and ANT_HOME set to build the 
    server.

    >ant OR
    >ant doc

APPLICATION REMOTE HOT DEPLOYMENT:

    Implement se.rupy.http.Service and deploy 
    the jar containing your application like 
    this:

    <target name="deploy">
        <java fork="yes" 
              classname="se.rupy.http.Deploy" 
              classpath="http.jar">
            <arg line="localhost:8000"/><!-- any host:port -->
            <arg line="service.jar"/><!-- your application jar -->
            <arg line="secret"/><!-- see run.bat and run.sh -->
        </java>
    </target>

LOGGING:

    Just add -log to the start script and you 
    will find access and error logs in a log 
    folder that will be created in the execution 
    path.

VERSION:

    0.1 - Alpha

    - Asynchronous response.
    - Added OP_WRITE so that the server can send 
      large amounts of data.
    - Finished and tested chunked transfer 
      encoding.
    - Session timeout / TCP disconnect feedback 
      to visited services.
    - 302 Found.
    - 304 Not Modified.
    - 404 Not Found.
    - 500 Internal Server Error.
    - Static content to disk.
    
    0.2 - Beta
    
    - Fixed a ton of bugs and refactored most 
      classes.
    - Added multipathed services, so that you 
      can deploy 
      one service at the same index in multiple 
      chains without having to write separate 
      services.
    - Added javadoc ant task.
    - Queue events when all workers are busy 
      to avoid selector thrashing.

      0.2.1

      - Fixes an extremely rare but fatal bug 
        which left 
        the server throttling at 99% CPU.
      - Also includes some helper method 
        additions and re-factorings to Hash.
      - Daemon now takes Properties, so you can 
        use a properties text file!
      - Probably some other small things here 
        and there.
      
      0.2.2

      - Refactored the deployment of archives 
        completely.
      - Fixed a couple of bugs with the query 
        parameters.
      
      0.2.3
      
      - Added streaming asynchronous push (Comet) 
        support and tested long-poll with a chat 
        demo.

      0.2.4

      - Removed activation.jar dependency, since 
        the only thing I used it for was mime 
        content-type lookup, you will now find 
        the mime types in the mime.txt file. 
        (reason: firefox + doctype + css + 
        content-type)
        
      0.2.5
      
      - Now content is dynamically read from 
        disk, to allow dynamic file upload.
      - Added so you can deploy an abstract 
        Service.
      - Added host management, so you can 
        deploy multiple domains on one rupy 
        instance. Not tested though.
        
    0.3 Stable
      
    - Fixed dynamic class loading of complex 
      hierarchies.
      
      0.3.1
      
      - Removed chunking of fixed length 
        responses.
      - Fixed large file worker/selector 
        deadlock.
      
      0.3.2
      
      - Fixed chunked streaming Output.flush() 
        to not write trailing zero length 
        chunk before end of reply.
      
      0.3.3
      
      - Added Expires header.
      - Added Event.hold() method, to allow 
        asynchronous streaming on the first 
        request.
      - Fixed deadlock when proxy closes 
        socket.
      - URLDecoding the URI.
      
      0.3.4
      
      - Fixed dynamic class loading of large 
        hierarchies.
      - Use the -live flag to enable expire 
        cache.
      - Added null service so you can filter 
        all 404 queries.
      - Fixed push, so now the chat should work 
        properly!
      
      0.3.5
      
      - Now you can only deploy from localhost 
        with password 'secret'.
      - Added start and stop methods to Daemon.
      - Added ability to log to custom PrintStream.
      
      0.3.6
      
      - Added XSS comet cookie query parameter 
        fail-over because IE doesen't allow 
        <script> to set a cookie!
        
      0.3.7
      
      - Fixed URLDecoding the path and not 
        the parameter part of the URI.
        
      0.3.8 GWT Compatible
      
      - Fixed classloading of war format.
      - Added ability to fetch current 
        classloader.
      
      0.3.9
      
      - Changed Reply.wakeup() api from 
        throwing exceptions to returning int's.
      - Moved register() into the block loop, 
        so lag won't drop comet clients.
      - Updated delay input to milliseconds, 
        so laggy comet clients won't timeout.
      - Worker now cleans in/out buffers upon 
        exception, this solves the threads being 
        locked if users cancels long requests 
        halfway in.
      - Added Listener so deployed jars can 
        communicate across classloaders.
      - Added simple logging with -log flag to 
        get access and error logs in a log folder 
        created in the execution path..
      - Added fixed length test and improved unit.
        
    0.4 Industrial
      
    - Fixed test.
    - Corrected default timeout value to 5 seconds.
    - Corrected boolean parameter parsing.
      
      0.4.1
      
      - Base64 parameter fix by akarchen
      - PUT and DELETE by mathias.funk
      - CancelledKeyException & thread lock fix, 
        thanks to mathias.funk
      - Fixed async streaming response.
      
      0.4.2
      
      - Fixed socket timeout loop.
      - Fixed boolean parameter.
      - Fixed SimpleDateFormat concurrency.
      - Added response code 505 for HTTP/1.0.
      - Fixed socket file descriptor leak. Added 
        -panel startup property so you can browse 
        to /panel to get worker and event status 
        to debug locks and leaks.
      
      0.4.3
      
      - Fixed small things here and there.
      - Added sandboxing for hosted mode. See 
        http://host.rupy.se. This is like 'Google 
        App Engine' but for rupy.
      - Switched content and service order in 
        event filtering.
        This way a service can mask a file in 
        order to protect it.
      - NullPointerException fix under high 
        load by hbaghdas.
      
      0.4.4
      
      - Fixed deadlock due to all events timing 
        out at the same time.
      - Content-Length changed from int to long, 
        so chrome can upload files larger than 4GB.
      - Patched security flaw in hosted mode.
      - Fixed "file" file descriptor leak.
      
      0.4.5
      
      - Adds pid.txt if run as app.
      - Added SHA-256 cookie salted hashing of 
        pass for secure deployment.
    
    0.5 Comet Stream
    
    - Improved pass hashing based on deployed 
      file hash. The hash chain is now:
      
      file -> pass -> cookie
    
    - Fixed -timeout 0, which didn't work before. 
      Not recommended though since secure deployment 
      uses cookies.
    
      For stateless high performance use "Head: 
      less" header instead:
    
    - Added "Head: less" header, which you only 
      need to supply on first request to each 
      keep-alive socket. This disables sessions 
      and all reading and writing of headers to 
      accommodate streaming comet protocol.
      
      See talk-0.9.1.jar
      
      Observe that Head: less requests as well as 
      comet responses via Event.hold() are not 
      logged to access log for obvious reasons.
      
    - Added deployment broadcast for PaaS virtual 
      hosting solution.
      
    1.0 Release
      
    - Fixed cluster hosting propagation deploy.
    - Added multicast for cluster realtime events.
    - For big files and old client computers + 
      slow internet connections we need to increase 
      the socket write buffer to avoid clients 
      being disconnected, example linux commands 
      (as root):
        
        > echo 'net.core.wmem_max=1048576' >> /etc/sysctl.conf
        > echo 'net.ipv4.tcp_wmem= 16384 65536 1048576' >> /etc/sysctl.conf
        > sysctl -p
        
      where 1048576 should be the size of your 
      largest file.
      
    1.1 Patch
    
    - Added so you can get your database password 
      from the domain controller on host cluster.
    - Added Head: less (no session and less response 
      headers) to Content-Type: text/event-stream
    - Fixed deployment archive date so old resources
      no longer break the cache.
    - Added Flash <policy-file-request/> handling.
    - Enable TCP no delay for headless sockets.
    
    1.2 Fixes
    
    - Catches UDP multicast receive Exceptions.
    - Allow access to individual cluster nodes 
      for any domain: So now one.project.domain.com 
      can be pointed to cluster node one and the 
      project.domain.com deploy will reply.
    - All request paths that starts with "/root/" 
      are now forbidden in hosted mode.
    - Uninstantiable services now complain upon 
      hot-deploy.
    - Threadlock prints stacktrace.
    - Added /api with all service endpoints when 
      -panel is used.
    - Accepts http://host prefix URIs now.
    - Added 400 Bad Request if host header is 
      missing.
    - Fixed wildcard "null" path service on host 
      and a potential hosted security problem.
    - Added a simple but high performance Async 
      client.
    - Added timeout for the async client.
    - Added COM port compatibility for p2p mesh 
      radio.
    - Fixed some wildcard stuff, preparing for 
      both proxy and load-balancing.
    
    1.3 Cluster (WIP)
    
    TODO:
    
    - Integrate ROOT.
    - Add deploy rollback.
    - Store CPU/SSD/NET for everyone.
    
have fun!