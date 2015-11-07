<pre>
+---------------------------+
| <i>The multiplayer solution.</i> |
+---------------------------+

Support:

  - unity
    - plugin is only 140 lines of C# code: <a href="https://github.com/tinspin/fuse/blob/master/src/Fuse.cs">Fuse.cs</a>
  - XHR/XDR, 99.9% of browsers, only IE7 missing
    - CORS compliant, static hosting: <a href="https://github.com/tinspin/fuse/blob/master/res/game.html">game.html</a>
  - 100% firewall pass-through
  - all gameplay types:
    - from two player turn-based
    - to real-time action MMO

Protocol:

  - client/server triplex HTTP, \n terminated or data: \n\n encapsulated, text based
    - dynamic presence position move packets for MMO
  - peer-to-peer UDP, binary physics packets for VR
    - position move and talk, look, head, body, hand packets
  - multicast UDP on cluster for load distribution

Platform:

  - proven for 5 years
  - 100% uptime on routing
  - 100% read uptime on data

Examples:

  - XHR multiplayer block-drop game: <a href="http://fuse.rupy.se">try it!</a>
    <img src="https://rawgit.com/tinspin/fuse/master/res/svg/blue.svg"><img src="https://rawgit.com/tinspin/fuse/master/res/svg/green.svg"><img src="https://rawgit.com/tinspin/fuse/master/res/svg/orange.svg"><img src="https://rawgit.com/tinspin/fuse/master/res/svg/purple.svg">
  - Java LWJGL VR MMO: <a href="http://aeonalpha.com">aeon</a>
    <img src="https://dl.dropboxusercontent.com/u/1352420/aeon_alpha.png">

+-------------------+
| <i>Work in progress!</i> |
+-------------------+

--> = async. broadcast to Read() (C#) or read(data) (XHR/XDR)
 -> = sync. return on Push(data) or push(data)

< > = mandatory
[ ] = optional

 *  = not implemented yet

In sort of chronological order:

+-----------------------------------+
| <i>Rule</i>                      -> <i>Echo</i> |
+-----------------------------------+

                            // register
                            // [name] if you can't store the &lt;id&gt; otherwise set
                            //        to empty string (||)
                            // [mail] if your users want recovery otherwise set 
                            //        to empty string (||)
                            // [pass] if you can't store the &lt;key&gt; otherwise set
                            //        to empty string (||)
                            //        preferably [pass] is a hash with salt 
                            //        for example we simply use md5(pass + name)
 <b><i>user</i></b>|[name]|[mail]|[pass]  -> user|done|&lt;salt&gt;|&lt;key&gt;|&lt;id&gt;
                            -> user|fail|name too short
                            -> user|fail|name too long
                            -> user|fail|name already registered
                            -> user|fail|name invalid       // only alphanumeric and .-
                            -> user|fail|name alpha missing // numeric reserved for &lt;id&gt;
                            -> user|fail|mail invalid       // only alphanumeric and .@-+
                            -> user|fail|mail already registered
                            -> user|fail|pass too short
 
                            // to get the &lt;id&gt; of a mail
                            // if you want to login with &lt;id&gt; below
 <b><i>mail</i></b>|&lt;mail&gt;                -> mail|done|&lt;id&gt;
                            -> mail|fail|user not found
 
                            // get salt for &lt;name&gt; or &lt;id&gt;
 <b><i>salt</i></b>|&lt;name&gt;/&lt;id&gt;           -> salt|done|&lt;salt&gt;
                            -> salt|fail|user not found
 
 <b><i>\/</i></b> anything below          -> main|fail|invalid salt
 
                            // login
                            // &lt;hash&gt; is either md5(&lt;key&gt; + &lt;salt&gt;)
                            //               or md5([pass] + &lt;salt&gt;)
                            //        we use md5(md5(pass + name) + &lt;salt&gt;)
                            //        make sure you keep the case correct
 <b><i>open</i></b>|&lt;salt&gt;|&lt;hash&gt;         -> open|done|&lt;name&gt;/&lt;id&gt;
                            -> open|fail|wrong pass
                            -> open|fail|wrong key

 <b><i>\/</i></b> anything below          -> main|fail|user not open

+-------------------------------+
| <i>Here you have to call pull()!</i> |
+-------------------------------+

                            // join a game
 <b><i>game</i></b>|&lt;salt&gt;|&lt;name&gt;         -> game|done
                            -> game|fail|name invalid
                     
 <b><i>\/</i></b> anything below          -> main|fail|user has no game

                            // pause game
*<b><i>away</i></b>|&lt;salt&gt;|&lt;bool&gt;         -> away|done
                           --> <b><i>hold</b></i>|&lt;name&gt;
                           --> <b><i>free</b></i>|&lt;name&gt;

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
 <b><i>play</i></b>|&lt;salt&gt;[|seed]         -> play|done
                           --> <b><i>lock</i></b>[|seed]                  // to start the game
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

 <b><i>/\</b></i> type not implemented    -> main|fail|type not found

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

 <b><i>info</i></b>, <b><i>ping</i></b>, <b><i>time</i></b>, <b><i>host</i></b>, <b><i>pull</i></b>, <b><i>pick</i></b>, <b><i>push</i></b>, <b><i>hide</i></b>, <b><i>show</i></b>, <b><i>nick</i></b>, <b><i>view</i></b>, <b><i>fill</i></b>
</pre>