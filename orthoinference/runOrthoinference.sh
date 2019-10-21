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

# Small Test
#projSpecies=(atha)	# Projected species (4-char abbv); may contain multiple species, space-delimited
projSpecies=(adur)	# Projected species (4-char abbv); may contain multiple species, space-delimited

# New Compara
#projSpecies=(ccar etef mpol cann ccan ttur)
# Existing Compara
#projSpecies=(achi atau atri ahal alyr atha bvul bdis bnap bole brap crei ccri ccap csat cmer dcar drot gsul gmax grai hann hvul lper lang mesc mtru macu natt obar obra ogla oglu olon omer oniv opun oruf osai oluc phal phvh pvul ppat ptri pper smoe sita slyc stub sbic tcac tpra taes tdic tura vang vrad vvin zmay)	# Projected species (4-char abbv); may contain multiple species, space-delimited

# New Inparanoid
#projSpecies=(aoff csat csti crub clan coli hluh hlup nnuc pedu shis zjap)	# Projected species (4-char abbv); may contain multiple species, space-delimited
# Existing Inparanoid
#projSpecies=(adur aipa ccaj cari csin mgut egra fves jcur mdom oaus ogra omin ooff okas pdac pabi ptae spcc)	# Projected species (4-char abbv); may contain multiple species, space-delimited

## Run orthoinference for each species
for species in "${projSpecies[@]}"
do
	cmd="java -jar target/orthoinference-0.0.1-SNAPSHOT-jar-with-dependencies.jar $configPath $refSpecies $species > ./species_logs/orthoinference_$species.out;"
	echo $cmd
	eval $cmd
done
echo "Orthoinference complete"
