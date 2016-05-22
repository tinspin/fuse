<pre>
<img width="140" height="80" src="https://rawgit.com/tinspin/fuse/master/res/fuse.svg">

+---------------------------+
| <i>The multiplayer solution.</i> |
+---------------------------+

Support:

  - unity
    - plugin is only one file of C# code: <a href="https://github.com/tinspin/fuse/blob/master/src/Fuse.cs">Fuse.cs</a>, <a href="https://dl.dropboxusercontent.com/u/1352420/Fuse.zip">Minimalist Unity Project</a>
  - javascript XHR/XDR, 99.9% of browsers, only IE7 missing
    - CORS compliant, static hosting: <a href="https://github.com/tinspin/fuse/blob/master/res/play.html">play.html</a>, <a href="https://github.com/tinspin/fuse/blob/master/res/cube.html">cube.html</a>, <a href="https://github.com/tinspin/fuse/blob/master/res/bomb.html">bomb.html</a>
  - java will be added later, if somebody needs it now you can hopefully 
    figure it out from <a href="http://rupy.se/talk.zip">src/stream/Client.java</a>.
  - 100% firewall pass-through
  - all gameplay types:
    - from two player turn-based
    - to real-time action MMO

Protocol:

  - uses |;, separation:
    - '|' protocol
    - ';' objects
    - ',' attributes
  - client/server triplex HTTP, comet upstream 
    '\n' terminated or 'data: \n\n' encapsulated
    - dynamic presence position move packets for MMO
  - peer-to-peer UDP, binary physics packets for VR
    - position move and talk, look, head, body, hand packets
  - multicast UDP on cluster for load distribution
    - concurrency ~25.000 mess./sec. on 4x raspberry pi 2

Platform:

  - low internal latency: <a href="http://fuse.rupy.se/data">stat</a>
  - multithreaded NIO with queue, linear perf.:
    - 1000 mess./sec. on raspberry pi 1
    - 6500 mess./sec. on raspberry pi 2
  - 100% uptime on routing with round-robin DNS
  - 100% read uptime on persistence with custom 
    async distributed JSON file system database 
    that uses ext4 indexing

Examples:

  - javascript block-drop game: <a href="http://fuse.rupy.se">cube</a> (open-source, try single-player <a href="http://fuse.rupy.se/cube.html">cube.html</a>)
  - java 3D VR MMO space shooter: <a href="http://aeonalpha.com">aeon</a> (closed-source)
    
+-------------------+
| <i>Work in progress!</i> |
+-------------------+

