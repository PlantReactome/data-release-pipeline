#!/bin/bash
DIR=$(dirname "$(readlink -f "$0")") # Directory of the script -- allows the script to invoked from anywhere
cd $DIR

## Update repo
#git pull
## Create new jar file with orthoinference code
#mvn clean compile assembly:single

## set args - all required
configPath="src/main/resources/config.properties"	# Path to main configuration file
refSpecies="osat"	# Reference species (4-char abbv)

#=======
# Projected species (4-char abbv); may contain multiple species, space-delimited
# New Compara
projSpecies=(coli mgut egra fves hluh hlup jcur nnuc shis aoff)
#=======

## Run orthoinference for each species
for species in "${projSpecies[@]}"
do
	cmd="java -jar target/orthoinference-0.0.1-SNAPSHOT-jar-with-dependencies.jar $configPath $refSpecies $species > ./species_logs/orthoinference_$species.out;"
	echo $cmd
	eval $cmd
done
echo "Orthoinference complete"

mysqldump --defaults-file=.my_ortho.cnf -h localhost test_slice_oryza_sativa_20_ortho_osat_all_no_pdul > ./mysql_ortho_dumps/without_pdul/test_slice_oryza_sativa_20_ortho_osat_all_no_pdul_10.sql
cp ./mysql_ortho_dumps/without_pdul/test_slice_oryza_sativa_20_ortho_osat_all_no_pdul_10.sql ~/Documents/projects/plant_reactome/releases/r63/mysql_db/without_pdulcis/
echo "Run 10: Inparanoid projections complete (without P.dulcis)"
