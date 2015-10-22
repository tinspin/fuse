```
Protocol:           --> = broadcast to Pull() (event(path, data) for XHR)
                     -> = direct return on Push()

<> = mandatory
[] = optional

In sort of chronological order:

<type>                 <echo>
 
                     // register new user
                     // [mail] if you want recovery, set [mail] to empty 
                     //        string (||) if you want pass without mail
                     // [pass] if your platform cannot persist the key 
                     //        preferably this is a hash with salt 
                     //        for example we simply use md5(pass + name)
                     // use <id> as name if you want anonymous users
 join|[mail]|[pass]  -> join|done|<key>|<id>
                     -> join|fail|name too short
                     -> join|fail|pass too short
                     -> join|fail|name alpha missing // numeric reserved for <id>
                     -> join|fail|name invalid // only alphanumeric and .
                     -> join|fail|mail invalid // only alphanumeric and .@-+
                     -> join|fail|name already registered
                     -> join|fail|mail already registered

 salt                -> salt|done|<salt>
 
  -> main|fail|name missing
  -> main|fail|name too short
 
                     // login old user
                     // <hash> is either md5(<key> + <salt>)
                     //               or md5([pass] + <salt>)
                     //        we use md5(md5(pass + name) + <salt>)
                     //        make sure you keep the case correct
 user|<salt>|<hash>  -> user|done
                     -> user|fail|user not found
                     -> user|fail|salt not found
                     -> user|fail|wrong pass

 <pull> = here you should call Pull(<name>) (C#)
          or pull(<name>) (XHR) with the name you
          successfully logged in as.

 -> main|fail|user not authorized

                     // add friend
 ally|<name>         -> ally|done
                     -> ally|fail|user not found

                     // set status
 away|<boolean>      -> away|done

                     // enable peer-to-peer
 peer|<192.168...>   -> peer|done                    // send the internal IP

                     // host room
 host|<type>|<size>  -> host|done
                     -> host|fail|user not in lobby

                     // list rooms or data
 list|room           -> list|room|done|<name>+<type>+<size>|...
 list|data|<type>    -> list|data|done|<id>|...      // use load to get data
                     -> list|fail|wrong type

                     // join room
 room|<name>         -> room|done
                    --> here|<name>[|<ip>]           // in new room, all to all
                                                        IP if peer was set
                    --> gone|<name>                  // in lobby
                     -> room|fail|room not found
                     -> room|fail|room is locked
                     -> room|fail|already in room
                     -> room|fail|room is full

                     // exit room
 exit                -> exit|done
                    --> here|<name>[|<ip>]           // in lobby, all to all
                                                        IP if peer was set
                    --> gone|<name>                  // in old room OR
                    --> stop|<name>                  // in old room when maker leaves 
                                                        then room is dropped and everyone 
                                                        put back in lobby
                     -> exit|fail|user in lobby

                     // lock room before the game starts
 lock                -> lock|done
                    --> link|<name>                  // to everyone in room, can be used 
                                                        to start the game
                     -> lock|fail|user not room host

                     // insert and select data
 save|<type>|<json>  -> save|done|<id>|<key>         // to update data use this key in json
                     -> save|fail|data too large
 load|<type>|<id>    -> load|done|<json>             // use id from list|data|<type>
                     -> load|fail|data not found

                     // chat anywhere
 chat|<text>         -> chat|done
                    --> text|<name>|<text>

                     // send any gameplay data
 send|<data>         -> send|done
                    --> sent|<name>|<data>
 
                     // motion for 3D MMO games with dynamic here/gone
 move|<data>         -> move|done
                    --> data|<name>|<data>
                     // <data> = <x>+<y>+<z>|<x>+<y>+<z>+<w>|<action>(|<speed>|...)
                     //          position   |orientation    |key/button

 -> main|fail|type not found

<soon> // future protocol

 pull // load cards
 pick // select card
 push // show new cards

<peer> // peer protocol

 talk // send voice
 head // send head/body movement
 hand // send hand movements
```