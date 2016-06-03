:: C++ with MinGW
g++ src\Fuse.cpp -o src\Fuse.exe -lws2_32 -lpthread -DHAVE_STRUCT_TIMESPEC
:: C# with Mono
::"C:\Program Files (x86)\Mono\bin\mcs.bat" src\Fuse.cs
pause