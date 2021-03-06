package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.gk.model.GKInstance;
import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaClass;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class EWASInferrer {

	private static final Logger logger = LogManager.getLogger();
	private static MySQLAdaptor dba;
	static boolean altRefDbExists = false;
	private static String altRefDbId;
	private static GKInstance instanceEditInst;
	private static GKInstance ensgDbInst;
	private static GKInstance enspDbInst;
	private static GKInstance alternateDbInst;
	private static GKInstance uniprotDbInst;
	private static GKInstance speciesInst;
	private static Map<String, String[]> homologueMappings = new HashMap<String,String[]>();
	private static Map<String, List<String>> ensgMappings = new HashMap<>();
	private static Map<String, GKInstance> referenceGeneProductIdenticals = new HashMap<String,GKInstance>();
	private static Map<String,GKInstance> ewasIdenticals = new HashMap<String,GKInstance>();
	private static Map<String,GKInstance> residueIdenticals = new HashMap<String,GKInstance>();

	// Creates an array of inferred EWAS instances from the homologue mappings file (<reference 4-char abbv>_species_mapping.txt).
	@SuppressWarnings("unchecked")
	public static List<GKInstance> inferEWAS(GKInstance ewasInst) throws InvalidAttributeException, Exception
	{
		/*
		.get EWAS
		.what kind is EWAS it? (class: RGP/RNA/DNA)
			.look for appropriate type of homolog(s)
				.allows for id-switching template (G’s for T’s - very specific to PR, and a hack)
				.maybe also refDB switching? b/c Ensemble-Gramene: won't be in orthopair files
			.curated RGP (* current case)
				.make RefRNASeq, too, just for grins?
				.make RefDNASeq
				*.make homolog EWAS for RGP
			.curated RNA (* new case)
				.check for pre-existence (avoid dupes)
				.make RefDNASeq
				*.make homolog EWAS for curated RNA
			.curated DNA (* new case)
				.check for pre-existence (avoid dupes)
				.make projected RefDNASeq
				.make homolog EWAS for curated DNA
		*/
		List<GKInstance> infEWASInstances = new ArrayList<GKInstance>();
		if (((GKInstance) ewasInst.getAttributeValue(referenceEntity)).getAttributeValue(identifier) != null) {  // protect from NPE for incomplete EWAS
			String referenceEntityId = ((GKInstance) ewasInst.getAttributeValue(referenceEntity)).getAttributeValue(identifier).toString();
			if (homologueMappings.get(referenceEntityId) != null)
			{
				// Iterate through the array of homologue mappings, attempting to infer EWAS instances for each.
				for (String homologue : homologueMappings.get(referenceEntityId)) {

					// Handles homologues formatted as either DB:ID or just ID
					String homologueSource = homologue.contains(":") ? homologue.split(":")[0] : "";
					String homologueId = homologue.contains(":") ? homologue.split(":")[1] : homologue;

					if (checkValidSpeciesProtein(homologueId)) {
						GKInstance infReferenceGeneProductInst = null;
						if (referenceGeneProductIdenticals.get(homologueId) == null) {
							infReferenceGeneProductInst = InstanceUtilities.createNewInferredGKInstance((GKInstance) ewasInst.getAttributeValue(referenceEntity));
							infReferenceGeneProductInst.addAttributeValue(identifier, homologueId);
							// Reference DB can differ between homologue mappings, but can be differentiated by the 'homologueSource' found in each mapping.
							// With PANTHER data, the Protein IDs are exclusively UniProt
							GKInstance referenceDatabaseInst = homologueSource.isEmpty() ? uniprotDbInst : enspDbInst;
							//GKInstance referenceDatabaseInst = homologueSource.equals("ENSP") ? enspDbInst : uniprotDbInst;
							infReferenceGeneProductInst.addAttributeValue(referenceDatabase, referenceDatabaseInst);

							// Creates ReferenceDNASequence instance from ReferenceEntity
							List<GKInstance> inferredReferenceDNAInstances = createReferenceDNASequence(homologueId);
							infReferenceGeneProductInst.addAttributeValue(referenceGene, inferredReferenceDNAInstances);

							infReferenceGeneProductInst.addAttributeValue(species, speciesInst);
							String referenceGeneProductSource = homologueSource.isEmpty() ? "UniProt" : homologueSource.toUpperCase();
							//String referenceGeneProductSource = homologueSource.equals("ENSP") ? "ENSEMBL:" : "UniProt:";
							infReferenceGeneProductInst.setAttributeValue(_displayName, referenceGeneProductSource + ":" + homologueId);
							infReferenceGeneProductInst = InstanceUtilities.checkForIdenticalInstances(infReferenceGeneProductInst,null);
							referenceGeneProductIdenticals.put(homologueId, infReferenceGeneProductInst);
						} else {
							infReferenceGeneProductInst = referenceGeneProductIdenticals.get(homologueId);
						}
						// Creating inferred EWAS
						GKInstance infEWASInst = InstanceUtilities.createNewInferredGKInstance(ewasInst);
						infEWASInst.addAttributeValue(referenceEntity, infReferenceGeneProductInst);

						// Method for adding start/end coordinates. It is convoluted due to a quirk with assigning the name differently based on coordinate value (see infer_events.pl lines 1190-1192).
						// The name of the entity needs to be at the front of the 'name' array if the coordinate is over 1, and rearranging arrays in Java for this was a bit tricky.
						for (int startCoord : (Collection<Integer>) ewasInst.getAttributeValuesList(startCoordinate)) {
							infEWASInst.addAttributeValue(startCoordinate, startCoord);
						}
						for (int endCoord : (Collection<Integer>) ewasInst.getAttributeValuesList(endCoordinate)) {
							infEWASInst.addAttributeValue(endCoordinate, endCoord);
						}
						if (infEWASInst.getAttributeValue(startCoordinate) != null && (int) infEWASInst.getAttributeValue(startCoordinate) > 1 || infEWASInst.getAttributeValue(endCoordinate) != null && (int) infEWASInst.getAttributeValue(endCoordinate) > 1) {
							List<String> infEWASInstNames = (ArrayList<String>) ((GKInstance) ewasInst).getAttributeValuesList(name);
							infEWASInst.addAttributeValue(name, infEWASInstNames.get(0));
							infEWASInst.addAttributeValue(name, homologueId);
						} else {
							infEWASInst.addAttributeValue(name, homologueId);
						}
						String curCompartment = "";
						if (ewasInst.getAttributeValue(compartment) != null) {
							curCompartment = ((GKInstance) ewasInst.getAttributeValue(compartment)).getDisplayName();
						}
						String ewasDisplayName = (String) infEWASInst.getAttributeValue(name) + (curCompartment.length() > 0 ? " [" + curCompartment + "]" : "");
						infEWASInst.setAttributeValue(_displayName, ewasDisplayName);

						// Infer residue modifications. This was another step where the name of an EWAS can change.
						// For this, it is based on the existence of the string 'phospho' in the name of the psiMod attribute.
						// If true, 'phospho-' is prepended to the EWAS' name attribute.
						List<GKInstance> infModifiedResidueInstances = new ArrayList<GKInstance>();
						boolean phosFlag = true;
						for (GKInstance modifiedResidueInst : (Collection<GKInstance>) ewasInst.getAttributeValuesList(hasModifiedResidue)) {
							String infModifiedResidueDisplayName = "";
							GKInstance infModifiedResidueInst = InstanceUtilities.createNewInferredGKInstance(modifiedResidueInst);
							infModifiedResidueInst.addAttributeValue(referenceSequence, infReferenceGeneProductInst);
							infModifiedResidueDisplayName += infReferenceGeneProductInst.getDisplayName();
							for (int coordinateValue : (Collection<Integer>) modifiedResidueInst.getAttributeValuesList(coordinate)) {
								infModifiedResidueInst.addAttributeValue(coordinate, coordinateValue);
							}
							if (infModifiedResidueInst.getSchemClass().isValidAttribute(modification)) {
								for (GKInstance modifiedInst : (Collection<GKInstance>) modifiedResidueInst.getAttributeValuesList(modification)) {
									infModifiedResidueInst.addAttributeValue(modification, modifiedInst);
								}
								if (infModifiedResidueInst.getAttributeValue(modification) != null) {
									infModifiedResidueDisplayName += " " + ((GKInstance) infModifiedResidueInst.getAttributeValue(modification)).getDisplayName();
								}
							}
							// Update name depending on the presence of 'phospho' in the Psimod's name attribute
							GKInstance firstPsiModInst = (GKInstance) modifiedResidueInst.getAttributeValue(psiMod);
							if (phosFlag && firstPsiModInst.getAttributeValue(name).toString().contains("phospho")) {
								String phosphoName = "phospho-" + infEWASInst.getAttributeValue(name);
								List<GKInstance> ewasNames = (ArrayList<GKInstance>) infEWASInst.getAttributeValuesList(name);
								ewasNames.remove(0);
								infEWASInst.setAttributeValue(name, phosphoName);
								// In the Perl version, this code block modifies the 'name' attribute to include 'phosopho-', but in the process it drops the other names contained. I believe this is unintentional.
								// This would mean attributes without the 'phospho- ' addition would retain their array of names, while attributes containing 'phospho-' would only contain a single name attribute.
								// I've assumed this is incorrect for the rewrite -- Instances that modify the name attribute to prepend 'phospho-' retain their name array. (Justin Cook 2018)
								infEWASInst.addAttributeValue(name, ewasNames);
								String phosphoDisplayName = phosphoName + (curCompartment.length() > 0 ? " [" + curCompartment + "]" : "");
								infEWASInst.setAttributeValue(_displayName, phosphoDisplayName);
								// This flag ensures the 'phospho-' is only prepended once.
								phosFlag = false;
							}
							for (GKInstance psiModInst : (Collection<GKInstance>) modifiedResidueInst.getAttributeValuesList(psiMod)) {
								infModifiedResidueInst.addAttributeValue(psiMod, psiModInst);
							}
							if (infModifiedResidueInst.getAttributeValue(psiMod) != null) {
								infModifiedResidueDisplayName += " " + ((GKInstance) infModifiedResidueInst.getAttributeValue(psiMod)).getDisplayName();
							}
							infModifiedResidueInst.setAttributeValue(_displayName, modifiedResidueInst.getAttributeValue(_displayName));
							// Update name to reflect that coordinate values are taken from humans. This takes place after cache retrieval, since the name from DB won't contain updated name.
							if (modifiedResidueInst.getAttributeValue(coordinate) != null) {
								String newModifiedResidueDisplayName = modifiedResidueInst.getAttributeValue(_displayName).toString() + " (in " + EventsInferrer.refSpeciesName + ")";
								infModifiedResidueInst.setAttributeValue(_displayName, newModifiedResidueDisplayName);

							} else {
								if (infModifiedResidueInst.getSchemClass().isa(InterChainCrosslinkedResidue)) {
									infModifiedResidueInst.setDisplayName(infModifiedResidueDisplayName);
								}
							}
							// Database-checker gave errors related to missing 'secondReferenceSequence' and 'equivalentTo' attributes in InterChainCrosslinkedResidues
							// This was because they were never populated. This block is the fix.
							if (infModifiedResidueInst.getSchemClass().isa(InterChainCrosslinkedResidue)) {
								if (modifiedResidueInst.getAttributeValue(secondReferenceSequence) != null) {
									for (GKInstance secondRefSequenceInst : (Collection<GKInstance>) modifiedResidueInst.getAttributeValuesList(secondReferenceSequence)) {
										infModifiedResidueInst.addAttributeValue(secondReferenceSequence, secondRefSequenceInst);
									}
								}
								if (modifiedResidueInst.getAttributeValue("equivalentTo") != null) {
									for (GKInstance equivalentToInst : (Collection<GKInstance>) modifiedResidueInst.getAttributeValuesList("equivalentTo")) {
										infModifiedResidueInst.addAttributeValue("equivalentTo", equivalentToInst);
									}
								}
							}
							// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which slows things.
							String cacheKey = InstanceUtilities.getCacheKey((GKSchemaClass) infModifiedResidueInst.getSchemClass(), infModifiedResidueInst);
							if (residueIdenticals.get(cacheKey) != null) {
								infModifiedResidueInst = residueIdenticals.get(cacheKey);
							} else {
								infModifiedResidueInst = InstanceUtilities.checkForIdenticalInstances(infModifiedResidueInst, null);
								residueIdenticals.put(cacheKey, infModifiedResidueInst);
							}
							infModifiedResidueInstances.add((GKInstance) infModifiedResidueInst);
						}
						infEWASInst.addAttributeValue(hasModifiedResidue, infModifiedResidueInstances);
						// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which slows things.
						String cacheKey = InstanceUtilities.getCacheKey((GKSchemaClass) infEWASInst.getSchemClass(), infEWASInst);
						if (ewasIdenticals.get(cacheKey) != null) {
							infEWASInst = ewasIdenticals.get(cacheKey);
						} else {
							infEWASInst = InstanceUtilities.checkForIdenticalInstances(infEWASInst, ewasInst);
							ewasIdenticals.put(cacheKey, infEWASInst);
						}

						infEWASInst = InstanceUtilities.addAttributeValueIfNecessary(infEWASInst, ewasInst, inferredFrom);
						dba.updateInstanceAttribute(infEWASInst, inferredFrom);
						ewasInst = InstanceUtilities.addAttributeValueIfNecessary(ewasInst, infEWASInst, inferredTo);
						dba.updateInstanceAttribute(ewasInst, inferredTo);

						infEWASInstances.add((GKInstance) infEWASInst);
					}
				}
			}
		} else {
			logger.warn(ewasInst + " does not have an identifier. Skip.");
		}
		return infEWASInstances;
	}

	// Homologous Protein IDs can exist in ${source}_${target}_mapping.txt but the corresponding Gene ID might not exist in ${target}_gene_protein_mapping.txt.
	// This is different from when we built Orthopairs files using Compara, since the homology mapping file was generated using IDs from the gene-protein file.
	// This function prevents a Null Exception from killing the entire Reaction's inference, rather than just the EWAS inference.
	private static boolean checkValidSpeciesProtein(String homologueId) {
		return ensgMappings.containsKey(homologueId);
	}

	// Creates ReferenceGeneSequence instance based on ENSG identifier mapped to protein. Creates an instance for the primary database and an alternate, if it exists.
	private static List<GKInstance> createReferenceDNASequence(String homologueId) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		List<GKInstance> referenceDNAInstances = new ArrayList<GKInstance>();
		List<String> ensgIds = ensgMappings.get(homologueId);
		for (String ensgId : ensgIds)
		{
			SchemaClass referenceDNAClass = dba.getSchema().getClassByName(ReferenceDNASequence);
			GKInstance referenceDNAInst = new GKInstance(referenceDNAClass);
			referenceDNAInst.setDbAdaptor(dba);
			referenceDNAInst.addAttributeValue(created, instanceEditInst);
			referenceDNAInst.addAttributeValue(identifier, ensgId);
			referenceDNAInst.addAttributeValue(referenceDatabase, ensgDbInst);
			referenceDNAInst.addAttributeValue(species, speciesInst);
			referenceDNAInst.setAttributeValue(_displayName, ensgDbInst.getAttributeValue(name) + ":" + ensgId);
			referenceDNAInst = InstanceUtilities.checkForIdenticalInstances(referenceDNAInst,null);
			referenceDNAInstances.add(referenceDNAInst);
			if (altRefDbExists)
			{
				GKInstance alternateRefDNAInst = new GKInstance(referenceDNAClass);
				alternateRefDNAInst.setDbAdaptor(dba);
				String altDbIdentifier = (String) ensgId;
				if (altRefDbId != null)
				{
					altDbIdentifier = altDbIdentifier.replaceAll(altRefDbId, "");
				}
				alternateRefDNAInst.addAttributeValue(created, instanceEditInst);
				alternateRefDNAInst.addAttributeValue(identifier, altDbIdentifier);
				alternateRefDNAInst.addAttributeValue(referenceDatabase, alternateDbInst);
				alternateRefDNAInst.addAttributeValue(species, speciesInst);
				alternateRefDNAInst.setAttributeValue(_displayName, alternateDbInst.getAttributeValue(name) + ":" + ensgId);
				alternateRefDNAInst = InstanceUtilities.checkForIdenticalInstances(alternateRefDNAInst, null);
				referenceDNAInstances.add(alternateRefDNAInst);
			}
		}
		return referenceDNAInstances;
	}

	// These are setup functions called at the beginning of the 'inferEvent' script
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}

	public static void setInstanceEdit(GKInstance instanceEditCopy)
	{
		instanceEditInst = instanceEditCopy;
	}

	// Sets the HashMap of species-specific homologue-identifier mappings
	public static void setHomologueMappingFile(Map<String, String[]> homologueMappingsCopy) throws IOException
	{
		homologueMappings = homologueMappingsCopy;
	}

	// Read the species-specific ENSG gene-protein mappings, and create a Hashmap with the contents
	public static void readENSGMappingFile(String toSpecies, String pathToOrthopairs) throws IOException
	{
		String mappingFileName = toSpecies + "_gene_protein_mapping.txt";
		String mappingFilePath = pathToOrthopairs + mappingFileName;
		FileReader fr = new FileReader(mappingFilePath);
		BufferedReader br = new BufferedReader(fr);

		String currentLine;
		while ((currentLine = br.readLine()) != null)
		{
			String[] tabSplit = currentLine.split("\t");
			String ensgKey = tabSplit[0];
			String[] proteins = tabSplit[1].split(" ");
			for (String protein : proteins)
			{
				String proteinId = protein.contains(":") ? protein.split(":")[1] : protein;

				if (ensgMappings.get(proteinId) == null)
				{
					List<String> singleArray = new ArrayList<>();
					singleArray.add(ensgKey);
					ensgMappings.put(proteinId, singleArray);
				} else {
					ensgMappings.get(proteinId).add(ensgKey);
				}
			}
		}
		br.close();
		fr.close();
	}

	// Fetches Uniprot DB instance
	@SuppressWarnings("unchecked")
	public static void fetchAndSetUniprotDbInstance() throws Exception
	{
		Collection<GKInstance> uniprotDbInstances = (Collection<GKInstance>) dba.fetchInstanceByAttribute(ReferenceDatabase, name, "=", "UniProt");
		uniprotDbInst = uniprotDbInstances.iterator().next();
	}
