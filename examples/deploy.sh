#!/bin/bash


cd out
for f in xml*/
do
	echo $f
	cd $f
	~/Documents/BEAST/BEAST.v2.6.2.Linux/beast/bin/beast -overwrite poems.xml & 
	cd ../

done	
