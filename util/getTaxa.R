




args = commandArgs(trailingOnly=TRUE)
aln = readLines(args[1])



mat = grep("matrix", aln)
end = grep(";", aln)
end = end[end > mat][1]

taxa = aln[(mat+1):(end-1)]
taxa = sapply(strsplit(taxa, "( |\t)"), function(ele) ele[2])
taxa = taxa[taxa != ""]
taxa = taxa[!is.na(taxa)]
taxa = sort(taxa)

cat(paste("There are", length(taxa), "taxa\n\n"))

write(paste(taxa, collapse = "\n"), "taxa.txt")



