```
Protocol:           --> = broadcast to Pull() (event(path, data) for XHR)
                     -> = direct return on Push()

<> = mandatory
[] = optional

In sort of chronological order:

<type>                 <echo>

 -> main|fail|name missing
 
                     // register new user
                     // [pass] if your platform cannot persist the key
 join|[pass]         -> join|done|<key>
                     -> join|fail|<name> contains bad characters
                     -> join|fail|<name> already registered

                     // login old user
                     // <hash> is either md5(<key> + <salt>)
                     //               or md5([pass] + <salt>)
 salt                -> salt|done|<salt>
 user|<salt>|<hash>  -> user|done
                     -> user|fail|user not found
                     -> user|fail|salt not found
                     -> user|fail|wrong hash

 <pull> = here you should call Pull(<name>) (C#)
          or pull(<name>) (XHR) with the name you
          successfully logged in as.

 -> main|fail|user '<name>' not authorized

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
                     -> list|fail|can only list 'room' or 'data'

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

                     // real-time gameplay packets
 move|<data>         -> move|done
                    --> data|<name>|<data>
                     // <data> = <x>+<y>+<z>|<x>+<y>+<z>+<w>|<action>(|<speed>|...)
                     //          position   |orientation    |key/button

 -> main|fail|type '<type>' not found

<soon> // future protocol

 pull // load cards
 pick // select card
 push // show new cards

<peer> // peer protocol

 talk // send voice
 head // send head/body movement
 hand // send hand movements
```