o-> = async. broadcast to Read() (C#) or read(data) (XHR/XDR) including self
x-> = async. broadcast to Read() (C#) or read(data) (XHR/XDR) excluding self
i-> = async. send to one user for unique feedback.
 -> = sync. return on Push(data) or push(data)

<…> = mandatory
[…] = optional
{…} = json

 *  = not implemented yet

&lt;spot&gt; = &lt;x&gt;,&lt;y&gt;,&lt;z&gt;
&lt;tree&gt; for <b><i>here</i></b>/<b><i>gone</i></b>/<b><i>chat</i></b>:

The server is the <b><i>root</i></b>
The game is a <b><i>stem</i></b> and also a room.
The room is a <b><i>leaf</i></b>

In sort of chronological order:

+-----------------------------------+
| <i>Rule</i>                      -> <i>Echo</i> |
+-----------------------------------+

                            // to get latency
 <b><i>ping</i></b>                       -> ping|done
 
                            // to get server time
 <b><i>time</i></b>                       -> time|done|HH:mm:ss

                            // to get server date
 <b><i>date</i></b>                       -> date|done|yyyy/MM/dd

                            // register
                            // [name] if you can't store the &lt;id&gt; otherwise set
                            //        to empty string (||)
                            // [pass] if you can't store the &lt;key&gt; otherwise set
                            //        to empty string (||)
                            //        preferably [pass] is a hash with salt 
                            //        we simply use <i>hash</i>(pass + name.toLowerCase())
                            // [mail] if your users want recovery otherwise set 
                            //        to empty string (|)
 <b><i>user</i></b>|[name]|[pass]|[mail]  -> user|done|&lt;salt&gt;|&lt;key&gt;|&lt;id&gt;
                            -> user|fail|name too short     // min 3
                            -> user|fail|name too long      // max 12
                            -> user|fail|name already registered
                            -> user|fail|name invalid       // [a-zA-Z0-9.\\-]+
                            -> user|fail|name alpha missing // [0-9]+ reserved for &lt;id&gt;
                            -> user|fail|mail invalid       // [a-zA-Z0-9.@\\-\\+]+
                            -> user|fail|mail already registered
                            -> user|fail|pass too short
 
                            // get salt for &lt;name&gt; or &lt;id&gt;
 <b><i>salt</i></b>|&lt;name&gt;/&lt;id&gt;           -> salt|done|&lt;salt&gt;
                            -> salt|fail|name not found
                            -> salt|fail|id not found
                            -> salt|fail|unknown problem
 
 <b><i>\/</i></b> anything below          -> main|fail|salt not found
 
                            // login
                            // &lt;hash&gt; is either <i>hash</i>(&lt;key&gt; + &lt;salt&gt;)
                            //               or <i>hash</i>(<i>hash</i>(pass + name.toLowerCase()) + &lt;salt&gt;)
 <b><i>sign</i></b>|&lt;salt&gt;|&lt;hash&gt;         -> sign|done|&lt;name&gt;/&lt;id&gt;
                            -> sign|fail|wrong pass
                            -> sign|fail|unknown problem

 <b><i>\/</i></b> anything below          -> main|fail|not authorized

+-------------------------------+
| <i>Here you have to call pull()!</i> |
+-------------------------------+

<i>You have to wait until pull completes and you receive the first <b>noop</b> before you continue.</i>

+------------------------------------------------------+
| <i>Below this line &lt;name&gt;/&lt;id&gt; is replaced with &lt;user&gt;</i>. |
+------------------------------------------------------+

                            // join a game
 <b><i>game</i></b>|&lt;salt&gt;|&lt;name&gt;         -> game|done
                           x-> <b><i>here</i></b>|&lt;tree&gt;|&lt;user&gt;[|ip]
                           x-> <b><i>ally</i></b>|&lt;user&gt;
                           x-> <b><i>away</b></i>|&lt;user&gt;                  // AFK
                           o-> <b><i>name</b></i>|&lt;user&gt;|&lt;name&gt;           // if &lt;id&gt; used and name set
                           o-> <b><i>nick</b></i>|&lt;user&gt;|&lt;nick&gt;           // if &lt;id&gt; used and nick set
                            -> game|fail|name invalid       // [a-zA-Z]+

 <b><i>\/</i></b> anything below          -> main|fail|no game

                            // set nick for user
 <b><i>nick</i></b>|&lt;salt&gt;|&lt;nick&gt;         -> nick|done
                            -> nick|fail|nick invalid       // [a-zA-Z0-9.\\-]+

                            // get nick for any id
 <b><i>nick</i></b>|&lt;salt&gt;|&lt;id&gt;           -> nick|done|&lt;nick&gt;
                            -> nick|fail|not found

                            // set name for user
 <b><i>name</i></b>|&lt;salt&gt;|&lt;name&gt;         -> name|done
                            -> name|fail|name invalid       // [a-zA-Z0-9.\\-]+
                            -> name|fail|name alpha missing // [0-9]+ reserved for &lt;id&gt;
                            -> name|fail|taken

                            // get name for any id
 <b><i>name</i></b>|&lt;salt&gt;|&lt;id&gt;           -> name|done|&lt;name&gt;
                            -> name|fail|not found

                            // set pass
 <b><i>pass</i></b>|&lt;salt&gt;|&lt;pass&gt;         -> pass|done
 
                            // set mail
 <b><i>mail</i></b>|&lt;salt&gt;|&lt;mail&gt;         -> mail|done
                            -> mail|fail|mail invalid       // [a-zA-Z0-9.@\\-\\+]+

                            // AFK
 <b><i>away</i></b>|&lt;salt&gt;                -> away|done
                           x-> <b><i>away</b></i>|&lt;user&gt;
                           o-> <b><i>hold</b></i>                         // pause

                            // back
 <b><i>back</i></b>|&lt;salt&gt;                -> back|done
                           x-> <b><i>back</b></i>|&lt;user&gt;
                           o-> <b><i>free</b></i>                         // unpause

                            // add/remove friend
 <b><i>ally</i></b>|&lt;salt&gt;|&lt;user&gt;[|info]  -> ally|done[|poll]
                            -> ally|fail|user not online
                            -> ally|fail|user busy
                           i-> <b><i>poll</i></b>|ally|&lt;user&gt;[|info]

                            // get user persistent data
 <b><i>hard</i></b>|&lt;salt&gt;|&lt;user&gt;|&lt;name&gt;  -> hard|done|{…}
                            -> hard|fail|not found
                            
                            // get user inventory item
 <b><i>item</i></b>|&lt;salt&gt;|&lt;user&gt;|&lt;name&gt;  -> item|done|{…}
                            -> item|fail|not found
                            
                            // get user transient data
 <b><i>soft</i></b>|&lt;salt&gt;|&lt;user&gt;|&lt;name&gt;  -> soft|done|{…}
                            -> soft|fail|salt not found
                            -> soft|fail|user not found
                            -> soft|fail|not found
                            
                            // drop item, how many and which
 <b><i>drop</i></b>|&lt;salt&gt;|&lt;name&gt;|&lt;many&gt;  -> drop|done|&lt;salt&gt;
                            -> drop|fail|not found
                            -> drop|fail|not enough
                           o-> item|&lt;salt&gt,&lt;spot&gt;,&lt;name&gt;,&lt;many&gt;

                            // pick item
 <b><i>pick</i></b>|&lt;salt&gt;|&lt;salt&gt;         -> pick|done
                            -> pick|fail|not found
                           o-> pick|&lt;user&gt;|&lt;item&gt;           // salt
                           i-> real|{…}                     // which real item was picked
                           
                            // get user country (ISO 3166)
*<b><i>flag</i></b>|&lt;salt&gt;|&lt;user&gt;         -> flag|done|&lt;code&gt;

                            // enable peer-to-peer
                            // before you send this it is important to 
                            // inform the user that privacy is lost!
 <b><i>peer</i></b>|&lt;salt&gt;|&lt;ip&gt;           -> peer|done                    // send the internal IP

                            // host room
 <b><i>room</i></b>|&lt;salt&gt;|&lt;type&gt;|&lt;size&gt;  -> room|done
                           x-> <b><i>room</i></b>|&lt;user&gt;,&lt;type&gt;,&lt;size&gt;
                            -> room|fail|not in lobby
                            -> room|fail|type invalid       // [a-zA-Z]+

                            // list rooms, room items or data (hard, soft and inventory items)
 <b><i>list</i></b>|&lt;salt&gt;|room           -> list|done|room|&lt;user&gt;,&lt;type&gt;,&lt;here&gt;,&lt;size&gt;;…
 <b><i>list</i></b>|&lt;salt&gt;|room|item      -> list|done|room|item|&lt;salt&gt,&lt;spot&gt;,&lt;name&gt;,&lt;many&gt;;…
*<b><i>list</i></b>|&lt;salt&gt;|user           -> list|done|user|&lt;user&gt;;…
 <b><i>list</i></b>|&lt;salt&gt;|user|hard      -> list|done|user|hard|&lt;name&gt;,&lt;size&gt;;…
 <b><i>list</i></b>|&lt;salt&gt;|user|soft      -> list|done|user|soft|&lt;name&gt;,&lt;size&gt;;…
 <b><i>list</i></b>|&lt;salt&gt;|user|item      -> list|done|user|item|&lt;name&gt;,&lt;many&gt;;…
                            -> list|fail|not found
                            -> list|fail|type missing
                            -> list|fail|wrong type

                            // join room
                            // between <i>lock</i> and <i>view</i> nobody can join
                            // this sends a poll to the user if he has no room
 <b><i>join</i></b>|&lt;salt&gt;|&lt;user&gt;[|info]  -> join|done|poll/room
                           i-> <b><i>poll</i></b>|join|&lt;user&gt;[|info]
                           x-> <b><i>here</i></b>|&lt;tree&gt;|&lt;user&gt;[|ip]      // leaf, in new room
                           x-> <b><i>ally</i></b>|&lt;user&gt;
                           x-> <b><i>gone</i></b>|&lt;tree&gt;|&lt;user&gt;|&lt;room&gt;    // stem, in lobby
                           x-> <b><i>lock</i></b>|&lt;room&gt;                  // in lobby if room is full
                            -> join|fail|not found
                            -> join|fail|already here
                            -> join|fail|is full
                            -> join|fail|user busy

                            // accept poll, you can only have one poll per user at the same time.
                            // &lt;user&gt; is the user name received in the poll request
                            // type = join; player joins the room of the host
                            // type = ally; both players stored as friends
 <b><i>poll</i></b>|&lt;salt&gt;|&lt;user&gt;|&lt;bool&gt;  -> poll|done|&lt;type&gt;
                            -> poll|fail|user not online
                            -> poll|fail|wrong user
                            -> poll|fail|type not found

                            // permanently ban user from room
*<b><i>kick</i></b>|&lt;salt&gt;|&lt;user&gt;         -> kick|done
                            -> kick|fail|not creator
                            -> kick|fail|not here
 
                            // exit room
 <b><i>exit</i></b>|&lt;salt&gt;                -> exit|done
                           x-> <b><i>here</i></b>|&lt;tree&gt;|&lt;user&gt;[|ip]      // stem, in lobby
                           x-> <b><i>ally</i></b>|&lt;user&gt;
                           x-> <b><i>away</b></i>|&lt;user&gt;                  // AFK, in lobby if creator exit
                           x-> <b><i>gone</i></b>|&lt;tree&gt;|&lt;user&gt;|&lt;room&gt;    // leaf, in old room
                           x-> <b><i>drop</i></b>|&lt;user&gt;                  // in lobby if creator leaves
                           x-> <b><i>stop</i></b>|&lt;user&gt;                  // in old room if creator leaves
                           x-> <b><i>open</i></b>|&lt;room&gt;                  // in lobby if room is not full
                            -> exit|fail|in lobby

                            // quit platform
 <b><i>quit</i></b>|&lt;salt&gt;                -> quit|done
                           x-> <b><i>quit</i></b>|&lt;user&gt;

                            /* data handling save/load/tear:
                             * the default disk stored [type] is "hard"
                             * to save inventory items to disk use [type] = "item" (needs "count" number)
                             * to save transient data in memory use [type] = "soft" (disappears on relogin)
                             */

                            // save data
 <b><i>save</i></b>|&lt;salt&gt;|&lt;name&gt;|{…}[|type] -> save|done
                            -> save|fail|name too short // min 3
                            -> save|fail|name too long // max 12
                            -> save|fail|unknown problem

                            // load data
 <b><i>load</i></b>|&lt;salt&gt;|&lt;name&gt;[|type]  -> load|done|{…}
                            -> load|fail|not found
                            
                            // delete data
 <b><i>tear</i></b>|&lt;salt&gt;|&lt;name&gt;[|type]  -> tear|done
                            -> tear|fail|not found

                            // play game
 <b><i>play</i></b>|&lt;salt&gt;[|seed]         -> play|done
                           o-> <b><i>play</i></b>[|seed]                  // to start the game
                           x-> <b><i>view</i></b>|&lt;room&gt;                  // in lobby if room has started
                            -> play|fail|in lobby
                            -> play|fail|not creator
                            -> play|fail|only one player
                            -> play|fail|already playing
                            -> play|fail|someone is away

                            // game over
 <b><i>over</i></b>|&lt;salt&gt;[|data]         -> over|done                    // insecure, only for development
                           o-> <b><i>over</b></i>|&lt;user&gt;[|data]           // the game is over
                            -> over|fail|not playing

+------------------------------------------------------------+
| <i>These have to be sent in a separate thread from rendering.</i> |
+------------------------------------------------------------+

                            // chat in any room
                            // &lt;tree&gt; are root, stem or leaf
 <b><i>chat</i></b>|&lt;salt&gt;|&lt;tree&gt;|&lt;text&gt;  -> chat|done                    // @[user] of private destination
                           o-> <b><i>chat</i></b>|&lt;tree&gt;|&lt;user&gt;|&lt;text&gt;
                            -> chat|fail|not online

                            // send any gameplay data to room
 <b><i>send</i></b>|&lt;salt&gt;|&lt;data&gt;         -> send|done
                           x-> <b><i>send</i></b>|&lt;user&gt;|&lt;data&gt;
 
                            // send any gameplay data to room
 <b><i>show</i></b>|&lt;salt&gt;|&lt;data&gt;         -> show|done
                           o-> <b><i>show</i></b>|&lt;user&gt;|&lt;data&gt;
 
                            // motion for 3D MMO games with dynamic here/gone
 <b><i>move</i></b>|&lt;salt&gt;|&lt;data&gt;         -> move|done
                           x-> <b><i>move</i></b>|&lt;user&gt;|&lt;x&gt;,&lt;y&gt;,&lt;z&gt;;&lt;x&gt;,&lt;y&gt;,&lt;z&gt;,&lt;w&gt;;&lt;action&gt;(;&lt;speed&gt;;…)
                            //             position   |orientation    |key/button

                            // destination to interpolate to
*<b><i>goal</i></b>|&lt;salt&gt;|&lt;spot&gt;         -> goal|done
                           x-> <b><i>goal</i></b>|&lt;user&gt;|&lt;spot&gt;
                            
                            // object to follow/attack
*<b><i>hunt</i></b>|&lt;salt&gt;|&lt;salt&gt;|&lt;kill&gt;  -> hunt|done
                           x-> <b><i>hunt</i></b>|&lt;user&gt;|&lt;salt&gt;|&lt;kill&gt;
                            
 <b><i>/\</b></i> type not found          -> main|fail|type not found

+------------------+       
| <i>Broadcast rules.</i> |
+------------------+

                           o-> <b><i>noop</i></b>                         // no operation; to keep socket alive
                           o-> <b><i>item</i></b>|&lt;salt&gt,&lt;spot&gt;,&lt;name&gt;,&lt;many&gt;  // items appearing in room
                           o-> <b><i>warn</i></b>|boot/info/none|&lt;text&gt;   // to broadcast global messages

+-----------------+       
| <i>Sketched rules.</i> |
+-----------------+

// peer protocol

*<b><i>talk</i></b> // send voice
*<b><i>look</i></b> // send eye movement
*<b><i>head</i></b> // send head movement
*<b><i>body</i></b> // send body movement
*<b><i>hand</i></b> // send hand movement

// name pool

 lead
 head tail
 push pull
 show hide
 gain lose
 item info data      tape task step
 time host           solo      slow slot
                     skip skin size site
      fill full make seal seek sell
 slay ruin rise poll said rank rate star
 drop made halt vote read      rest peek
 body text sent loss plan page need
 find      idle keep mark mask mate look
      wake like have home
 undo tool turn      grab grip grow file
 face edit      echo drop copy      busy
      base
 paid rent cash sold coin earn bill sale
 tone tune mute walk
 zero none

// todo

 - Hm, done?!

// attribution

 pop.mp3 - Mark DiAngelo
 snap.mp3 - Mark DiAngelo
 
// credits

 Pilot Developer: Jonas Oien
 Feedback: Søren Tramm
           Nicolai Farver
</pre>