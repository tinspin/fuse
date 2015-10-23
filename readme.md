<pre>
protocol            --> = broadcast to Pull() (message(data) for XHR)
                     -> = direct return on Push()

< > = mandatory
[ ] = optional
 *  = not implemented yet

in sort of chronological order:

<type>                 <echo>
 
                     // register
                     // [mail] if you want recovery, set [mail] to empty 
                     //        string (||) if you want pass without mail
                     // [pass] if your platform cannot persist the key 
                     //        preferably this is a hash with salt 
                     //        for example we simply use md5(pass + name)
                     // use <id> as name if you want anonymous users
 user|[mail]|[pass]  -> user|done|<key>|<id>
                     -> user|fail|name too short
                     -> user|fail|pass too short
                     -> user|fail|name alpha missing // numeric reserved for <id>
                     -> user|fail|name invalid // only alphanumeric and .
                     -> user|fail|mail invalid // only alphanumeric and .@-+
                     -> user|fail|name already registered
                     -> user|fail|mail already registered
 
-> main|fail|name missing
-> main|fail|name too short
 
 salt                -> salt|done|<salt>
 
                     // login
                     // <hash> is either md5(<key> + <salt>)
                     //               or md5([pass] + <salt>)
                     //        we use md5(md5(pass + name) + <salt>)
                     //        make sure you keep the case correct
 open|<salt>|<hash>  -> open|done
                     -> open|fail|user not found
                     -> open|fail|salt not found
                     -> open|fail|wrong pass

<pull> = here you should call Pull(<name>) (C#)
         or pull(<name>) (XHR) with the name you
         successfully logged in as.

-> main|fail|user not authorized

                     // how many users or rooms does the server host
*info|<type>         -> info|done|<user>            // if <type> = 'user'
                     -> info|done|<room>            // if <type> = 'room'
                     
                     // tcp keep-alive for push socket
*ping                -> ping|done

                     // ask server for local time
*time                -> time|done|<date>            // ISO 8601 date
                                                    // yyyy-MM-dd'T'HH:mm:ss.SSSZ

                     // add friend
*ally|<name>         -> ally|done
                     -> ally|fail|user not found

                     // set status
*away|<boolean>      -> away|done

                     // enable peer-to-peer
 peer|<192.168...>   -> peer|done                    // send the internal IP

                     // add client as IPv6 host
*host|<IPv6>         -> host|done                    // send the global IPv6

                     // host room
 room|<type>|<size>  -> room|done
                    --> made|<name>+<type>+<size>    // in lobby
                     -> room|fail|user not in lobby

                     // list unlocked rooms with space left or data
 list|room           -> list|done|room|<name>+<type>+<size>|...
 list|data|<type>    -> list|done|data|<id>|...      // use load to get data
                     -> list|fail|wrong type

                     // join room
 join|<name>         -> join|done
                    --> here|<name>[|<ip>]           // in new room, all to all
                                                        IP if peer was set
                    --> gone|<name>                  // in lobby
                     -> join|fail|room not found
                     -> join|fail|room is locked
                     -> join|fail|already in room
                     -> join|fail|room is full

                     // permanently ban user from room
*kick|<name>         -> kick|done
                     -> kick|fail|not creator
                     -> kick|fail|user not here
 
                     // quit room
 quit                -> quit|done
                    --> here|<name>[|<ip>]           // in lobby, all to all
                                                        IP if peer was set
                    --> gone|<name>                  // in old room OR
                    --> stop|<name>                  // in old room when maker leaves 
                                                        then room is dropped and everyone 
                                                        put back in lobby
                    --> halt|<name>                  // in lobby if creator or last user leaves
                     -> exit|fail|user in lobby

                     // user exit
*exit                -> exit|done
                    --> kill|<name>
                    
                     // lock room before the game starts
*lock                -> lock|done
                    --> link|<name>                  // to everyone in room, can be used 
                                                        to start the game
                     -> lock|fail|user not room host

                     // insert and select data
 save|<type>|<json>  -> save|done|<id>|<key>         // to update data use this key in json
                     -> save|fail|data too large
 load|<type>|<id>    -> load|done|<json>             // use id from list|data|<type>
                     -> load|fail|data not found

                     // chat in any room
 chat|<text>         -> chat|done                    // @[name] of private destination
                    --> text|<name>|<text>
                     -> chat|fail|user not online

                     // send any gameplay data to room
 send|<data>         -> send|done
                    --> sent|<name>|<data>
 
                     // motion for 3D MMO games with dynamic here/gone
*move|<data>         -> move|done
                    --> data|<name>|<data>
                     // <data> = <x>+<y>+<z>|<x>+<y>+<z>+<w>|<action>(|<speed>|...)
                     //          position   |orientation    |key/button

-> main|fail|type not found

<soon> // future protocol

*pull // load cards
*pick // select card
*push // show new cards

<peer> // peer protocol

*talk // send voice
*head // send head/body movement
*hand // send hand movements
</pre>