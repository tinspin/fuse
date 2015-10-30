<pre>
+---------------------------+
| <i>The multiplayer solution.</i> |
+---------------------------+

Support:

  - Unity
    - plugin is only 140 lines of C# code: <a href="https://github.com/tinspin/fuse/blob/master/src/Fuse.cs">Fuse.cs</a>
  - XHR/XDR, 99.9% of browsers, only IE7 missing
    - CORS compliant, static hosting: <a href="https://github.com/tinspin/fuse/blob/master/res/game.html">game.html</a>
  - 100% firewall pass-through
  - all gameplay types:
    - from two player turn-based
    - to real-time action MMO

Protocol:

  - client/server triplex HTTP, \n or \n\n terminated, text based
    - dynamic presence position move packets
  - peer-to-peer UDP, binary physics packets
    - position move and talk, look, head, body, hand packets
  - multicast UDP on cluster for load distribution

Platform:

  - proven for 5 years
  - 100% uptime on routing
  - 100% read uptime on data

+-------------------+
| <i>Work in progress!</i> |
+-------------------+

--> = async. broadcast to Read() (C#) or read(data) (XHR/XDR)
 -> = sync. return on Push(data) or push(data)

< > = mandatory
[ ] = optional

 *  = not implemented yet

In sort of chronological order:

&lt;rule&gt;                        &lt;echo&gt;
 
                            // register
                            // [name] if you can't store the &lt;id&gt; otherwise set
                            //        to empty string (||)
                            // [mail] if you want recovery otherwise set 
                            //        to empty string (||)
                            // [pass] if you cannot persist the key otherwise set
                            //        to empty string (||)
                            //        preferably [pass] is a hash with salt 
                            //        for example we simply use md5(pass + name)
 <b><i>user</i></b>|[name]|[mail]|[pass]  -> user|done|&lt;key&gt;|&lt;id&gt;|&lt;salt&gt;
                            -> user|fail|name too short
                            -> user|fail|name too long
                            -> user|fail|name invalid       // only alphanumeric and .-
                            -> user|fail|name alpha missing // numeric reserved for &lt;id&gt;
                            -> user|fail|mail invalid       // only alphanumeric and .@-+
                            -> user|fail|pass too short
                            -> user|fail|name already registered
                            -> user|fail|mail already registered
 
                            // to get the &lt;id&gt; of a mail to use as &lt;name&gt;
                            // if you want to login with mail instead of name
 <b><i>mail</i></b>|&lt;mail&gt;                -> mail|done|&lt;id&gt;
                            -> mail|fail|user not found
 
                            // get salt for &lt;name&gt; or &lt;id&gt;
 <b><i>salt</i></b>|&lt;name&gt;/&lt;key&gt;/&lt;id&gt;     -> salt|done|&lt;salt&gt;
                            -> salt|fail|user not found
 
 -> main|fail|invalid salt
 
                            // login
                            // &lt;hash&gt; is either md5(&lt;key&gt; + &lt;salt&gt;)
                            //               or md5([pass] + &lt;salt&gt;)
                            //        we use md5(md5(pass + name) + &lt;salt&gt;)
                            //        make sure you keep the case correct
                            // if you hash the pass with the mail you can use mail as 
                            // name if you replace the name with the returned &lt;name&gt; 
                            // which will be the &lt;id&gt; or &lt;name&gt; if name is stored
 <b><i>open</i></b>|&lt;salt&gt;|&lt;hash&gt;         -> open|done|&lt;name&gt;
                            -> open|fail|user not found
                            -> open|fail|wrong pass

-> main|fail|user not open

+-------------------------------+
| <i>Here you have to call pull()!</i> |
+-------------------------------+

                            // join a game
 <b><i>game</i></b>|&lt;salt&gt;|&lt;name&gt;         -> game|done
                            -> game|fail|name invalid
                     
-> main|fail|user has no game

                            // add friend
*<b><i>ally</i></b>|&lt;salt&gt;|&lt;name&gt;         -> ally|done
                            -> ally|fail|user not found

                            // enable peer-to-peer
 <b><i>peer</i></b>|&lt;salt&gt;|&lt;ip&gt;           -> peer|done                    // send the internal IP

                            // host room
 <b><i>room</i></b>|&lt;salt&gt;|&lt;type&gt;|&lt;size&gt;  -> room|done
                           --> <b><i>made</i></b>|&lt;name&gt;+&lt;type&gt;+&lt;size&gt;    // in lobby
                            -> room|fail|user not in lobby
                            -> room|fail|type invalid       // only alpha

                            // list rooms or data
 <b><i>list</i></b>|&lt;salt&gt;|room           -> list|done|room|&lt;name&gt;+&lt;type&gt;+&lt;size&gt;|...
 <b><i>list</i></b>|&lt;salt&gt;|data|&lt;type&gt;    -> list|done|data|&lt;id&gt;|...      // use load to get data
                            -> list|fail|wrong type

                            // join room
                            // between full and lock nobody can join
                            // if you join after lock you can only view the game
 <b><i>join</i></b>|&lt;salt&gt;|&lt;name&gt;         -> join|done
                           --> <b><i>here</i></b>|&lt;name&gt;[|&lt;ip&gt;]           // in new room
                           --> <b><i>gone</i></b>|&lt;name&gt;|&lt;room&gt;           // in lobby
                            -> join|fail|room not found
                            -> join|fail|already in room
                            -> join|fail|room is full

                            // permanently ban user from room
*<b><i>kick</i></b>|&lt;salt&gt;|&lt;name&gt;         -> kick|done
                            -> kick|fail|not creator
                            -> kick|fail|user not here
 
                            // quit room
 <b><i>quit</i></b>|&lt;salt&gt;                -> quit|done
                           --> <b><i>here</i></b>|&lt;name&gt;[|&lt;ip&gt;]           // in lobby
                           --> <b><i>halt</i></b>|&lt;name&gt;                  // in lobby if creator leaves
                           --> <b><i>gone</i></b>|&lt;name&gt;                  // in old room
                           --> <b><i>stop</i></b>|&lt;name&gt;                  // in old room if creator leaves

                            // user exit
 <b><i>exit</i></b>|&lt;salt&gt;                -> exit|done
                           --> <b><i>kill</i></b>|&lt;name&gt;
                            -> exit|fail|user in lobby
                    
                            // lock room before the game starts
*<b><i>play</i></b>|&lt;salt&gt;                -> play|done
                           --> <b><i>lock</i></b>                         // to start the game
                            -> play|fail|user in lobby
                            -> play|fail|user not creator
                            -> play|fail|only one player

                            // insert data
 <b><i>save</i></b>|&lt;salt&gt;|&lt;type&gt;|&lt;json&gt;  -> save|done|&lt;id&gt;|&lt;key&gt;         // use key to update
                            -> save|fail|data too large

                            // select data
 <b><i>load</i></b>|&lt;salt&gt;|&lt;type&gt;|&lt;id&gt;    -> load|done|&lt;json&gt;             // use id from list|data|&lt;type&gt;
                            -> load|fail|data not found

                            // chat in any room
 <b><i>chat</i></b>|&lt;salt&gt;|&lt;text&gt;         -> chat|done                    // @[name] of private destination
                           --> <b><i>text</i></b>|&lt;name&gt;|&lt;text&gt;
                            -> chat|fail|user not online

                            // send any gameplay data to room
 <b><i>send</i></b>|&lt;salt&gt;|&lt;data&gt;         -> send|done
                           --> <b><i>sent</i></b>|&lt;name&gt;|&lt;data&gt;
 
                            // motion for 3D MMO games with dynamic here/gone
*<b><i>move</i></b>|&lt;salt&gt;|&lt;data&gt;         -> move|done
                           --> <b><i>data</i></b>|&lt;name&gt;|&lt;data&gt;
                            // &lt;data&gt; = &lt;x&gt;+&lt;y&gt;+&lt;z&gt;|&lt;x&gt;+&lt;y&gt;+&lt;z&gt;+&lt;w&gt;|&lt;action&gt;(|&lt;speed&gt;|...)
                            //          position   |orientation    |key/button

-> main|fail|type not found

+----------------+
| <i>Sketched rules</i> |
+----------------+

// peer protocol

*<b><i>talk</i></b> // send voice
*<b><i>look</i></b> // send eye movement
*<b><i>head</i></b> // send head movement
*<b><i>body</i></b> // send body movement
*<b><i>hand</i></b> // send hand movement

// name pool

 <b><i>info</i></b>, <b><i>ping</i></b>, <b><i>time</i></b>, <b><i>away</i></b>, <b><i>host</i></b>, <b><i>pull</i></b>, <b><i>pick</i></b>, <b><i>push</i></b>, <b><i>hide</i></b>, <b><i>show</i></b>, <b><i>nick</i></b>, <b><i>view</i></b>, <b><i>fill</i></b>
</pre>