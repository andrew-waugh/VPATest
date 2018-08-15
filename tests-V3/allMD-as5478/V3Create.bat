@echo off
set code="C:\Users\Andrew\Documents\Work\VERSCode\neoVEO"
set bin="C:\Program Files\Java\jdk1.8.0_162\bin"
rem set code="J:\PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS\VERS-2015\neoVEO"
rem set bin="C:\Program Files\Java\jdk1.8.0_144\bin"
set versclasspath=%code%/dist/*
%bin%\java -classpath %versclasspath% VEOCreate.CreateVEOs -v -c ./control.txt -t ./templates -o .. %*
