@echo off
if exist "J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS/VERSCode" (
	set code="J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS/VERSCode"
) else (
	set code="C:/Users/Andrew/Documents/Work/VERSCode"
)
java -classpath %code%/VPATest/dist/* VPATest.CreateBulkV2 -r 50 -m 100 -t ./testData -o ./output -ha SHA-512 -s ./testData/signer.pfx Ag0nc1eS -v  %*
