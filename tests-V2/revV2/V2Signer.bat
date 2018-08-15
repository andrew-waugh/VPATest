@echo off
set codebase="C:/Users/Andrew/Documents/Work"
set bin="C:\Program Files\Java\jdk1.8.0_162\bin"
rem set codebase="J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS"
rem set bin="C:\Program Files\Java\jdk1.8.0_144\bin"
set versclasspath=%codebase%/VERSCode/V2Signer/dist/*
%bin%\java -classpath %versclasspath% veosigner.VEOSigner -s ../signer.pfx -p Ag0nc1eS -o . %*
