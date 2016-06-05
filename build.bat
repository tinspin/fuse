:: C++ with MinGW, you might have to copy libstdc++-6.dll to the src folder!
g++ -std=c++11 src\Fuse.cpp -o src\Fuse.exe -lws2_32 -lpthread -DHAVE_STRUCT_TIMESPEC
:: C# with Mono
::"C:\Program Files (x86)\Mono\bin\mcs.bat" src\Fuse.cs
pause