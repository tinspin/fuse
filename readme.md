<pre>
why yet another multiplayer solution?

support:
- Unity
  - plugin is only 140 lines of C# code: <a href="https://github.com/tinspin/fuse/blob/master/src/Fuse.cs">Fuse.cs</a>
- XHR/XDR, 99.9% of browsers, only IE7 missing
  - CORS compliant, static hosting for you.
- 100% uptime on routing
- 100% read uptime on data
- 100% firewall pass-through
- all gameplay types:
  - from two player turn-based
  - to real-time action MMO

protocol:
- client/server HTTP + peer-to-peer UDP hybrid
- simple but compact & readable standard
- scalable multicast UDP cluster server

+-----+
| RFC |
+-----+

protocol            --> = broadcasted to Read() (C#) or read(data) (XHR/XDR)
                     -> = direct return on Push(data)

< > = mandatory
[ ] = optional
 *  = not implemented yet

in sort of chronological order:

&lt;type&gt;                 &lt;echo&gt;
 
                     // register
                     // [mail] if you want recovery, set [mail] to empty 
                     //        string (||) if you want pass without mail
                     // [pass] if your platform cannot persist the key 
                     //        preferably this is a hash with salt 
                     //        for example we simply use md5(pass + name)
                     // use &lt;id&gt; as name if you want anonymous users
 <b><i>user</i></b>|[mail]|[pass]  -> user|done|&lt;key&gt;|&lt;id&gt;
                     -> user|fail|name too short
                     -> user|fail|pass too short
                     -> user|fail|name alpha missing // numeric reserved for &lt;id&gt;
                     -> user|fail|name invalid       // only alphanumeric and .
                     -> user|fail|mail invalid       // only alphanumeric and .@-+
                     -> user|fail|name already registered
                     -> user|fail|mail already registered
 
-> main|fail|name missing
-> main|fail|name too short
 
 <b><i>salt</i></b>                -> salt|done|&lt;salt&gt;
 
                     // login
                     // &lt;hash&gt; is either md5(&lt;key&gt; + &lt;salt&gt;)
                     //               or md5([pass] + &lt;salt&gt;)
                     //        we use md5(md5(pass + name) + &lt;salt&gt;)
                     //        make sure you keep the case correct
 <b><i>open</i></b>|&lt;salt&gt;|&lt;hash&gt;  -> open|done
                     -> open|fail|user not found
                     -> open|fail|salt not found
                     -> open|fail|wrong pass

-> main|fail|user not open

+-------------------------------+
| <i>here you have to call pull()!</i> |
+-------------------------------+

                     // join a game
*<b><i>game</i></b>|&lt;name&gt;         -> game|done
                     -> game|fail|name invalid
                     
-> main|fail|user has no game

                     // how many users or rooms does the server host
*<b><i>info</i></b>|&lt;type&gt;         -> info|done|&lt;user&gt;             // if &lt;type&gt; = 'user'
                     -> info|done|&lt;room&gt;             // if &lt;type&gt; = 'room'
                     
                     // tcp keep-alive for push socket
*<b><i>ping</i></b>                -> ping|done

                     // ask server for local time
*<b><i>time</i></b>                -> time|done|&lt;date&gt;             // ISO 8601 date
                                                     // yyyy-MM-dd'T'HH:mm:ss.SSSZ

                     // add friend
*<b><i>ally</i></b>|&lt;name&gt;         -> ally|done
                     -> ally|fail|user not found

                     // set status
*<b><i>away</i></b>|&lt;boolean&gt;      -> away|done

                     // enable peer-to-peer
 <b><i>peer</i></b>|&lt;ip&gt;           -> peer|done                    // send the internal address (192.168.)

                     // add client as host
*<b><i>host</i></b>|&lt;port&gt;         -> host|done                    // send the port

                     // host room
 <b><i>room</i></b>|&lt;type&gt;|&lt;size&gt;  -> room|done
                    --> <b><i>made</i></b>|&lt;name&gt;+&lt;type&gt;+&lt;size&gt;    // in lobby
                     -> room|fail|user not in lobby
                     -> room|fail|type invalid       // only alpha

                     // list unlocked rooms with space left or data
 <b><i>list</i></b>|room           -> list|done|room|&lt;name&gt;+&lt;type&gt;+&lt;size&gt;|...
 <b><i>list</i></b>|data|&lt;type&gt;    -> list|done|data|&lt;id&gt;|...      // use load to get data
                     -> list|fail|wrong type

                     // join room
 <b><i>join</i></b>|&lt;name&gt;         -> join|done
                    --> <b><i>here</i></b>|&lt;name&gt;[|&lt;ip&gt;]           // in new room
                    --> <b><i>gone</i></b>|&lt;name&gt;                  // in lobby
                     -> join|fail|room not found
                     -> join|fail|room is locked
                     -> join|fail|already in room
                     -> join|fail|room is full

                     // permanently ban user from room
*<b><i>kick</i></b>|&lt;name&gt;         -> kick|done
                     -> kick|fail|not creator
                     -> kick|fail|user not here
 
                     // quit room
 <b><i>quit</i></b>                -> quit|done
                    --> <b><i>here</i></b>|&lt;name&gt;[|&lt;ip&gt;]           // in lobby
                    --> <b><i>gone</i></b>|&lt;name&gt;                  // in old room
                    --> <b><i>stop</i></b>|&lt;name&gt;                  // if creator or last user leaves
                     -> exit|fail|user in lobby

                     // user exit
*<b><i>exit</i></b>                -> exit|done
                    --> <b><i>kill</i></b>|&lt;name&gt;
                    
                     // lock room before the game starts
*<b><i>lock</i></b>                -> lock|done
                    --> <b><i>link</i></b>|&lt;name&gt;                  // to everyone, used to start the game
                     -> lock|fail|user not room host

                     // insert and select data
 <b><i>save</i></b>|&lt;type&gt;|&lt;json&gt;  -> save|done|&lt;id&gt;|&lt;key&gt;         // to update data use this key in json
                     -> save|fail|data too large
 <b><i>load</i></b>|&lt;type&gt;|&lt;id&gt;    -> load|done|&lt;json&gt;             // use id from list|data|&lt;type&gt;
                     -> load|fail|data not found

                     // chat in any room
 <b><i>chat</i></b>|&lt;text&gt;         -> chat|done                    // @[name] of private destination
                    --> <b><i>text</i></b>|&lt;name&gt;|&lt;text&gt;
                     -> chat|fail|user not online

                     // send any gameplay data to room
 <b><i>send</i></b>|&lt;data&gt;         -> send|done
                    --> <b><i>sent</i></b>|&lt;name&gt;|&lt;data&gt;
 
                     // motion for 3D MMO games with dynamic here/gone
*<b><i>move</i></b>|&lt;data&gt;         -> move|done
                    --> <b><i>data</i></b>|&lt;name&gt;|&lt;data&gt;
                     // &lt;data&gt; = &lt;x&gt;+&lt;y&gt;+&lt;z&gt;|&lt;x&gt;+&lt;y&gt;+&lt;z&gt;+&lt;w&gt;|&lt;action&gt;(|&lt;speed&gt;|...)
                     //          position   |orientation    |key/button

-> main|fail|type not found

&lt;peer&gt; // peer protocol

*<b><i>talk</i></b> // send voice
*<b><i>head</i></b> // send head/body movement
*<b><i>hand</i></b> // send hand movements

&lt;soon&gt; // future protocol

*<b><i>pull</i></b> // load cards
*<b><i>pick</i></b> // select card
*<b><i>push</i></b> // show new cards

*<b><i>hide</i></b>
*<b><i>show</i></b>
</pre>