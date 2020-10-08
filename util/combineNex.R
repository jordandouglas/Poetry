
library(ape)



args = commandArgs(trailingOnly=TRUE)



sequences = list()
starts = numeric(0)
stops = numeric(0)
partition.names = gsub("[.].+", "", gsub(".+/", "", args))


start = 1
for (nex in args){



	nexus = read.nexus.data(nex)
	taxa = names(nexus)






	stop = start + length(nexus[[2]]) - 1 - 5

	starts = c(starts, start)
	stops = c(stops, stop)
	start = stop+1

	for (t in taxa){

		#if (t == "Gbr") next

		seq = paste(nexus[[t]], collapse = "")


		# Remove final 5 characters
		#seq = substring(seq, 1, nchar(seq)-5)

		if (length(sequences[[t]]) == 0){
			sequences[[t]] = seq
		}else{
			sequences[[t]] = paste(sequences[[t]], seq, sep = "")
		}

	}

}


for (i in names(sequences)){
	cat(paste(i, "has", nchar(sequences[[i]]), "sites\n"))
}





write.nexus.data(sequences, "alignment.nex")


# Partitions
nexus.in = readLines("alignment.nex")

partitions = "BEGIN SETS;\n\t[loci]"

for (i in 1:length(partition.names)){

	p = paste0("\tCHARSET ", partition.names[i], " = ", starts[i], "-", stops[i])
	partitions = c(partitions, p)

}
partitions = c(partitions, "END;")
partitions = paste(partitions, collapse = "\n")

nexus.in  = c(nexus.in, partitions)


# Get the correct number of sites
nexus.in = gsub("NCHAR=1", paste0("NCHAR=", stops[length(stops)]), nexus.in)


write(nexus.in, "alignment.nex")


