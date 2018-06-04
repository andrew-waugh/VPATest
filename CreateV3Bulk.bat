@echo off
rem set code="C:\Users\Andrew\Documents\Work\VERS-2015\VPA"
rem set bin="C:\Program Files\Java\jdk1.8.0_162\bin"
set code="J:\PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS\VERS-2015\VPATest"
set bin="C:\Program Files\Java\jdk1.8.0_144\bin"
set versclasspath=%code%/dist/*
%bin%\java -classpath %versclasspath% VPATest.CreateBulkV3 -r 50 -m 100 -p 0.2 -t ./testData -o ./output -ha SHA-512 -s ./testData/signer.pfx Ag0nc1eS -v %*