/*
	// Creates instance pertaining to the species Ensembl Protein DB
	public static void createEnsemblProteinDbInstance(String toSpeciesLong, String toSpeciesReferenceDbUrl, String toSpeciesEnspAccessUrl) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		String enspSpeciesDb = "ENSEMBL_" + toSpeciesLong + "_PROTEIN";
		enspDbInst = new GKInstance(dba.getSchema().getClassByName(ReferenceDatabase));
		enspDbInst.setDbAdaptor(dba);
		enspDbInst.addAttributeValue(created, instanceEditInst);
		enspDbInst.addAttributeValue(name, "Ensembl");
		enspDbInst.addAttributeValue(name, enspSpeciesDb);
		enspDbInst.addAttributeValue(url, toSpeciesReferenceDbUrl);
		enspDbInst.addAttributeValue(accessUrl, toSpeciesEnspAccessUrl);
		enspDbInst.setAttributeValue(_displayName, "Ensembl");
		dba.storeInstance(enspDbInst);
	}

	// Creates instance pertaining to the species Ensembl Gene DB
	public static void createEnsemblGeneDBInstance(String toSpeciesLong, String toSpeciesReferenceDbUrl, String toSpeciesEnsgAccessUrl) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		String ensgSpeciesDb = "ENSEMBL_" + toSpeciesLong + "_GENE";
		ensgDbInst = new GKInstance(dba.getSchema().getClassByName(ReferenceDatabase));
		ensgDbInst.setDbAdaptor(dba);
		ensgDbInst.addAttributeValue(created, instanceEditInst);
		ensgDbInst.addAttributeValue(name, "ENSEMBL");
		ensgDbInst.addAttributeValue(name, ensgSpeciesDb);
		ensgDbInst.addAttributeValue(url, toSpeciesReferenceDbUrl);
		ensgDbInst.addAttributeValue(accessUrl, toSpeciesEnsgAccessUrl);
		ensgDbInst.setAttributeValue(_displayName, "ENSEMBL");
		dba.storeInstance(ensgDbInst);
	}
*/
	// Creates instance pertaining to the species Protein DB (could be Ensembl, could be another refDb)
	public static void createProteinDbInstance(String toSpeciesLong, JSONObject refDbJSON) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		String dbName = ((JSONArray)refDbJSON.get("dbname")).get(0).toString().toUpperCase();
		enspDbInst = new GKInstance(dba.getSchema().getClassByName(ReferenceDatabase));
		enspDbInst.setDbAdaptor(dba);
		enspDbInst.addAttributeValue(created, instanceEditInst);
		enspDbInst.addAttributeValue(name, dbName);
		if (dbName.equals("ENSEMBL")) {
			String enspSpeciesDb = "ENSEMBL_" + toSpeciesLong + "_PROTEIN";
			enspDbInst.addAttributeValue(name, enspSpeciesDb);
		}
		enspDbInst.addAttributeValue(url, refDbJSON.get("url"));
		enspDbInst.addAttributeValue(accessUrl, refDbJSON.get("access"));
		enspDbInst.setAttributeValue(_displayName, dbName);
		dba.storeInstance(enspDbInst);
	}

	// Creates instance pertaining to the species Gene DB (could be Ensembl, could be another refDb)
	public static void createGeneDBInstance(String toSpeciesLong, JSONObject refDbJSON) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		String dbName = ((JSONArray)refDbJSON.get("dbname")).get(0).toString().toUpperCase();
		ensgDbInst = new GKInstance(dba.getSchema().getClassByName(ReferenceDatabase));
		ensgDbInst.setDbAdaptor(dba);
		ensgDbInst.addAttributeValue(created, instanceEditInst);
		ensgDbInst.addAttributeValue(name, dbName);
		if (dbName.equals("ENSEMBL")) {
			String ensgSpeciesDb = "ENSEMBL_" + toSpeciesLong + "_GENE";
			ensgDbInst.addAttributeValue(name, ensgSpeciesDb);
		}
		ensgDbInst.addAttributeValue(url, refDbJSON.get("url"));
		ensgDbInst.addAttributeValue(accessUrl, refDbJSON.get("ensg_access"));
		ensgDbInst.setAttributeValue(_displayName, dbName);
		dba.storeInstance(ensgDbInst);
	}

	// Create instance pertaining to any alternative reference DB for the species
	public static void createAlternateReferenceDBInstance(String toSpeciesLong, JSONObject altRefDbJSON) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		alternateDbInst = new GKInstance(dba.getSchema().getClassByName(ReferenceDatabase));
		alternateDbInst.setDbAdaptor(dba);
		alternateDbInst.addAttributeValue(created, instanceEditInst);
		alternateDbInst.addAttributeValue(name, ((JSONArray) altRefDbJSON.get("dbname")).get(0));
		alternateDbInst.addAttributeValue(url, altRefDbJSON.get("url"));
		alternateDbInst.addAttributeValue(accessUrl, altRefDbJSON.get("access"));
		alternateDbInst.setAttributeValue(_displayName, ((JSONArray) altRefDbJSON.get("dbname")).get(0));
		alternateDbInst = InstanceUtilities.checkForIdenticalInstances(alternateDbInst, null);
		if (altRefDbJSON.get("alt_id") != null)
		{
			altRefDbId = (String) altRefDbJSON.get("alt_id");
		}
		altRefDbExists = true;
	}

	public static void setAltRefDbToFalse()
	{
		altRefDbExists = false;
	}

	// Sets the species instance for inferEWAS to use
	public static void setSpeciesInstance(GKInstance speciesInstCopy)
	{
		speciesInst = speciesInstCopy;
	}

	public static void resetVariables()
	{
		homologueMappings = new HashMap<>();
		ensgMappings = new HashMap<>();
		referenceGeneProductIdenticals = new HashMap<>();
		ewasIdenticals = new HashMap<>();
		residueIdenticals = new HashMap<>();
	}
}
