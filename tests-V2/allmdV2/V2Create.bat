@echo off
set code="C:\Users\Andrew\Documents\Work\VERSCode\V2Generator"
set bin="C:\Program Files\Java\jdk1.8.0_162\bin"
rem set code="J:\PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS\VERSCode\V2Generator"
rem set bin="C:\Program Files\Java\jdk1.8.0_144\bin"
set versclasspath=%code%/dist/*
%bin%\java -classpath %versclasspath% VEOGenerator.VEOCreator -v -t . -s ../signer.pfx -p Ag0nc1eS -d ./controlFile.txt -o ../../output
