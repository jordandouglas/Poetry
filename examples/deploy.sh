#!/bin/bash


TEMPLATE=../../template.sh

cd out
for f in xml*/
do
	echo $f
	cd $f


	mc3=false
	if grep -q "CoupledMCMC" "out.xml"; then
  		mc3=true
	fi



	rand=$RANDOM
	echo "Dispatching ${f%?} with seed $rand"
	sed "s/JOBID/${f%?}/g" ${TEMPLATE} > temp.sl
	sed "s/RANDOMSEED/$rand/g" temp.sl > temp2.sl

	if ${mc3} ; then
		echo "MCMCMC"
		sed "s/THREADS//g" temp2.sl > temp.sl
		sed "s/MEMORY/4000/g" temp.sl > temp2.sl
	else
		echo "MCMC"
		sed "s/THREADS/-threads 4/g" temp2.sl > temp.sl
		sed "s/MEMORY/4000/g" temp.sl > temp2.sl
	fi


	mv temp2.sl temp.sl

	#sbatch temp.sl
	sleep 2
	rm -f temp.sl 



	
	cd ../

done	
